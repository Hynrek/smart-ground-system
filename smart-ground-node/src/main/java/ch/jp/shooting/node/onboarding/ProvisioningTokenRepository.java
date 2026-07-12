package ch.jp.shooting.node.onboarding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProvisioningTokenRepository extends JpaRepository<ProvisioningTokenRecord, UUID> {
    Optional<ProvisioningTokenRecord> findByToken(String token);
}
