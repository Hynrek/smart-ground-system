package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Zuordnung: Bereich → Segmente
 */
@NullMarked
public class RangeSegmentEntry {
    @JsonProperty("rangeId")
    public UUID rangeId;

    @JsonProperty("segmentIds")
    public List<UUID> segmentIds = new ArrayList<>();

    public RangeSegmentEntry() {
    }

    public RangeSegmentEntry(UUID rangeId) {
        this.rangeId = rangeId;
    }
}
