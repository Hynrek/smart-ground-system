package ch.jp.shooting.node.sync;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;

import java.time.Instant;

/**
 * Persistenter Sync-Cursor pro Entität (z. B. "serie"). Datei-basiertes H2 → überlebt den Neustart,
 * damit der Node nach einem Reboot vom korrekten Cursor weitersynct statt alles neu zu ziehen.
 */
@NullMarked
@Entity
@Table(name = "sync_state")
public class SyncState {

    @Id
    @Column(name = "entity")
    private String entity;

    @Column(name = "cursor", nullable = false)
    private Instant cursor;

    public String getEntity() { return entity; }
    public void setEntity(String entity) { this.entity = entity; }
    public Instant getCursor() { return cursor; }
    public void setCursor(Instant cursor) { this.cursor = cursor; }
}
