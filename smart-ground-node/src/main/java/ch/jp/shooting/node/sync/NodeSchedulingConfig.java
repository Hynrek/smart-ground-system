package ch.jp.shooting.node.sync;

import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Aktiviert @Scheduled für den Node (bislang lief nichts periodisch). */
@NullMarked
@Configuration
@EnableScheduling
class NodeSchedulingConfig {
}
