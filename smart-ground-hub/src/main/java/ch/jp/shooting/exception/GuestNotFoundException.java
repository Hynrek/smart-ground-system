package ch.jp.shooting.exception;

import java.util.UUID;

public class GuestNotFoundException extends NotFoundException {
    public GuestNotFoundException(UUID id) {
        super("Gast nicht gefunden: " + id);
    }
}
