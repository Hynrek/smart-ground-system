package ch.jp.shooting.config;

import ch.jp.shooting.exception.MqttDynsecException;
import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.repository.SmartBoxRepository;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * SmartBox-Domänenlogik rund um den Broker-Login-Lebenszyklus einer Box: MAC-Validierung,
 * Erzeugung eines Zufallspassworts und Persistenz des {@code mqttUsername}. Das reine
 * dynsec-Draht-Protokoll liegt getrennt in {@link MqttDynsecClient}.
 *
 * <p><b>Das erzeugte Passwort wird NIE persistiert</b> (keine DB-Spalte, kein Logging des
 * Klartexts). Es wird genau einmal an den Aufrufer zurückgegeben, der es beim nächsten
 * Config-Push an die Box ausliefert (siehe {@link SmartBoxConfigPushService}).
 */
@Service
@NullMarked
public class SmartBoxCredentialService {

    private static final Logger log = LoggerFactory.getLogger(SmartBoxCredentialService.class);

    /**
     * Strikte MAC-Adress-Validierung (Doppelpunkt-getrennte Hex-Paare). SICHERHEITSKRITISCH:
     * Der dynsec-Username wird via {@code smartboxes/%u/#}-ACL (siehe dynsec-init.sh) in die
     * Topic-Isolation eingesetzt. Ein Username mit MQTT-Wildcards ({@code +}/{@code #}) würde
     * diese Isolation aushebeln – daher werden ausschließlich saubere MAC-Adressen zugelassen.
     */
    static final Pattern MAC_PATTERN = Pattern.compile("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$");

    private final MqttDynsecClient dynsecClient;
    private final SmartBoxRepository smartBoxRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public SmartBoxCredentialService(MqttDynsecClient dynsecClient, SmartBoxRepository smartBoxRepository) {
        this.dynsecClient = dynsecClient;
        this.smartBoxRepository = smartBoxRepository;
    }

    /**
     * Legt für eine neue Box einen Broker-Login an (Username = MAC, Zufallspasswort, Rolle
     * {@code smartbox}), setzt {@code box.mqttUsername} und speichert die Box.
     *
     * @return das erzeugte Klartext-Passwort (NUR zur einmaligen Auslieferung per Config-Push).
     * @throws IllegalArgumentException wenn die MAC-Adresse nicht der strikten MAC-Form entspricht.
     * @throws MqttDynsecException      wenn der Broker den Login nicht anlegen kann.
     */
    public String provisionCredentials(SmartBox box) {
        String mac = box.getMacAddress();
        if (!MAC_PATTERN.matcher(mac).matches()) {
            throw new IllegalArgumentException(
                "Ungültige MAC-Adresse für dynsec-Provisionierung (erwartet AA:BB:CC:DD:EE:FF): " + mac);
        }

        String password = generatePassword();
        // Erst am Broker anlegen; erst bei Erfolg mqttUsername setzen/speichern, damit ein
        // Broker-Fehler die Box im Zustand "noch nicht provisioniert" belässt (Retry bei
        // nächster Discovery).
        dynsecClient.createSmartboxClient(mac, password);
        box.setMqttUsername(mac);
        smartBoxRepository.save(box);
        log.info("SmartBox {} provisioniert (Broker-Login angelegt).", mac);
        return password;
    }

    /**
     * Widerruft den Broker-Login einer Box (löscht den dynsec-Client, trennt aktive Sessions)
     * und leert {@code box.mqttUsername}. Schlägt der Broker-Aufruf fehl, wird der Fehler laut
     * geloggt UND weitergeworfen (kein stiller Fallback), damit DB und Broker nicht unbemerkt
     * auseinanderlaufen (revozierte Box mit noch gültigem Login).
     *
     * @throws MqttDynsecException wenn der Broker-Aufruf fehlschlägt.
     */
    public void revokeCredentials(SmartBox box) {
        String username = box.getMqttUsername();
        if (username == null) {
            // Box hatte nie einen Broker-Login (z.B. nie erfolgreich provisioniert) – nichts zu tun.
            return;
        }
        try {
            dynsecClient.deleteSmartboxClient(username);
        } catch (MqttDynsecException e) {
            log.error("Broker-Revoke für SmartBox {} fehlgeschlagen – DB/Broker möglicherweise nicht synchron: {}",
                username, e.getMessage());
            throw e;
        }
        box.setMqttUsername(null);
        smartBoxRepository.save(box);
        log.info("SmartBox {} Broker-Login widerrufen.", username);
    }

    /** Kryptographisch sicheres Zufallspasswort (24 Byte, URL-safe Base64 ohne Padding). */
    private String generatePassword() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
