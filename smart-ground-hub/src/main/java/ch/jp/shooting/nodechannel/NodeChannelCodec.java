package ch.jp.shooting.nodechannel;

import ch.jp.smartground.nodechannel.NodeChannelMessage;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

/** (De)serialisiert das node-channel-Envelope mit einem dedizierten Jackson-2-Mapper (nicht dem HTTP-Konverter). */
@Component
@NullMarked
public class NodeChannelCodec {

    private final ObjectMapper mapper = new ObjectMapper()
            // Vorwärtskompatibel mit dem Node: dessen Jackson-3-Mapper (tools.jackson) lehnt unbekannte
            // Felder standardmässig nicht ab. Ein künftig auf einer Seite ergänztes Envelope-Feld (das
            // Protokoll ist versioniert/erweiterbar) soll die andere Seite nicht mit einer
            // Deserialisierungs-Exception brechen.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public String toJson(NodeChannelMessage message) {
        try {
            return mapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new IllegalStateException("node-channel: Serialisierung fehlgeschlagen", e);
        }
    }

    public NodeChannelMessage fromJson(String json) {
        try {
            return mapper.readValue(json, NodeChannelMessage.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("node-channel: ungültiges Envelope", e);
        }
    }
}
