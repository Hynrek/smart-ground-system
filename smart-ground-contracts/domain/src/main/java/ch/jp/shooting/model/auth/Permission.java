package ch.jp.shooting.model.auth;

/**
 * Alle Berechtigungen im System. Jede Rolle hat eine feste Menge davon.
 * Die action()-Methode liefert den DB-Schlüssel (lowercase).
 */
public enum Permission {

    MANAGE_USERS,
    MANAGE_RANGES,
    MANAGE_SERIES_TEMPLATES,
    MANAGE_PASSE_TEMPLATES,
    MANAGE_COMPETITIONS,
    OPERATE_RANGE,
    START_TRAINING,
    START_COMPETITION,
    MANAGE_SERIES,
    RESERVE_REMOTE,
    VIEW_REMOTE,
    PLAY_SERIES,
    PLAY_COMPETITION;

    public String action() {
        return name().toLowerCase();
    }
}
