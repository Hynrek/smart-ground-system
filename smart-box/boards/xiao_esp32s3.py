from micropython import const
import esp

# --- KONFIGURATION ---
BOX_TYPE       = "xiao-esp32s3"
LED_PIN        = const(21)      # Onboard-LED des XIAO ESP32-S3
LED_ON         = 0              # aktiv-low: 0 = ein (invertierte LED-Logik)
WDT_TIMEOUT_MS = const(8000)


def board_init():
    """Unterdrückt ESP32-Debug-Ausgabe auf UART."""
    esp.osdebug(None)
