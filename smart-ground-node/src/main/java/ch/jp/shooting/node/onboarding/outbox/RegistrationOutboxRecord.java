package ch.jp.shooting.node.onboarding.outbox;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Ausgehende Geräte-Registrierung, die zum Hub steigen soll. Trägt bewusst KEIN K_Box —
 * der Hub braucht nur Identität (UUID + MAC + Deskriptoren); K_Box bleibt node-lokal.
 * Minimale durable Seam: der Retry/Drain-Worker gehört dem Sync-Fundament (#2).
 */
@Entity
@Table(name = "registration_outbox")
public class RegistrationOutboxRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "box_id", nullable = false)
    private UUID boxId;

    @Column(name = "mac_address", nullable = false)
    private String macAddress;

    @Column(name = "box_type")
    private String boxType;

    @Column(name = "app_version")
    private String appVersion;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "capabilities_json", length = 4000)
    private String capabilitiesJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    public UUID getId() { return id; }
    public UUID getBoxId() { return boxId; }
    public void setBoxId(UUID boxId) { this.boxId = boxId; }
    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    public String getBoxType() { return boxType; }
    public void setBoxType(String boxType) { this.boxType = boxType; }
    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    public String getCapabilitiesJson() { return capabilitiesJson; }
    public void setCapabilitiesJson(String capabilitiesJson) { this.capabilitiesJson = capabilitiesJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
