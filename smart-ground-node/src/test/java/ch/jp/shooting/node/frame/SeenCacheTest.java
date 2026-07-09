package ch.jp.shooting.node.frame;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeenCacheTest {

    private static final byte[] MAC_A = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
    private static final byte[] MAC_B = {0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f};

    @Test
    void firstSighting_isNotDuplicate() {
        SeenCache cache = new SeenCache(5000);
        assertThat(cache.isDuplicate(MAC_A, 1, 1_000)).isFalse();
    }

    @Test
    void repeatWithinWindow_isDuplicate() {
        SeenCache cache = new SeenCache(5000);
        cache.isDuplicate(MAC_A, 1, 1_000);
        assertThat(cache.isDuplicate(MAC_A, 1, 1_000 + 4_999)).isTrue();
    }

    @Test
    void repeatAfterWindowExpires_isNotDuplicate() {
        SeenCache cache = new SeenCache(5000);
        cache.isDuplicate(MAC_A, 1, 1_000);
        assertThat(cache.isDuplicate(MAC_A, 1, 1_000 + 5_000)).isFalse();
    }

    @Test
    void differentFrameId_sameMac_isNotDuplicate() {
        SeenCache cache = new SeenCache(5000);
        cache.isDuplicate(MAC_A, 1, 1_000);
        assertThat(cache.isDuplicate(MAC_A, 2, 1_000)).isFalse();
    }

    @Test
    void sameFrameId_differentMac_isNotDuplicate() {
        SeenCache cache = new SeenCache(5000);
        cache.isDuplicate(MAC_A, 1, 1_000);
        assertThat(cache.isDuplicate(MAC_B, 1, 1_000)).isFalse();
    }
}
