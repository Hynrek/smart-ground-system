package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Request zum Erstellen einer Gruppe mit Spielern.
 */
@NullMarked
public class GroupCreateRequest {
    @JsonProperty("name")
    public String name;

    @JsonProperty("members")
    public List<SessionPlayerCreateRequest> members = new ArrayList<>();

    public GroupCreateRequest() {
    }

    public GroupCreateRequest(String name) {
        this.name = name;
    }
}
