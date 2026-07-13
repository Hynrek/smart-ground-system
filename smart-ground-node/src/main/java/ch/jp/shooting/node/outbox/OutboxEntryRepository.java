package ch.jp.shooting.node.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEntryRepository extends JpaRepository<OutboxEntry, Long> {
    List<OutboxEntry> findByStatusOrderBySequenceAsc(String status);
    List<OutboxEntry> findByEntityTypeOrderBySequenceAsc(String entityType);
}
