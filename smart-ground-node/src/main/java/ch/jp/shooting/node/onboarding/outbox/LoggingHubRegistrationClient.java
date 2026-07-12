package ch.jp.shooting.node.onboarding.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Default-Impl: kein Hub-Endpoint (#2) — protokolliert und meldet "nicht gesendet". */
@Component
public class LoggingHubRegistrationClient implements HubRegistrationClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingHubRegistrationClient.class);

    @Override
    public boolean register(RegistrationOutboxRecord row) {
        log.info("Geräte-Registrierung {} (MAC {}) in Outbox — Hub-Push folgt mit Sync-Fundament (#2)",
                row.getBoxId(), row.getMacAddress());
        return false;
    }
}
