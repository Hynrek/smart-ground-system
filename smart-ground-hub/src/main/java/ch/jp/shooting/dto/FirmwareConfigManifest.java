package ch.jp.shooting.dto;

import ch.jp.shooting.model.CommunicationDirection;
import ch.jp.shooting.model.DeviceKind;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FirmwareConfigManifest(
    String version,
    @JsonProperty("box_type")
    String boxType,
    @JsonProperty("signal_types")
    List<SignalTypeEntry> signalTypes
) {

    public record SignalTypeEntry(
        CommunicationDirection direction,
        DeviceKind device,
        String command,
        @JsonProperty("group_name")
        String groupName,
        String name,
        @JsonProperty("signal_duration_ms")
        int signalDurationMs,
        @JsonProperty("delay_signal_duration_ms")
        Integer delaySignalDurationMs
    ) {}
}
