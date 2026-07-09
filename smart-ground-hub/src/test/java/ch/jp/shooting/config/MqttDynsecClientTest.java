package ch.jp.shooting.config;

import ch.jp.shooting.exception.MqttDynsecException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MqttDynsecClientTest {

    @Mock MessageChannel mqttOutboundChannel;

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<JsonNode> sentCommands = new ArrayList<>();

    /**
     * Verdrahtet den gemockten Outbound-Channel so, dass er synchron die passende
     * dynsec-Antwort in den Client zurückspielt (der echte Broker publiziert auf das feste
     * Response-Topic; hier ruft der Answer direkt handleResponse auf). {@code responder}
     * liefert pro Kommando-Name den Response-JSON.
     */
    private MqttDynsecClient clientRespondingWith(Function<String, String> responder) {
        MqttDynsecClient client = new MqttDynsecClient(mqttOutboundChannel, mapper, 2000);
        when(mqttOutboundChannel.send(any())).thenAnswer(inv -> {
            Message<?> msg = inv.getArgument(0);
            assertThat(msg.getHeaders().get("mqtt_topic")).isEqualTo(MqttDynsecClient.CONTROL_TOPIC);
            JsonNode envelope = mapper.readTree((String) msg.getPayload());
            JsonNode command = envelope.path("commands").path(0);
            sentCommands.add(command);
            String cmd = command.path("command").asText();
            String responseJson = responder.apply(cmd);
            client.handleResponse(MessageBuilder.withPayload(responseJson.getBytes()).build());
            return true;
        });
        return client;
    }

    private static String ok(String command) {
        return "{\"responses\":[{\"command\":\"" + command + "\"}]}";
    }

    private static String err(String command, String error) {
        return "{\"responses\":[{\"command\":\"" + command + "\",\"error\":\"" + error + "\"}]}";
    }

    @Test
    void createSmartboxClientSendsInlinePasswordAndRole() {
        MqttDynsecClient client = clientRespondingWith(MqttDynsecClientTest::ok);

        client.createSmartboxClient("AA:BB:CC:DD:EE:FF", "secretpw");

        assertThat(sentCommands).hasSize(1);
        JsonNode cmd = sentCommands.get(0);
        assertThat(cmd.path("command").asText()).isEqualTo("createClient");
        assertThat(cmd.path("username").asText()).isEqualTo("AA:BB:CC:DD:EE:FF");
        assertThat(cmd.path("password").asText()).isEqualTo("secretpw");
        JsonNode role = cmd.path("roles").path(0);
        assertThat(role.path("rolename").asText()).isEqualTo("smartbox");
        assertThat(role.path("priority").asInt()).isEqualTo(10);
    }

    @Test
    void createSmartboxClientRecoversFromAlreadyExistsBySettingPassword() {
        // createClient -> "Client already exists"; danach muss setClientPassword folgen.
        MqttDynsecClient client = clientRespondingWith(cmd ->
            cmd.equals("createClient") ? err("createClient", "Client already exists") : ok(cmd));

        client.createSmartboxClient("AA:BB:CC:DD:EE:FF", "freshpw");

        assertThat(sentCommands).hasSize(2);
        assertThat(sentCommands.get(0).path("command").asText()).isEqualTo("createClient");
        assertThat(sentCommands.get(1).path("command").asText()).isEqualTo("setClientPassword");
        assertThat(sentCommands.get(1).path("password").asText()).isEqualTo("freshpw");
    }

    @Test
    void createSmartboxClientPropagatesOtherErrors() {
        MqttDynsecClient client = clientRespondingWith(cmd -> err("createClient", "Role not found"));

        assertThatThrownBy(() -> client.createSmartboxClient("AA:BB:CC:DD:EE:FF", "pw"))
            .isInstanceOf(MqttDynsecException.class)
            .hasMessageContaining("Role not found");
        // Kein Selbstheilungs-Setpassword bei unbekanntem Fehler.
        assertThat(sentCommands).hasSize(1);
    }

    @Test
    void deleteSmartboxClientSendsDeleteCommand() {
        MqttDynsecClient client = clientRespondingWith(MqttDynsecClientTest::ok);

        client.deleteSmartboxClient("AA:BB:CC:DD:EE:FF");

        assertThat(sentCommands).hasSize(1);
        assertThat(sentCommands.get(0).path("command").asText()).isEqualTo("deleteClient");
        assertThat(sentCommands.get(0).path("username").asText()).isEqualTo("AA:BB:CC:DD:EE:FF");
    }

    @Test
    void deleteSmartboxClientIsIdempotentOnClientNotFound() {
        MqttDynsecClient client = clientRespondingWith(cmd -> err("deleteClient", "Client not found"));
        // Darf NICHT werfen – bereits gelöscht ist ein akzeptabler Endzustand.
        client.deleteSmartboxClient("AA:BB:CC:DD:EE:FF");
        assertThat(sentCommands).hasSize(1);
    }

    @Test
    void timesOutWhenNoResponseArrives() {
        // Channel akzeptiert, spielt aber KEINE Antwort zurück -> Timeout.
        MqttDynsecClient client = new MqttDynsecClient(mqttOutboundChannel, mapper, 150);
        when(mqttOutboundChannel.send(any())).thenReturn(true);

        assertThatThrownBy(() -> client.createSmartboxClient("AA:BB:CC:DD:EE:FF", "pw"))
            .isInstanceOf(MqttDynsecException.class)
            .hasMessageContaining("Timeout");
    }
}
