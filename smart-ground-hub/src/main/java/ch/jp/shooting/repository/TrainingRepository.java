package ch.jp.shooting.repository;

import ch.jp.shooting.model.Training;
import ch.jp.shooting.model.auth.User;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

@NullMarked
public interface TrainingRepository extends JpaRepository<Training, UUID> {
    List<Training> findByOwner(User owner);
}
