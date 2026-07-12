package ch.jp.shooting.node.onboarding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HexFormat;

/** Default-Impl der {@link RadioSender}-Seam: protokolliert den Frame, bis die Serial-Anbindung existiert. */
@Component
public class LoggingRadioSender implements RadioSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingRadioSender.class);

    @Override
    public void send(byte[] destMac, byte[] frame) {
        log.info("ONBOARD_OFFER an {} ({} B): {}",
                HexFormat.ofDelimiter(":").formatHex(destMac), frame.length, HexFormat.of().formatHex(frame));
    }
}
