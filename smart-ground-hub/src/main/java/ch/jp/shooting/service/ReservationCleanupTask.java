package ch.jp.shooting.service;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class ReservationCleanupTask {

    private static final Logger logger = LoggerFactory.getLogger(ReservationCleanupTask.class);

    private final ReservationService reservationService;

    public ReservationCleanupTask(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    // Läuft alle 5 Minuten
    @Scheduled(fixedRateString = "300000", initialDelayString = "300000")
    public void cleanupInactiveReservations() {
        logger.info("Starting cleanup of inactive reservations");
        reservationService.expireInactiveReservations();
        logger.info("Cleanup of inactive reservations completed");
    }
}
