package ch.jp.shooting.node.nodechannel;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CommandDeduplicatorTest {

    @Test
    void handle_runsWorkOnce_perCommandId_andCachesOutcome() {
        var dedup = new CommandDeduplicator(1000);
        var id = UUID.randomUUID();
        var runs = new AtomicInteger(0);

        String first = dedup.handle(id, () -> { runs.incrementAndGet(); return "OK"; });
        String second = dedup.handle(id, () -> { runs.incrementAndGet(); return "SHOULD_NOT_RUN"; });

        assertThat(first).isEqualTo("OK");
        assertThat(second).isEqualTo("OK");        // cached outcome, not re-run
        assertThat(runs.get()).isEqualTo(1);       // work executed exactly once
    }

    @Test
    void handle_distinctIds_runIndependently() {
        var dedup = new CommandDeduplicator(1000);
        assertThat(dedup.handle(UUID.randomUUID(), () -> "A")).isEqualTo("A");
        assertThat(dedup.handle(UUID.randomUUID(), () -> "B")).isEqualTo("B");
    }
}
