package ch.jp.shooting.node.nodechannel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NodeChannelClientBackoffTest {

    @Test
    void backoff_growsExponentially_thenCaps() {
        long initial = 1000;
        long max = 30000;
        assertThat(NodeChannelClient.nextBackoffMs(0, initial, max)).isEqualTo(1000);
        assertThat(NodeChannelClient.nextBackoffMs(1, initial, max)).isEqualTo(2000);
        assertThat(NodeChannelClient.nextBackoffMs(2, initial, max)).isEqualTo(4000);
        assertThat(NodeChannelClient.nextBackoffMs(3, initial, max)).isEqualTo(8000);
        // caps at max regardless of how large the attempt count grows
        assertThat(NodeChannelClient.nextBackoffMs(10, initial, max)).isEqualTo(30000);
        assertThat(NodeChannelClient.nextBackoffMs(100, initial, max)).isEqualTo(30000);
    }
}
