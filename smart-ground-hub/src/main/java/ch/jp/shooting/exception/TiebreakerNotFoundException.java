package ch.jp.shooting.exception;

import java.util.UUID;

public class TiebreakerNotFoundException extends RuntimeException {
    public TiebreakerNotFoundException(UUID id) {
        super("Tiebreaker not found: " + id);
    }
}
