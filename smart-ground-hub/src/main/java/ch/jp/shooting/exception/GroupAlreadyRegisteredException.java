package ch.jp.shooting.exception;

import org.jspecify.annotations.NullMarked;

/**
 * Exception: Eine andere Gruppe ist bereits an diesem Bereich registriert.
 */
@NullMarked
public class GroupAlreadyRegisteredException extends RuntimeException {
    public GroupAlreadyRegisteredException(String message) {
        super(message);
    }

    public GroupAlreadyRegisteredException(String message, Throwable cause) {
        super(message, cause);
    }
}
