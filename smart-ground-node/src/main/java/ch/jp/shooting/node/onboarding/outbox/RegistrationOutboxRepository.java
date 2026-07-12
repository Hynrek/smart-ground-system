package ch.jp.shooting.node.onboarding.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RegistrationOutboxRepository extends JpaRepository<RegistrationOutboxRecord, UUID> {
    List<RegistrationOutboxRecord> findByStatus(String status);
}
