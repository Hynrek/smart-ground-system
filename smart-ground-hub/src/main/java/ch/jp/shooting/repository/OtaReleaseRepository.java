package ch.jp.shooting.repository;

import ch.jp.shooting.model.OtaRelease;
import ch.jp.shooting.model.OtaType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OtaReleaseRepository extends JpaRepository<OtaRelease, UUID> {
    Optional<OtaRelease> findByTypeAndVersion(OtaType type, String version);
    List<OtaRelease> findAllByOrderByCreatedAtDesc();
}
