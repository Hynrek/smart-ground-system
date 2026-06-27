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
- Allowed stdlib modules: `network`, `time`, `machine`, `json`, `sys`, `gc`, `os` (only in `ota.py`), `hashlib` (only in `ota.py`)
- MQTT: `umqtt.simple` (preferred); fall back to `umqtt.robust`
- OTA HTTP download uses `urequests` (lazy-imported inside `ota.py`'s `_default_http_stream`)
- No `asyncio` / `uasyncio` — all logic is synchronous and polling-based
- No `import os` for **file I/O** — use `open()` directly. Exception: `ota.py` imports `os` for **directory operations** (`mkdir`/`listdir`/`rename`/`remove`/`stat`) that `open()` cannot do, and `mqttutils.py` reads `os.uname()` for the kernel version. Neither is plain file read/write.
- Do not import modules you don't use — every imported module stays in RAM for the lifetime of the process

---

## Project Structure

```
smart-box/
├── main.py                             # Entry point; auto-runs on boot (incl. OTA boot supervisor)
├── mqttutils.py                        # MQTT connection, message routing, security, config persistence, OTA status
├── ota.py                              # OTA: download/verify/stage/apply App Code + esp32.Partition firmware update
├── hardware.py                         # GpioManager class + onboard LED
├── networkutils.py                     # WiFi connect/reconnect helpers
├── accesspoint.py                      # Captive portal for first-time WiFi setup
├── accesspoint.html                    # HTML served by the captive portal
├── boards/
│   ├── pico2w.py                       # Board-Konstanten und Init für Raspberry Pi Pico 2W
│   ├── xiao_esp32s3.py                 # Board-Konstanten und Init für XIAO ESP32-S3
│   └── test_board.py                   # Neutraler Stub für Host-Tests
├── systemconfig/
│   ├── accesspoint_config.json         # AP SSID/password (read-only at runtime)
│   └── firmware_config.json            # Firmware version + capability registry (read-only at runtime)
└── userconfig/
    ├── client_config.json              # WiFi credentials + broker IP (written by setup portal)
    ├── device_config.json              # Active device/GPIO config (written after each config push)
    └── ota_state.json                  # OTA probation state (phase, version, boot_attempts) — written during updates
```

---

## Module Responsibilities

### `main.py`
Selects the board module at the very top of the file via `sys.platform` → `boards/<name>.py` map, registers it as `sys.modules["board"]`, and calls `board.board_init()`. This happens before all other imports because `hardware.py` needs `board.LED_PIN` at module load time. Uses `board.WDT_TIMEOUT_MS` for the watchdog timeout. Loads `userconfig/client_config.json`; falls back to `start_ap()` if absent or incomplete (`client_network_ssid` or `broker_ip` missing). Pre-loads GPIO from `userconfig/device_config.json` before MQTT connects. Honors `broker_port` (default 1883). Connects MQTT, sends discovery **once on boot**, then runs the main polling loop. Activates a `machine.WDT` (timeout `WDT_TIMEOUT_MS`, 8 s) immediately before the loop — main-loop only, since AP/first-connect can legitimately wait; feeds it each tick and threads it through the reconnect helpers. Checks WiFi and MQTT connection before every `check_msg()` call; calls `update_device_pulses()` on every tick. Publishes a **heartbeat** (`publish_heartbeat`) every `PUBLISH_INTERVAL_S` (20 s) instead of re-announcing discovery, and runs `gc.collect()` after each publish.

### `mqttutils.py`
Everything MQTT-related plus security:
- `connect_mqtt(client_id, broker, port)` — connects, subscribes to `smartboxes/{mac}/command` and `smartboxes/{mac}/config`, stores the client globally so ACKs and discovery reuse the same connection
- `reconnect_mqtt()` — retries `connect_mqtt` up to `RECONNECT_ATTEMPTS` (12) times with `RECONNECT_DELAY_S` (10 s) between attempts
- `message_callback(topic, msg)` — central router: `/config` → `_handle_config()`, `/command` → validates, applies security checks, calls `gpio_manager.set()`
- `_handle_config(payload)` — resets GPIO, rebuilds device map, saves to flash, sends ACK only if ≥1 device was initialised successfully
- `_send_config_ack()` — publishes `OK` to `smartboxes/{mac}/config/ack`
- `_send_device_command_ack(device_id)` — publishes `OK` to `smartboxes/{mac}/device/{deviceId}/executed`
- `publish_discovery(client_id)` — publishes the discovery payload **once on boot** via the persistent client; reads firmware version and box type from `systemconfig/firmware_config.json` (never hardcoded)
- `publish_heartbeat(client_id)` — publishes `{ "mac": ... }` to `smartboxes/{mac}/status` every `PUBLISH_INTERVAL_S`; the backend uses it for liveness (lastSeen/ONLINE) without triggering a config push
- `reconnect_mqtt(wdt=None)` — retries `connect_mqtt`; feeds the optional watchdog during the wait
- `load_device_config()` / `save_device_config(devices)` — read/write `userconfig/device_config.json`
- `load_firmware_config()` — reads `systemconfig/firmware_config.json` (cached in module global after first load)
- `update_device_pulses()` — thin wrapper around `gpio_manager.tick()`
- `_update_known_devices(devices)` — rebuilds `_known_devices` set; prunes stale `_blocked_devices` entries

Security state in module globals: `_known_devices` (set of authorised UUIDs) and `_blocked_devices` (admin BLOCK via `ADMIN_BLOCK_TOKEN`, removed only by UNBLOCK). No rate limiting and no auto-block — see **Security Model**.

### `hardware.py`
`GpioManager` manages a `device_id → {pin, device, direction, command, signal_duration_ms, blocked}` mapping.
- `setup(devices)` — initialises pins from config list; GPIO pins set to 0; LED devices need no pin object
- `reset()` — turns all pins off, clears the map and pulse tracker
- `set(device_id, value, signal_duration_ms=None)` — single-phase, non-blocking. For GPIO/LED with effective `signal_duration_ms > 0`, drives HIGH and schedules auto-LOW after the duration (tracked via `ticks_ms`). **Busy-reject:** an ON command for a device with a pulse already active is ignored (returns False). OFF cancels any active pulse. LED devices reject `value=0`. Command-level `signal_duration_ms` overrides the config value. No delay handling (delay is a backend concern — see Known Issues).
- `tick()` — called every main loop tick (via `update_device_pulses()`); expires timed pulses using `ticks_diff` (handles 32-bit rollover correctly)
- `feed_sleep_ms(total_ms, wdt=None, chunk_ms=FEED_SLEEP_CHUNK_MS)` — sleeps in chunks, feeding the optional watchdog between them (used by the reconnect waits)
- `known_ids()` — returns list of registered device UUIDs
- Module-level `led = Pin(board.LED_PIN, Pin.OUT)` — pin comes from the board module, not hardcoded.
- **Status-LED helpers** (boot/connection feedback on the onboard LED): `led_on()`, `led_off()`, `led_toggle()`, and `status_blink(times=3, on_ms=150, off_ms=150)` (blinks, then leaves the LED off). Timing constants `STARTUP_BLINKS`, `BLINK_ON_MS`, `BLINK_OFF_MS`, `CONNECTING_TOGGLE_MS` live in the `# --- KONFIGURATION ---` block. See **Boot Status LED** below.

### `networkutils.py`
- `get_mac_address()` — returns 12-char lowercase hex MAC string used as MQTT client ID and topic segment
- `connect_wifi(ssid, password, timeout=20, wdt=None)` — `ticks_ms`-deadline poll loop honoring `timeout` seconds; toggles the onboard LED every `CONNECTING_TOGGLE_MS` (250 ms) while waiting, feeds the optional watchdog, and calls `led_off()` on success; returns `True` on success
- `reconnect_wifi(ssid, pw, wdt=None)` — retries up to `RECONNECT_ATTEMPTS` (12) times with `RECONNECT_DELAY_S` (10 s) between attempts, feeding the optional watchdog during each wait (chunked via `feed_sleep_ms`)
- `get_sta_ip()` — returns current IP or `None`

### `accesspoint.py`
Captive portal for first-time WiFi setup. Runs in AP mode (SSID/password from `systemconfig/accesspoint_config.json`), serves `accesspoint.html`, writes validated credentials to `userconfig/client_config.json`. Only accepts keys `client_network_ssid`, `client_network_pw`, `broker_ip`, `broker_port`. Requires `client_network_ssid` and `broker_ip`; rejects with 400 otherwise. Reboots after saving. Keep entirely separate from MQTT logic — it runs as an alternative boot path, not alongside MQTT. Calls `led_on()` (solid LED) before entering the socket loop to signal AP mode.

### `boards/` (board modules)

One file per supported hardware type. All files are uploaded to every device; the correct one is selected at runtime by `main.py` using `sys.platform`. Each module exports a fixed interface:

| Symbol | Type | Description |
|---|---|---|
| `BOX_TYPE` | `str` | Canonical name sent in the discovery payload |
| `LED_PIN` | `int` or `str` | Passed to `machine.Pin()` for the onboard LED |
| `WDT_TIMEOUT_MS` | `int` | Watchdog timeout in milliseconds |
| `board_init()` | function | Called once at boot; no-op if nothing board-specific is needed |

`main.py` registers the selected module as `sys.modules["board"]` so other modules can do a plain `import board`. Adding support for a new board = one new file in `boards/`, no changes to core logic.

---

## Boot Status LED

The onboard LED signals the boot/connection phase so the box can be diagnosed without a serial console. All signals occur **before** MQTT is live, so they never conflict with MQTT-controllable LED devices.

| Phase | LED behavior | Driven from |
|---|---|---|
| Boot/startup | Blink 3× (150 ms on / 150 ms off) | `status_blink()` at top of `main.py` |
| Access-point setup mode | Solid ON | `led_on()` in `accesspoint.start_ap()` |
| Connecting to WLAN | Toggle every 250 ms | `connect_wifi()` in `networkutils.py` |
| Connected to WLAN | OFF | `led_off()` in `connect_wifi()` on success |

Because `reconnect_wifi()` calls `connect_wifi()`, the connecting blink also appears during runtime WiFi-reconnects (intended feedback). Helpers live in `hardware.py`; `networkutils.py` and `accesspoint.py` import them (no import cycle — `hardware` imports only `time`/`machine`/`micropython`).

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

4. **Avoid unbounded growing lists** — `_blocked_devices` is pruned in `_update_known_devices` on every config push (stale device IDs removed)

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
| SmartBox → Backend | `smartboxes/discovery` | Boot announcement (sent once per boot); triggers config push |
| SmartBox → Backend | `smartboxes/{mac}/status` | Heartbeat / liveness — sent every 20 s; payload `{ "mac": "..." }` |
| Backend → SmartBox | `smartboxes/{mac}/config` | Full device/GPIO configuration |
| SmartBox → Backend | `smartboxes/{mac}/config/ack` | Confirms config was applied (`OK`) |
| Backend → SmartBox | `smartboxes/{mac}/command` | Device control command |
| SmartBox → Backend | `smartboxes/{mac}/device/{deviceId}/executed` | Per-device command ACK (`OK`) |
| Backend → SmartBox | `smartboxes/{mac}/ota` | OTA update trigger (`{ type, version, url, sha256, size }`) |
| SmartBox → Backend | `smartboxes/{mac}/ota/status` | OTA progress (`{ version, phase, progress, detail }`) |

### Discovery payload (SmartBox → `smartboxes/discovery`)
`appVersion` (App Code) comes from `systemconfig/firmware_config.json`; `firmwareVersion` (the MicroPython kernel) comes from `os.uname().release`; box type comes from the board module.
```json
{ "mac": "aabbccddeeff", "appVersion": "0.6", "firmwareVersion": "micropython-1.23.0", "boxType": "xiao-esp32s3", "ip": "192.168.1.42" }
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
      "blocked": false
    }
  ]
}
```
`device` is `"GPIO"` or `"LED"`. For GPIO, `command` is the pin number as a string. LED devices use the onboard LED. The firmware ignores any extra keys (e.g. a `delay_ms` still sent by an older backend).

### Command payload (Backend → `smartboxes/{mac}/command`)
```json
{ "command": "ON", "commandId": "<uuid>", "deviceId": "<uuid>", "signalDurationMs": 500 }
```
Valid `command` values: `ON`, `OFF`, `BLOCK`, `UNBLOCK`. `signalDurationMs` is an optional override for the per-device config value. (Delay is no longer handled by firmware — see Known Issues.)

---

## Security Model

Commands pass through these checks in `message_callback` before reaching hardware:

1. **Allowlist** — `deviceId` must be in `_known_devices` (populated from the last config push). Unknown IDs are rejected and dropped (no ACK).
2. **Admin block** — `BLOCK` sets a permanent (`ADMIN_BLOCK_TOKEN`) block; only `UNBLOCK` removes it. No auto-expiry.
3. `BLOCK`/`UNBLOCK` bypass the ON/OFF security path and do not require the device to be in `_known_devices` for UNBLOCK.

Operational (not security): an ON command for a device whose pulse is still running is ignored (**busy-reject**, in `GpioManager.set`); OFF always cancels. This replaced the former per-command rate limiter.

There is **no rate limiter and no auto-block**. They were removed deliberately: the rate limiter compared against second-resolution `time.time()` (so its 0.1 s window never worked) and fed an auto-block that, in practice, locked legitimate devices on bursts/misconfig while giving unknown IDs (already rejected by the allowlist) an unbounded-growth memory vector. Do not reintroduce them; the allowlist + admin block + busy-reject are the model. Do not otherwise bypass or weaken these checks.

**MQTT delivery:** commands are QoS 0 (umqtt.simple). The firmware confirms execution via the `/executed` ACK; reliable delivery is the backend's responsibility (retry on missing ACK), not the firmware's.

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

- `KeyboardInterrupt` → clean shutdown, **no** explicit reset
- All other exceptions → `machine.reset()` after a 5-second delay
- `finally` → always disconnect MQTT, deactivate both WLAN interfaces
- Always create a fresh `network.WLAN()` instance in `finally` — do not rely on outer variables that may not be defined if the error happened early

> **Watchdog caveat (RP2):** once the main loop has started, `machine.WDT` is active and **cannot be stopped** on the RP2. So a `KeyboardInterrupt` in normal operation runs the cleanup but the box still resets ~`WDT_TIMEOUT_MS` (8 s) later — the "no reset" guarantee only fully holds on the AP/first-connect path, where the WDT isn't running yet. This limits REPL debugging after Ctrl-C: re-flash via BOOTSEL if you need an un-watched session. Relatedly, a very slow `MQTTClient.connect()` (broker unreachable >8 s) will be cut short by a WDT reset; this is intended recovery — equivalent to the reset the reconnect path already performs on failure.

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

Describes firmware capabilities; read when building the discovery payload. `app_version` is the App Code version reported as `appVersion` (the OTA target the backend compares against); `firmware_version` is retained for backward compatibility. The `boxType` field in the discovery payload comes from `board.BOX_TYPE`; `firmwareVersion` (the MicroPython kernel) comes from `os.uname()`, not this file:
```json
{
  "app_version": "0.6",
  "firmware_version": "0.6",
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

## Host Tests

Logic that doesn't need real hardware is unit-tested under CPython. From `smart-box/`:

```bash
python -m unittest discover -s tests -t . -v
```

`tests/_stubs.py` installs fake `machine`/`micropython`/`network`/`umqtt.simple` modules into `sys.modules` and patches a **controllable clock** (`ticks_ms`/`ticks_add`/`ticks_diff`/`sleep_ms`, advanced via `_stubs.clock.advance(ms)`) onto the real `time` module, so timing is deterministic with no real sleeps. `tests/__init__.py` puts the repo root on `sys.path` and installs the stubs before any firmware import. Covered: scheduler timing + busy-reject, security routing (allowlist/admin block), config handling, discovery/heartbeat payloads, and the watchdog-fed sleep. Hardware-only paths (`main.py`, real GPIO/WDT/WiFi) are still verified manually on the Pico 2W. `boards/test_board.py` provides a neutral board stub (`BOX_TYPE = "test-board"`, `LED_PIN = 0`). `tests/_stubs.py` registers it as `sys.modules["board"]` before any firmware module is imported, so `import board` in `hardware.py` and `mqttutils.py` resolves correctly under CPython.

## Known Issues & Open Work

### Delay handling moved to the backend (firmware no longer applies `delay_ms`)
The firmware previously blocked the polling loop on `time.sleep_ms(delay_ms)` in `GpioManager.set()`. Delay is now entirely a backend concern: the firmware ignores the field and never delays. If a delayed-release / staggered-double requirement is confirmed, it is implemented backend-side (scheduling the command publish at send-time using `DeviceType.delaySignalDurationMs`, which is retained in the backend domain). Backend MQTT payloads should drop `delay_ms` (config) / `delaySignalDurationMs` (command) — tracked as a backend task.

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

## OTA Updates (cable-free, over the LAN)

The firmware can update itself over the network — no cable needed. Two distinct things can be updated, with deliberately different mechanisms:

| Term | What it is | Mechanism |
|---|---|---|
| **App Code** | the SmartBox `.py` logic (`main.py`, `mqttutils.py`, `ota.py`, `boards/`, configs) | file-level: download → verify → stage → atomic swap + backup |
| **Firmware** | the MicroPython kernel image | native `esp32.Partition` A/B OTA (ESP-IDF bootloader auto-rollback) |

App Code OTA is the everyday path; Firmware OTA is rare (kernel bumps). Both share the MQTT control channel and the HTTP download transport. Firmware OTA only works on the ESP32-S3 (the RP2350/Pico needs physical BOOTSEL for the kernel).

### `ota.py` module

All OTA logic lives here (the one module allowed to `import os`, for directory operations). Key functions:

- `handle_command(payload, publish_status, wdt, reset, live_root)` — entry from the MQTT router; orchestrates the APP path (download → verify → apply → `begin_probation` → reset) or the FIRMWARE path. Rejects a second command while one is in progress (`is_busy`).
- `download_app(base_url, manifest_sha256, wdt)` — streams `manifest.json` to `OTA_STAGING_DIR`, verifies it against the **MQTT-supplied hash** (the trust anchor, since per-file hashes live inside the HTTP-fetched manifest), then streams + per-file-SHA-256-verifies each file. Rejects path traversal (`..`). Raises `OtaError` on any failure — **live code is never touched until everything is downloaded and verified.**
- `verify_file(path, expected_hex)` — streaming SHA-256 (manual hex; MicroPython `hashlib` has no `hexdigest()`).
- `apply_app(manifest, live_root)` — writes a `pending` marker (file list), backs up each live file to `OTA_BACKUP_DIR`, copies staging over live, writes an `applied` marker, cleans up.
- `recover_interrupted_apply(live_root)` — boot-time: if `pending` exists without `applied` (power lost mid-apply), restores from backup.
- `probation_check(live_root)` — boot-time: counts probation boots; after `MAX_PROBATION_BOOTS` without a healthy confirm, restores the backup and leaves a one-shot `pending_report`. The new version gets the boots before that to prove itself.
- `confirm_boot_healthy()` — called after a successful MQTT connect; ends probation, deletes the backup, returns `("APPLIED", version)`. For FIRMWARE also calls `Partition.mark_app_valid_cancel_rollback()`.
- `take_pending_report()` — returns and clears the one-shot `ROLLED_BACK` report after a rollback reboot.
- HTTP is the replaceable seam `http_stream` (real impl uses `urequests`; host tests inject a fake).

`http_stream`/`esp32.Partition`/real flash writes are verified manually on the ESP32-S3; all other logic is host-tested under CPython via `tests/_stubs.py` (which now also stubs `esp32.Partition` and `os.uname`).

### Boot flow (in `main.py`, before the watchdog starts)

`ota.recover_interrupted_apply()` → `ota.probation_check()` (on rollback: reset into the restored old version) → connect MQTT → `set_watchdog(wdt)` → `ota.confirm_boot_healthy()` (publish `APPLIED`) → `ota.take_pending_report()` (publish `ROLLED_BACK` after a rollback reboot).

### Topics & payloads

- Trigger `smartboxes/{mac}/ota`: `{ "type": "APP"|"FIRMWARE", "version", "url", "sha256", "size" }` (`sha256` = manifest/image hash).
- Status `smartboxes/{mac}/ota/status`: `{ "version", "phase", "progress", "detail" }`, phase ∈ `DOWNLOADING|VERIFYING|APPLYING|APPLIED|FAILED|ROLLED_BACK`.
- App manifest `GET {url}/manifest.json`: `{ "appVersion", "files": [ { "path", "sha256", "size" } ] }`; files at `GET {url}/files/{path}`. Firmware image at `GET {url}`.

### Known OTA limitations

- **Health = "boots and runs", not "broker reachable".** `confirm_boot_healthy()` runs right after WiFi connects (before MQTT), so a temporarily unreachable broker does **not** trigger a false rollback of a healthy new version. The flip side: probation ends (backup deleted) once the box reaches the main path, so a new version that boots+runs but is functionally broken in a way that survives WiFi connect won't auto-rollback — always test a build on hardware before pushing it to a fleet.
- **Unreachable HTTP server during download → watchdog reset.** `urequests.get()` blocks on DNS/TCP connect with no watchdog feed. If the OTA URL host is unreachable, the connect timeout can exceed `WDT_TIMEOUT_MS` (8 s) and the WDT resets the box mid-OTA. This is safe recovery (same as an unreachable broker): `recover_interrupted_apply()` restores any half-applied state on the next boot; no `FAILED` status is published in this case.
- **Newly-added files are not crash-safe during apply.** `apply_app` only backs up files that already exist. A file the update *adds* (no live counterpart) has no backup, so a power loss while copying it leaves it partial. The previous code is fully restored, so this is safe **unless** the update adds a brand-new module that the restored old `main.py` imports — avoid OTA updates that both add a new imported module and depend on it in the same release, or split into two releases.

### Manual (cable) re-flash — still the recovery path

If a box is bricked (e.g. before OTA is deployed, or a kernel that won't boot): on the ESP32-S3, re-flash MicroPython over USB with `esptool`, then re-upload project files via `mpremote`/Thonny.
