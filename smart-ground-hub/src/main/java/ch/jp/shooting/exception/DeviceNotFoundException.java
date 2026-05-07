package ch.jp.shooting.exception;

import java.util.UUID;

public class DeviceNotFoundException extends RuntimeException {

    public DeviceNotFoundException(UUID id) {
        super("Device with id " + id.toString() + " not found");
    }

    public DeviceNotFoundException(String id) {
        super("Device with id " + id + " not found");
    }
}