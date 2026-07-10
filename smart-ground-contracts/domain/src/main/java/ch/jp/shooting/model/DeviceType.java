package ch.jp.shooting.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.jspecify.annotations.NullMarked;
import java.util.UUID;

@Entity
@Table(name = "device_types",
    uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "signal_type_id"}))
@NullMarked
public class DeviceType {

    public DeviceType() {}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name = "";

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "signal_type_id", nullable = false)
    private SignalType signalType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private DeviceTypeGroup group;

    @Column(name = "signal_duration_ms", nullable = false)
    @Positive
    private int signalDurationMs = 100;

    @Column(name = "delay_signal_duration_ms")
    @PositiveOrZero
    private Integer delaySignalDurationMs;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public SignalType getSignalType() { return signalType; }
    public void setSignalType(SignalType signalType) { this.signalType = signalType; }
    public DeviceTypeGroup getGroup() { return group; }
    public void setGroup(DeviceTypeGroup group) { this.group = group; }
    public int getSignalDurationMs() { return signalDurationMs; }
    public void setSignalDurationMs(int signalDurationMs) { this.signalDurationMs = signalDurationMs; }
    public Integer getDelaySignalDurationMs() { return delaySignalDurationMs; }
    public void setDelaySignalDurationMs(Integer delaySignalDurationMs) { this.delaySignalDurationMs = delaySignalDurationMs; }
}