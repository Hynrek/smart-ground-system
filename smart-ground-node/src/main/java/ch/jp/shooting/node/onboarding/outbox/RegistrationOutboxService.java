package ch.jp.shooting.node.onboarding.outbox;

import ch.jp.shooting.node.box.BoxRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Schreibt die Geräte-Registrierung durable in die Outbox und macht EINEN Best-Effort-Push.
 * Kein Retry/Drain-Worker in Plan 2 — bleibt die Zeile PENDING, holt der Sync-Fundament-Worker (#2) sie später ab.
 */
@Service
public class RegistrationOutboxService {

    private final RegistrationOutboxRepository repository;
    private final HubRegistrationClient hubClient;

    public RegistrationOutboxService(RegistrationOutboxRepository repository, HubRegistrationClient hubClient) {
        this.repository = repository;
        this.hubClient = hubClient;
    }

    @Transactional
    public RegistrationOutboxRecord enqueueAndAttempt(BoxRecord box) {
        RegistrationOutboxRecord row = new RegistrationOutboxRecord();
        row.setBoxId(box.getId());
        row.setMacAddress(box.getMacAddress());
        row.setBoxType(box.getBoxType());
        row.setAppVersion(box.getAppVersion());
        row.setFirmwareVersion(box.getFirmwareVersion());
        row.setCapabilitiesJson(box.getCapabilitiesJson());
        row.setCreatedAt(Instant.now());
        row.setStatus("PENDING");
        row.setAttempts(0);
        row = repository.save(row);

        row.setAttempts(row.getAttempts() + 1);
        try {
            if (hubClient.register(row)) {
                row.setStatus("SENT");
            }
        } catch (RuntimeException e) {
            row.setLastError(e.getMessage());
        }
        return repository.save(row);
    }
}
