package ch.jp.shooting.nodechannel;

import ch.jp.smartground.nodechannel.NodeChannelMessage;
import ch.jp.smartground.nodechannel.NodeChannelTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifiziert die Jackson-2 <-> Jackson-3 Wire-Kompatibilität des node-channel-Envelopes end-to-end.
 * `NodeChannelMessage` liegt in `contracts` genau deshalb, damit Hub (Jackson 2, com.fasterxml.jackson)
 * und Node (Jackson 3, tools.jackson) nicht auseinanderdriften können — aber bisher testete keine
 * bestehende Testklasse das tatsächlich: die Hub-Integrationstests serialisieren/deserialisieren beide
 * mit dem Hub-Codec (Jackson 2), die Node-Tests analog nur mit Jackson 3. Dieser Test serialisiert mit
 * einem echten Jackson-2-Mapper und deserialisiert mit einem echten Jackson-3-Mapper (und umgekehrt) —
 * beide Major-Versionen liegen bereits transitiv auf dem Hub-Klassenpfad (Jackson 2 über `contracts`,
 * Jackson 3 über `spring-boot-starter-web`/`spring-boot-jackson`), es musste keine neue Abhängigkeit
 * hinzugefügt werden. Beide Richtungen werden abgedeckt.
 */
class NodeChannelCrossCodecTest {

    // Bewusst unabhängig von NodeChannelCodec (Produktionscode) instanziiert, damit dieser Test die
    // Wire-Kompatibilität der beiden Jackson-Major-Versionen prüft, nicht bloss den Hub-eigenen Codec.
    private final com.fasterxml.jackson.databind.ObjectMapper jackson2 =
            new com.fasterxml.jackson.databind.ObjectMapper();
    private final tools.jackson.databind.ObjectMapper jackson3 =
            tools.jackson.databind.json.JsonMapper.builder().build();

    static Stream<NodeChannelMessage> messages() {
        return Stream.of(
                NodeChannelMessage.hello("node-1", "secret"),
                NodeChannelMessage.helloAck(),
                NodeChannelMessage.heartbeat("node-1"),
                NodeChannelMessage.command(UUID.randomUUID(), "FIRE", "{\"duration\":100}"),
                NodeChannelMessage.commandAck(UUID.randomUUID(), NodeChannelTypes.OUTCOME_OK)
        );
    }

    @ParameterizedTest
    @MethodSource("messages")
    void jackson2Serialize_jackson3Deserialize_roundTrips(NodeChannelMessage original) throws Exception {
        String json = jackson2.writeValueAsString(original);
        NodeChannelMessage roundTripped = jackson3.readValue(json, NodeChannelMessage.class);

        assertMatches(original, roundTripped);
    }

    @ParameterizedTest
    @MethodSource("messages")
    void jackson3Serialize_jackson2Deserialize_roundTrips(NodeChannelMessage original) throws Exception {
        String json = jackson3.writeValueAsString(original);
        NodeChannelMessage roundTripped = jackson2.readValue(json, NodeChannelMessage.class);

        assertMatches(original, roundTripped);
    }

    @Test
    void jackson2_ignoresUnknownProperty_forwardCompatWithFutureEnvelopeFields() throws Exception {
        // Simuliert ein künftig auf einer Seite ergänztes Feld (das Envelope ist versioniert/erweiterbar,
        // siehe node-channel #4 Doku) — die andere Seite darf daran nicht mit einer
        // Deserialisierungs-Exception scheitern. Jackson 3 tut das bereits standardmässig nicht; dieser
        // Test belegt, dass der Hub-eigene Jackson-2-Mapper (NodeChannelCodec) dasselbe Verhalten hat.
        String jsonWithExtraField = "{\"v\":1,\"type\":\"HEARTBEAT\",\"nodeId\":\"node-1\",\"futureField\":\"x\"}";

        NodeChannelMessage parsed = new NodeChannelCodec().fromJson(jsonWithExtraField);

        assertThat(parsed.type()).isEqualTo(NodeChannelTypes.TYPE_HEARTBEAT);
        assertThat(parsed.nodeId()).isEqualTo("node-1");
    }

    private void assertMatches(NodeChannelMessage original, NodeChannelMessage roundTripped) {
        assertThat(roundTripped.v()).isEqualTo(original.v());
        assertThat(roundTripped.type()).isEqualTo(original.type());
        assertThat(roundTripped.nodeId()).isEqualTo(original.nodeId());
        assertThat(roundTripped.token()).isEqualTo(original.token());
        assertThat(roundTripped.commandId()).isEqualTo(original.commandId());
        assertThat(roundTripped.commandType()).isEqualTo(original.commandType());
        assertThat(roundTripped.payloadJson()).isEqualTo(original.payloadJson());
        assertThat(roundTripped.outcome()).isEqualTo(original.outcome());
    }
}
