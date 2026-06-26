from micropython import const

# --- KONFIGURATION ---
BOX_TYPE       = "pico2w"
LED_PIN        = "LED"          # Pico-spezifischer benannter Pin
WDT_TIMEOUT_MS = const(8000)


def board_init():
    """Keine spezielle Initialisierung erforderlich."""
    pass
