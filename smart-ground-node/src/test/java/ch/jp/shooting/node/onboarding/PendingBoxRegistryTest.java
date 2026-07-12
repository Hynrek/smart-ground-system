package ch.jp.shooting.node.onboarding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PendingBoxRegistryTest {

    private final PendingBoxRegistry registry = new PendingBoxRegistry();

    @Test
    void onHello_registersBox_listAndFindReturnIt() {
        registry.onHello("AA:BB:CC:DD:EE:01", -40, new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

        assertThat(registry.list()).hasSize(1);
        PendingBox box = registry.find("AA:BB:CC:DD:EE:01").orElseThrow();
        assertThat(box.mac()).isEqualTo("AA:BB:CC:DD:EE:01");
        assertThat(box.rssi()).isEqualTo(-40);
        assertThat(box.boxNonce()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(box.firstSeen()).isNotNull();
        assertThat(box.lastSeen()).isEqualTo(box.firstSeen());
    }

    @Test
    void onHello_sameMacTwice_keepsFirstSeenRefreshesLastSeen() throws InterruptedException {
        registry.onHello("AA:BB:CC:DD:EE:02", -50, new byte[8]);
        PendingBox first = registry.find("AA:BB:CC:DD:EE:02").orElseThrow();
        Thread.sleep(5);
        registry.onHello("AA:BB:CC:DD:EE:02", -30, new byte[]{9, 9, 9, 9, 9, 9, 9, 9});

        assertThat(registry.list()).hasSize(1);
        PendingBox second = registry.find("AA:BB:CC:DD:EE:02").orElseThrow();
        assertThat(second.firstSeen()).isEqualTo(first.firstSeen());
        assertThat(second.lastSeen()).isAfterOrEqualTo(first.lastSeen());
        assertThat(second.rssi()).isEqualTo(-30);
        assertThat(second.boxNonce()).containsExactly(9, 9, 9, 9, 9, 9, 9, 9);
    }

    @Test
    void remove_dropsBox() {
        registry.onHello("AA:BB:CC:DD:EE:03", -60, new byte[8]);
        registry.remove("AA:BB:CC:DD:EE:03");
        assertThat(registry.find("AA:BB:CC:DD:EE:03")).isEmpty();
        assertThat(registry.list()).isEmpty();
    }
}
