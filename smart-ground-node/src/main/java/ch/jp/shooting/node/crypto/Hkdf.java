package ch.jp.shooting.node.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

/**
 * HKDF (RFC 5869) auf Basis von HMAC-SHA256 — leitet K_S aus K_Box ab (ADR-003).
 */
public final class Hkdf {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int HASH_LENGTH = 32;

    private Hkdf() {
    }

    public static byte[] extract(byte[] salt, byte[] ikm) {
        return hmac(salt, ikm);
    }

    public static byte[] expand(byte[] prk, byte[] info, int length) {
        int n = (length + HASH_LENGTH - 1) / HASH_LENGTH;
        byte[] okm = new byte[length];
        byte[] t = new byte[0];
        int copied = 0;
        for (int i = 1; i <= n; i++) {
            byte[] input = new byte[t.length + info.length + 1];
            System.arraycopy(t, 0, input, 0, t.length);
            System.arraycopy(info, 0, input, t.length, info.length);
            input[input.length - 1] = (byte) i;
            t = hmac(prk, input);
            int toCopy = Math.min(HASH_LENGTH, length - copied);
            System.arraycopy(t, 0, okm, copied, toCopy);
            copied += toCopy;
        }
        return okm;
    }

    private static byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 nicht verfuegbar", e);
        }
    }
}
