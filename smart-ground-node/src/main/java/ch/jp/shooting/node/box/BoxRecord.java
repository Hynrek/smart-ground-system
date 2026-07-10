package ch.jp.shooting.node.box;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "box_records")
public class BoxRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "mac_address", nullable = false, unique = true)
    private String macAddress;

    @Column(name = "k_box", nullable = false)
    private byte[] kBox;

    @Column(name = "box_type")
    private String boxType;

    @Column(name = "app_version")
    private String appVersion;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "capabilities_json", length = 4000)
    private String capabilitiesJson;

    @Column(name = "provisioned_at", nullable = false)
    private Instant provisionedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_status")
    private String lastStatus;

    public UUID getId() { return id; }
    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    public byte[] getKBox() { return kBox; }
    public void setKBox(byte[] kBox) { this.kBox = kBox; }
    public String getBoxType() { return boxType; }
    public void setBoxType(String boxType) { this.boxType = boxType; }
    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    public String getCapabilitiesJson() { return capabilitiesJson; }
    public void setCapabilitiesJson(String capabilitiesJson) { this.capabilitiesJson = capabilitiesJson; }
    public Instant getProvisionedAt() { return provisionedAt; }
    public void setProvisionedAt(Instant provisionedAt) { this.provisionedAt = provisionedAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public String getLastStatus() { return lastStatus; }
    public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }
}
