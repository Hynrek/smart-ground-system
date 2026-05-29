package ch.jp.shooting.repository;

import ch.jp.shooting.model.RangePosition;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NullMarked
public interface RangePositionRepository extends JpaRepository<RangePosition, UUID> {

    List<RangePosition> findByRangeIdOrderBySortOrderAsc(UUID rangeId);

    Optional<RangePosition> findByRangeIdAndLabel(UUID rangeId, String label);

    boolean existsByRangeIdAndLabel(UUID rangeId, String label);

    int countByRangeId(UUID rangeId);
}
