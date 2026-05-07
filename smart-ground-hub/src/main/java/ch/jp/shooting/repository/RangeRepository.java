package ch.jp.shooting.repository;

import ch.jp.shooting.model.Range;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

@NullMarked
public interface RangeRepository extends JpaRepository<Range, UUID> {
    boolean existsByName(String name);
}