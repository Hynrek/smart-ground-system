package ch.jp.shooting.config;

import ch.jp.shooting.exception.MqttDynsecException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Dünner Client für die JSON-über-MQTT-Kontroll-API des Mosquitto-Dynamic-Security-Plugins
 * ({@code $CONTROL/dynamic-security/v1}). Legt pro SmartBox einen Broker-Login an bzw.
 * löscht ihn wieder. Diese Klasse kennt AUSSCHLIESSLICH das Draht-Protokoll – die
 * SmartBox-Domänenlogik (MAC-Validierung, Passwort-Erzeugung, Persistenz) liegt in
 * {@link SmartBoxCredentialService}.
 *
 * <h2>Live gegen mosquitto 2.1.2 verifiziertes Draht-Protokoll</h2>
 * <ul>
 *   <li><b>Request-Topic:</b> {@code $CONTROL/dynamic-security/v1}, Payload
 *       {@code {"commands":[ {command...} ]}}.</li>
 *   <li><b>Response-Topic:</b> <b>fest</b> {@code $CONTROL/dynamic-security/v1/response}
 *       (die MQTTv5-{@code ResponseTopic}-Property wird vom Plugin IGNORIERT, die
 *       {@code CorrelationData} wird NICHT zurückgespiegelt). Deshalb keine
 *       MQTTv5-Request/Response-Mechanik nötig – wir abonnieren schlicht dieses feste
 *       Topic (über den vorhandenen Inbound-Adapter, siehe {@link MqttConfig}).</li>
 *   <li><b>Response-Payload:</b> {@code {"responses":[ {"command":"<name>"[,"error":"..."]} ]}}.
 *       Erfolg = Objekt ohne {@code error}-Feld; Fehler = {@code error}-String.</li>
 *   <li><b>Korrelation:</b> Da der Broker Antworten weder pro Client noch pro Request
 *       zuordnet, wird pro Exchange serialisiert (nur EIN Request gleichzeitig „in flight“,
 *       via {@link #lock}). Bei der geringen Frequenz (Box-Registrierung/-Löschung) ist das
 *       ausreichend und die einfachste sichere Lösung.</li>
 *   <li>{@code createClient} mit inline {@code password}+{@code roles} ist atomar (bei
 *       ungültiger Rolle wird der Client NICHT angelegt) – ein einziger Round-Trip.</li>
 *   <li>{@code deleteClient} trennt eine aktive Session automatisch mit (live verifiziert:
 *       der verbundene Client wird sofort mit „Not authorized“ getrennt). Ein separates
 *       {@code disconnectClient} existiert in dynsec 2.1.2 NICHT (liefert „Unknown command“).</li>
 * </ul>
 */
@Component
@NullMarked
public class MqttDynsecClient {

    private static final Logger log = LoggerFactory.getLogger(MqttDynsecClient.class);

    static final String CONTROL_TOPIC  = "$CONTROL/dynamic-security/v1";
    static final String RESPONSE_TOPIC = CONTROL_TOPIC + "/response";

    // Die von Task A (dynsec-init.sh) angelegte Rolle für SmartBoxen.
    static final String SMARTBOX_ROLE      = "smartbox";
    static final int    SMARTBOX_PRIORITY  = 10;

    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper;
    private final long responseTimeoutMs;

    // Serialisiert Request/Response: nur ein ausstehender Request gleichzeitig.
    private final ReentrantLock lock = new ReentrantLock();
    private volatile @Nullable CompletableFuture<JsonNode> pending;

    public MqttDynsecClient(
            @Qualifier("mqttOutboundChannel") MessageChannel mqttOutboundChannel,
            ObjectMapper objectMapper,
            @Value("${mqtt.dynsec.response-timeout-ms:5000}") long responseTimeoutMs) {
        this.mqttOutboundChannel = mqttOutboundChannel;
        this.objectMapper = objectMapper;
        this.responseTimeoutMs = responseTimeoutMs;
    }

    /**
     * Legt einen Broker-Login für eine SmartBox an: Client + Passwort + Rolle {@code smartbox}
     * in einem Round-Trip. Existiert der Client bereits (DB/Broker-Desync aus einem früheren
     * Teilfehler), wird stattdessen nur das Passwort neu gesetzt, damit das soeben erzeugte,
     * gleich per Config-Push ausgelieferte Passwort auch am Broker gilt (Selbstheilung).
     *
     * @throws MqttDynsecException bei jedem anderen Broker-Fehler oder Timeout.
     */
    public void createSmartboxClient(String username, String password) {
        ObjectNode createClient = objectMapper.createObjectNode();
        createClient.put("command", "createClient");
        createClient.put("username", username);
        createClient.put("password", password);
        ArrayNode roles = createClient.putArray("roles");
        ObjectNode role = roles.addObject();
        role.put("rolename", SMARTBOX_ROLE);
        role.put("priority", SMARTBOX_PRIORITY);

        try {
            sendCommand("createClient", createClient);
            log.info("Dynsec-Client für SmartBox {} angelegt (Rolle {}).", username, SMARTBOX_ROLE);
        } catch (MqttDynsecException e) {
            if (e.isClientAlreadyExists()) {
                // Desync: Client existiert am Broker, aber nicht in unserer DB. Wir setzen das
                // frische Passwort, damit die Box mit dem gleich gepushten Passwort verbinden kann.
                log.warn("Dynsec-Client {} existierte bereits (DB/Broker-Desync) – setze Passwort neu.", username);
                ObjectNode setPw = objectMapper.createObjectNode();
                setPw.put("command", "setClientPassword");
                setPw.put("username", username);
                setPw.put("password", password);
                sendCommand("setClientPassword", setPw);
            } else {
                throw e;
            }
        }
    }

    /**
     * Löscht den Broker-Login einer SmartBox. Trennt eine ggf. aktive Session automatisch mit
     * (dynsec-{@code deleteClient} disconnected den Client – live verifiziert). Idempotent:
     * ein bereits gelöschter Client ({@code "Client not found"}) wird als Erfolg behandelt.
     *
     * @throws MqttDynsecException bei jedem anderen Broker-Fehler oder Timeout.
     */
    public void deleteSmartboxClient(String username) {
        ObjectNode deleteClient = objectMapper.createObjectNode();
        deleteClient.put("command", "deleteClient");
        deleteClient.put("username", username);
        try {
            sendCommand("deleteClient", deleteClient);
            log.info("Dynsec-Client für SmartBox {} gelöscht (aktive Session getrennt).", username);
        } catch (MqttDynsecException e) {
            if (e.isClientNotFound()) {
                log.warn("Dynsec-Client {} war bereits gelöscht – Revoke idempotent übersprungen.", username);
                return;
            }
            throw e;
        }
    }

    /**
     * Callback des {@link SmartBoxMqttRouter} für jede Nachricht auf {@link #RESPONSE_TOPIC}.
     * Vervollständigt den aktuell ausstehenden Request (falls vorhanden).
     */
    public void handleResponse(Message<?> message) {
        CompletableFuture<JsonNode> future = pending;
        if (future == null) {
            // Späte Antwort eines bereits abgelaufenen Requests o.ä. – ignorieren.
            log.debug("Dynsec-Response ohne ausstehenden Request empfangen, ignoriert.");
            return;
        }
        try {
            String json = payloadToString(message.getPayload());
            future.complete(objectMapper.readTree(json));
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
    }

    /**
     * Publiziert genau ein Kommando (im {@code {"commands":[...]}}-Envelope) und wartet auf die
     * korrespondierende Antwort. Serialisiert über {@link #lock}, sodass immer nur ein Request
     * gleichzeitig unterwegs ist.
     */
    private void sendCommand(String expectedCommand, ObjectNode command) {
        lock.lock();
        try {
            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            pending = future;

            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.putArray("commands").add(command);
            String payload = objectMapper.writeValueAsString(envelope);

            mqttOutboundChannel.send(
                MessageBuilder.withPayload(payload)
                    .setHeader("mqtt_topic", CONTROL_TOPIC)
                    .setHeader("mqtt_qos", 1)
                    .build()
            );

            JsonNode response = future.get(responseTimeoutMs, TimeUnit.MILLISECONDS);
            checkResponse(expectedCommand, response);
        } catch (TimeoutException e) {
            throw new MqttDynsecException(
                "Timeout (" + responseTimeoutMs + " ms) beim Warten auf dynsec-Antwort für Kommando '"
                + expectedCommand + "' – Broker erreichbar?", null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MqttDynsecException("Unterbrochen beim Warten auf dynsec-Antwort für '" + expectedCommand + "'", null, e);
        } catch (ExecutionException e) {
            throw new MqttDynsecException("Antwort für dynsec-Kommando '" + expectedCommand + "' konnte nicht gelesen werden",
                null, e.getCause());
        } catch (MqttDynsecException e) {
            throw e;
        } catch (Exception e) {
            throw new MqttDynsecException("Fehler beim Senden des dynsec-Kommandos '" + expectedCommand + "'", null, e);
        } finally {
            pending = null;
            lock.unlock();
        }
    }

    /** Prüft die {@code responses[0]} auf ein {@code error}-Feld und wirft ggf. eine {@link MqttDynsecException}. */
    private void checkResponse(String expectedCommand, JsonNode response) {
        JsonNode responses = response.path("responses");
        if (!responses.isArray() || responses.isEmpty()) {
            throw new MqttDynsecException("Unerwartete dynsec-Antwort (keine 'responses'): " + response, null);
        }
        JsonNode first = responses.get(0);
        JsonNode error = first.get("error");
        if (error != null && !error.isNull()) {
            String errorText = error.asText();
            throw new MqttDynsecException(
                "dynsec-Kommando '" + expectedCommand + "' abgelehnt: " + errorText, errorText);
        }
    }

    private String payloadToString(Object payload) {
        return switch (payload) {
            case byte[] b -> new String(b, StandardCharsets.UTF_8);
            case String s -> s;
            default       -> String.valueOf(payload);
        };
    }
}
