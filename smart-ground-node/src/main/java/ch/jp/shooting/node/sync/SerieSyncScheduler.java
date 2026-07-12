package ch.jp.shooting.node.sync;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Zieht periodisch Serien vom Hub. Ein toter Backhaul ist kein Fehler (die Spec: „Ein toter Backhaul
 * ist kein Bug") — deshalb wird eine Ausnahme geloggt und geschluckt, der nächste Tick versucht es erneut.
 */
@NullMarked
@Component
class SerieSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SerieSyncScheduler.class);

    private final SerieSyncService serieSyncService;

    SerieSyncScheduler(SerieSyncService serieSyncService) {
        this.serieSyncService = serieSyncService;
    }

    @Scheduled(fixedDelayString = "${sync.serie.interval-ms:15000}")
    void pullSerien() {
        try {
            int applied = serieSyncService.sync();
            if (applied > 0) {
                log.info("Serie-Sync: {} Zeilen angewandt", applied);
            }
        } catch (RuntimeException e) {
            // Hub nicht erreichbar o. ä. — kein Alarm, nächster Tick holt es nach.
            log.debug("Serie-Sync übersprungen (Hub nicht erreichbar?): {}", e.getMessage());
        }
    }
}
