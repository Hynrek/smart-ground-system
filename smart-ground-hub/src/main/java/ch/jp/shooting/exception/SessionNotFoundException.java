package ch.jp.shooting.exception;

import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/**
 * Exception: Session nicht gefunden.
 */
@NullMarked
public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(String message) {
        super(message);
    }

    public SessionNotFoundException(UUID sessionId) {
        super("Session not found: " + sessionId);
    }

    public SessionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
