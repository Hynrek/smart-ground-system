package ch.jp.shooting.node.crypto;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

/**
 * AES-256-GCM ueber javax.crypto — verschluesselt Betriebs- und Pairing-Frames unter K_Box/K_S
 * (ADR-002/ADR-003). Ein-/Ausgabe ist ciphertext||tag konkateniert (16-Byte-Tag am Ende),
 * passend zum Frame-Body-Layout aus
 * docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md Abschnitt 4.
 */
public final class AesGcm {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BITS = 128;

    private AesGcm() {
    }

    public static byte[] encrypt(byte[] key, byte[] iv, byte[] aad, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(aad);
            return cipher.doFinal(plaintext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM-Verschluesselung fehlgeschlagen", e);
        }
    }

    public static byte[] decrypt(byte[] key, byte[] iv, byte[] aad, byte[] ciphertextAndTag) throws AEADBadTagException {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(aad);
            return cipher.doFinal(ciphertextAndTag);
        } catch (AEADBadTagException e) {
            throw e;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM-Entschluesselung fehlgeschlagen", e);
        }
    }
}
