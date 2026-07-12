package ch.jp.shooting.nodechannel;

/**
 * Ergebnis eines node-channel-Dispatch. Erwartete Zustände sind typisiert, nicht exceptional (die Spec).
 * COMMAND_OUTCOME_UNKNOWN heisst ausdrücklich NICHT „fehlgeschlagen": ein Timeout sagt nicht, ob das
 * Command lief — der Aufrufer liest den Zielzustand neu, statt einen Fehlschlag zu behaupten.
 */
public enum CommandOutcome {
    OK,
    REJECTED,
    COMMAND_OUTCOME_UNKNOWN,
    NODE_UNREACHABLE
}
