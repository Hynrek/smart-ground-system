package ch.jp.shooting.node.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.AEADBadTagException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AesGcmTest {

    @Test
    void encrypt_matchesAllFixtureVectors() {
        List<GcmVector> vectors = CryptoTestVectors.load().aes256_gcm();
        assertThat(vectors).isNotEmpty();

        for (GcmVector v : vectors) {
            byte[] key = CryptoTestVectors.hex(v.key());
            byte[] iv = CryptoTestVectors.hex(v.iv());
            byte[] aad = CryptoTestVectors.hex(v.aad());
            byte[] plaintext = CryptoTestVectors.hex(v.plaintext());
            byte[] expected = concat(CryptoTestVectors.hex(v.ciphertext()), CryptoTestVectors.hex(v.tag()));

            byte[] actual = AesGcm.encrypt(key, iv, aad, plaintext);
            assertThat(actual).as("vector " + v.name()).isEqualTo(expected);
        }
    }

    @Test
    void decrypt_matchesAllFixtureVectors() throws AEADBadTagException {
        List<GcmVector> vectors = CryptoTestVectors.load().aes256_gcm();

        for (GcmVector v : vectors) {
            byte[] key = CryptoTestVectors.hex(v.key());
            byte[] iv = CryptoTestVectors.hex(v.iv());
            byte[] aad = CryptoTestVectors.hex(v.aad());
            byte[] ciphertextAndTag = concat(CryptoTestVectors.hex(v.ciphertext()), CryptoTestVectors.hex(v.tag()));
            byte[] expectedPlaintext = CryptoTestVectors.hex(v.plaintext());

            byte[] actual = AesGcm.decrypt(key, iv, aad, ciphertextAndTag);
            assertThat(actual).as("vector " + v.name()).isEqualTo(expectedPlaintext);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
