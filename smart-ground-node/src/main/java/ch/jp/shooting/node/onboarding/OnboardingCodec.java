package ch.jp.shooting.node.onboarding;

import ch.jp.shooting.node.frame.FrameHeader;

import java.util.Arrays;

/**
 * Baut und parst die unauthentifizierten Onboarding-Frames HELLO und ONBOARD_OFFER.
 * Kein MIC, kein AES-GCM — eine fabrikneue Box hat noch kein K_Box (siehe
 * docs/superpowers/specs/2026-07-11-smartbox-coupling-design.md).
 *
 * HELLO         = header(16) ‖ box_nonce(8)
 * ONBOARD_OFFER = header(16) ‖ echo_nonce(8) ‖ token(16) ‖ fingerprint(32)
 *                 ‖ ssid_len(1) ‖ ssid ‖ psk_len(1) ‖ psk ‖ url_len(1) ‖ url
 */
public final class OnboardingCodec {

    private static final int NONCE_LENGTH = 8;
    private static final int TOKEN_LENGTH = 16;
    private static final int FINGERPRINT_LENGTH = 32;
    private static final int OFFER_VARFIELDS_START = FrameHeader.SIZE + NONCE_LENGTH + TOKEN_LENGTH + FINGERPRINT_LENGTH;

    private OnboardingCodec() {
    }

    public static byte[] buildHello(FrameHeader header, byte[] boxNonce) {
        return concat(header.encode(), boxNonce);
    }

    public static byte[] boxNonceOf(byte[] helloFrame) {
        return Arrays.copyOfRange(helloFrame, FrameHeader.SIZE, FrameHeader.SIZE + NONCE_LENGTH);
    }

    public static byte[] buildOnboardOffer(FrameHeader header, byte[] echoNonce, byte[] token,
                                            byte[] fingerprint, byte[] ssid, byte[] psk, byte[] url) {
        return concat(header.encode(), echoNonce, token, fingerprint,
                lengthPrefixed(ssid), lengthPrefixed(psk), lengthPrefixed(url));
    }

    public static byte[] echoNonceOf(byte[] offerFrame) {
        int start = FrameHeader.SIZE;
        return Arrays.copyOfRange(offerFrame, start, start + NONCE_LENGTH);
    }

    public static byte[] tokenOf(byte[] offerFrame) {
        int start = FrameHeader.SIZE + NONCE_LENGTH;
        return Arrays.copyOfRange(offerFrame, start, start + TOKEN_LENGTH);
    }

    public static byte[] fingerprintOf(byte[] offerFrame) {
        int start = FrameHeader.SIZE + NONCE_LENGTH + TOKEN_LENGTH;
        return Arrays.copyOfRange(offerFrame, start, start + FINGERPRINT_LENGTH);
    }

    public static byte[] ssidOf(byte[] offerFrame) {
        return varFieldAt(offerFrame, OFFER_VARFIELDS_START, 0);
    }

    public static byte[] pskOf(byte[] offerFrame) {
        return varFieldAt(offerFrame, OFFER_VARFIELDS_START, 1);
    }

    public static byte[] urlOf(byte[] offerFrame) {
        return varFieldAt(offerFrame, OFFER_VARFIELDS_START, 2);
    }

    /** Liefert das indexTh laengen-praefixierte Feld ab {@code start} (0=ssid, 1=psk, 2=url). */
    private static byte[] varFieldAt(byte[] frame, int start, int index) {
        int pos = start;
        for (int i = 0; i < index; i++) {
            int len = frame[pos] & 0xFF;
            pos += 1 + len;
        }
        int len = frame[pos] & 0xFF;
        return Arrays.copyOfRange(frame, pos + 1, pos + 1 + len);
    }

    private static byte[] lengthPrefixed(byte[] field) {
        if (field.length > 255) {
            throw new IllegalArgumentException("Feld zu lang fuer 1-Byte-Laengenpraefix: " + field.length);
        }
        byte[] out = new byte[field.length + 1];
        out[0] = (byte) field.length;
        System.arraycopy(field, 0, out, 1, field.length);
        return out;
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
