# smart-box — MicroPython Firmware Development Guide

## Project Overview

The Smart Box firmware runs on a Raspberry Pi Pico 2W microcontroller and manages physical devices (GPIO outputs, LEDs, sensors) over MQTT. It receives commands from the backend, publishes device state and sensor events, and handles the initial WiFi access-point setup flow.

---

## Superpowers Workflow

When using Claude Code with Superpowers, follow this workflow for all non-trivial changes:

1. **Brainstorm** — clarify requirements, explore design alternatives, present options before writing any code
2. **Plan** — break work into small tasks with exact file paths and expected test outcomes; get approval before executing
3. **Implement** — test-driven: write a failing test, implement the minimum to pass, then refactor
4. **Review** — verify each task against the plan before moving to the next; critical issues block progress
5. **Finish** — confirm all tests pass, present merge/PR/discard options

### Decisions go into CLAUDE.md

Any architectural or design decision made during a session must be reflected back into this file before the session closes. This includes:

- New modules or significant changes to module responsibilities
- Changes to MQTT topic structure or payload schemas
- Changes to config file schemas or key names
- Security model changes
- New known issues or resolution of existing ones
- Any constraint or convention agreed upon during brainstorming

Do not leave decisions only in commit messages or conversation history — this file is the single source of truth for the firmware.

---

## Stack & Versions

- **MicroPython**: 1.23+
- **Board**: Raspberry Pi Pico 2W (RP2350 chip, ARM Cortex-M33)
- **RAM**: 520 KB SRAM — treat as a hard constraint
- **WiFi**: Built-in CYW43, managed via MicroPython `network` module
- **Onboard LED**: `Pin("LED", Pin.OUT)` — module-level `led` in `hardware.py`
- **MQTT**: `umqtt.simple` (preferred) or `umqtt.robust` (fallback)
- **Flash**: ~8 MB (includes MicroPython kernel)
- **No asyncio**: Synchronous polling loop only

## Language & Runtime

- **MicroPython 1.23+** only — no CPython-specific syntax or libraries
- Allowed stdlib modules: `network`, `time`, `machine`, `json`, `sys`, `gc`
- MQTT: `umqtt.simple` (preferred); fall back to `umqtt.robust`
- No `asyncio` / `uasyncio` — all logic is synchronous and polling-based
- No `import os` for file I/O — use `open()` directly
- Do not import modules you don't use — every imported module stays in RAM for the lifetime of the process

---

## Project Structure

```
smart-box/
├── main.py                             # Entry point; auto-runs on Pico boot
├── mqttutils.py                        # MQTT connection, message routing, security, config persistence
├── hardware.py                         # GpioManager class + onboard LED
├── networkutils.py                     # WiFi connect/reconnect helpers
├── accesspoint.py                      # Captive portal for first-time WiFi setup
├── accesspoint.html                    # HTML served by the captive portal
├── systemconfig/
│   ├── accesspoint_config.json         # AP SSID/password (read-only at runtime)
│   └── firmware_config.json            # Firmware version + capability registry (read-only at runtime)
└── userconfig/
    ├── client_config.json              # WiFi credentials + broker IP (written by setup portal)
    └── device_config.json              # Active device/GPIO config (written after each config push)
```

---

## Module Responsibilities

### `main.py`
Entry point. Loads `userconfig/client_config.json`; falls back to `start_ap()` if absent or incomplete (`client_network_ssid` or `broker_ip` missing). Pre-loads GPIO from `userconfig/device_config.json` before MQTT connects. Connects MQTT, sends initial discovery, then runs the main polling loop. Checks WiFi and MQTT connection before every `check_msg()` call; calls `update_device_pulses()` on every tick. Republishes discovery every `PUBLISH_INTERVAL_S` (20 s) and runs `gc.collect()` after each publish.

### `mqttutils.py`
Everything MQTT-related plus security:
- `connect_mqtt(client_id, broker, port)` — connects, subscribes to `smartboxes/{mac}/command` and `smartboxes/{mac}/config`, stores the client globally so ACKs and discovery reuse the same connection
- `reconnect_mqtt()` — retries `connect_mqtt` up to `RECONNECT_ATTEMPTS` (12) times with `RECONNECT_DELAY_S` (10 s) between attempts
- `message_callback(topic, msg)` — central router: `/config` → `_handle_config()`, `/command` → validates, applies security checks, calls `gpio_manager.set()`
- `_handle_config(payload)` — resets GPIO, rebuilds device map, saves to flash, sends ACK only if ≥1 device was initialised successfully
- `_send_config_ack()` — publishes `OK` to `smartboxes/{mac}/config/ack`
- `_send_device_command_ack(device_id)` — publishes `OK` to `smartboxes/{mac}/device/{deviceId}/executed`
- `publish_discovery(broker, port, client_id)` — publishes discovery payload via the persistent client; reads firmware version and box type from `systemconfig/firmware_config.json` (never hardcoded)
- `load_device_config()` / `save_device_config(devices)` — read/write `userconfig/device_config.json`
- `load_firmware_config()` — reads `systemconfig/firmware_config.json` (cached in module global after first load)
- `update_device_pulses()` — thin wrapper around `gpio_manager.update_pulses()`
- `_update_known_devices(devices)` — rebuilds `_known_devices` set; cleans up stale rate-limit and failure-counter entries

Security state in module globals: `_known_devices` (set of authorised UUIDs), `_last_command_time` (rate limiting, min `RATE_LIMIT_S` = 0.1 s), `_failed_commands` (auto-block after `MAX_FAILED_ATTEMPTS` = 5), `_blocked_devices` (timed auto-block `BLOCK_DURATION_S` = 300 s, or permanent `ADMIN_BLOCK_TOKEN` until UNBLOCK).

### `hardware.py`
`GpioManager` manages a `device_id → {pin, device, direction, command, signal_duration_ms, delay_ms, blocked}` mapping.
- `setup(devices)` — initialises pins from config list; GPIO pins set to 0; LED devices need no pin object
- `reset()` — turns all pins off, clears the map and pulse tracker
- `set(device_id, value, signal_duration_ms, delay_ms)` — sets a pin; for GPIO/LED devices with `signal_duration_ms > 0`, starts a timed pulse (HIGH → auto-LOW after duration tracked via `ticks_ms`); LED devices reject `value=0`; command-level `signal_duration_ms`/`delay_ms` override config values
- `update_pulses()` — called every main loop tick; expires timed pulses using `ticks_diff` (handles 32-bit rollover correctly)
- `known_ids()` — returns list of registered device UUIDs
- Module-level `led = Pin("LED", Pin.OUT)` for onboard LED

### `networkutils.py`
- `get_mac_address()` — returns 12-char lowercase hex MAC string used as MQTT client ID and topic segment
- `connect_wifi(ssid, password, timeout=20)` — second-by-second polling; returns `True` on success
- `reconnect_wifi(ssid, pw)` — retries up to `RECONNECT_ATTEMPTS` (12) times with `RECONNECT_DELAY_S` (10 s) between attempts
- `get_sta_ip()` — returns current IP or `None`

### `accesspoint.py`
Captive portal for first-time WiFi setup. Runs in AP mode (SSID/password from `systemconfig/accesspoint_config.json`), serves `accesspoint.html`, writes validated credentials to `userconfig/client_config.json`. Only accepts keys `client_network_ssid`, `client_network_pw`, `broker_ip`, `broker_port`. Requires `client_network_ssid` and `broker_ip`; rejects with 400 otherwise. Reboots after saving. Keep entirely separate from MQTT logic — it runs as an alternative boot path, not alongside MQTT.

---

## Critical Constraints

### Memory Budget (Most Important)

The Pico 2W has **520 KB of SRAM**. MicroPython + network stack + MQTT library consume ~400 KB, leaving only **~120 KB for application code**.

**Memory rules (strict):**

1. **Call `gc.collect()` after every MQTT message received** — done in `message_callback`'s `finally` block

2. **Release parsed JSON dicts immediately**
   ```python
   d = json.loads(msg)
   device_id = d['deviceId']
   del d
   gc.collect()
   ```

3. **Avoid string concatenation in loops** — use individual `print()` calls or pre-format outside the loop

4. **Avoid unbounded growing lists** — `_last_command_time`, `_failed_commands`, `_blocked_devices` are cleaned up in `_update_known_devices` on every config push

5. **Use `micropython.const()` for integer constants**

6. **Do not import modules you don't use** — each import stays in RAM for the process lifetime

7. **Keep the main loop flat — avoid deep call stacks** — each frame costs stack space

8. **Check free heap** (debugging only, remove before committing):
   ```python
   import gc
   print("Freier Heap:", gc.mem_free(), "Bytes")
   ```

---

## Running & Flashing

### Setup

1. **Install MicroPython firmware** on Pico 2W
   ```bash
   # Download latest from https://micropython.org/download/rp2-pico-w/
   # Hold BOOTSEL while plugging in, copy .uf2 to the RPI-RP2 drive
   ```

2. **Connect via Serial/REPL**
   ```bash
   # Thonny IDE (recommended for Windows)
   # or: picocom /dev/ttyACM0 -b 115200
   ```

3. **Upload project files**
   ```bash
   pip install mpremote
   mpremote connect <port> cp main.py :main.py
   mpremote connect <port> cp mqttutils.py :mqttutils.py
   mpremote connect <port> cp hardware.py :hardware.py
   mpremote connect <port> cp networkutils.py :networkutils.py
   mpremote connect <port> cp accesspoint.py :accesspoint.py
   mpremote connect <port> cp accesspoint.html :accesspoint.html
   mpremote connect <port> cp systemconfig/accesspoint_config.json :systemconfig/accesspoint_config.json
   mpremote connect <port> cp systemconfig/firmware_config.json :systemconfig/firmware_config.json
   ```

### First Boot

On first boot, `main.py` checks for `userconfig/client_config.json`. If absent or incomplete, it starts the captive portal:

1. Phone/laptop connects to SSID from `systemconfig/accesspoint_config.json` (`SmartRange-Client-Setup`)
2. Opens browser → `192.168.4.1`
3. Fills in WiFi SSID, password, and broker IP → writes to `userconfig/client_config.json`
4. Pico reboots and connects normally

---

## MQTT Topics & Payload Schemas

The SmartBox MAC address (12-char lowercase hex) is used as the per-box topic segment and as the MQTT client ID.

| Direction | Topic | Purpose |
|---|---|---|
| SmartBox → Backend | `smartboxes/discovery` | Boot announcement; triggers config push |
| SmartBox → Backend | `smartboxes/{mac}/status` | Heartbeat / health (not yet implemented) |
| Backend → SmartBox | `smartboxes/{mac}/config` | Full device/GPIO configuration |
| SmartBox → Backend | `smartboxes/{mac}/config/ack` | Confirms config was applied (`OK`) |
| Backend → SmartBox | `smartboxes/{mac}/command` | Device control command |
| SmartBox → Backend | `smartboxes/{mac}/device/{deviceId}/executed` | Per-device command ACK (`OK`) |

### Discovery payload (SmartBox → `smartboxes/discovery`)
Firmware version and box type are always read from `systemconfig/firmware_config.json`.
```json
{ "mac": "aabbccddeeff", "firmwareVersion": "0.6", "boxType": "pico2w", "ip": "192.168.1.42" }
```

### Config payload (Backend → `smartboxes/{mac}/config`)
```json
{
  "devices": [
    {
      "device_id": "<uuid>",
      "alias": "Werfer 1",
      "device": "GPIO",
      "direction": "OUTPUT",
      "command": "15",
      "signal_duration_ms": 500,
      "delay_ms": null,
      "blocked": false
    }
  ]
}
```
`device` is `"GPIO"` or `"LED"`. For GPIO, `command` is the pin number as a string. LED devices use the onboard LED.

### Command payload (Backend → `smartboxes/{mac}/command`)
```json
{ "command": "ON", "commandId": "<uuid>", "deviceId": "<uuid>", "signalDurationMs": 500, "delaySignalDurationMs": null }
```
Valid `command` values: `ON`, `OFF`, `BLOCK`, `UNBLOCK`. `signalDurationMs` and `delaySignalDurationMs` are optional overrides for the per-device config values.

---

## Security Model

Commands pass through a multi-layer check in `message_callback` before reaching hardware:

1. **Allowlist** — `deviceId` must be in `_known_devices` (populated from the last config push). Unknown IDs are rejected and logged as failed attempts.
2. **Admin block** — `BLOCK` command sets a permanent (`ADMIN_BLOCK_TOKEN`) block; only `UNBLOCK` removes it. No auto-expiry.
3. **Auto-block** — after `MAX_FAILED_ATTEMPTS` (5) consecutive failures, device is blocked for `BLOCK_DURATION_S` (300 s).
4. **Rate limiting** — minimum `RATE_LIMIT_S` (0.1 s) between commands per device.
5. `BLOCK`/`UNBLOCK` bypass the ON/OFF security path and do not require the device to be in `_known_devices` for UNBLOCK.

Do not bypass or weaken these checks when adding new features.

---

## Safe-Exit Pattern (mandatory in `main.py`)

```python
try:
    # main logic

except KeyboardInterrupt:
    print("Programm durch Benutzer beendet.")

except Exception as e:
    print("Kritischer Fehler:", e)
    time.sleep(5)
    machine.reset()

finally:
    try:
        if client:
            client.disconnect()
    except Exception as e:
        print("MQTT-Trennung fehlgeschlagen:", e)
    try:
        network.WLAN(network.STA_IF).active(False)
    except Exception:
        pass
    try:
        network.WLAN(network.AP_IF).active(False)
    except Exception:
        pass
    print("Aufräumen abgeschlossen.")
```

- `KeyboardInterrupt` → clean shutdown, **no** reset
- All other exceptions → `machine.reset()` after a 5-second delay
- `finally` → always disconnect MQTT, deactivate both WLAN interfaces
- Always create a fresh `network.WLAN()` instance in `finally` — do not rely on outer variables that may not be defined if the error happened early

---

## Configuration Files

### `systemconfig/accesspoint_config.json` (read-only at runtime)

Set at flash time, controls the setup captive portal:
```json
{
  "accesspoint_ssid": "SmartRange-Client-Setup",
  "accesspoint_pass": "12345678"
}
```

### `systemconfig/firmware_config.json` (read-only at runtime)

Describes firmware capabilities; read when building the discovery payload:
```json
{
  "firmware_version": "0.6",
  "box_type": "pico2w",
  "supported_device_kinds": ["GPIO", "LED"],
  "supported_directions": ["INPUT", "OUTPUT"]
}
```
Never overwrite this file from the backend.

### `userconfig/client_config.json` (written by captive portal)

```json
{
  "client_network_ssid": "MyNetwork",
  "client_network_pw": "mypassword",
  "broker_ip": "192.168.1.100"
}
```
`broker_port` is optional (defaults to 1883). Never hardcode these values.

### `userconfig/device_config.json` (written by SmartBox after config push)

```json
{
  "devices": [
    {
      "device_id": "<uuid>",
      "alias": "Werfer 1",
      "device": "GPIO",
      "direction": "OUTPUT",
      "command": "15",
      "signal_duration_ms": 500,
      "delay_ms": null,
      "blocked": false
    }
  ]
}
```
Overwritten on every config push. Firmware metadata does **not** go here — it lives in `systemconfig/firmware_config.json`.

---

## Conventions

### Code Style

| Convention | Rule |
|---|---|
| Comments | German inline comments (`# Kommentar`) |
| Identifiers | English (`device_id`, `connect_mqtt`, `gpio_manager`) |
| Config constants | Top of each file under a `# --- KONFIGURATION ---` block |
| f-strings | Simple only — avoid complex expressions inside `{}` (MicroPython support is limited) |
| Error handling | Always `except Exception as e:` + `print(...)` — never silent bare `except:` |
| Module size | Small and single-responsibility |

### What to Avoid

- ❌ Do not use `asyncio` / `uasyncio`
- ❌ Do not hardcode credentials, firmware version, or broker IP outside the config files or `# --- KONFIGURATION ---` block
- ❌ Do not suppress exceptions silently
- ❌ Do not call `client.wait_msg()` in the main loop (it blocks)
- ❌ Do not put firmware metadata in `userconfig/device_config.json`
- ❌ Do not bypass the security checks in `message_callback`
- ❌ Do not add CPython-only dependencies

---

## Troubleshooting

### Board doesn't respond in REPL
Press `^C` to trigger `KeyboardInterrupt`. If frozen, hold BOOTSEL and replug to enter bootloader mode.

### Memory errors during MQTT receive
Ensure `gc.collect()` is called in the `finally` block of every message handler. Check `gc.mem_free()` in REPL.

### WiFi connection fails
Check `userconfig/client_config.json`. If the file is missing or `client_network_ssid`/`broker_ip` are empty, the captive portal starts automatically. Debug in REPL:
```python
import network; wlan = network.WLAN(network.STA_IF); wlan.active(True); wlan.connect('ssid', 'pw'); print(wlan.status())
```

### Device doesn't trigger on command
1. Check that a config push has been received (device must be in `_known_devices`)
2. Verify the GPIO pin number in `userconfig/device_config.json`
3. Manually trigger in REPL: `from machine import Pin; p = Pin(15, Pin.OUT); p.value(1)`

---

## Known Issues & Open Work

### HIGH: `delay_ms` blocks the main loop
`time.sleep_ms(delay_ms)` in `GpioManager.set()` blocks the entire polling loop for the duration. For large delays this starves MQTT keep-alive. Replace with a non-blocking delayed-pulse mechanism when this becomes a problem.

### HIGH: Topic structure routes all devices to one topic
All commands arrive on `smartboxes/{mac}/command`. The intended future structure is `smartboxes/{mac}/device/{deviceId}/command` — one topic per device. Coordinate with the backend before changing.

---

## Code Review Checklist (Firmware-Specific)

Before merging any changes:

- [ ] Firmware runs on physical Pico 2W without crashes
- [ ] `gc.collect()` called in `finally` after every MQTT message handler
- [ ] No unbounded lists or string concatenation in loops
- [ ] `micropython.const()` used for integer constants
- [ ] Safe-exit pattern in `main.py` with `try/except/finally`
- [ ] Configuration constants in `# --- KONFIGURATION ---` block at top of file
- [ ] German inline comments, English identifiers and docstrings
- [ ] No hardcoded credentials, firmware version, or broker IP
- [ ] Free heap > 30 KB under normal operation
- [ ] Security checks in `message_callback` not bypassed

---

## Firmware Updates

No OTA update yet. To update firmware:

1. Put board in bootloader mode (hold BOOTSEL, replug)
2. Re-flash MicroPython kernel if needed
3. Re-upload project files via mpremote or Thonny

Future: Implement OTA via MQTT for remote updates.
