package ch.jp.shooting.model;

import ch.jp.shooting.model.auth.User;
import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "ablaeufe")
@NullMarked
public class Ablauf {

    public Ablauf() {}

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
}
