package ch.jp.shooting.exception;

import java.util.UUID;

public class PasseNotFoundException extends NotFoundException {
    public PasseNotFoundException(UUID id) {
        super("Passe nicht gefunden: " + id);
    }
}
