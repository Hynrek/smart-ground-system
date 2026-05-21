package ch.jp.shooting.repository;

import ch.jp.shooting.model.Guest;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

@NullMarked
public interface GuestRepository extends JpaRepository<Guest, UUID> {
}
