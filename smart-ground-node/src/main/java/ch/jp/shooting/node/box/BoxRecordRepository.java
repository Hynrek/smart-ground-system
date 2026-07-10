package ch.jp.shooting.node.box;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BoxRecordRepository extends JpaRepository<BoxRecord, UUID> {
    Optional<BoxRecord> findByMacAddress(String macAddress);
}
