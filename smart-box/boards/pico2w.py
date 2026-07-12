from micropython import const

# --- KONFIGURATION ---
BOX_TYPE       = "pico2w"
LED_PIN        = "LED"          # Pico-spezifischer benannter Pin
LED_ON         = 1              # aktiv-high: 1 = ein
WDT_TIMEOUT_MS = const(8000)


def board_init():
    """Keine spezielle Initialisierung erforderlich."""
    pass
