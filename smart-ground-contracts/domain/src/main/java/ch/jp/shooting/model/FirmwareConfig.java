package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NullMarked
@Entity
@Table(name = "firmware_configs",
    uniqueConstraints = @UniqueConstraint(columnNames = {"version", "box_type"}))
public class FirmwareConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Nullable
    private UUID id;

    @Column(nullable = false)
    private String version = "";

    @Column(name = "box_type", nullable = false)
    private String boxType = "";

    @Column(name = "capabilities_json", columnDefinition = "TEXT")
    @Nullable
    private String capabilitiesJson;

    @Column(name = "config_schema_version")
    @Nullable
    private String configSchemaVersion;

    @OneToMany(mappedBy = "firmwareConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SignalType> signalTypes = new ArrayList<>();

    public FirmwareConfig() {
    }

    public FirmwareConfig(String version, String boxType) {
        this.version = version;
        this.boxType = boxType;
    }

    public @Nullable UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getBoxType() {
        return boxType;
    }

    public void setBoxType(String boxType) {
        this.boxType = boxType;
    }

    public List<SignalType> getSignalTypes() {
        return signalTypes;
    }

    public void setSignalTypes(List<SignalType> signalTypes) {
        this.signalTypes = signalTypes;
    }

    public @Nullable String getCapabilitiesJson() {
        return capabilitiesJson;
    }

    public void setCapabilitiesJson(@Nullable String capabilitiesJson) {
        this.capabilitiesJson = capabilitiesJson;
    }

    public @Nullable String getConfigSchemaVersion() {
        return configSchemaVersion;
    }

    public void setConfigSchemaVersion(@Nullable String configSchemaVersion) {
        this.configSchemaVersion = configSchemaVersion;
    }
}
