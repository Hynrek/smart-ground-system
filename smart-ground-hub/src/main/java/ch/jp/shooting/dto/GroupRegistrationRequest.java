package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/**
 * Request zum Registrieren einer Gruppe an einem Bereich mit Segment.
 */
@NullMarked
public class GroupRegistrationRequest {
    @JsonProperty("rangeId")
    public UUID rangeId;

    @JsonProperty("segmentId")
    public UUID segmentId;

    public GroupRegistrationRequest() {
    }

    public GroupRegistrationRequest(UUID rangeId, UUID segmentId) {
        this.rangeId = rangeId;
        this.segmentId = segmentId;
    }
}
