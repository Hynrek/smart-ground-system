package ch.jp.shooting.repository;

import ch.jp.shooting.model.DeviceTypeGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceTypeGroupRepository extends JpaRepository<DeviceTypeGroup, UUID> {
    Optional<DeviceTypeGroup> findByName(String name);
}
