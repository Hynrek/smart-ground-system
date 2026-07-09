package ch.jp.shooting.exception;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Signalisiert einen Fehler beim Ansprechen des Mosquitto-Dynamic-Security-Plugins
 * über dessen {@code $CONTROL/dynamic-security/v1}-Kontroll-API (Anlegen/Löschen von
 * Broker-Logins pro SmartBox). Reiner MQTT-Pfad – KEIN REST-Fehler, daher nicht im
 * {@code GlobalExceptionHandler} gemappt (analog zu
 * {@code SmartBoxConfigPushService.FirmwareNotResolvedException}).
 *
 * <p>{@link #getDynsecError()} trägt – falls vorhanden – die vom Plugin gelieferte
 * Fehler-Zeichenkette (z.B. {@code "Client already exists"}, {@code "Client not found"}),
 * damit aufrufende Dienste bestimmte, harmlose Fehler idempotent behandeln können.
 */
@NullMarked
public class MqttDynsecException extends RuntimeException {

    // Vom Plugin exakt so gelieferte Fehlertexte (live verifiziert gegen mosquitto 2.1.2).
    public static final String ERR_CLIENT_ALREADY_EXISTS = "Client already exists";
    public static final String ERR_CLIENT_NOT_FOUND      = "Client not found";

    private final @Nullable String dynsecError;

    public MqttDynsecException(String message) {
        this(message, null, null);
    }

    public MqttDynsecException(String message, @Nullable String dynsecError) {
        this(message, dynsecError, null);
    }

    public MqttDynsecException(String message, @Nullable String dynsecError, @Nullable Throwable cause) {
        super(message, cause);
        this.dynsecError = dynsecError;
    }

    /** Der rohe {@code error}-Text aus der Plugin-Antwort, oder {@code null} bei lokalen Fehlern (z.B. Timeout). */
    public @Nullable String getDynsecError() {
        return dynsecError;
    }

    public boolean isClientAlreadyExists() {
        return ERR_CLIENT_ALREADY_EXISTS.equals(dynsecError);
    }

    public boolean isClientNotFound() {
        return ERR_CLIENT_NOT_FOUND.equals(dynsecError);
    }
}
