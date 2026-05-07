# AI Agent Instructions — smart-box/mqtt (SmartBox Firmware)

> **Read this file before touching any code in this directory.**
> The `web(old)/` sibling folder is obsolete — ignore it entirely.

---

## What this firmware does

This is the MicroPython firmware for the **Raspberry Pi Pico 2W SmartBox**. On boot it:
1. Loads WiFi + broker credentials from flash (`userconfig/client_config.json`). If missing, starts a captive-portal AP for first-time setup instead.
2. Initialises GPIO pins from the last known device config (`userconfig/device_config.json`) so the box is immediately ready, even before the backend pushes a fresh config.
3. Connects to the MQTT broker and subscribes to its per-box command and config topics.
4. Publishes a discovery message so the backend recognises the box and triggers a config push.
5. Runs a synchronous polling loop: checks messages, updates timed GPIO pulses, republishes discovery periodically.

---

## Hardware

| Property | Value |
|---|---|
| Board | Raspberry Pi Pico 2W (RP2350 chip) |
| RAM | **520 KB SRAM** — treat as a hard constraint (see Memory section below) |
| WiFi | Built-in CYW43, managed via MicroPython `network` module |
| Onboard LED | `Pin("LED", Pin.OUT)` — module-level `led` in `hardware.py` |

---

## ⚠️ Memory — the most important constraint

**This is not a server.** MicroPython's runtime, the network stack, and the MQTT library together consume a large portion of the 520 KB before any application code runs. Heap exhaustion causes silent instability or hard crashes.

### Hard rules every agent must follow

| Rule | Rationale |
|---|---|
| Call `gc.collect()` after processing every inbound MQTT message | JSON parsing creates many intermediate objects; collect immediately after use |
| Parse JSON → extract needed values → let the dict go out of scope → `gc.collect()` | Do not keep parsed payloads alive beyond the handler function |
| Never build strings by concatenation in a loop | Each `+` allocates a new object; use individual `print()` calls or pre-format outside loops |
| Do not let any list or dict grow without a cap | Queues, history, retry buffers — bound them or avoid them |
| Use `micropython.const()` for integer constants | Prevents allocating a name in the module namespace |
| Do not import modules you don't use | Every imported module stays in RAM for the lifetime of the process |
| Keep the main loop flat — avoid deep call stacks | Each frame costs stack space |

### Debugging memory pressure

Add temporarily, remove before committing:
```python
import gc
print("Freier Heap:", gc.mem_free(), "Bytes")
```

---

## Language & Runtime

- **MicroPython 1.23+** only — no CPython-specific syntax or libraries
- Allowed stdlib modules: `network`, `time`, `machine`, `json`, `sys`, `gc`
- MQTT: `umqtt.simple` (preferred); fall back to `umqtt.robust`
- **No asyncio** — all logic is synchronous and polling-based; do not introduce `uasyncio` unless explicitly requested
- No `import os` for file I/O unless explicitly needed — use `open()` directly

---

## Code Style

| Convention | Rule |
|---|---|
| Comments | German inline comments (`# Kommentar`) |
| Identifiers | English (`device_id`, `connect_mqtt`, `gpio_manager`) |
| Config constants | Top of each file under a `# --- KONFIGURATION ---` block |
| Module size | Small and single-responsibility |
| f-strings | Simple only — avoid complex expressions inside `{}` (MicroPython support is limited) |
| Error handling | Always `except Exception as e:` + `print(...)` — never silent bare `except:` |

---

## File Structure & Module Responsibilities

```
mqtt/
├── main.py                             # Entry point; auto-runs on Pico boot
├── mqttutils.py                        # MQTT connection, message routing, security, config persistence
├── hardware.py                         # GpioManager class + onboard LED
├── networkutils.py                     # WiFi connect/reconnect helpers
├── accesspoint.py                      # Captive portal for first-time WiFi setup
├── accesspoint.html                    # HTML served by the captive portal
├── systemconfig/
│   ├── accesspoint_config.json         # AP mode SSID/password (read-only at runtime)
│   └── firmware_config.json            # Firmware version + capability registry (read-only at runtime)
└── userconfig/
    ├── client_config.json              # WiFi credentials + broker IP (written by setup portal)
    └── device_config.json              # Active device/GPIO config (written after each config push)
```

### `main.py`
Entry point. Loads `client_config.json`; falls back to `start_ap()` if absent or incomplete. Connects WiFi, pre-loads GPIO from `device_config.json`, connects MQTT, sends initial discovery, then runs the main polling loop. Handles WiFi/MQTT reconnection before every `check_msg()` call. Calls `update_device_pulses()` on every tick.

### `mqttutils.py`
Everything MQTT-related plus security:
- `connect_mqtt(client_id, broker, port)` — connects, subscribes to `/command` and `/config`, stores the client globally so ACKs and discovery reuse the same connection
- `reconnect_mqtt()` — retries `connect_mqtt` up to `RECONNECT_ATTEMPTS` times
- `message_callback(topic, msg)` — central router: `/config` → `_handle_config()`, `/command` → validates, applies security checks, calls `gpio_manager.set()`
- `_handle_config(payload)` — resets GPIO, rebuilds device map, saves to flash, sends ACK
- `_send_config_ack()` — publishes `OK` to `smartboxes/{mac}/config/ack`
- `_send_device_command_ack(device_id)` — publishes `OK` to `smartboxes/{mac}/device/{deviceId}/executed`
- `publish_discovery(broker, port, client_id)` — publishes discovery payload via the persistent client; reads firmware version from `systemconfig/firmware_config.json` (never hardcoded)
- `load_device_config()` / `save_device_config(devices)` — read/write `userconfig/device_config.json`
- `load_firmware_config()` — reads `systemconfig/firmware_config.json`
- `update_device_pulses()` — thin wrapper around `gpio_manager.update_pulses()`

Security state managed in module globals: `_known_devices` (set of authorised UUIDs), `_last_command_time` (rate limiting), `_failed_commands` (failure counter), `_blocked_devices` (timed or admin blocks).

### `hardware.py`
`GpioManager` manages a `device_id → {pin, device, direction, command, signal_duration_ms, delay_ms, blocked}` mapping.
- `setup(devices)` — initialises pins from config list; GPIO pins set to 0; LED devices need no pin object
- `reset()` — turns all pins off, clears the map and pulse tracker
- `set(device_id, value)` — sets a pin; for GPIO devices with `signal_duration_ms > 0`, starts a timed pulse (HIGH → auto-LOW after duration, tracked via `ticks_ms`)
- `update_pulses()` — called every main loop tick; expires timed pulses using `ticks_diff` (handles 32-bit rollover correctly)
- `known_ids()` — returns list of registered device UUIDs
- Module-level `led = Pin("LED", Pin.OUT)` for onboard LED status use

### `networkutils.py`
- `get_mac_address()` — returns 12-char lowercase hex MAC string used as MQTT client ID and topic segment
- `connect_wifi(ssid, password, timeout=20)` — second-by-second polling; returns `True` on success
- `reconnect_wifi(ssid, pw)` — retries up to `RECONNECT_ATTEMPTS` times with `RECONNECT_DELAY_S` between attempts
- `get_sta_ip()` — returns current IP or `None`

### `accesspoint.py`
Captive portal for first-time WiFi setup. Runs in AP mode, serves `accesspoint.html`, writes credentials to `userconfig/client_config.json`. Keep entirely separate from MQTT logic — it runs as an alternative boot path, not alongside MQTT.

---

## MQTT Topics & Payload Schemas

The SmartBox MAC address (12-char lowercase hex) is used as the per-box topic segment and as the MQTT client ID.

| Direction | Topic | Purpose |
|---|---|---|
| SmartBox → Backend | `smartboxes/discovery` | Boot announcement; triggers config push |
| SmartBox → Backend | `smartboxes/{mac}/status` | Heartbeat / health |
| Backend → SmartBox | `smartboxes/{mac}/config` | Full device/GPIO configuration |
| SmartBox → Backend | `smartboxes/{mac}/config/ack` | Confirms config was applied |
| Backend → SmartBox | `smartboxes/{mac}/command` | Device control command |
| SmartBox → Backend | `smartboxes/{mac}/device/{deviceId}/executed` | Per-device command ACK |

### Discovery payload (SmartBox → `smartboxes/discovery`)
Firmware version and box type are always read from `systemconfig/firmware_config.json` — never hardcode them.
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
`device` is `"GPIO"` or `"LED"`. `command` is the GPIO pin number as a string for GPIO devices.

### Command payload (Backend → `smartboxes/{mac}/command`)
```json
{ "command": "ON", "commandId": "<uuid>", "deviceId": "<uuid>" }
```
Valid `command` values: `ON`, `OFF`, `BLOCK`, `UNBLOCK`.

### Config ACK (SmartBox → `smartboxes/{mac}/config/ack`)
```
OK
```

### Device command ACK (SmartBox → `smartboxes/{mac}/device/{deviceId}/executed`)
```
OK
```

---

## Security Model

Commands pass through a multi-layer check before reaching hardware:

1. **Allowlist** — `deviceId` must be in `_known_devices` (populated from the last config push). Unknown IDs are rejected and logged as failed attempts.
2. **Blocked devices** — devices can be blocked by the backend (`BLOCK` command, no auto-expiry) or automatically after `MAX_FAILED_ATTEMPTS` consecutive failures (`BLOCK_DURATION_S = 300 s` auto-expiry).
3. **Rate limiting** — minimum `RATE_LIMIT_S` (0.1 s) between commands per device.
4. `BLOCK` / `UNBLOCK` commands are admin-only and bypass the ON/OFF security path.

Do not bypass or weaken these checks when adding new features.

---

## Safe Exit Pattern (mandatory in `main.py`)

```python
try:
    # main logic

except KeyboardInterrupt:
    print("Programm durch Benutzer beendet.")

except Exception as e:
    print("Kritischer Fehler:", e)
    time.sleep(5)
    machine.reset()  # Neustart bei nicht behebbarem Fehler

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
- All other exceptions → `machine.reset()` after a short delay
- `finally` → always disconnect MQTT, deactivate both WLAN interfaces
- Always create a fresh `network.WLAN()` instance in `finally` — do not rely on outer variables that may not be defined yet if the error happened early

---

## Config Files — What Goes Where

| File | Written by | Contains | Notes |
|---|---|---|---|
| `systemconfig/accesspoint_config.json` | Developer (flashed) | AP SSID/password | Read-only at runtime |
| `systemconfig/firmware_config.json` | Developer (flashed) | Firmware version, box type, capability flags | Read-only at runtime; never overwrite from backend |
| `userconfig/client_config.json` | Captive portal | WiFi SSID/password, broker IP | Persists across reboots |
| `userconfig/device_config.json` | SmartBox (after config push) | Active device list | Overwritten on every config push |

Firmware identity is **never** sent from the backend — it lives in `firmware_config.json` and is read by the SmartBox when building the discovery payload.

---

## Known Issues & Open Work

### 🔴 CRITICAL: No device command ACK handler on backend
The SmartBox publishes to `smartboxes/{mac}/device/{deviceId}/executed` after a successful command. The backend currently has no handler for this topic.
**Fix needed (backend):** implement `SmartBoxDeviceAckHandler`.

### 🟡 HIGH: Topic structure routes all devices to one topic
All commands arrive on `smartboxes/{mac}/command`. The intended future structure is `smartboxes/{mac}/device/{deviceId}/command` — one topic per device. Coordinate with backend before changing.

### 🟡 HIGH: `delay_ms` blocks the main loop
`time.sleep_ms(delay_ms)` in `GpioManager.set()` blocks the entire polling loop for the duration. For large delays this starves MQTT keep-alive. Replace with a non-blocking delayed-pulse mechanism when this becomes a problem.

---

## What to Avoid

- ❌ Do not use `asyncio` / `uasyncio` unless explicitly requested
- ❌ Do not use complex f-string expressions — MicroPython support is limited
- ❌ Do not hardcode credentials, firmware version, or broker IP outside the `# --- KONFIGURATION ---` block or the appropriate config file
- ❌ Do not suppress exceptions silently — always `print()` the error
- ❌ Do not call `client.wait_msg()` in the main loop (it blocks)
- ❌ Do not read, modify, or reference anything in `web(old)/`
- ❌ Do not add CPython-only dependencies
- ❌ Do not put firmware metadata (version, box type) in `userconfig/device_config.json` — it belongs in `systemconfig/firmware_config.json`
- ❌ Do not bypass the security checks in `message_callback`
