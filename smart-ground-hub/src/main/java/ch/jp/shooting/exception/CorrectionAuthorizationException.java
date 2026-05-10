package ch.jp.shooting.exception;

import org.jspecify.annotations.NullMarked;

/**
 * Exception: Benutzer ist nicht berechtigt, Korrektionen durchzuführen (nicht Admin).
 */
@NullMarked
public class CorrectionAuthorizationException extends RuntimeException {
    public CorrectionAuthorizationException(String message) {
        super(message);
    }

    public CorrectionAuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
