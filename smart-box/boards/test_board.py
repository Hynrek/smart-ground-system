# --- KONFIGURATION ---
BOX_TYPE       = "test-board"
LED_PIN        = 0              # Neutraler Pin für CPython-Tests
LED_ON         = 1              # aktiv-high (neutraler Standard für Tests)
WDT_TIMEOUT_MS = 8000


def board_init():
    """Keine spezielle Initialisierung erforderlich."""
    pass
