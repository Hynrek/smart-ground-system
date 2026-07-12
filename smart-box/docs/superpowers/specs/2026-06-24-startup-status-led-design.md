# Startup Status LED — Design

**Date:** 2026-06-24
**Component:** smart-box firmware (MicroPython, Pico 2W)
**Status:** Approved

## Goal

Provide visible feedback on the onboard LED during the boot/connection sequence so
the operator can tell what the box is doing without a serial console.

## Behavior (four states)

| # | State | Where | LED behavior |
|---|-------|-------|--------------|
| 1 | Boot/startup | top of `main.py`, before `load_config()` | Blink 3× (150 ms on / 150 ms off) |
| 2 | Access-point setup mode | `start_ap()` in `accesspoint.py`, before the socket loop | Solid ON |
| 3 | Connecting to WLAN | `connect_wifi()` in `networkutils.py` | Toggle every 250 ms while waiting |
| 4 | Connected to WLAN | end of `connect_wifi()` on success | OFF |

Cadence: "snappy" — 150 ms blink for startup, 250 ms toggle while connecting.

## Design

### `hardware.py` — centralized LED helpers

The onboard `led = Pin("LED", Pin.OUT)` already lives here, so the status helpers
belong here too. Add a `# --- KONFIGURATION ---` block and small functions:

```python
from micropython import const
STARTUP_BLINKS        = const(3)
BLINK_ON_MS           = const(150)
BLINK_OFF_MS          = const(150)
CONNECTING_TOGGLE_MS  = const(250)

def led_on():     led.value(1)
def led_off():    led.value(0)
def led_toggle(): led.toggle()
def status_blink(times=STARTUP_BLINKS, on_ms=BLINK_ON_MS, off_ms=BLINK_OFF_MS):
    # blink `times` mal, danach LED aus
    ...
```

`status_blink()` leaves the LED OFF when it finishes.

### `main.py` — startup blink

Call `status_blink()` at the very top of the `try` block, before `load_config()`.
Requires importing the helper from `hardware` at the top of `main.py` (the module
is already loaded on the normal boot path; minimal RAM cost).

### `accesspoint.py` — solid ON in AP mode

Import `led_on` from `hardware`; call it just before entering the blocking socket
accept loop in `start_ap()`.

### `networkutils.py` — blink while connecting, off on success

Restructure the wait loop in `connect_wifi()`. Currently:

```python
for _ in range(timeout):
    if wlan.isconnected(): return True
    time.sleep(1)
return False
```

Replace with a `ticks_ms`-based deadline loop that honors the same `timeout`
(seconds) but toggles the LED every `CONNECTING_TOGGLE_MS` (250 ms):

- On `wlan.isconnected()` → `led_off()`, return `True`.
- On deadline reached → return `False` (caller decides next state; if it falls
  through to `start_ap()`, that sets solid ON).

Import `led_off`, `led_toggle`, `CONNECTING_TOGGLE_MS` from `hardware`.

## Consequences / notes

- `connect_wifi()` is also called by `reconnect_wifi()`, so the connecting blink
  also appears during runtime WiFi-reconnects. This is desirable feedback.
- The onboard LED is shared with MQTT-controllable LED devices, but all four
  signals occur before MQTT is live, and state 4 leaves it OFF, so there is no
  conflict with normal device operation.
- No import cycle: `hardware` imports nothing from `networkutils`/`accesspoint`.
- Conventions per `smart-box/CLAUDE.md`: German inline comments, English
  identifiers, `const()` for integer constants, config constants in a
  `# --- KONFIGURATION ---` block.

## Verification

Firmware on physical hardware — no automated test harness. Verify manually on the
Pico 2W:

1. Power on with no/invalid config → 3 startup blinks, then AP mode solid ON.
2. Power on with valid config → 3 startup blinks, then connecting blink, then OFF
   once associated.
3. Drop WiFi during runtime → connecting blink resumes, OFF on reconnect.

Helpers are kept tiny and isolated so the logic is reviewable without a board.
