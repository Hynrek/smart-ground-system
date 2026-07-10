package ch.jp.shooting.node.box;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/**
 * Box-zugewandter Discovery-/Provisionierungs-Endpunkt. Hand-spezifiziert statt über
 * openapi.yaml generiert: die SmartBox ist ein MicroPython-Client, nicht ein Java-
 * Konsument von contracts — gleiche bewusste Ausnahme wie OtaDownloadController im Hub.
 */
@RestController
public class BoxDiscoveryController {

    private final BoxProvisioningService provisioningService;
    private final BoxRecordRepository repository;

    public BoxDiscoveryController(BoxProvisioningService provisioningService, BoxRecordRepository repository) {
        this.provisioningService = provisioningService;
        this.repository = repository;
    }

    @PostMapping("/box-api/v1/discovery")
    public BoxDiscoveryResponse discover(@RequestBody BoxDiscoveryRequest request) {
        boolean wasKnown = repository.findByMacAddress(request.macAddress()).isPresent();
        BoxRecord record = provisioningService.provision(
                request.macAddress(), request.appVersion(), request.firmwareVersion(),
                request.boxType(), request.capabilitiesJson());
        return new BoxDiscoveryResponse(Base64.getEncoder().encodeToString(record.getKBox()), !wasKnown);
    }
}
