package ch.jp.shooting.exception;

import org.jspecify.annotations.NullMarked;

/**
 * Exception: Gruppen-Registrierung ist ungültig (Segment gehört nicht zu Bereich, etc.).
 */
@NullMarked
public class InvalidGroupRegistrationException extends RuntimeException {
    public InvalidGroupRegistrationException(String message) {
        super(message);
    }

    public InvalidGroupRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
