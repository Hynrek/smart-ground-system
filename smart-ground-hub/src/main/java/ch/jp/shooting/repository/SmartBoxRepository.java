package ch.jp.shooting.repository;

import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.model.SmartBoxStates;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@NullMarked
public interface SmartBoxRepository extends JpaRepository<SmartBox, UUID> {
    Optional<SmartBox> findByMacAddress(String macAddress);

    @Modifying
    @Query("UPDATE SmartBox s SET s.status = :status WHERE s.status = :oldStatus AND s.lastSeen < :threshold")
    int updateStaleBoxes(
            @Param("status") SmartBoxStates status,
            @Param("oldStatus") SmartBoxStates oldStatus,
            @Param("threshold") Instant threshold);
}