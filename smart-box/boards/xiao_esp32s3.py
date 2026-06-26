from micropython import const
import esp

BOX_TYPE       = "xiao-esp32s3"
LED_PIN        = const(21)      # Onboard-LED des XIAO ESP32-S3
WDT_TIMEOUT_MS = const(8000)


def board_init():
    esp.osdebug(None)           # ESP32-Debug-Ausgabe auf UART unterdrücken
