package ch.jp.shooting.dto;

import org.jspecify.annotations.NullMarked;
import java.time.OffsetDateTime;
import java.util.UUID;

@NullMarked
public class ReservationDTO {
    private UUID id;
    private UUID rangeId;
    private String username;
    private OffsetDateTime startedAt;
    private OffsetDateTime lastActivityAt;
    private String status;

    public ReservationDTO() {}

    public ReservationDTO(UUID id, UUID rangeId, String username, OffsetDateTime startedAt, OffsetDateTime lastActivityAt, String status) {
        this.id = id;
        this.rangeId = rangeId;
        this.username = username;
        this.startedAt = startedAt;
        this.lastActivityAt = lastActivityAt;
        this.status = status;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getRangeId() { return rangeId; }
    public void setRangeId(UUID rangeId) { this.rangeId = rangeId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(OffsetDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
