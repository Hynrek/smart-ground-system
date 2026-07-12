package ch.jp.shooting.node.sync;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Lokaler Spiegel einer Hub-Serie (hub-api, abwärts). Die id wird VOM HUB vergeben (nicht generiert) —
 * sie ist der idempotente Upsert-Schlüssel. deleted=true ist ein Grabstein: die Zeile bleibt, damit der
 * Node-Lesepfad (Teilprojekt #5) Löschungen kennt. Trägt bewusst nur Identität + Nutzdaten, keine FKs.
 */
@NullMarked
@Entity
@Table(name = "synced_serie")
public class SyncedSerie {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "ownership", nullable = false)
    private String ownership;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "range_id")
    @Nullable
    private UUID rangeId;

    @Column(name = "steps_json", columnDefinition = "TEXT", nullable = false)
    private String stepsJson = "[]";

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOwnership() { return ownership; }
    public void setOwnership(String ownership) { this.ownership = ownership; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    @Nullable
    public UUID getRangeId() { return rangeId; }
    public void setRangeId(@Nullable UUID rangeId) { this.rangeId = rangeId; }
    public String getStepsJson() { return stepsJson; }
    public void setStepsJson(String stepsJson) { this.stepsJson = stepsJson; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
