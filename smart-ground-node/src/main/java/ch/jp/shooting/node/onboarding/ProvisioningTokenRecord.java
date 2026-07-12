package ch.jp.shooting.node.onboarding;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Einmaliges Provisioning-Token (TTL, an eine MAC gebunden), node-seitig persistiert. */
@Entity
@Table(name = "provisioning_tokens")
public class ProvisioningTokenRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token", nullable = false, unique = true)
    private String token;

    @Column(name = "mac_address", nullable = false)
    private String macAddress;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used", nullable = false)
    private boolean used;

    public UUID getId() { return id; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
}
