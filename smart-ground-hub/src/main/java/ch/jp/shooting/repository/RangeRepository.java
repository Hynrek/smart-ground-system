package ch.jp.shooting.repository;

import ch.jp.shooting.model.Range;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

@NullMarked
public interface RangeRepository extends JpaRepository<Range, UUID> {
    boolean existsByName(String name);

    Optional<Range> findByName(String name);

    @Query("SELECT r FROM Range r WHERE r.assignedUser.id = :userId")
    Optional<Range> findByAssignedUserId(@Param("userId") UUID userId);
}