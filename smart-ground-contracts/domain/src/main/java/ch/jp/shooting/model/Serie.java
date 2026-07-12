package ch.jp.shooting.model;

import ch.jp.shooting.model.auth.User;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.UUID;


@Entity
// idx_serien_updated_at: der Sync-Cursor-Endpoint (hub-api) scannt nach updated_at.
@Table(name = "serien", indexes = @Index(name = "idx_serien_updated_at", columnList = "updated_at"))
// Global-Filter: soft-gelöschte Serien sind für JEDE Hibernate-Abfrage unsichtbar
// (findById, findAllById, existsById, alle Finder, Passe-/Play-/Competition-Joins) —
// exakt das Verhalten des früheren Hard-Delete. Einzige Ausnahme ist die native
// Sync-Query (SerieRepository.findForSyncFrom), die Grabsteine bewusst mitliest.
@SQLRestriction("deleted = false")
@NullMarked
public class Serie {

    public Serie() {}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /**
     * 'user' = privat, 'range' = platzweit sichtbar
     */
    @Column(nullable = false, length = 10)
    private String ownership = "user";

    /**
     * Optionale Platz-Zuordnung
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "range_id")
    @Nullable
    private Range range;

    /**
     * Besitzer (JWT-Subject = E-Mail)
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /**
     * Schritte als JSON-Array (Step[]).
     */
    @Column(name = "steps_json", columnDefinition = "TEXT", nullable = false)
    private String stepsJson = "[]";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Vom Hub gestempelte Autoritätszeit jeder Änderung; treibt den Sync-Cursor (hub-api). */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** Soft-Delete-Grabstein: true = gelöscht. Bleibt in der Zeile, damit der Node die Löschung sieht. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean published = false;

    /** Bei jedem UPDATE die Autoritätszeit neu stempeln (INSERT deckt die Feld-Initialisierung ab). */
    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOwnership() { return ownership; }
    public void setOwnership(String ownership) { this.ownership = ownership; }
    public @Nullable Range getRange() { return range; }
    public void setRange(@Nullable Range range) { this.range = range; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public String getStepsJson() { return stepsJson; }
    public void setStepsJson(String stepsJson) { this.stepsJson = stepsJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
}
