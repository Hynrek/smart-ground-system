package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Request zum Erstellen eines Spielers in einer Session.
 */
@NullMarked
public class SessionPlayerCreateRequest {
    @JsonProperty("type")
    public String type; // 'USER' oder 'GUEST'

    @JsonProperty("displayName")
    public String displayName;

    @JsonProperty("userId")
    @Nullable
    public UUID userId; // null für Gäste

    public SessionPlayerCreateRequest() {
    }

    public SessionPlayerCreateRequest(String type, String displayName) {
        this.type = type;
        this.displayName = displayName;
    }
}
