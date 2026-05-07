package ch.jp.shooting.exception;

import java.util.UUID;

public class RangeHasDevicesException extends RuntimeException {

    public RangeHasDevicesException(UUID rangeId, int deviceCount) {
        super("Range " + rangeId + " has " + deviceCount + " device(s) assigned. Remove devices first.");
    }
}