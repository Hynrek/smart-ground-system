package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ota_releases",
       uniqueConstraints = @UniqueConstraint(columnNames = {"type", "version"}))
@NullMarked
public class OtaRelease {

    public OtaRelease() {}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Nullable
    private UUID id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OtaType type = OtaType.APP;

    @Column(nullable = false)
    private String version = "";

    // APP: SHA-256 des manifest.json; FIRMWARE: SHA-256 des .bin-Images
    @Column(nullable = false)
    private String sha256 = "";

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public @Nullable UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public OtaType getType() { return type; }
    public void setType(OtaType type) { this.type = type; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
