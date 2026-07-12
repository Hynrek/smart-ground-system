package ch.jp.smartground.nodechannel;

/** Konstanten des node-channel-Protokolls. */
public final class NodeChannelTypes {

    private NodeChannelTypes() {}

    /** Schema-Version im Envelope-Feld {@code v}. Unbekannte Versionen/Typen werden ignoriert (geloggt). */
    public static final int PROTOCOL_VERSION = 1;

    public static final String TYPE_HELLO = "HELLO";
    public static final String TYPE_HELLO_ACK = "HELLO_ACK";
    public static final String TYPE_HEARTBEAT = "HEARTBEAT";
    public static final String TYPE_COMMAND = "COMMAND";
    public static final String TYPE_COMMAND_ACK = "COMMAND_ACK";

    public static final String OUTCOME_OK = "OK";
    public static final String OUTCOME_REJECTED = "REJECTED";
}
