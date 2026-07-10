package ch.jp.shooting.node.box;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// Hinweis: Spring Boot 4 hat den @DataJpaTest-Slice in ein separates Modul
// (spring-boot-data-jpa-test) ausgelagert, das in dieser Offline-Umgebung nicht
// verfuegbar ist (siehe smart-ground-hub / BoxRecordRepositoryTest fuer denselben
// Workaround). Daher wird der Persistenz-Test ueber den vollen Kontext
// (@SpringBootTest) mit dem file-basierten H2-Datasource aus application.properties
// gefahren.
@SpringBootTest
@Transactional
class BoxProvisioningServiceTest {

    @Autowired
    private BoxRecordRepository repository;

    private BoxProvisioningService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new BoxProvisioningService(repository, new KBoxGenerator());
    }

    @Test
    void provision_newMac_generatesFreshKBox() {
        BoxRecord record = service.provision("AA:BB:CC:DD:EE:01", "1.0.0", "micropython-1.23", "thrower", "{}");

        assertThat(record.getKBox()).hasSize(32);
        assertThat(repository.findByMacAddress("AA:BB:CC:DD:EE:01")).isPresent();
    }

    @Test
    void provision_sameMacTwice_returnsSameKBox() {
        BoxRecord first = service.provision("AA:BB:CC:DD:EE:02", "1.0.0", "micropython-1.23", "thrower", "{}");
        BoxRecord second = service.provision("AA:BB:CC:DD:EE:02", "1.0.1", "micropython-1.24", "thrower", "{}");

        assertThat(second.getKBox()).isEqualTo(first.getKBox());
        assertThat(second.getAppVersion()).isEqualTo("1.0.1");
        assertThat(second.getFirmwareVersion()).isEqualTo("micropython-1.24");
    }

    @Test
    void provision_differentMacs_generateDifferentKBoxes() {
        BoxRecord a = service.provision("AA:BB:CC:DD:EE:03", "1.0.0", "micropython-1.23", "thrower", "{}");
        BoxRecord b = service.provision("AA:BB:CC:DD:EE:04", "1.0.0", "micropython-1.23", "thrower", "{}");

        assertThat(a.getKBox()).isNotEqualTo(b.getKBox());
    }
}
