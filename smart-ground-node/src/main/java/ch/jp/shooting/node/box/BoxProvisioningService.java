package ch.jp.shooting.node.box;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class BoxProvisioningService {

    private final BoxRecordRepository repository;
    private final KBoxGenerator kBoxGenerator;

    public BoxProvisioningService(BoxRecordRepository repository, KBoxGenerator kBoxGenerator) {
        this.repository = repository;
        this.kBoxGenerator = kBoxGenerator;
    }

    @Transactional
    public BoxRecord provision(String macAddress, String appVersion, String firmwareVersion,
                                String boxType, String capabilitiesJson) {
        BoxRecord record = repository.findByMacAddress(macAddress).orElseGet(() -> {
            BoxRecord fresh = new BoxRecord();
            fresh.setMacAddress(macAddress);
            fresh.setKBox(kBoxGenerator.generate());
            fresh.setProvisionedAt(Instant.now());
            return fresh;
        });
        record.setAppVersion(appVersion);
        record.setFirmwareVersion(firmwareVersion);
        record.setBoxType(boxType);
        record.setCapabilitiesJson(capabilitiesJson);
        return repository.save(record);
    }
}
