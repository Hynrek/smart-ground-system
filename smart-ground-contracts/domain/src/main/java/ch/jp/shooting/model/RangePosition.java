package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

@Entity
@Table(
    name = "range_positions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"range_id", "label"})
)
@NullMarked
public class RangePosition {

    public RangePosition() {}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "range_id", nullable = false)
    private Range range;

    @Column(nullable = false, length = 32)
    private String label = "";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    /** Das aktuell zugeordnete Gerät – null wenn Slot leer. */
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "device_id", unique = true)
    @Nullable
    private Device device;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Range getRange() { return range; }
    public void setRange(Range range) { this.range = range; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public @Nullable Device getDevice() { return device; }
    public void setDevice(@Nullable Device device) { this.device = device; }
}
