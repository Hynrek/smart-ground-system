package ch.jp.shooting.node.nodechannel;

/**
 * Ausführungs-Seam für ein über den node-channel empfangenes Command. Gibt ein Ergebnis-Constant zurück
 * ({@code NodeChannelTypes.OUTCOME_OK}/{@code OUTCOME_REJECTED}). Die echte ESP-NOW-Zustellung an die Box
 * ersetzt die Logging-Default-Impl später (Phase 2b) — dieselbe Seam-Form wie RadioSender.
 */
public interface NodeCommandHandler {
    String handle(String commandType, String payloadJson);
}
