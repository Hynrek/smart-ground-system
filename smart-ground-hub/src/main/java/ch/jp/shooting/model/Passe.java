package ch.jp.shooting.model;

import ch.jp.shooting.model.auth.User;
import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "passen")
@NullMarked
public class Passe {

    public Passe() {}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /**
     * Geordnete Liste der referenzierten Serie-IDs (JSON-Array von UUID-Strings).
     * Die Serien werden beim Lesen live verbunden (kein Snapshot mehr).
     */
    @Column(name = "serie_ids_json", columnDefinition = "TEXT", nullable = false)
    private String serieIdsJson = "[]";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public String getSerieIdsJson() { return serieIdsJson; }
    public void setSerieIdsJson(String serieIdsJson) { this.serieIdsJson = serieIdsJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
