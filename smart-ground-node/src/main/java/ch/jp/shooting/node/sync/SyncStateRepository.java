package ch.jp.shooting.node.sync;

import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;

@NullMarked
public interface SyncStateRepository extends JpaRepository<SyncState, String> {
}
