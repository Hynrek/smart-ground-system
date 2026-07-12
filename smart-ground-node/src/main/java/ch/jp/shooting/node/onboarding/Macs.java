package ch.jp.shooting.node.onboarding;

/** Wandelt MAC-Strings (Doppelpunkt- oder reine Hex-Form) in 6 rohe Bytes für den Frame-Header. */
public final class Macs {

    private Macs() {
    }

    public static byte[] parse(String mac) {
        String hex = mac.replace(":", "").replace("-", "");
        if (hex.length() != 12) {
            throw new IllegalArgumentException("MAC muss 6 Byte sein: " + mac);
        }
        byte[] out = new byte[6];
        for (int i = 0; i < 6; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
