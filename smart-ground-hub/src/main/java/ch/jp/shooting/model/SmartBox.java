package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "smart_boxes")
@NullMarked
public class SmartBox {

    public SmartBox() {}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Nullable
    private UUID id;

    @Column(name = "mac_address", nullable = false, unique = true)
    private String macAddress = "";

    @Column(name = "alias")
    @Nullable
    private String alias;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SmartBoxStates status = SmartBoxStates.OFFLINE;

    @Column(name = "last_seen")
    @Nullable
    private Instant lastSeen;

    @Column(name = "firmware_version")
    @Nullable
    private String firmwareVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firmware_config_id")
    @Nullable
    private FirmwareConfig firmwareConfig;

    @Column(name = "mqtt_username")
    @Nullable
    private String mqttUsername;

    @Column(name = "config_synced", nullable = false)
    private boolean configSynced = false;

    @OneToMany(mappedBy = "smartBox", fetch = FetchType.LAZY)
    private List<Device> devices = new ArrayList<>();

    public @Nullable UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    public @Nullable String getAlias() { return alias; }
    public void setAlias(@Nullable String alias) { this.alias = alias; }
    public SmartBoxStates getStatus() { return status; }
    public void setStatus(SmartBoxStates status) { this.status = status; }
    public @Nullable Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(@Nullable Instant lastSeen) { this.lastSeen = lastSeen; }
    public @Nullable String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(@Nullable String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    public @Nullable FirmwareConfig getFirmwareConfig() { return firmwareConfig; }
    public void setFirmwareConfig(@Nullable FirmwareConfig firmwareConfig) { this.firmwareConfig = firmwareConfig; }
    public @Nullable String getMqttUsername() { return mqttUsername; }
    public void setMqttUsername(@Nullable String mqttUsername) { this.mqttUsername = mqttUsername; }
    public List<Device> getDevices() { return devices; }
    public boolean isConfigSynced() { return configSynced; }
    public void setConfigSynced(boolean configSynced) { this.configSynced = configSynced; }
}