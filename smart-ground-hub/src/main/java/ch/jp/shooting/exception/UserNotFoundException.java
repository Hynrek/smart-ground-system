package ch.jp.shooting.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID id) {
        super("User with id " + id + " not found");
    }

    public UserNotFoundException(String email) {
        super("User with email " + email + " not found");
    }

    private UserNotFoundException(String field, String value) {
        super("User with " + field + " " + value + " not found");
    }

    public static UserNotFoundException forQrToken(String qrToken) {
        return new UserNotFoundException("QR token", qrToken);
    }
}
