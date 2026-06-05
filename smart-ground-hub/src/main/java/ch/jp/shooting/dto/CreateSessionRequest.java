package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Request zum Erstellen einer neuen Spielsession.
 */
@NullMarked
public class CreateSessionRequest {
    @JsonProperty("type")
    public String type; // 'competition', 'training', 'free'

    @JsonProperty("templateId")
    @Nullable
    public UUID templateId; // Optional: Template als Basis verwenden

    @JsonProperty("programs")
    @Nullable
    public List<Object> programs; // Program[] (Frontend-Format)

    @JsonProperty("rangeSegmentMap")
    @Nullable
    public List<RangeSegmentEntry> rangeSegmentMap; // Bereichs-Segment-Zuordnung

    @JsonProperty("groups")
    @Nullable
    public List<GroupCreateRequest> groups; // Gruppen mit Spielern

    @JsonProperty("name")
    @Nullable
    public String name;

    @JsonProperty("passen")
    @Nullable
    public List<PasseSnapshot> passen;

    public CreateSessionRequest() {
    }
}
