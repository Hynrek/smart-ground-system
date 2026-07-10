package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@NullMarked
public class Reservation {

    public Reservation() {}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "range_id", nullable = false)
    private Range range;

    @Column(nullable = false)
    private String username = "";

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt = OffsetDateTime.now();

    @Column(name = "last_activity_at", nullable = false)
    private OffsetDateTime lastActivityAt = OffsetDateTime.now();

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ReservationStatus status = ReservationStatus.ACTIVE;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Range getRange() { return range; }
    public void setRange(Range range) { this.range = range; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(OffsetDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }

    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }

    public enum ReservationStatus {
        ACTIVE,
        RELEASED
    }
}
