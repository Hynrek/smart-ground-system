package ch.jp.shooting.repository;

import ch.jp.shooting.model.SignalType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SignalTypeRepository extends JpaRepository<SignalType, UUID> {
    List<SignalType> findByFirmwareConfigId(UUID firmwareConfigId);
}
