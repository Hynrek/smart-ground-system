package ch.jp.shooting.exception;

import java.util.UUID;

public class DeviceAlreadyAssignedException extends RuntimeException {

    public DeviceAlreadyAssignedException(UUID deviceId, UUID rangeId) {
        super("Device " + deviceId + " is already assigned to range " + rangeId);
    }
}