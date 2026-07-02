package ch.jp.shooting.repository;

import ch.jp.shooting.model.DeviceType;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NullMarked
public interface DeviceTypeRepository extends JpaRepository<DeviceType, UUID> {
    // Used by config-push resolution
    Optional<DeviceType> findByGroupIdAndSignalType_FirmwareConfigId(UUID groupId, UUID firmwareConfigId);

    // Used for compatibility check when registering a Device to a SmartBox
    List<DeviceType> findByGroupId(UUID groupId);

    Optional<DeviceType> findByName(String name);
}