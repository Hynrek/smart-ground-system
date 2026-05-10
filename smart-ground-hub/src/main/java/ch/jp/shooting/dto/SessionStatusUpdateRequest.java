package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;

/**
 * Request zum Aktualisieren des Session-Status.
 */
@NullMarked
public class SessionStatusUpdateRequest {
    @JsonProperty("status")
    public String status; // 'active', 'paused', 'completed', 'abandoned'

    public SessionStatusUpdateRequest() {
    }

    public SessionStatusUpdateRequest(String status) {
        this.status = status;
    }
}
