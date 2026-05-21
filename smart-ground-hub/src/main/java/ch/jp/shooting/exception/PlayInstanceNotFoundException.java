package ch.jp.shooting.exception;

import java.util.UUID;

public class PlayInstanceNotFoundException extends NotFoundException {
    public PlayInstanceNotFoundException(UUID id) {
        super("Play-Instanz nicht gefunden: " + id);
    }
}
