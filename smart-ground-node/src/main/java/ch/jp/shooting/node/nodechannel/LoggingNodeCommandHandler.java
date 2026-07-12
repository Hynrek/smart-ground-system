package ch.jp.shooting.node.nodechannel;

import ch.jp.smartground.nodechannel.NodeChannelTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Default-Impl der {@link NodeCommandHandler}-Seam: protokolliert nur (kein echter Funk, Phase 2b). */
@Component
public class LoggingNodeCommandHandler implements NodeCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingNodeCommandHandler.class);

    @Override
    public String handle(String commandType, String payloadJson) {
        log.info("node-channel: Command {} empfangen (Platzhalter, kein Funk): {}", commandType, payloadJson);
        return NodeChannelTypes.OUTCOME_OK;
    }
}
