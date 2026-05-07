package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "devices")
@NullMarked
public class Device {

    public Device() {}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "smartbox_id", nullable = false)
    private SmartBox smartBox;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "range_id")
    @Nullable
    private Range range;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private DeviceTypeGroup deviceTypeGroup;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "device_type_id", nullable = false)
    private DeviceType deviceType;

    @Column(name = "pin_config", columnDefinition = "TEXT")
    @Nullable
    private String pinConfig;

    @Column(name = "config_json", columnDefinition = "TEXT")
    @Nullable
    private String configJson;

    @Column(nullable = false)
    private String alias = "";

    @Column(name = "delay_signal_duration_ms")
    private Integer delaySignalDurationMs;

    @Column(name = "fire_delay_ms")
    private Integer fireDelayMs;

    @Column(nullable = false)
    private boolean blocked = false;

    @Column(nullable = false)
    private boolean healthy = true;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public SmartBox getSmartBox() { return smartBox; }
    public void setSmartBox(SmartBox smartBox) { this.smartBox = smartBox; }
    public @Nullable Range getRange() { return range; }
    public void setRange(@Nullable Range range) { this.range = range; }
    public DeviceTypeGroup getDeviceTypeGroup() { return deviceTypeGroup; }
    public void setDeviceTypeGroup(DeviceTypeGroup deviceTypeGroup) { this.deviceTypeGroup = deviceTypeGroup; }
    public DeviceType getDeviceType() { return deviceType; }
    public void setDeviceType(DeviceType deviceType) { this.deviceType = deviceType; }
    public @Nullable String getPinConfig() { return pinConfig; }
    public void setPinConfig(@Nullable String pinConfig) { this.pinConfig = pinConfig; }
    public @Nullable String getConfigJson() { return configJson; }
    public void setConfigJson(@Nullable String configJson) { this.configJson = configJson; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public Integer getDelaySignalDurationMs() { return delaySignalDurationMs; }
    public void setDelaySignalDurationMs(Integer delaySignalDurationMs) { this.delaySignalDurationMs = delaySignalDurationMs; }
    public Integer getFireDelayMs() { return fireDelayMs; }
    public void setFireDelayMs(Integer fireDelayMs) { this.fireDelayMs = fireDelayMs; }
    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }
    public boolean isHealthy() { return healthy; }
    public void setHealthy(boolean healthy) { this.healthy = healthy; }
}
