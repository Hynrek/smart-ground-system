package ch.jp.shooting.node.outbox;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Eine ausgehende Änderung (Serie oder PlayInstance), die zum Hub steigen soll. `sequence`
 * ist Primärschlüssel UND FIFO-Ordnungsschlüssel — EIN globaler Sequenzraum über beide
 * entityType-Werte hinweg, damit eine Serie garantiert vor der PlayInstance geht, die auf
 * sie verweist, ohne dass irgendwo eine explizite Abhängigkeitsgraph-Logik nötig ist.
 * `payloadJson` trägt den vollen generierten Outbox-Item-DTO (contracts), dekodiert beim
 * Push bzw. beim Read-your-writes-Merge (NodeSerieReadService).
 */
@Entity
@Table(name = "outbox_entries")
public class OutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sequence;

    @Column(name = "entity_type", nullable = false, length = 20)
    private String entityType; // "SERIE" | "PLAY_INSTANCE"

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "payload_json", columnDefinition = "TEXT", nullable = false)
    private String payloadJson;

    @Column(name = "status", nullable = false, length = 10)
    private String status = "PENDING"; // PENDING | SENT | FAILED

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    public Long getSequence() { return sequence; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
