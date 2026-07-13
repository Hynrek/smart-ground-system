package ch.jp.shooting.node.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodischer Trigger für den Outbox-Drain. `@EnableScheduling` kommt aus NodeSchedulingConfig (#2). */
@Component
public class OutboxDrainScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxDrainScheduler.class);

    private final OutboxDrainService drainService;

    public OutboxDrainScheduler(OutboxDrainService drainService) {
        this.drainService = drainService;
    }

    @Scheduled(fixedDelayString = "${outbox.serie.interval-ms:15000}")
    public void drain() {
        int sent = drainService.drainOnce();
        if (sent > 0) {
            log.info("Outbox drain: {} entries pushed", sent);
        }
    }
}
