package ch.jp.shooting.exception;

import ch.jp.shooting.model.OtaType;

public class OtaReleaseNotFoundException extends RuntimeException {
    public OtaReleaseNotFoundException(OtaType type, String version) {
        super("OTA-Release nicht gefunden: " + type + " " + version);
    }
}
