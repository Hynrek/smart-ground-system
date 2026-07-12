# Firmware Lean Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve the firmware architecture-review findings — delete the rate limiter and auto-block, make the GPIO scheduler single-phase and non-blocking (delay removed), split discovery into a boot-only announce + 20 s status heartbeat, add a watchdog, plus small cleanups — backed by a new host test harness; trim the matching delay fields from the backend MQTT contract.

**Architecture:** MicroPython firmware on a Pico 2W with a synchronous polling loop. Logic that is host-testable (security routing, scheduler timing, config parsing, payload shapes, watchdog-fed sleep) is covered by stdlib `unittest` with fake `machine`/`network`/`umqtt`/`time` modules and a controllable clock. Hardware-only wiring (real GPIO, WDT, WiFi) is validated by a manual Pico smoke test. The backend already supports heartbeats and REST-triggered config pushes, so the only backend change is removing the now-unused delay fields from the config-push and command payloads.

**Tech Stack:** MicroPython 1.23+, Python 3.14 + `unittest` (host tests), Spring Boot 4 / Java 25 (backend), Jackson, Mosquitto MQTT.

**Reference spec:** `docs/superpowers/specs/2026-06-26-firmware-lean-pass-design.md`

**Conventions (from `smart-box/CLAUDE.md`):** German inline comments, English identifiers, `micropython.const()` for integer constants, config constants under a `# --- KONFIGURATION ---` block, `except Exception as e: print(...)` (never silent), preserve the safe-exit pattern in `main.py`.

**Test run command (from the `smart-box/` directory):**
```
python -m unittest discover -s tests -t . -v
```

---

## File Structure

**Firmware — create:**
- `tests/__init__.py` — puts the repo root on `sys.path`, installs the fake modules
- `tests/_stubs.py` — fake `machine`, `micropython`, `network`, `umqtt.simple`; controllable clock patched onto the real `time` module
- `tests/test_scheduler.py` — `GpioManager` timing + busy-reject
- `tests/test_security.py` — `message_callback` routing/security
- `tests/test_config.py` — `_handle_config` + `_update_known_devices`
- `tests/test_publish.py` — discovery + heartbeat payloads
- `tests/test_feed_sleep.py` — watchdog-fed chunked sleep

**Firmware — modify:**
- `hardware.py` — single-phase scheduler, busy-reject, drop `delay_ms`, rename `update_pulses`→`tick`, add `feed_sleep_ms`
- `mqttutils.py` — delete rate limiter + auto-block, busy-aware ON/OFF path, `publish_discovery` signature, add `publish_heartbeat`, prune `_blocked_devices`, `reconnect_mqtt(wdt=…)`
- `networkutils.py` — `connect_wifi`/`reconnect_wifi` feed the watchdog
- `main.py` — boot-only discovery, heartbeat loop, `broker_port`, WDT
- `CLAUDE.md` — security model, scheduler, heartbeat, WDT, QoS

**Backend — modify:**
- `config/MqttCommandPublisher.java` — drop `delaySignalDurationMs` from record + method
- `service/RangePositionService.java` — drop delay arg at the call site
- `api/DeviceController.java` — drop delay arg at the call site
- `config/SmartBoxConfigPushService.java` — drop `delay_ms` from `DeviceConfigEntry` + `buildPayload`
- `CLAUDE.md` — document the deferred backend-side delay as an OPEN item

---

## Task 1: Test harness scaffold

**Files:**
- Create: `tests/__init__.py`
- Create: `tests/_stubs.py`
- Create: `tests/test_smoke.py` (temporary; deleted at the end of this task)

- [ ] **Step 1: Create the stub module**

Create `tests/_stubs.py`:

```python
"""Fake MicroPython-Module + steuerbare Uhr für Host-Tests (CPython)."""
import sys
import types
import time as _time

# --- Steuerbare Uhr (ersetzt time.ticks_* / sleep_ms) ---
class _Clock:
    def __init__(self):
        self.ms = 0
    def advance(self, d):
        self.ms += d
    def reset(self):
        self.ms = 0

clock = _Clock()

def _ticks_ms():
    return clock.ms

def _ticks_add(t, d):
    return t + d

def _ticks_diff(a, b):
    return a - b

def _sleep_ms(d):
    clock.advance(d)

def _sleep(s):
    clock.advance(int(s * 1000))

# Reale time-Modul-Attribute ergänzen statt ersetzen (bricht unittest nicht).
_time.ticks_ms = _ticks_ms
_time.ticks_add = _ticks_add
_time.ticks_diff = _ticks_diff
_time.sleep_ms = _sleep_ms
_time.sleep = _sleep

# --- machine ---
machine = types.ModuleType("machine")

class Pin:
    OUT = 1
    IN = 0
    def __init__(self, id, mode=None):
        self.id = id
        self._v = 0
        # Ungültige GPIO-Nummer simulieren (Pico 2W: 0..40)
        if isinstance(id, int) and id > 40:
            raise ValueError("ungueltiger Pin: {}".format(id))
    def value(self, v=None):
        if v is None:
            return self._v
        self._v = 1 if v else 0
    def toggle(self):
        self._v = 0 if self._v else 1

class WDT:
    def __init__(self, timeout=0):
        self.timeout = timeout
        self.fed = 0
    def feed(self):
        self.fed += 1

def _reset():
    raise SystemExit("machine.reset")

machine.Pin = Pin
machine.WDT = WDT
machine.reset = _reset
sys.modules["machine"] = machine

# --- micropython ---
micropython = types.ModuleType("micropython")
micropython.const = lambda x: x
sys.modules["micropython"] = micropython

# --- network ---
network = types.ModuleType("network")

class WLAN:
    STA_IF = 0
    AP_IF = 1
    def __init__(self, iface=0):
        self.iface = iface
    def active(self, *a):
        return True
    def isconnected(self):
        return True
    def connect(self, *a):
        pass
    def config(self, what=None, **kw):
        if what == "mac":
            return b"\xaa\xbb\xcc\xdd\xee\xff"
        return None
    def ifconfig(self):
        return ("192.168.1.42", "255.255.255.0", "192.168.1.1", "192.168.1.1")
    def status(self):
        return 3

network.WLAN = WLAN
network.STA_IF = 0
network.AP_IF = 1
sys.modules["network"] = network

# --- umqtt.simple ---
umqtt = types.ModuleType("umqtt")
umqtt_simple = types.ModuleType("umqtt.simple")

class MQTTClient:
    def __init__(self, client_id, broker, port=1883, keepalive=0):
        self.client_id = client_id
        self.broker = broker
        self.port = port
        self.published = []
        self.subscribed = []
        self.cb = None
        self.connected = False
    def set_callback(self, cb):
        self.cb = cb
    def connect(self):
        self.connected = True
    def subscribe(self, topic):
        self.subscribed.append(topic)
    def publish(self, topic, payload):
        self.published.append((topic, payload))
    def check_msg(self):
        pass
    def disconnect(self):
        self.connected = False

umqtt_simple.MQTTClient = MQTTClient
sys.modules["umqtt"] = umqtt
sys.modules["umqtt.simple"] = umqtt_simple
```

- [ ] **Step 2: Create the package init that installs stubs + path**

Create `tests/__init__.py`:

```python
import os
import sys

# Repo-Wurzel (smart-box/) auf den Pfad, damit `import hardware` etc. funktioniert
_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)

# Fakes installieren, BEVOR Firmware-Module importiert werden
from . import _stubs  # noqa: E402,F401
```

- [ ] **Step 3: Create a temporary smoke test**

Create `tests/test_smoke.py`:

```python
from tests import _stubs
import unittest


class SmokeTest(unittest.TestCase):
    def test_stubs_import_firmware_modules(self):
        import hardware
        import mqttutils
        import networkutils
        # Steuerbare Uhr funktioniert
        _stubs.clock.reset()
        import time
        self.assertEqual(time.ticks_ms(), 0)
        _stubs.clock.advance(500)
        self.assertEqual(time.ticks_ms(), 500)
        # Onboard-LED ist ein Stub-Pin
        self.assertEqual(hardware.led.value(), 0)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 4: Run the smoke test**

Run (from `smart-box/`): `python -m unittest tests.test_smoke -v`
Expected: PASS (1 test). This proves the firmware modules import cleanly under the fakes.

- [ ] **Step 5: Delete the temporary smoke test**

Delete `tests/test_smoke.py` (real tests replace it in later tasks).

- [ ] **Step 6: Commit**

```bash
git add tests/__init__.py tests/_stubs.py
git commit -m "[firmware] add host test harness (stubs + controllable clock)"
```

---

## Task 2: Single-phase scheduler + busy-reject + delay removal (`hardware.py`)

**Files:**
- Modify: `hardware.py` — `GpioManager.setup`, `GpioManager.set`, `GpioManager.update_pulses`→`tick`; add `feed_sleep_ms`
- Test: `tests/test_scheduler.py`

- [ ] **Step 1: Write the failing scheduler tests**

Create `tests/test_scheduler.py`:

```python
from tests import _stubs
import unittest
import hardware


def _gpio(device_id, pin, duration=0, blocked=False):
    return {
        "device_id": device_id, "alias": "d", "device": "GPIO",
        "direction": "OUTPUT", "command": str(pin),
        "signal_duration_ms": duration, "blocked": blocked,
    }


def _led(device_id, duration=0):
    return {
        "device_id": device_id, "alias": "l", "device": "LED",
        "direction": "OUTPUT", "command": "ON",
        "signal_duration_ms": duration, "blocked": False,
    }


class SchedulerTest(unittest.TestCase):
    def setUp(self):
        _stubs.clock.reset()
        self.gm = hardware.GpioManager()

    def _pin(self, device_id):
        return self.gm._devices[device_id]["pin"].value()

    def test_pulse_starts_and_expires(self):
        self.gm.setup([_gpio("a", 15, duration=500)])
        self.assertTrue(self.gm.set("a", 1))
        self.assertEqual(self._pin("a"), 1)
        _stubs.clock.advance(499)
        self.gm.tick()
        self.assertEqual(self._pin("a"), 1)          # noch aktiv
        _stubs.clock.advance(1)
        self.gm.tick()
        self.assertEqual(self._pin("a"), 0)          # abgelaufen
        self.assertNotIn("a", self.gm._pulse_active)

    def test_busy_reject(self):
        self.gm.setup([_gpio("a", 15, duration=500)])
        self.assertTrue(self.gm.set("a", 1))
        self.assertFalse(self.gm.set("a", 1))        # läuft noch -> abgelehnt
        self.assertEqual(self._pin("a"), 1)

    def test_off_cancels_pulse(self):
        self.gm.setup([_gpio("a", 15, duration=500)])
        self.gm.set("a", 1)
        self.assertTrue(self.gm.set("a", 0))
        self.assertEqual(self._pin("a"), 0)
        self.assertNotIn("a", self.gm._pulse_active)

    def test_no_duration_is_not_busy(self):
        self.gm.setup([_gpio("a", 15, duration=0)])
        self.assertTrue(self.gm.set("a", 1))
        self.assertEqual(self._pin("a"), 1)
        self.assertNotIn("a", self.gm._pulse_active)  # kein Puls -> nicht beschäftigt
        self.assertTrue(self.gm.set("a", 1))          # erneut erlaubt

    def test_duration_override(self):
        self.gm.setup([_gpio("a", 15, duration=100)])
        self.gm.set("a", 1, 300)                       # Override 300 ms
        _stubs.clock.advance(150)
        self.gm.tick()
        self.assertEqual(self._pin("a"), 1)            # 150 < 300 -> noch an
        _stubs.clock.advance(160)
        self.gm.tick()
        self.assertEqual(self._pin("a"), 0)

    def test_led_rejects_off(self):
        self.gm.setup([_led("l")])
        self.assertFalse(self.gm.set("l", 0))

    def test_blocked_entry_rejected(self):
        self.gm.setup([_gpio("a", 15, blocked=True)])
        self.assertFalse(self.gm.set("a", 1))

    def test_set_has_no_delay_param(self):
        import inspect
        params = list(inspect.signature(hardware.GpioManager.set).parameters)
        self.assertEqual(params, ["self", "device_id", "value", "signal_duration_ms"])


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run to verify failure**

Run: `python -m unittest tests.test_scheduler -v`
Expected: FAIL — `set()` still has a `delay_ms` param and `tick` does not exist (`AttributeError`).

- [ ] **Step 3: Rewrite `GpioManager.setup` (drop `delay_ms`)**

In `hardware.py`, replace the body of `setup()` so it no longer reads or stores `delay_ms`. The device-map entry loses the `"delay_ms"` key:

```python
    def setup(self, devices):
        """
        Initialisiert GPIO-Pins für alle Geräte aus der Config-Payload.

        :param devices: Liste von Dicts mit 'device_id', 'device', 'direction',
                        'command', 'signal_duration_ms', 'blocked'.
        """
        for dev in devices:
            device_id = dev.get("device_id", "")
            device_kind = dev.get("device", "GPIO")
            direction = dev.get("direction", "OUTPUT")
            command = dev.get("command", "")
            signal_duration_ms = dev.get("signal_duration_ms", 0)
            blocked = dev.get("blocked", False)

            if not device_id or not command:
                print("Ungültiger Geräte-Eintrag in Config, übersprungen:", dev)
                continue

            try:
                # Nur GPIO-Geräte brauchen einen Pin; LED nutzt die Onboard-LED
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
                    "blocked": blocked
                }
                print("GPIO initialisiert: device={} type={} cmd={} duration_ms={}".format(
                    device_id, device_kind, command, signal_duration_ms))
            except Exception as e:
                print("GPIO-Initialisierung fehlgeschlagen: device={} fehler={}".format(device_id, e))
```

- [ ] **Step 4: Rewrite `GpioManager.set` (single-phase, busy-reject, no delay/sleep)**

Replace `set()` entirely:

```python
    def set(self, device_id, value, signal_duration_ms=None):
        """
        Setzt den Ausgangszustand eines Geräts (einphasig, nicht-blockierend).

        :param device_id:          UUID des Geräts (str).
        :param value:              1 = EIN, 0 = AUS.
        :param signal_duration_ms: Überschreibt den konfigurierten Wert, wenn angegeben.
        :returns: True bei Erfolg; False wenn unbekannt, blockiert, beschäftigt (laufender
                  Puls) oder OFF-Befehl für LED.
        """
        entry = self._devices.get(device_id)
        if entry is None or entry["blocked"]:
            return False

        # LED-Geräte akzeptieren nur ON-Befehle (value=1)
        if entry["device"] == "LED" and value == 0:
            print("Fehler: OFF-Befehl für LED-Gerät blockiert: device={}".format(device_id))
            return False

        # Busy-Reject: laufenden Puls nicht erneut auslösen
        if value == 1 and device_id in self._pulse_active:
            print("Gerät beschäftigt, Befehl ignoriert: device={}".format(device_id))
            return False

        effective_duration_ms = signal_duration_ms if signal_duration_ms is not None else entry["signal_duration_ms"]
        device_kind = entry["device"]

        if device_kind == "GPIO":
            pin = entry["pin"]
            if value == 1 and effective_duration_ms > 0:
                pin.value(1)
                self._pulse_active[device_id] = time.ticks_add(
                    time.ticks_ms(), effective_duration_ms)
                print("GPIO-Puls gestartet: device={} duration_ms={}".format(
                    device_id, effective_duration_ms))
                return True
            pin.value(value)
            self._pulse_active.pop(device_id, None)

        elif device_kind == "LED":
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
```

- [ ] **Step 5: Rename `update_pulses` → `tick`**

Rename the method and its docstring; the body is unchanged:

```python
    def tick(self):
        """
        Läuft jeden Main-Loop-Tick: beendet abgelaufene Pulse (GPIO + LED).
        Nutzt ticks_ms()/ticks_diff() für ms-Auflösung und korrekten 32-Bit-Überlauf.
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
```

- [ ] **Step 6: Run the scheduler tests**

Run: `python -m unittest tests.test_scheduler -v`
Expected: PASS (8 tests).

- [ ] **Step 7: Commit**

```bash
git add hardware.py tests/test_scheduler.py
git commit -m "[firmware] single-phase non-blocking scheduler with busy-reject; drop delay_ms"
```

---

## Task 3: Watchdog-fed chunked sleep (`hardware.py`)

**Files:**
- Modify: `hardware.py` — add `feed_sleep_ms`
- Test: `tests/test_feed_sleep.py`

- [ ] **Step 1: Write the failing test**

Create `tests/test_feed_sleep.py`:

```python
from tests import _stubs
import unittest
import hardware


class FeedSleepTest(unittest.TestCase):
    def setUp(self):
        _stubs.clock.reset()

    def test_chunks_and_feeds(self):
        wdt = _stubs.machine.WDT(timeout=8000)
        hardware.feed_sleep_ms(10000, wdt)            # 4000 + 4000 + 2000
        self.assertEqual(wdt.fed, 3)
        self.assertEqual(_stubs.clock.ms, 10000)

    def test_no_wdt_just_sleeps(self):
        hardware.feed_sleep_ms(5000, None)
        self.assertEqual(_stubs.clock.ms, 5000)

    def test_short_sleep_single_chunk(self):
        wdt = _stubs.machine.WDT()
        hardware.feed_sleep_ms(1000, wdt)
        self.assertEqual(wdt.fed, 1)
        self.assertEqual(_stubs.clock.ms, 1000)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run to verify failure**

Run: `python -m unittest tests.test_feed_sleep -v`
Expected: FAIL — `hardware` has no attribute `feed_sleep_ms`.

- [ ] **Step 3: Add `feed_sleep_ms` to `hardware.py`**

Add a `CHUNK` constant in the `# --- KONFIGURATION ---` block and the helper just after the LED helpers (before `class GpioManager`):

```python
FEED_SLEEP_CHUNK_MS  = const(4000)  # Max. Schlafstück, damit der Watchdog gefüttert wird


def feed_sleep_ms(total_ms, wdt=None, chunk_ms=FEED_SLEEP_CHUNK_MS):
    """
    Schläft total_ms und füttert dabei einen optionalen Watchdog in Stücken,
    damit lange Wartezeiten (z.B. Reconnect-Delays) keinen WDT-Reset auslösen.
    """
    remaining = total_ms
    while remaining > 0:
        step = chunk_ms if remaining > chunk_ms else remaining
        time.sleep_ms(step)
        if wdt is not None:
            wdt.feed()
        remaining -= step
```

- [ ] **Step 4: Run the test**

Run: `python -m unittest tests.test_feed_sleep -v`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add hardware.py tests/test_feed_sleep.py
git commit -m "[firmware] add watchdog-fed chunked sleep helper"
```

---

## Task 4: Delete rate limiter + auto-block; busy-aware ON/OFF (`mqttutils.py`)

**Files:**
- Modify: `mqttutils.py` — constants, globals, `_update_known_devices`, `_is_device_blocked`, `_admin_block_device`, `_admin_unblock_device`, `message_callback`; delete `_check_rate_limit`/`_record_failed_attempt`/`_clear_failed_attempts`
- Test: `tests/test_security.py`

- [ ] **Step 1: Write the failing security tests**

Create `tests/test_security.py`:

```python
from tests import _stubs
import json
import unittest
import mqttutils
import hardware

MAC = "aabbccddeeff"
CMD_TOPIC = ("smartboxes/%s/command" % MAC).encode()
EXEC_PREFIX = "smartboxes/%s/device/" % MAC


def _cmd(command, device_id, signal=None):
    d = {"command": command, "commandId": "c1", "deviceId": device_id}
    if signal is not None:
        d["signalDurationMs"] = signal
    return json.dumps(d).encode()


def _gpio(device_id, pin, duration=0):
    return {
        "device_id": device_id, "alias": "d", "device": "GPIO",
        "direction": "OUTPUT", "command": str(pin),
        "signal_duration_ms": duration, "blocked": False,
    }


class SecurityTest(unittest.TestCase):
    def setUp(self):
        _stubs.clock.reset()
        mqttutils._known_devices = set()
        mqttutils._blocked_devices = {}
        mqttutils._client_id = MAC
        mqttutils._mqtt_client = _stubs.umqtt_simple.MQTTClient("box", "broker")
        hardware.gpio_manager.reset()

    def _exec_acks(self):
        return [t for (t, _) in mqttutils._mqtt_client.published if t.startswith(EXEC_PREFIX)]

    def _arm(self, device_id, pin=15, duration=0):
        devs = [_gpio(device_id, pin, duration)]
        mqttutils._update_known_devices(devs)
        hardware.gpio_manager.setup(devs)

    def test_unknown_device_rejected_no_ack(self):
        mqttutils.message_callback(CMD_TOPIC, _cmd("ON", "ghost"))
        self.assertEqual(self._exec_acks(), [])

    def test_known_on_acks(self):
        self._arm("a")
        mqttutils.message_callback(CMD_TOPIC, _cmd("ON", "a"))
        self.assertEqual(len(self._exec_acks()), 1)

    def test_busy_on_not_acked_twice(self):
        self._arm("a", duration=500)
        mqttutils.message_callback(CMD_TOPIC, _cmd("ON", "a"))
        mqttutils.message_callback(CMD_TOPIC, _cmd("ON", "a"))  # busy
        self.assertEqual(len(self._exec_acks()), 1)

    def test_admin_block_then_on_ignored(self):
        self._arm("a")
        mqttutils.message_callback(CMD_TOPIC, _cmd("BLOCK", "a"))
        before = len(self._exec_acks())
        mqttutils.message_callback(CMD_TOPIC, _cmd("ON", "a"))
        self.assertEqual(len(self._exec_acks()), before)   # ON erzeugt kein neues ACK

    def test_unblock_restores(self):
        self._arm("a")
        mqttutils.message_callback(CMD_TOPIC, _cmd("BLOCK", "a"))
        mqttutils.message_callback(CMD_TOPIC, _cmd("UNBLOCK", "a"))
        n = len(self._exec_acks())
        mqttutils.message_callback(CMD_TOPIC, _cmd("ON", "a"))
        self.assertEqual(len(self._exec_acks()), n + 1)

    def test_no_autoblock_on_repeated_unknown(self):
        for _ in range(6):
            mqttutils.message_callback(CMD_TOPIC, _cmd("ON", "ghost"))
        self.assertEqual(self._exec_acks(), [])
        self.assertNotIn("ghost", mqttutils._blocked_devices)  # keine Auto-Blockierung

    def test_rate_limit_artifacts_removed(self):
        for name in ("_check_rate_limit", "_record_failed_attempt",
                     "_clear_failed_attempts", "_last_command_time", "_failed_commands"):
            self.assertFalse(hasattr(mqttutils, name), "soll entfernt sein: " + name)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run to verify failure**

Run: `python -m unittest tests.test_security -v`
Expected: FAIL — `test_rate_limit_artifacts_removed` fails (the symbols still exist) and the busy/no-autoblock behavior differs.

- [ ] **Step 3: Trim constants and globals**

In `mqttutils.py`, in the `# --- KONFIGURATION ---` block delete `RATE_LIMIT_S`, `MAX_FAILED_ATTEMPTS`, `BLOCK_DURATION_S`. Keep `ADMIN_BLOCK_TOKEN`. The remaining security constants block:

```python
ADMIN_BLOCK_TOKEN    = "ADMIN"  # Token für manuelle Admin-Blockierung (keine Auto-Freigabe)
```

In the module-global state block delete `_last_command_time` and `_failed_commands`; keep:

```python
_known_devices = set()  # Autorisierte Geräte-IDs
_blocked_devices = {}   # Admin-blockierte Geräte: device_id -> ADMIN_BLOCK_TOKEN
```

- [ ] **Step 4: Delete the rate-limit / failure-counter functions**

Delete these three functions entirely from `mqttutils.py`: `_check_rate_limit`, `_record_failed_attempt`, `_clear_failed_attempts`.

- [ ] **Step 5: Simplify `_is_device_blocked`**

Replace it with the admin-only form:

```python
def _is_device_blocked(device_id):
    """Prüft, ob ein Gerät administrativ blockiert ist (kein Auto-Block mehr)."""
    return _blocked_devices.get(device_id) == ADMIN_BLOCK_TOKEN
```

- [ ] **Step 6: Drop `_clear_failed_attempts` calls in the admin block helpers**

In `_admin_block_device`, remove the `_clear_failed_attempts(device_id)` line. In `_admin_unblock_device`, remove the `_clear_failed_attempts(device_id)` line. (Both functions otherwise unchanged.)

- [ ] **Step 7: Rewrite `_update_known_devices` to prune `_blocked_devices`**

```python
def _update_known_devices(devices):
    """
    Aktualisiert die Liste autorisierter Geräte aus der Config.
    Entfernt Admin-Blockierungen für nicht mehr konfigurierte Geräte,
    damit _blocked_devices nicht unbegrenzt wächst.
    """
    global _known_devices
    new_ids = set()
    for dev in devices:
        device_id = dev.get("device_id", "")
        if device_id:
            new_ids.add(device_id)

    stale = _known_devices - new_ids
    for device_id in stale:
        _blocked_devices.pop(device_id, None)

    _known_devices = new_ids
    print("Autorisierte Geräte aktualisiert: {} Gerät(e)".format(len(_known_devices)))
```

- [ ] **Step 8: Rewrite the ON/OFF section of `message_callback`**

Replace everything from `# --- Befehl parsen ---` through the end of the `try` body (before `finally:`) with:

```python
        # --- Befehl parsen ---
        try:
            data = json.loads(msg)
            command = data.get("command", "").upper()
            device_id = data.get("deviceId", None)
            signal_duration_ms = data.get("signalDurationMs", None)
            del data
        except (ValueError, AttributeError) as e:
            print("Fehler: Ungültiges JSON-Format:", e)
            return

        # --- Befehlsvalidierung ---
        if not device_id:
            print("Fehler: deviceId erforderlich, Befehl blockiert")
            return

        if command not in ("ON", "OFF", "BLOCK", "UNBLOCK"):
            print("Fehler: Ungültiger Befehl '{}', blockiert".format(command))
            return

        # --- Admin-Befehle (BLOCK/UNBLOCK) ---
        if command == "BLOCK":
            if _admin_block_device(device_id):
                _send_device_command_ack(device_id)
            return

        if command == "UNBLOCK":
            if _admin_unblock_device(device_id):
                _send_device_command_ack(device_id)
            return

        # --- Sicherheits-Checks für ON/OFF ---
        if device_id not in _known_devices:
            print("Fehler: Unbekanntes Gerät blockiert: device={}".format(device_id))
            return

        if _is_device_blocked(device_id):
            print("Fehler: Gerät blockiert: device={}".format(device_id))
            return

        # --- Befehl ausführen (ON/OFF) ---
        success = False
        if command == "ON":
            success = gpio_manager.set(device_id, 1, signal_duration_ms)
        elif command == "OFF":
            success = gpio_manager.set(device_id, 0, signal_duration_ms)

        # ACK nur bei tatsächlicher Annahme (busy/blockiert -> success False, kein ACK)
        if success:
            _send_device_command_ack(device_id)
            print("Befehl erfolgreich: device={} command={}".format(device_id, command))
```

(The `finally: gc.collect()` block stays exactly as-is. Note the removed `delaySignalDurationMs` parse and the removed `signal_duration_ms` override now passes through unchanged.)

- [ ] **Step 9: Run the security tests**

Run: `python -m unittest tests.test_security -v`
Expected: PASS (7 tests).

- [ ] **Step 10: Commit**

```bash
git add mqttutils.py tests/test_security.py
git commit -m "[firmware] delete rate limiter + auto-block; busy-aware ON/OFF, accept-time ACK"
```

---

## Task 5: Config handling regression coverage (`tests` only)

**Files:**
- Test: `tests/test_config.py`

This task locks the existing `_handle_config` behavior (now that it no longer carries delay) with tests; no production code changes are expected. If a test fails, fix `mqttutils._handle_config` minimally to match.

- [ ] **Step 1: Write the config tests**

Create `tests/test_config.py`:

```python
from tests import _stubs
import json
import unittest
import mqttutils
import hardware

MAC = "aabbccddeeff"
CFG_TOPIC = ("smartboxes/%s/config" % MAC).encode()
CFG_ACK = "smartboxes/%s/config/ack" % MAC


def _cfg(devices):
    return json.dumps({"devices": devices}).encode()


class ConfigTest(unittest.TestCase):
    def setUp(self):
        _stubs.clock.reset()
        mqttutils._known_devices = set()
        mqttutils._blocked_devices = {}
        mqttutils._client_id = MAC
        mqttutils._mqtt_client = _stubs.umqtt_simple.MQTTClient("box", "broker")
        hardware.gpio_manager.reset()
        # Flash-Schreiben in Tests unterdrücken
        self._saved = []
        mqttutils.save_device_config = lambda devs: self._saved.append(devs)

    def _acks(self):
        return [t for (t, _) in mqttutils._mqtt_client.published if t == CFG_ACK]

    def test_valid_config_builds_map_and_acks(self):
        devs = [{"device_id": "a", "alias": "d", "device": "GPIO", "direction": "OUTPUT",
                 "command": "15", "signal_duration_ms": 500, "blocked": False}]
        mqttutils.message_callback(CFG_TOPIC, _cfg(devs))
        self.assertEqual(hardware.gpio_manager.known_ids(), ["a"])
        self.assertEqual(mqttutils._known_devices, {"a"})
        self.assertEqual(len(self._acks()), 1)
        self.assertEqual(len(self._saved), 1)

    def test_empty_config_clears_and_acks(self):
        mqttutils.message_callback(CFG_TOPIC, _cfg([]))
        self.assertEqual(hardware.gpio_manager.known_ids(), [])
        self.assertEqual(len(self._acks()), 1)        # leere Config quittiert (Reset)

    def test_all_devices_fail_no_ack(self):
        # Pin > 40 lässt den Stub-Pin scheitern -> kein Gerät initialisiert
        devs = [{"device_id": "a", "alias": "d", "device": "GPIO", "direction": "OUTPUT",
                 "command": "99", "signal_duration_ms": 0, "blocked": False}]
        mqttutils.message_callback(CFG_TOPIC, _cfg(devs))
        self.assertEqual(hardware.gpio_manager.known_ids(), [])
        self.assertEqual(self._acks(), [])            # kein erfolgreiches Gerät -> kein ACK
        self.assertEqual(self._saved, [])

    def test_stale_admin_block_pruned_on_reconfig(self):
        mqttutils._blocked_devices = {"old": mqttutils.ADMIN_BLOCK_TOKEN}
        mqttutils._known_devices = {"old"}
        devs = [{"device_id": "new", "alias": "d", "device": "GPIO", "direction": "OUTPUT",
                 "command": "15", "signal_duration_ms": 0, "blocked": False}]
        mqttutils.message_callback(CFG_TOPIC, _cfg(devs))
        self.assertNotIn("old", mqttutils._blocked_devices)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the config tests**

Run: `python -m unittest tests.test_config -v`
Expected: PASS (4 tests). If `test_all_devices_fail_no_ack` fails, confirm `_handle_config` still guards `if initialized == 0 and len(devices) > 0: return` before saving/acking.

- [ ] **Step 3: Commit**

```bash
git add tests/test_config.py
git commit -m "[firmware] add config-handling regression tests"
```

---

## Task 6: Discovery → heartbeat split (`mqttutils.py`)

**Files:**
- Modify: `mqttutils.py` — `publish_discovery` signature; add `publish_heartbeat`; `update_device_pulses` calls `tick()`
- Test: `tests/test_publish.py`

- [ ] **Step 1: Write the failing publish tests**

Create `tests/test_publish.py`:

```python
from tests import _stubs
import json
import unittest
import mqttutils

MAC = "aabbccddeeff"


class PublishTest(unittest.TestCase):
    def setUp(self):
        _stubs.clock.reset()
        mqttutils._mqtt_client = _stubs.umqtt_simple.MQTTClient("box", "broker")

    def test_discovery_payload(self):
        ok = mqttutils.publish_discovery(MAC)
        self.assertTrue(ok)
        topic, payload = mqttutils._mqtt_client.published[-1]
        self.assertEqual(topic, "smartboxes/discovery")
        d = json.loads(payload)
        self.assertEqual(d["mac"], MAC)
        self.assertIn("firmwareVersion", d)
        self.assertIn("boxType", d)
        self.assertIn("ip", d)

    def test_heartbeat_payload(self):
        ok = mqttutils.publish_heartbeat(MAC)
        self.assertTrue(ok)
        topic, payload = mqttutils._mqtt_client.published[-1]
        self.assertEqual(topic, "smartboxes/%s/status" % MAC)
        self.assertEqual(json.loads(payload), {"mac": MAC})

    def test_discovery_no_client_returns_false(self):
        mqttutils._mqtt_client = None
        self.assertFalse(mqttutils.publish_discovery(MAC))

    def test_heartbeat_no_client_returns_false(self):
        mqttutils._mqtt_client = None
        self.assertFalse(mqttutils.publish_heartbeat(MAC))

    def test_discovery_signature_has_only_client_id(self):
        import inspect
        self.assertEqual(list(inspect.signature(mqttutils.publish_discovery).parameters),
                         ["client_id"])


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run to verify failure**

Run: `python -m unittest tests.test_publish -v`
Expected: FAIL — `publish_discovery` still takes `(broker, port, client_id)` and `publish_heartbeat` does not exist.

- [ ] **Step 3: Change `publish_discovery` signature and add `publish_heartbeat`**

Replace `publish_discovery` and add `publish_heartbeat` directly after it:

```python
def publish_discovery(client_id):
    """
    Veröffentlicht einmalig beim Boot eine Discovery-Nachricht über die bestehende
    MQTT-Verbindung, damit das Backend die Box erkennt und einen Config-Push auslöst.
    Firmware-Version/Box-Typ kommen aus systemconfig/firmware_config.json.

    Topic:   smartboxes/discovery
    Payload: { "mac", "firmwareVersion", "boxType", "ip" }
    """
    if _mqtt_client is None:
        print("MQTT-Verbindung nicht verfügbar – Discovery nicht möglich.")
        return False

    firmware = load_firmware_config()
    firmware_version = firmware.get("firmware_version", "unknown")
    box_type = firmware.get("box_type", "unknown")

    try:
        ip_address = network.WLAN(network.STA_IF).ifconfig()[0]
        payload = json.dumps({
            "mac":             client_id,
            "firmwareVersion": firmware_version,
            "boxType":         box_type,
            "ip":              ip_address,
        })
        _mqtt_client.publish("smartboxes/discovery", payload)
        print("Discovery gesendet:", payload)
        return True
    except Exception as e:
        print("Discovery fehlgeschlagen:", e)
        return False


def publish_heartbeat(client_id):
    """
    Veröffentlicht einen Heartbeat über die bestehende MQTT-Verbindung. Das Backend
    (SmartBoxStatusHandler) aktualisiert damit lastSeen + ONLINE, OHNE einen Config-Push
    auszulösen. Ersetzt das frühere periodische Discovery.

    Topic:   smartboxes/{mac}/status
    Payload: { "mac": "..." }
    """
    if _mqtt_client is None:
        print("MQTT-Verbindung nicht verfügbar – Heartbeat nicht möglich.")
        return False
    try:
        topic = "smartboxes/{}/status".format(client_id)
        payload = json.dumps({"mac": client_id})
        _mqtt_client.publish(topic, payload)
        print("Heartbeat gesendet:", topic)
        return True
    except Exception as e:
        print("Heartbeat fehlgeschlagen:", e)
        return False
```

- [ ] **Step 4: Point `update_device_pulses` at `tick()`**

Update the wrapper (name kept so `main.py` is unaffected here):

```python
def update_device_pulses():
    """Aktualisiert alle aktiven Pulse. Aus der Main-Loop jeden Tick aufrufen."""
    gpio_manager.tick()
```

- [ ] **Step 5: Run the publish tests + full suite**

Run: `python -m unittest tests.test_publish -v`
Expected: PASS (5 tests).
Run: `python -m unittest discover -s tests -t . -v`
Expected: PASS (all tests across files).

- [ ] **Step 6: Commit**

```bash
git add mqttutils.py tests/test_publish.py
git commit -m "[firmware] add status heartbeat; discovery becomes boot-only publish"
```

---

## Task 7: Watchdog feeding in reconnect helpers (`networkutils.py`, `mqttutils.py`)

**Files:**
- Modify: `networkutils.py` — `connect_wifi`, `reconnect_wifi` accept + feed `wdt`
- Modify: `mqttutils.py` — `reconnect_mqtt` accepts + feeds `wdt`

These touch hardware/timing paths and are validated on the Pico (Task 10), but the chunked sleep they rely on is already unit-tested (Task 3).

- [ ] **Step 1: Update `networkutils.py` imports and helpers**

Change the hardware import to add `feed_sleep_ms`:

```python
from hardware import led_off, led_toggle, feed_sleep_ms, CONNECTING_TOGGLE_MS
```

Update `connect_wifi` to accept and feed an optional watchdog inside the wait loop:

```python
def connect_wifi(ssid, password, timeout=20, wdt=None):
    print('Try to connect to:', ssid)
    wlan = network.WLAN(network.STA_IF)
    wlan.active(True)
    wlan.connect(ssid, password)

    # ticks_ms-Deadline; LED toggelt während des Verbindungsaufbaus.
    deadline = time.ticks_add(time.ticks_ms(), timeout * 1000)
    while time.ticks_diff(deadline, time.ticks_ms()) > 0:
        if wlan.isconnected():
            print("Connected to Wi-Fi!")
            print('IP Address:', wlan.ifconfig()[0])
            led_off()
            return True
        led_toggle()
        time.sleep_ms(CONNECTING_TOGGLE_MS)
        if wdt is not None:
            wdt.feed()

    print('Status', wlan.status())
    return False
```

Update `reconnect_wifi` to thread the watchdog and use the chunked sleep:

```python
def reconnect_wifi(ssid, pw, wdt=None):
    """Versucht WiFi wiederherzustellen. Füttert den optionalen Watchdog. True bei Erfolg."""
    for attempt in range(RECONNECT_ATTEMPTS):
        print("WiFi Wiederverbindung, Versuch", attempt + 1)
        if connect_wifi(ssid, pw, wdt=wdt):
            return True
        feed_sleep_ms(RECONNECT_DELAY_S * 1000, wdt)
    return False
```

- [ ] **Step 2: Update `reconnect_mqtt` in `mqttutils.py`**

Add `feed_sleep_ms` to the hardware import line:

```python
from hardware import led, gpio_manager, feed_sleep_ms
```

Replace `reconnect_mqtt`:

```python
def reconnect_mqtt(wdt=None):
    """
    Stellt die MQTT-Verbindung mit den zuletzt verwendeten Parametern wieder her.
    Füttert den optionalen Watchdog während der Wartezeit.
    """
    for attempt in range(RECONNECT_ATTEMPTS):
        print("MQTT Wiederverbindung, Versuch", attempt + 1)
        client = connect_mqtt(_client_id, _mqtt_broker, _mqtt_port)
        if client:
            return client
        feed_sleep_ms(RECONNECT_DELAY_S * 1000, wdt)
    return None
```

- [ ] **Step 3: Run the full suite (no regressions)**

Run: `python -m unittest discover -s tests -t . -v`
Expected: PASS (all tests). `led` may now be reported unused by linters in `mqttutils` — it is still used by `_handle_config`/LED paths via `gpio_manager`; leave the import as-is only if referenced, otherwise keep (it was already imported).

- [ ] **Step 4: Commit**

```bash
git add networkutils.py mqttutils.py
git commit -m "[firmware] feed watchdog through wifi/mqtt reconnect waits"
```

---

## Task 8: Main loop — boot-only discovery, heartbeat, broker_port, WDT (`main.py`)

**Files:**
- Modify: `main.py`

Not host-unit-tested (it is the top-level script that runs the device); validated on hardware in Task 10. Make the edits exactly.

- [ ] **Step 1: Update imports**

Change the `mqttutils` import line to add `publish_heartbeat`:

```python
from mqttutils import publish_discovery, publish_heartbeat, connect_mqtt, reconnect_mqtt, load_device_config, update_device_pulses
from mqttutils import _update_known_devices
```

- [ ] **Step 2: Add a WDT timeout constant**

In the `# --- KONFIGURATION ---` block add:

```python
WDT_TIMEOUT_MS       = 8000    # Watchdog-Timeout (nahe rp2-Maximum; Build-Limit beim Flashen prüfen)
```

- [ ] **Step 3: Replace the connect + main-loop block**

Replace the block starting at `# MQTT verbinden` through the `time.sleep_ms(50)` line (inside the innermost `else:`) with:

```python
            # MQTT verbinden (broker_port aus Config respektieren, Default 1883)
            try:
                broker_port = int(config.get('broker_port') or 1883)
            except (ValueError, TypeError):
                broker_port = 1883

            client = connect_mqtt(CLIENT_ID, config['broker_ip'], broker_port)
            if not client:
                print("MQTT-Verbindung fehlgeschlagen. Neustart in 5 Sekunden...")
                time.sleep(5)
                machine.reset()

            # Discovery EINMALIG beim Boot → Backend erkennt Box und pusht Config
            publish_discovery(CLIENT_ID)
            last_publish = time.time()

            # WLAN-Objekt einmalig vor der Schleife anlegen
            wlan = network.WLAN(network.STA_IF)

            # Watchdog erst im Normalbetrieb aktivieren – AP-/Erstverbindung darf lange dauern
            wdt = machine.WDT(timeout=WDT_TIMEOUT_MS)

            # Hauptschleife
            while True:
                wdt.feed()

                # WiFi prüfen und bei Bedarf wiederherstellen
                if not wlan.isconnected():
                    print("WiFi-Verbindung verloren. Wiederverbindung wird versucht...")
                    if not reconnect_wifi(config['client_network_ssid'], config['client_network_pw'], wdt):
                        print("WiFi Wiederverbindung fehlgeschlagen. Neustart...")
                        machine.reset()
                    client = reconnect_mqtt(wdt)
                    if not client:
                        print("MQTT Wiederverbindung fehlgeschlagen. Neustart...")
                        machine.reset()

                # MQTT-Nachrichten nicht-blockierend abrufen
                try:
                    client.check_msg()
                except Exception as e:
                    print("MQTT-Fehler:", e)
                    client = reconnect_mqtt(wdt)
                    if not client:
                        print("MQTT nicht wiederherstellbar. Neustart...")
                        machine.reset()

                # Abgelaufene Pulse beenden
                update_device_pulses()

                # Heartbeat periodisch senden (Liveness ohne Config-Push)
                now = time.time()
                if now - last_publish >= PUBLISH_INTERVAL_S:
                    publish_heartbeat(CLIENT_ID)
                    last_publish = now
                    gc.collect()

                time.sleep_ms(50)
```

(The `reconnect_wifi` import already exists at the top of `main.py`. The outer `try/except/finally` safe-exit block is unchanged.)

- [ ] **Step 4: Byte-compile check**

Run (from `smart-box/`): `python -m py_compile main.py mqttutils.py hardware.py networkutils.py`
Expected: no output (all modules parse). This catches syntax/indentation errors without a board.

- [ ] **Step 5: Run the full suite (no regressions)**

Run: `python -m unittest discover -s tests -t . -v`
Expected: PASS (all tests).

- [ ] **Step 6: Commit**

```bash
git add main.py
git commit -m "[firmware] boot-only discovery + status heartbeat loop, broker_port, watchdog"
```

---

## Task 9: Backend MQTT-contract cleanup (delay fields)

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/config/MqttCommandPublisher.java`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/service/RangePositionService.java:195-199`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/api/DeviceController.java:237-241`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/config/SmartBoxConfigPushService.java`

- [ ] **Step 1: Trim `MqttCommandPublisher`**

Replace the record (line 29) and method signature/body (lines 31-33) so the command payload no longer carries the delay:

```java
    public record CommandPayload(String command, String deviceId, int signalDurationMs) {}

    public void publishToTopic(String topic, String command, String deviceId, int signalDurationMs) {
        try {
            String payload = objectMapper.writeValueAsString(new CommandPayload(command, deviceId, signalDurationMs));
```

Remove the now-unused `import org.jspecify.annotations.Nullable;` (line 5) if no other `@Nullable` remains in the file.

- [ ] **Step 2: Update the `RangePositionService` call site**

Replace lines 194-199:

```java
        int signalDurationMs = device.getDeviceType().getSignalDurationMs();

        mqttCommandPublisher.publishToTopic(topic, command, device.getId().toString(), signalDurationMs);
```

(Deletes the `delaySignalDurationMs` local and the trailing argument.)

- [ ] **Step 3: Update the `DeviceController` call site**

Replace lines 236-241:

```java
        int signalDurationMs = device.getDeviceType().getSignalDurationMs();

        mqttCommandPublisher.publishToTopic(topic, command, id.toString(), signalDurationMs);
```

- [ ] **Step 4: Trim `SmartBoxConfigPushService`**

In `DeviceConfigEntry` (lines 53-62) remove the `delayMs` component:

```java
    public record DeviceConfigEntry(
        @JsonProperty("device_id")          UUID deviceId,
        String alias,
        String direction,
        String device,
        String command,
        @JsonProperty("signal_duration_ms") int signalDurationMs,
        boolean blocked
    ) {}
```

In `buildPayload` (lines 116-129) delete the `effectiveDelay` computation and drop the argument from the `new DeviceConfigEntry(...)`:

```java
            entries.add(new DeviceConfigEntry(
                device.getId(),
                device.getAlias(),
                signal.getCommunicationDirection().name(),
                signal.getDevice().name(),
                signal.getCommand(),
                deviceType.getSignalDurationMs(),
                device.isBlocked()
            ));
```

Update the class Javadoc payload example (line 24) to drop `"delay_ms"`.

> Keep `DeviceType.delaySignalDurationMs` / `Device.delaySignalDurationMs` and their REST/manifest plumbing — only the MQTT payloads change.

- [ ] **Step 5: Compile + test the backend**

Run (from `smart-ground-backend/`): `./mvnw -q compile`
Expected: BUILD SUCCESS (no references to the removed component remain).
Run: `./mvnw -q test`
Expected: BUILD SUCCESS. If any test asserts on `delay_ms`/`delaySignalDurationMs` in a payload, update it to the trimmed shape (none were found by grep, but verify).

- [ ] **Step 6: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/config/MqttCommandPublisher.java \
        smart-ground-backend/src/main/java/ch/jp/shooting/service/RangePositionService.java \
        smart-ground-backend/src/main/java/ch/jp/shooting/api/DeviceController.java \
        smart-ground-backend/src/main/java/ch/jp/shooting/config/SmartBoxConfigPushService.java
git commit -m "[backend] drop delay fields from config-push and command MQTT payloads"
```

---

## Task 10: Documentation + manual hardware verification

**Files:**
- Modify: `smart-box/CLAUDE.md`
- Modify: `smart-ground-backend/CLAUDE.md`

- [ ] **Step 1: Update `smart-box/CLAUDE.md`**

Make these edits to keep the doc the source of truth:

- **Module Responsibilities → `mqttutils.py`:** rewrite the security-state bullet to: "Security state in module globals: `_known_devices` (authorised UUIDs) and `_blocked_devices` (admin BLOCK via `ADMIN_BLOCK_TOKEN`, removed only by UNBLOCK). No rate limiting or auto-block." Add `publish_heartbeat(client_id)` and change `publish_discovery(client_id)` (boot-only). Note `reconnect_mqtt(wdt=None)`.
- **`hardware.py`:** `set(device_id, value, signal_duration_ms)` — single-phase, non-blocking, **busy-reject** of ON while a pulse is active; OFF cancels. `update_pulses()` is renamed `tick()`. Add `feed_sleep_ms(total_ms, wdt, chunk_ms)`. Remove all `delay_ms` references.
- **`networkutils.py`:** `connect_wifi(..., wdt=None)` and `reconnect_wifi(ssid, pw, wdt=None)` feed the watchdog.
- **`main.py`:** discovery is sent once on boot; the loop publishes a heartbeat to `smartboxes/{mac}/status` every `PUBLISH_INTERVAL_S`; honors `broker_port`; runs a `machine.WDT` (main loop only).
- **MQTT Topics:** mark `smartboxes/{mac}/status` as **implemented** (heartbeat, payload `{ "mac": "..." }`).
- **Config payload / device_config schema:** remove `delay_ms` from the device object in both the Config-payload and `device_config.json` examples. Note delay is no longer handled by firmware.
- **Command payload:** remove `delaySignalDurationMs`; keep `signalDurationMs`.
- **Security Model section:** replace with the two-layer model — (1) allowlist, (2) admin BLOCK/UNBLOCK — plus a note that ON is ignored while a device is busy (operational, not security). Delete the rate-limit and auto-block items.
- **Known Issues:** remove the resolved "`delay_ms` blocks the main loop" item.
- **Add a short "MQTT delivery" note:** commands are QoS 0; the backend tracks `/executed` ACKs and is responsible for retry-on-missing-ACK.

- [ ] **Step 2: Update `smart-ground-backend/CLAUDE.md`**

Add an OPEN item under the backend's not-yet-implemented / open-work section:

> **Backend-side actuation delay (deferred):** `DeviceType.delaySignalDurationMs` is retained in the domain but no longer sent to the firmware (removed from the config-push and command MQTT payloads). When a delayed-release / staggered-double requirement is confirmed, implement it backend-side by scheduling the command publish at send-time. Until then, no delay is applied.

- [ ] **Step 3: Commit the docs**

```bash
git add smart-box/CLAUDE.md smart-ground-backend/CLAUDE.md
git commit -m "[firmware] document lean security model, scheduler, heartbeat, WDT; backend delay deferral"
```

- [ ] **Step 4: Flash and smoke-test on the Pico 2W**

Upload `main.py`, `mqttutils.py`, `hardware.py`, `networkutils.py` (mpremote or Thonny). With the broker + backend running, verify:

1. **Boot:** 3 startup blinks → WLAN connect → exactly **one** discovery on `smartboxes/discovery`; thereafter a heartbeat on `smartboxes/{mac}/status` every ~20 s. Backend shows the box ONLINE and does **not** re-push config every 20 s.
2. **Config push:** GPIO initialised; an `ON` fires the pin for its duration and auto-offs; a second `ON` during the pulse is ignored (logged "beschäftigt"); `OFF` cancels.
3. **Admin block:** `BLOCK` → `ON` ignored; `UNBLOCK` → `ON` works again.
4. **WiFi drop:** connecting blink + reconnect; box does **not** re-send discovery; heartbeats resume; backend returns it to ONLINE; the watchdog does not reset the box during a successful reconnect.
5. **Heap:** `gc.mem_free()` in REPL > 30 KB under normal operation.

- [ ] **Step 5: Record the smoke-test result**

If all pass, note it in the PR/commit description. If anything fails, open a `superpowers:systematic-debugging` session before merging.

---

## Self-Review (completed during planning)

- **Spec coverage:** Harness (Task 1) ✓; rate-limiter delete + busy-reject (Tasks 2, 4) ✓; auto-block delete (Task 4) ✓; one-phase scheduler + delay removal (Task 2) ✓; heartbeat split (Task 6, 8) ✓; WDT (Tasks 3, 7, 8) ✓; cleanups — `broker_port` (Task 8), `_blocked_devices` prune (Task 4), publish signature (Task 6), QoS doc (Task 10), `RECONNECT_*` left intentional (no task needed) ✓; backend contract (Task 9) ✓; docs both repos (Task 10) ✓.
- **Type/name consistency:** `tick()` defined in Task 2, called by `update_device_pulses` in Task 6 and the loop in Task 8; `feed_sleep_ms` defined Task 3, used Task 7; `publish_discovery(client_id)`/`publish_heartbeat(client_id)` defined Task 6, called Task 8; `set(device_id, value, signal_duration_ms)` defined Task 2, called Task 4; `reconnect_mqtt(wdt)`/`reconnect_wifi(ssid, pw, wdt)` defined Task 7, called Task 8 — all consistent.
- **Placeholder scan:** none.
