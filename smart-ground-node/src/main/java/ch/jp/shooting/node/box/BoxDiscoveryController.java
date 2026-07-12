package ch.jp.shooting.node.box;

import ch.jp.shooting.node.onboarding.ProvisioningTokenService;
import ch.jp.shooting.node.onboarding.outbox.RegistrationOutboxService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/**
 * Box-zugewandter Discovery-/Provisionierungs-Endpunkt. Hand-spezifiziert statt über
 * openapi.yaml generiert: die SmartBox ist ein MicroPython-Client, nicht ein Java-
 * Konsument von contracts — gleiche bewusste Ausnahme wie OtaDownloadController im Hub.
 *
 * Discovery ist der token-gesicherte Erst-Kontakt (Provisionierung). Eine gültige Box
 * holt K_Box genau einmal hier ab und wechselt danach auf den ESP-NOW-Pairing-Pfad;
 * ein Token-loser Aufruf wird abgewiesen (sonst könnte ein MAC-Spoofer K_Box ziehen).
 */
@RestController
public class BoxDiscoveryController {

    private final BoxProvisioningService provisioningService;
    private final BoxRecordRepository repository;
    private final ProvisioningTokenService tokenService;
    private final RegistrationOutboxService outboxService;

    public BoxDiscoveryController(BoxProvisioningService provisioningService, BoxRecordRepository repository,
                                  ProvisioningTokenService tokenService, RegistrationOutboxService outboxService) {
        this.provisioningService = provisioningService;
        this.repository = repository;
        this.tokenService = tokenService;
        this.outboxService = outboxService;
    }

    @PostMapping("/box-api/v1/discovery")
    public BoxDiscoveryResponse discover(@RequestBody BoxDiscoveryRequest request) {
        tokenService.validateAndConsume(request.token(), request.macAddress());

        boolean wasKnown = repository.findByMacAddress(request.macAddress()).isPresent();
        BoxRecord record = provisioningService.provision(
                request.macAddress(), request.appVersion(), request.firmwareVersion(),
                request.boxType(), request.capabilitiesJson());
        outboxService.enqueueAndAttempt(record);
        return new BoxDiscoveryResponse(Base64.getEncoder().encodeToString(record.getKBox()), !wasKnown);
    }
}
