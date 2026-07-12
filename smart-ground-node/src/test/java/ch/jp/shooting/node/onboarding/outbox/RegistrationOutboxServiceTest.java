package ch.jp.shooting.node.onboarding.outbox;

import ch.jp.shooting.node.box.BoxProvisioningService;
import ch.jp.shooting.node.box.BoxRecord;
import ch.jp.shooting.node.box.BoxRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RegistrationOutboxServiceTest {

    @Autowired
    private RegistrationOutboxRepository repository;
    @Autowired
    private BoxProvisioningService provisioningService;
    @Autowired
    private BoxRecordRepository boxRepository;

    private BoxRecord box;

    @BeforeEach
    void setUp() {
        boxRepository.findByMacAddress("AA:BB:CC:DD:EE:40").ifPresent(boxRepository::delete);
        box = provisioningService.provision("AA:BB:CC:DD:EE:40", "1.0.0", "fw-1", "thrower", "{}");
    }

    @Test
    void enqueueAndAttempt_pushSucceeds_marksSent() {
        RegistrationOutboxService service = new RegistrationOutboxService(repository, row -> true);
        RegistrationOutboxRecord saved = service.enqueueAndAttempt(box);

        assertThat(saved.getStatus()).isEqualTo("SENT");
        assertThat(saved.getMacAddress()).isEqualTo("AA:BB:CC:DD:EE:40");
        assertThat(saved.getBoxId()).isEqualTo(box.getId());
    }

    @Test
    void enqueueAndAttempt_pushFails_staysPending() {
        RegistrationOutboxService service = new RegistrationOutboxService(repository, row -> false);
        RegistrationOutboxRecord saved = service.enqueueAndAttempt(box);

        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getAttempts()).isEqualTo(1);
    }

    @Test
    void enqueueAndAttempt_pushThrows_staysPendingWithError() {
        RegistrationOutboxService service = new RegistrationOutboxService(repository, row -> {
            throw new RuntimeException("hub down");
        });
        RegistrationOutboxRecord saved = service.enqueueAndAttempt(box);

        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(saved.getLastError()).contains("hub down");
    }
}
