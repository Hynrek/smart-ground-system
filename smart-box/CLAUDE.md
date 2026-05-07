# CLAUDE.md — smart-box (Raspberry Pi Pico 2W Firmware)

## Project Overview

This folder contains the MicroPython firmware for the **Smart Ground SmartBox** — a Raspberry Pi Pico 2W that connects to WiFi, registers itself with the backend via MQTT, receives a GPIO device configuration, and executes device control commands in real time.

The active firmware lives in `mqtt/`. The `web(old)/` folder is **obsolete** — ignore it entirely.

---

## Folder Structure

```
smart-box/
├── mqtt/                        ← Active firmware (deploy this)
│   ├── main.py                  ← Entry point; boots on Pico startup
│   ├── mqttutils.py             ← MQTT client, message routing, config persistence
│   ├── hardware.py              ← GpioManager class + onboard LED
│   ├── networkutils.py          ← WiFi connect/reconnect helpers
│   ├── accesspoint.py           ← Captive portal for first-time Wi-Fi setup
│   ├── accesspoint.html         ← HTML page served by the captive portal
│   ├── systemconfig/
│   │   └── accesspoint_config.json   ← AP mode SSID/password (read-only at runtime)
│   └── userconfig/
│       ├── client_config.json        ← WiFi + broker credentials (written by setup portal)
│       └── device_config.json        ← Device/GPIO config cached from last backend push
└── web(old)/                    ← OBSOLETE — do not read or modify
```

---

## Runtime Environment

- **Hardware:** Raspberry Pi Pico 2W (RP2350 chip, built-in CYW43 WiFi)
- **Language:** MicroPython 1.23+ only
- **Allowed stdlib modules:** `network`, `time`, `machine`, `json`, `sys`, `gc`
- **MQTT library:** `umqtt.simple` (preferred); fall back to `umqtt.robust` if unavailable
- **No asyncio** — all logic is synchronous and polling-based; do not introduce `uasyncio` unless explicitly asked
- **No CPython** — do not use any module that is not available in MicroPython

---

## Code Style

| Convention | Rule |
|---|---|
| Comments | German inline comments (`# Kommentar`) |
| Identifiers | English (`device_id`, `connect_mqtt`, `gpio_manager`) |
| Config constants | Top of each file under a `# --- KONFIGURATION ---` block |
| Module size | Small and single-responsibility; one module = one concern |
| f-strings | Simple only — avoid complex expressions inside `{}` (MicroPython support is limited) |
| Error handling | Always `except Exception as e:` + `print(...)` — never silent bare `except:` |

---

## Safe Exit Pattern (mandatory in `main.py`)

Every top-level entry point **must** use this structure:

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
    try: client.disconnect()
    except Exception: pass
    try:
        network.WLAN(network.STA_IF).active(False)
        network.WLAN(network.AP_IF).active(False)
    except Exception: pass
    print("Aufräumen abgeschlossen.")
```

Rules:
- `KeyboardInterrupt` → clean shutdown, **no** reset
- All other exceptions → `machine.reset()` after a short delay
- `finally` → always disconnect MQTT and deactivate both WLAN interfaces

---

## MQTT Architecture

### Topic Schema

| Direction | Topic | Description |
|---|---|---|
| SmartBox → Backend | `smartboxes/discovery` | Boot announcement; triggers config push from backend |
| SmartBox → Backend | `smartboxes/{mac}/status` | Heartbeat / health updates |
| Backend → SmartBox | `smartboxes/{mac}/config` | Full device/GPIO configuration |
| SmartBox → Backend | `smartboxes/{mac}/config/ack` | Confirms config was applied |
| Backend → SmartBox | `smartboxes/{mac}/command` | Device control command |

`{mac}` is the 12-character lowercase hex MAC address used as the client ID.

### Message Schemas

**Discovery payload** (SmartBox → `smartboxes/discovery`):
```json
{ "mac": "aabbccddeeff", "firmwareVersion": "0.6v", "ip": "192.168.1.42" }
```

**Config payload** (Backend → `smartboxes/{mac}/config`):
```json
{
  "devices": [
    {
      "id": "<uuid>",
      "alias": "Werfer 1",
      "gpioPin": 15,
      "signalType": "WERFER",
      "defaultSignalDurationS": 1
    }
  ]
}
```

**Command payload** (Backend → `smartboxes/{mac}/command`):
```json
{ "command": "ON", "deviceId": "<uuid>" }
```
> ⚠️ See Known Issues below — `deviceId` is currently missing from backend payloads.

**Config ACK** (SmartBox → `smartboxes/{mac}/config/ack`):
```
OK
```

### MQTT Client Pattern

The firmware uses **two client roles**:
- **Persistent subscriber** (`MQTTClient`): connected throughout the main loop; handles inbound config and command messages
- **Temporary publishers** (short-lived `MQTTClient` instances): used for discovery and config ACK to avoid blocking the main client

Always call `client.check_msg()` in the main loop (non-blocking). Never call `client.wait_msg()` in production.

---

## Module Responsibilities

### `main.py`
- Loads `userconfig/client_config.json`; falls back to captive portal (`start_ap()`) if missing or incomplete
- Connects WiFi, initialises GPIO from cached `device_config.json`, connects MQTT
- Sends initial discovery, then runs the polling loop
- Handles WiFi/MQTT reconnection before every `check_msg()` call

### `mqttutils.py`
- `connect_mqtt(client_id, broker, port)` — connects, subscribes to `/command` and `/config` topics, registers `message_callback`
- `message_callback(topic, msg)` — routes inbound messages:
  - `/config` → `_handle_config()` → reset GPIO, save to flash, send ACK
  - `/command` → parse JSON, route to `gpio_manager.set(device_id, value)` (or LED fallback if `deviceId` absent)
- `publish_discovery(broker, port, client_id)` — temporary client, publishes to `smartboxes/discovery`
- `load_device_config()` / `save_device_config()` — read/write `userconfig/device_config.json`

### `hardware.py`
- `GpioManager` — manages a `device_id → machine.Pin` mapping
  - `setup(devices)` — initialises pins from config list; sets all to 0
  - `reset()` — turns all pins off and clears the map
  - `set(device_id, value)` — sets a single pin; returns `False` if `device_id` unknown
  - `known_ids()` — returns list of registered device UUIDs
- `led` — module-level `Pin("LED", Pin.OUT)` for onboard LED

### `networkutils.py`
- `get_mac_address()` — returns 12-char hex MAC string (used as MQTT client ID)
- `connect_wifi(ssid, password, timeout=20)` — connects with a second-by-second polling loop
- `reconnect_wifi(ssid, pw)` — retries up to `RECONNECT_ATTEMPTS` times with `RECONNECT_DELAY_S` between attempts
- `get_sta_ip()` — returns current IP or `None`

---

## Known Issues & Open Work

These are confirmed gaps — fix them when working in this area:

### 🔴 CRITICAL: `deviceId` missing from command payloads

**Problem:** The backend sends `{"command": "ON", "commandId": "..."}` but omits `deviceId`. The SmartBox cannot route the command to the correct GPIO pin and falls back to controlling the onboard LED.

**Fix needed (backend side — `DeviceController.java`):** Include `"deviceId"` in the published JSON.  
**SmartBox side is ready** — `message_callback` already reads `deviceId` and calls `gpio_manager.set()` correctly.

### 🔴 CRITICAL: No device command ACK

**Problem:** After executing a command, the SmartBox sends no acknowledgment. The backend subscribes to `smartboxes/+/devices/ack` but has no handler, and the SmartBox never publishes to this topic.

**Fix needed (both sides):**
- SmartBox: after `gpio_manager.set()` succeeds, publish a result to `smartboxes/{mac}/device/{deviceId}/executed`
- Backend: implement `SmartBoxDeviceAckHandler`

### 🟡 HIGH: `defaultSignalDurationS` / `fireDelayMs` not used

**Problem:** The config includes a pulse duration per device, but commands are simple ON/OFF toggles with no timing. The pin stays permanently ON until an OFF command arrives.

**Fix needed (SmartBox side):** For `signalType == "WERFER"`, treat ON as a timed pulse: `set ON → sleep(duration) → set OFF` automatically.

### 🟡 HIGH: Topic structure doesn't route per-device

**Current:** `smartboxes/{mac}/command` — all devices on one box share a topic.  
**Better:** `smartboxes/{mac}/device/{deviceId}/command` — one topic per device, simpler routing.  
Coordinate this change with the backend before implementing.

---

## Deployment Notes

Files are uploaded directly to the Pico's filesystem (e.g. via `mpremote`, `rshell`, or Thonny). The Pico auto-runs `main.py` on power-up.

- **Flash all files in `mqtt/`** to the root of the Pico filesystem
- The `userconfig/` and `systemconfig/` directories must exist on the Pico
- `userconfig/client_config.json` is written by the captive portal and persists across reboots
- `userconfig/device_config.json` is written by the SmartBox after a successful config push

Do **not** deploy anything from `web(old)/`.

---

## Memory Constraints — Always Keep in Mind

> **This is not a server. The Raspberry Pi Pico 2W has 520 KB of SRAM.** MicroPython's runtime, the network stack, and the MQTT library together consume a significant chunk of that before a single line of application code runs. Every byte you allocate competes with the heap the firmware needs to stay alive.

AI agents working on this firmware must treat memory as a first-class constraint, not an afterthought. Violating these rules will cause silent heap exhaustion, `MemoryError` crashes, or — worst — hard-to-reproduce instability under real operating conditions.

### Hard rules

| Rule | Reason |
|---|---|
| Call `gc.collect()` after processing any inbound MQTT message | JSON parsing allocates multiple intermediate objects; collect immediately once they are no longer needed |
| Never build strings by concatenation in a loop | Each `+` creates a new object; use a list and `"".join()` — or, better, avoid building large strings at all |
| Do not keep parsed JSON dicts alive longer than necessary | Parse → extract the values you need → let the dict go out of scope → `gc.collect()` |
| Do not accumulate lists that grow unboundedly | Queues, history buffers, retry lists — cap them or avoid them entirely |
| Prefer `const()` for integer constants | `from micropython import const` avoids allocating a name in the module dict |
| Avoid deep call stacks | Each frame costs stack space; keep the main loop flat |
| Do not log with f-strings that format large objects | `print(f"config: {big_dict}")` allocates a temporary string; print key fields individually instead |

### Guidance for agents

- **Before adding any new data structure**, ask: does this need to exist for the full lifetime of the program, or only during one operation? If the latter, scope it to a function.
- **When adding a new MQTT handler**, always end it with `gc.collect()`.
- **When caching data to flash** (`device_config.json`), keep the JSON minimal. Do not persist data that can be re-derived from the backend config push.
- **Do not import modules you don't use.** Every imported module stays in RAM.
- **Test mental models against actual RAM.** You can check free heap at any point with:
  ```python
  import gc
  print("Freier Heap:", gc.mem_free(), "Bytes")
  ```
  Add this log line temporarily when debugging suspected memory pressure; remove it before committing.

---

## What to Avoid

- ❌ Do not use `asyncio` / `uasyncio` unless explicitly requested
- ❌ Do not use complex f-string expressions (MicroPython has limited support)
- ❌ Do not hardcode credentials anywhere outside the `# --- KONFIGURATION ---` block
- ❌ Do not suppress exceptions silently — always `print()` the error
- ❌ Do not call `client.wait_msg()` in the main loop (it blocks)
- ❌ Do not modify or reference anything in `web(old)/`
- ❌ Do not add CPython-only dependencies
