package ch.jp.shooting.repository;

import ch.jp.shooting.model.FirmwareConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FirmwareConfigRepository extends JpaRepository<FirmwareConfig, UUID> {
    Optional<FirmwareConfig> findByVersionAndBoxType(String version, String boxType);
}
