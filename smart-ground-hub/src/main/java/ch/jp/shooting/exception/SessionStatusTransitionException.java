package ch.jp.shooting.exception;

import org.jspecify.annotations.NullMarked;

/**
 * Exception: Session-Status-Übergang ist nicht erlaubt.
 */
@NullMarked
public class SessionStatusTransitionException extends RuntimeException {
    public SessionStatusTransitionException(String message) {
        super(message);
    }

    public SessionStatusTransitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
