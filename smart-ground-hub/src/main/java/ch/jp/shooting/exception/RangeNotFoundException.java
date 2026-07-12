package ch.jp.shooting.exception;

import java.util.UUID;

public class RangeNotFoundException extends RuntimeException {

    public RangeNotFoundException(UUID id) {
        super("Range with id " + id + " not found");
    }
}