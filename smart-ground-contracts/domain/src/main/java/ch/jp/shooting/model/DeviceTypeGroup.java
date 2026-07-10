package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

@NullMarked
@Entity
@Table(name = "device_type_groups")
public class DeviceTypeGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Nullable
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name = "";

    public DeviceTypeGroup() {
    }

    public DeviceTypeGroup(String name) {
        this.name = name;
    }

    public @Nullable UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
