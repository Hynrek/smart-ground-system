package ch.jp.shooting.exception;

import java.util.UUID;

public class RangePositionNotFoundException extends RuntimeException {
    public RangePositionNotFoundException(UUID id) {
        super("Keine Position gefunden mit ID: " + id);
    }
}
