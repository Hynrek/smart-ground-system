import time
from machine import Pin
from micropython import const

# --- KONFIGURATION ---
# Onboard-LED einmalig definieren – bleibt für Statusanzeige verfügbar
led = Pin("LED", Pin.OUT)

# Status-LED Timing (Boot-/Verbindungsanzeige)
STARTUP_BLINKS       = const(3)    # Anzahl Blinks beim Start
BLINK_ON_MS          = const(150)  # LED-AN-Dauer pro Blink
BLINK_OFF_MS         = const(150)  # LED-AUS-Dauer pro Blink
CONNECTING_TOGGLE_MS = const(250)  # Toggle-Intervall während WLAN-Verbindung


def led_on():
    """Schaltet die Onboard-LED dauerhaft ein."""
    led.value(1)


def led_off():
    """Schaltet die Onboard-LED aus."""
    led.value(0)


def led_toggle():
    """Wechselt den Zustand der Onboard-LED."""
    led.toggle()


def status_blink(times=STARTUP_BLINKS, on_ms=BLINK_ON_MS, off_ms=BLINK_OFF_MS):
    """Blinkt die Onboard-LED `times` mal und lässt sie danach ausgeschaltet."""
    for _ in range(times):
        led.value(1)
        time.sleep_ms(on_ms)
        led.value(0)
        time.sleep_ms(off_ms)


class GpioManager:
    """
    Verwaltet dynamisch zugewiesene GPIO-Pins basierend auf der Config-Payload
    des Backends.

    Struktur der device_map:
        { "<device-uuid>": {"pin": Pin-Objekt, "device": str, "direction": str, "command": str,
                            "signal_duration_ms": int, "delay_ms": int, "blocked": bool}, ... }

    Wird bei jedem Config-Push neu aufgebaut (reset() + setup() aufrufen).
    """

    def __init__(self):
        # Mapping: device_id (str) -> {"pin": Pin, "device": str, "direction": str, "command": str,
        #                                "signal_duration_ms": int, "delay_ms": int, "blocked": bool}
        self._devices = {}
        self._pulse_active = {}  # Tracking active pulse timers

    def setup(self, devices):
        """
        Initialisiert GPIO-Pins für alle Geräte aus der Config-Payload.

        :param devices: Liste von Dicts mit 'device_id', 'device', 'direction', 'command',
                        'signal_duration_ms', 'delay_ms', 'blocked'.
        """
        for dev in devices:
            device_id = dev.get("device_id", "")
            device_kind = dev.get("device", "GPIO")
            direction = dev.get("direction", "OUTPUT")
            command = dev.get("command", "")
            signal_duration_ms = dev.get("signal_duration_ms", 0)
            delay_ms = dev.get("delay_ms")
            blocked = dev.get("blocked", False)

            if not device_id or not command:
                print("Ungültiger Geräte-Eintrag in Config, übersprungen:", dev)
                continue

            try:
                # Nur GPIO-Geräte brauchen einen Pin; LED-Befehle interpretieren den command string
                pin = None
                if device_kind == "GPIO":
                    gpio_pin = int(command)
                    pin = Pin(gpio_pin, Pin.OUT)
                    pin.value(0)

                self._devices[device_id] = {
                    "pin": pin,
                    "device": device_kind,
                    "direction": direction,
                    "command": command,
                    "signal_duration_ms": signal_duration_ms,
                    "delay_ms": delay_ms,
                    "blocked": blocked
                }
                print("GPIO initialisiert: device={} type={} cmd={} duration_ms={}".format(
                    device_id, device_kind, command, signal_duration_ms))
            except Exception as e:
                print("GPIO-Initialisierung fehlgeschlagen: device={} fehler={}".format(device_id, e))

    def reset(self):
        """
        Schaltet alle verwalteten Pins ab und leert die Zuordnungstabelle.
        Wird vor jedem Config-Update aufgerufen.
        """
        for dev_info in self._devices.values():
            try:
                if dev_info["device"] == "GPIO" and dev_info["pin"] is not None:
                    dev_info["pin"].value(0)
                elif dev_info["device"] == "LED":
                    led.value(0)
            except Exception:
                pass
        self._devices.clear()
        self._pulse_active.clear()

    def set(self, device_id, value, signal_duration_ms=None, delay_ms=None):
        """
        Setzt den Ausgangszustand eines Pins.
        Für GPIO-Geräte und LED-Geräte mit Signal-Duration wird automatisch timed pulse ausgelöst.

        :param device_id:         UUID des Geräts (str).
        :param value:             1 = EIN, 0 = AUS.
        :param signal_duration_ms: Überschreibt den konfigurierten Wert, wenn angegeben.
        :param delay_ms:          Überschreibt den konfigurierten Delay-Wert, wenn angegeben.
        :returns:                 True bei Erfolg, False wenn device_id unbekannt, blockiert, oder OFF-Befehl für LED.
        """
        entry = self._devices.get(device_id)
        if entry is None or entry["blocked"]:
            return False

        # LED-Geräte akzeptieren nur ON-Befehle (value=1)
        if entry["device"] == "LED" and value == 0:
            print("Fehler: OFF-Befehl für LED-Gerät blockiert: device={}".format(device_id))
            return False

        # Befehlsspezifische Werte haben Vorrang vor der gespeicherten Konfiguration
        effective_delay_ms = delay_ms if delay_ms is not None else entry["delay_ms"]
        effective_duration_ms = signal_duration_ms if signal_duration_ms is not None else entry["signal_duration_ms"]

        # Respektiere Delay vor Ausführung
        if effective_delay_ms:
            time.sleep_ms(effective_delay_ms)

        device_kind = entry["device"]

        if device_kind == "GPIO":
            # Timed pulse – fahre Pin HIGH für signal_duration_ms, dann reset
            pin = entry["pin"]
            if value == 1 and effective_duration_ms > 0:
                pin.value(1)
                # ticks_ms() + ticks_add/ticks_diff für ms-genaue Pulse (time.time() ist nur sekundengenau)
                self._pulse_active[device_id] = time.ticks_add(
                    time.ticks_ms(), effective_duration_ms)
                print("GPIO-Puls gestartet: device={} duration_ms={}".format(
                    device_id, effective_duration_ms))
                return True
            pin.value(value)
            self._pulse_active.pop(device_id, None)

        elif device_kind == "LED":
            # LED-Puls – fahre LED HIGH für signal_duration_ms, dann reset
            if value == 1 and effective_duration_ms > 0:
                led.value(1)
                self._pulse_active[device_id] = time.ticks_add(
                    time.ticks_ms(), effective_duration_ms)
                print("LED-Puls gestartet: device={} duration_ms={}".format(
                    device_id, effective_duration_ms))
                return True
            led.value(value)
            self._pulse_active.pop(device_id, None)

        return True

    def known_ids(self):
        """Gibt alle bekannten device_ids zurück."""
        return list(self._devices.keys())

    def update_pulses(self):
        """
        Prüft alle aktiven Pulse (GPIO und LED) und schaltet sie aus, wenn die Duration
        überschritten wurde. Sollte regelmäßig aus dem Main-Loop aufgerufen werden.
        Verwendet ticks_ms()/ticks_diff() für ms-genaue Auflösung und korrekte
        Behandlung des 32-Bit-Überlaufs.
        """
        now = time.ticks_ms()
        expired = []
        for device_id, end_time in self._pulse_active.items():
            if time.ticks_diff(now, end_time) >= 0:
                dev_info = self._devices.get(device_id)
                if dev_info:
                    if dev_info["device"] == "GPIO":
                        dev_info["pin"].value(0)
                    elif dev_info["device"] == "LED":
                        led.value(0)
                    print("Puls beendet: device={} type={}".format(device_id, dev_info["device"]))
                expired.append(device_id)

        for device_id in expired:
            self._pulse_active.pop(device_id)


# Globale Instanz – wird von mqttutils importiert und in main.py konfiguriert
gpio_manager = GpioManager()
