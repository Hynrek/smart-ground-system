package ch.jp.shooting.node.onboarding;

import ch.jp.shooting.node.frame.FrameHeader;
import ch.jp.shooting.node.frame.FrameType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Orchestriert die Kopplung: schlägt die pending Box nach, mintet ein Einmal-Token,
 * baut den ONBOARD_OFFER (Plan-1-Codec) und emittiert ihn über die RadioSender-Seam.
 */
@Service
public class OnboardingService {

    private final PendingBoxRegistry registry;
    private final ProvisioningTokenService tokenService;
    private final NodeCertFingerprint certFingerprint;
    private final RadioSender radioSender;
    private final byte[] nodeMac;
    private final byte[] apSsid;
    private final byte[] apPsk;
    private final byte[] boxApiUrl;

    public OnboardingService(PendingBoxRegistry registry, ProvisioningTokenService tokenService,
                             NodeCertFingerprint certFingerprint, RadioSender radioSender,
                             @Value("${onboarding.node-mac}") String nodeMacHex,
                             @Value("${onboarding.ap-ssid}") String apSsid,
                             @Value("${onboarding.ap-psk}") String apPsk,
                             @Value("${onboarding.box-api-url}") String boxApiUrl) {
        this.registry = registry;
        this.tokenService = tokenService;
        this.certFingerprint = certFingerprint;
        this.radioSender = radioSender;
        this.nodeMac = Macs.parse(nodeMacHex);
        this.apSsid = apSsid.getBytes(StandardCharsets.UTF_8);
        this.apPsk = apPsk.getBytes(StandardCharsets.UTF_8);
        this.boxApiUrl = boxApiUrl.getBytes(StandardCharsets.UTF_8);
    }

    public CoupleResult couple(String mac) {
        PendingBox box = registry.find(mac).orElseThrow(() -> {
            ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.NOT_FOUND, "Gerät " + mac + " ist nicht mehr erreichbar.");
            detail.setType(URI.create("/errors/box-not-pending"));
            return new ErrorResponseException(HttpStatus.NOT_FOUND, detail, null);
        });

        ProvisioningTokenService.MintedToken token = tokenService.mint(mac);

        FrameHeader header = new FrameHeader(Macs.parse(mac), nodeMac, 1, 1, FrameType.ONBOARD_OFFER);
        byte[] frame = OnboardingCodec.buildOnboardOffer(header, box.boxNonce(), token.raw(),
                certFingerprint.sha256(), apSsid, apPsk, boxApiUrl);

        radioSender.send(Macs.parse(mac), frame);

        return new CoupleResult(mac, "offered", token.expiresAt());
    }
}
