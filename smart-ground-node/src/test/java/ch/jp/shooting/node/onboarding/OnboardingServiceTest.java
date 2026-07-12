package ch.jp.shooting.node.onboarding;

import ch.jp.shooting.node.frame.FrameHeader;
import ch.jp.shooting.node.frame.FrameType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class OnboardingServiceTest {

    @Autowired
    private PendingBoxRegistry registry;
    @Autowired
    private ProvisioningTokenService tokenService;
    @Autowired
    private NodeCertFingerprint certFingerprint;

    @Test
    void couple_emitsOnboardOfferWithTokenFingerprintAndEchoNonce() {
        byte[] boxNonce = {10, 20, 30, 40, 50, 60, 70, 80};
        registry.onHello("AA:BB:CC:DD:EE:30", -42, boxNonce);

        AtomicReference<byte[]> sentDest = new AtomicReference<>();
        AtomicReference<byte[]> sentFrame = new AtomicReference<>();
        RadioSender capturing = (dest, frame) -> { sentDest.set(dest); sentFrame.set(frame); };

        OnboardingService service = new OnboardingService(registry, tokenService, certFingerprint, capturing,
                "30:ae:a4:1f:2b:3c", "SmartGround-Node-1", "provision-pw-123", "https://192.168.4.1:8443");

        CoupleResult result = service.couple("AA:BB:CC:DD:EE:30");

        assertThat(result.mac()).isEqualTo("AA:BB:CC:DD:EE:30");
        assertThat(result.status()).isEqualTo("offered");
        assertThat(sentDest.get()).containsExactly(0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0x30);

        byte[] frame = sentFrame.get();
        FrameHeader header = FrameHeader.decode(frame);
        assertThat(header.type()).isEqualTo(FrameType.ONBOARD_OFFER);
        assertThat(OnboardingCodec.echoNonceOf(frame)).containsExactly(10, 20, 30, 40, 50, 60, 70, 80);
        assertThat(OnboardingCodec.fingerprintOf(frame)).isEqualTo(certFingerprint.sha256());
        assertThat(OnboardingCodec.tokenOf(frame)).hasSize(16);
        assertThat(new String(OnboardingCodec.ssidOf(frame), StandardCharsets.UTF_8)).isEqualTo("SmartGround-Node-1");
        assertThat(new String(OnboardingCodec.pskOf(frame), StandardCharsets.UTF_8)).isEqualTo("provision-pw-123");
        assertThat(new String(OnboardingCodec.urlOf(frame), StandardCharsets.UTF_8)).isEqualTo("https://192.168.4.1:8443");
    }

    @Test
    void couple_successiveCalls_emitDifferentFrameIds() {
        registry.onHello("AA:BB:CC:DD:EE:31", -40, new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        registry.onHello("AA:BB:CC:DD:EE:32", -41, new byte[]{8, 7, 6, 5, 4, 3, 2, 1});

        AtomicReference<byte[]> firstFrame = new AtomicReference<>();
        AtomicReference<byte[]> secondFrame = new AtomicReference<>();
        OnboardingService service = new OnboardingService(registry, tokenService, certFingerprint,
                (dest, frame) -> {
                    if (firstFrame.get() == null) {
                        firstFrame.set(frame);
                    } else {
                        secondFrame.set(frame);
                    }
                },
                "30:ae:a4:1f:2b:3c", "SmartGround-Node-1", "provision-pw-123", "https://192.168.4.1:8443");

        service.couple("AA:BB:CC:DD:EE:31");
        service.couple("AA:BB:CC:DD:EE:32");

        int firstFrameId = FrameHeader.decode(firstFrame.get()).frameId();
        int secondFrameId = FrameHeader.decode(secondFrame.get()).frameId();
        assertThat(firstFrameId).isNotEqualTo(secondFrameId);
    }

    @Test
    void couple_unknownMac_throwsTypedRejection() {
        OnboardingService service = new OnboardingService(registry, tokenService, certFingerprint,
                (dest, frame) -> { }, "30:ae:a4:1f:2b:3c", "S", "P", "https://x");

        assertThatThrownBy(() -> service.couple("AA:BB:CC:DD:EE:FF"))
                .isInstanceOf(ErrorResponseException.class);
    }
}
