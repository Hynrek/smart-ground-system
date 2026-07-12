package ch.jp.shooting.model;

/**
 * Phasen eines Eliminierungs-Turniers.
 */
public enum BracketPhase {
    /**
     * Bracket wurde erstellt, Seeding läuft
     */
    SETUP,

    /**
     * Spieler wurden gesät, warten auf Startsignal
     */
    SEEDING,

    /**
     * Bracket läuft, Matches werden gespielt
     */
    IN_PROGRESS,

    /**
     * Finale wird gespielt oder wurde erreicht
     */
    FINALS,

    /**
     * Turnier abgeschlossen, Sieger ermittelt
     */
    COMPLETED
}
