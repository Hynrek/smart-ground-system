package ch.jp.shooting.node.sync;

import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

@NullMarked
public interface SyncedSerieRepository extends JpaRepository<SyncedSerie, UUID> {
}
