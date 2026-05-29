package ch.jp.shooting.exception;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
