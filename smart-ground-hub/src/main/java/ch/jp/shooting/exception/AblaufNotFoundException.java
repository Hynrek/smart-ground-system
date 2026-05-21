package ch.jp.shooting.exception;

import java.util.UUID;

public class AblaufNotFoundException extends NotFoundException {
    public AblaufNotFoundException(UUID id) {
        super("Ablauf nicht gefunden: " + id);
    }
}
