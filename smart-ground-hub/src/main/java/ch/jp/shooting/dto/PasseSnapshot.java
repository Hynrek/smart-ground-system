package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot der Passen-Struktur beim Wettkampf-Start.
 * Gespeichert in LiveSession.programSnapshots als JSON-Array.
 */
@NullMarked
public class PasseSnapshot {
    @JsonProperty("id")
    public String id;       // Passe-UUID als String

    @JsonProperty("name")
    @Nullable
    public String name;

    @JsonProperty("serieIds")
    public List<String> serieIds = new ArrayList<>(); // geordnete Serie-UUIDs

    public PasseSnapshot() {}
}
