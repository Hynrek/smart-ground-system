package ch.jp.shooting.node.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.List;

record HkdfVector(String hash, String ikm, String salt, String info, int l, String prk, String okm) {
}

record GcmVector(String name, String key, String iv, String aad, String plaintext, String ciphertext, String tag) {
}

record Fixture(List<HkdfVector> hkdf_sha256_rfc5869, List<GcmVector> aes256_gcm) {
}

final class CryptoTestVectors {

    private static final String FIXTURE_PATH = "../docs/espnow/crypto-test-vectors.json";

    private CryptoTestVectors() {
    }

    static Fixture load() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(new File(FIXTURE_PATH), Fixture.class);
        } catch (IOException e) {
            throw new IllegalStateException("Krypto-Test-Vektoren nicht lesbar: " + FIXTURE_PATH, e);
        }
    }

    static byte[] hex(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
