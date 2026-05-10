package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ergebnisse für ein Programm innerhalb der Spielersession.
 */
@NullMarked
public class ProgramResult {
    @JsonProperty("programId")
    public UUID programId;

    @JsonProperty("segmentResults")
    public List<SegmentResult> segmentResults = new ArrayList<>();

    public ProgramResult() {
    }

    public ProgramResult(UUID programId) {
        this.programId = programId;
    }

    public List<SegmentResult> getSegmentResults() {
        return segmentResults;
    }
}
