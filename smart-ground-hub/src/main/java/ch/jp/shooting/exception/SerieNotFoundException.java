package ch.jp.shooting.exception;

import java.util.UUID;

public class SerieNotFoundException extends NotFoundException {
    public SerieNotFoundException(UUID id) {
        super("Serie nicht gefunden: " + id);
    }
}
