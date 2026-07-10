package ch.jp.shooting.node.box;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// Hinweis: Spring Boot 4 hat den @DataJpaTest-Slice in ein separates Modul
// (spring-boot-data-jpa-test) ausgelagert, das in dieser Offline-Umgebung nicht
// verfuegbar ist (siehe smart-ground-hub fuer denselben Workaround). Daher wird
// der Persistenz-Test ueber den vollen Kontext (@SpringBootTest) mit dem
// file-basierten H2-Datasource aus application.properties gefahren.
@SpringBootTest
@Transactional
class BoxRecordRepositoryTest {

    @Autowired
    private BoxRecordRepository repository;

    @Test
    void findByMacAddress_returnsSavedRecord() {
        BoxRecord record = new BoxRecord();
        record.setMacAddress("AA:BB:CC:DD:EE:FF");
        record.setKBox(new byte[32]);
        record.setBoxType("thrower");
        record.setAppVersion("1.0.0");
        record.setFirmwareVersion("micropython-1.23");
        record.setCapabilitiesJson("{}");
        record.setProvisionedAt(Instant.now());
        repository.save(record);

        var found = repository.findByMacAddress("AA:BB:CC:DD:EE:FF");

        assertThat(found).isPresent();
        assertThat(found.get().getKBox()).hasSize(32);
    }

    @Test
    void findByMacAddress_unknownMac_returnsEmpty() {
        assertThat(repository.findByMacAddress("00:00:00:00:00:00")).isEmpty();
    }
}
