package ch.jp.shooting.node.pairing;

import ch.jp.shooting.node.crypto.AesGcm;
import ch.jp.shooting.node.crypto.Hkdf;
import ch.jp.shooting.node.frame.FrameHeader;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Baut und parst die Pairing-Frames DISCOVER/OFFER/CONFIRM unter K_Box
 * (ADR-003, docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md Abschnitt 3).
 */
public final class PairingCodec {

    private static final int MIC_LENGTH = 16;
    private static final byte[] SESSION_KEY_INFO = "smart-ground-espnow-session".getBytes(StandardCharsets.UTF_8);
    private static final int SESSION_KEY_LENGTH = 32;

    private PairingCodec() {
    }

    public static byte[] buildDiscover(FrameHeader header, byte[] boxUuid, byte[] nonceB, byte[] kBox) {
        byte[] headerBytes = header.encode();
        byte[] body = concat(boxUuid, nonceB);
        byte[] mic = mic(kBox, concat(headerBytes, body));
        return concat(headerBytes, body, mic);
    }

    public static boolean verifyDiscover(byte[] frame, byte[] kBox) {
        byte[] headerBytes = Arrays.copyOfRange(frame, 0, FrameHeader.SIZE);
        byte[] body = Arrays.copyOfRange(frame, FrameHeader.SIZE, frame.length - MIC_LENGTH);
        byte[] mic = Arrays.copyOfRange(frame, frame.length - MIC_LENGTH, frame.length);
        byte[] expected = mic(kBox, concat(headerBytes, body));
        return Arrays.equals(mic, expected);
    }

    public static byte[] boxUuidOf(byte[] discoverFrame) {
        return Arrays.copyOfRange(discoverFrame, FrameHeader.SIZE, FrameHeader.SIZE + 16);
    }

    public static byte[] nonceBOf(byte[] discoverFrame) {
        return Arrays.copyOfRange(discoverFrame, FrameHeader.SIZE + 16, FrameHeader.SIZE + 24);
    }

    public static byte[] buildOffer(FrameHeader header, int radioId, int channel, byte[] nonceN, byte[] nonceB,
                                     byte[] kBox) {
        byte[] headerBytes = header.encode();
        byte[] gcmNonce = concat(new byte[4], nonceN);
        byte[] ciphertextAndTag = AesGcm.encrypt(kBox, gcmNonce, headerBytes, nonceB);
        byte[] body = concat(new byte[]{(byte) radioId, (byte) channel}, nonceN, ciphertextAndTag);
        return concat(headerBytes, body);
    }

    public static byte[] nonceBFromOffer(byte[] offerFrame, byte[] kBox) {
        byte[] headerBytes = Arrays.copyOfRange(offerFrame, 0, FrameHeader.SIZE);
        byte[] nonceN = Arrays.copyOfRange(offerFrame, FrameHeader.SIZE + 2, FrameHeader.SIZE + 10);
        byte[] ciphertextAndTag = Arrays.copyOfRange(offerFrame, FrameHeader.SIZE + 10, offerFrame.length);
        byte[] gcmNonce = concat(new byte[4], nonceN);
        try {
            return AesGcm.decrypt(kBox, gcmNonce, headerBytes, ciphertextAndTag);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("PAIR_OFFER: Entschluesselung fehlgeschlagen", e);
        }
    }

    public static int radioIdOf(byte[] offerFrame) {
        return offerFrame[FrameHeader.SIZE] & 0xFF;
    }

    public static int channelOf(byte[] offerFrame) {
        return offerFrame[FrameHeader.SIZE + 1] & 0xFF;
    }

    public static byte[] nonceNOfOffer(byte[] offerFrame) {
        return Arrays.copyOfRange(offerFrame, FrameHeader.SIZE + 2, FrameHeader.SIZE + 10);
    }

    public static byte[] buildConfirm(FrameHeader header, byte[] nonceN, byte[] kBox) {
        byte[] headerBytes = header.encode();
        byte[] mic = mic(kBox, concat(headerBytes, nonceN));
        return concat(headerBytes, nonceN, mic);
    }

    public static boolean verifyConfirm(byte[] frame, byte[] kBox) {
        byte[] headerBytes = Arrays.copyOfRange(frame, 0, FrameHeader.SIZE);
        byte[] nonceN = Arrays.copyOfRange(frame, FrameHeader.SIZE, frame.length - MIC_LENGTH);
        byte[] mic = Arrays.copyOfRange(frame, frame.length - MIC_LENGTH, frame.length);
        byte[] expected = mic(kBox, concat(headerBytes, nonceN));
        return Arrays.equals(mic, expected);
    }

    public static byte[] nonceNOfConfirm(byte[] confirmFrame) {
        return Arrays.copyOfRange(confirmFrame, FrameHeader.SIZE, confirmFrame.length - MIC_LENGTH);
    }

    public static byte[] deriveSessionKey(byte[] kBox, byte[] nonceB, byte[] nonceN) {
        byte[] salt = concat(nonceB, nonceN);
        byte[] prk = Hkdf.extract(salt, kBox);
        return Hkdf.expand(prk, SESSION_KEY_INFO, SESSION_KEY_LENGTH);
    }

    private static byte[] mic(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] full = mac.doFinal(message);
            return Arrays.copyOf(full, MIC_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 nicht verfuegbar", e);
        }
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) {
            total += p.length;
        }
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }
}
