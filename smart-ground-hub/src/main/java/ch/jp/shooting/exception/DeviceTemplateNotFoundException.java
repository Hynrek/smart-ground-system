package ch.jp.shooting.exception;

import org.jspecify.annotations.NullMarked;
import java.util.UUID;

@NullMarked
public class DeviceTemplateNotFoundException extends RuntimeException {
    public DeviceTemplateNotFoundException(UUID id) {
        super("DeviceTemplate nicht gefunden: " + id);
    }
}
