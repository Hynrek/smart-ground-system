package ch.jp.shooting.exception;

import java.util.UUID;

public class RangePositionOccupiedException extends RuntimeException {
    public RangePositionOccupiedException(UUID deviceId) {
        super("Gerät " + deviceId + " ist bereits einer anderen Position zugeordnet.");
    }
}
