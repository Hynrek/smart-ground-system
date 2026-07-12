package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class PatchMemberRequest {
    @JsonProperty("paid")
    public boolean paid;

    public PatchMemberRequest() {}
}
