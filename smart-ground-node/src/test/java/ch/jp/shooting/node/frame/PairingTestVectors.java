package ch.jp.shooting.node.frame;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

record PairingConstants(String k_box, String box_uuid, String box_mac, String node_mac, String dest_broadcast,
                         String nonce_b, String nonce_n, String session_key_info_utf8, String session_key_info_hex) {
}

record PairDiscoverVector(String dest_mac, String src_mac, int frame_id, int ttl, int type, String header,
                           String box_uuid, String nonce_b, String mic, String frame) {
}

record PairOfferVector(String dest_mac, String src_mac, int frame_id, int ttl, int type, String header,
                        int radio_id, int channel, String nonce_n, String gcm_nonce, String plaintext_nonce_b,
                        String ciphertext, String tag, String frame) {
}

record PairConfirmVector(String dest_mac, String src_mac, int frame_id, int ttl, int type, String header,
                          String nonce_n, String mic, String frame) {
}

record SessionKeyVector(String salt, String info_hex, String k_s) {
}

record PairingFixture(PairingConstants constants, PairDiscoverVector pair_discover, PairOfferVector pair_offer,
                       PairConfirmVector pair_confirm, SessionKeyVector session_key) {
}

public final class PairingTestVectors {

    private static final String FIXTURE_PATH = "../docs/espnow/pairing-test-vectors.json";

    private PairingTestVectors() {
    }

    public static PairingFixture load() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(new File(FIXTURE_PATH), PairingFixture.class);
        } catch (IOException e) {
            throw new IllegalStateException("Pairing-Test-Vektoren nicht lesbar: " + FIXTURE_PATH, e);
        }
    }

    public static byte[] hex(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
