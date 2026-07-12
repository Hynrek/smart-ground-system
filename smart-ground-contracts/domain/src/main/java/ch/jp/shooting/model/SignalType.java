package ch.jp.shooting.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

@NullMarked
@Entity
@Table(name = "signal_types",
    uniqueConstraints = @UniqueConstraint(columnNames = {"firmware_config_id", "device", "command"}))
public class SignalType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Nullable
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "firmware_config_id", nullable = false)
    private FirmwareConfig firmwareConfig;

    @Enumerated(EnumType.STRING)
    @Column(name = "communication_direction", nullable = false)
    private CommunicationDirection communicationDirection;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceKind device;

    @Column(nullable = false)
    @NotBlank
    private String command = "";

    public SignalType() {
    }

    public SignalType(FirmwareConfig firmwareConfig, CommunicationDirection communicationDirection,
                      DeviceKind device, String command) {
        this.firmwareConfig = firmwareConfig;
        this.communicationDirection = communicationDirection;
        this.device = device;
        this.command = command;
    }

    public @Nullable UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public FirmwareConfig getFirmwareConfig() {
        return firmwareConfig;
    }

    public void setFirmwareConfig(FirmwareConfig firmwareConfig) {
        this.firmwareConfig = firmwareConfig;
    }

    public CommunicationDirection getCommunicationDirection() {
        return communicationDirection;
    }

    public void setCommunicationDirection(CommunicationDirection communicationDirection) {
        this.communicationDirection = communicationDirection;
    }

    public DeviceKind getDevice() {
        return device;
    }

    public void setDevice(DeviceKind device) {
        this.device = device;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }
}
