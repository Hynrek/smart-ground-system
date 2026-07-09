package ch.jp.shooting.node.frame;

/**
 * Frame-Typ-Katalog aus docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md Abschnitt 2.
 * Nur PAIR_DISCOVER/PAIR_OFFER/PAIR_CONFIRM werden in Baustein B tatsaechlich gebaut/geparst; die
 * Betriebs-Typen sind hier schon als Konstanten vorhanden, weil das Enum sonst bei Baustein C erneut
 * aufgemacht werden muesste und Frame-Codes an genau einer Stelle stehen sollen.
 */
public enum FrameType {
    PAIR_DISCOVER((byte) 0x01),
    PAIR_OFFER((byte) 0x02),
    PAIR_CONFIRM((byte) 0x03),
    DISCOVERY((byte) 0x10),
    CONFIG((byte) 0x11),
    CONFIG_ACK((byte) 0x12),
    COMMAND((byte) 0x13),
    EXECUTED((byte) 0x14),
    HEARTBEAT((byte) 0x15);

    private final byte code;

    FrameType(byte code) {
        this.code = code;
    }

    public byte code() {
        return code;
    }

    public static FrameType fromCode(byte code) {
        for (FrameType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unbekannter Frame-Typ: 0x" + String.format("%02x", code));
    }
}
