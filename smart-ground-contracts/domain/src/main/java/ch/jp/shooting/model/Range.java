package ch.jp.shooting.model;

import ch.jp.shooting.model.auth.User;
import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Entity
@Table(name = "ranges")
@NullMarked
public class Range {

    public Range() {}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name = "";

    @Nullable
    private String description;

    @Column(nullable = false)
    private boolean locked = false;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    @Nullable
    private User assignedUser;

    @OneToMany(mappedBy = "range", fetch = FetchType.LAZY)
    private List<Device> devices = new ArrayList<>();

    @OneToMany(mappedBy = "range", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<RangePosition> positions = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public @Nullable String getDescription() { return description; }
    public void setDescription(@Nullable String description) { this.description = description; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public List<Device> getDevices() { return devices; }
    public void setDevices(List<Device> devices) { this.devices = devices; }
    public List<RangePosition> getPositions() { return positions; }
    public void setPositions(List<RangePosition> positions) { this.positions = positions; }
    public @Nullable User getAssignedUser() { return assignedUser; }
    public void setAssignedUser(@Nullable User assignedUser) { this.assignedUser = assignedUser; }
}
