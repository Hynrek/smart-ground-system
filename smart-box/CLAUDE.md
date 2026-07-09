# smart-box — MicroPython Firmware Development Guide

## Project Overview

The Smart Box firmware runs on the **Seeed XIAO ESP32-S3** (the current and only supported target) and manages physical devices (GPIO outputs, LEDs, sensors) over MQTT. It receives commands from the backend, publishes device state and sensor events, handles the initial WiFi access-point setup flow, and can update itself over the LAN (OTA). The board abstraction in `boards/` remains — `boards/pico2w.py` is parked (Raspberry Pi Pico 2W is no longer an active target) and re-adding a board is one new file, no core changes.

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

- **MicroPython**: v1.28.0 kernel (`ESP32_GENERIC_S3-SPIRAM_OCT` image in `setup/`); code sticks to 1.23+ APIs
- **Board**: Seeed XIAO ESP32-S3 (dual-core Xtensa LX7; 512 KB internal SRAM + 8 MB PSRAM via the SPIRAM image; 8 MB flash)
- **WiFi**: Built-in, managed via MicroPython `network` module
- **Onboard LED**: `Pin(board.LED_PIN)` — GPIO 21 on the XIAO, **active-low** (`LED_ON = 0` in `boards/xiao_esp32s3.py`); module-level `led` in `hardware.py`
- **MQTT**: `umqtt.simple` (vendored in `lib/umqtt/`)
- **No asyncio**: Synchronous polling loop only

## Language & Runtime

- **MicroPython 1.23+** only — no CPython-specific syntax or libraries
- Allowed stdlib modules: `network`, `time`, `machine`, `json`, `sys`, `gc`, `os` (only in `ota.py`), `hashlib` (`ota.py`, `espnow_crypto.py`), `struct` (`frame_envelope.py`)
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
├── espnow_crypto.py                    # AES-256-GCM (ucryptolib ECB + hand-rolled GHASH/CTR) + HKDF-SHA256 (ESP-NOW pairing/session keys)
├── frame_envelope.py                   # Klartext-Routing-Header (pack/unpack) + Duplikat-Erkennung (SeenCache) fuer ESP-NOW-Frames
├── hardware.py                         # GpioManager class + onboard LED
├── networkutils.py                     # WiFi connect/reconnect helpers
├── accesspoint.py                      # Captive portal for first-time WiFi setup
├── accesspoint.html                    # HTML served by the captive portal
├── boards/
│   ├── xiao_esp32s3.py                 # Board-Konstanten und Init für XIAO ESP32-S3 (aktuelles Ziel)
│   ├── pico2w.py                       # Pico 2W — geparkt, kein aktives Ziel mehr
│   └── test_board.py                   # Neutraler Stub für Host-Tests
├── lib/
│   └── umqtt/                          # Vendored umqtt.simple (uploaded with the app code)
├── systemconfig/
│   ├── accesspoint_config.json         # AP SSID/password (read-only at runtime)
│   ├── firmware_config.json            # App version + capability manifest + config schema version (read-only at runtime)
│   └── ca.crt                          # Pinned Dev-CA root (PEM), used as MQTT TLS cadata (read-only at runtime)
├── userconfig/
│   ├── client_config.json              # WiFi credentials + broker IP + MQTT dynsec username/password (written by setup portal / config-push)
│   ├── device_config.json              # Active device/GPIO config + config_schema_version (written after each config push)
│   └── ota_state.json                  # OTA probation state (phase, version, boot_attempts) — written during updates
├── tests/                              # Host tests (CPython + stubs) — see Host Tests
├── tools/
│   └── pack_ota.py                     # Builds an OTA App-Code release zip + manifest
├── dist/                               # Packed OTA releases (build output)
├── setup/                              # MicroPython kernel images for flashing (e.g. ESP32-S3 .bin)
└── docs/                               # Superpowers plans/notes
```

---

## Module Responsibilities

### `main.py`
Selects the board module at the very top of the file via `sys.platform` → `boards/<name>.py` map, registers it as `sys.modules["board"]`, and calls `board.board_init()`. This happens before all other imports because `hardware.py` needs `board.LED_PIN` at module load time. Uses `board.WDT_TIMEOUT_MS` for the watchdog timeout. Loads `userconfig/client_config.json`; falls back to `start_ap()` if absent or incomplete (`client_network_ssid` or `broker_ip` missing). Pre-loads GPIO from `userconfig/device_config.json` before MQTT connects. Honors `broker_port` (default 8883, TLS). Passes `mqtt_username`/`mqtt_password` from `client_config.json` (both optional) through to `connect_mqtt()`. Connects MQTT, sends discovery **once on boot**, then runs the main polling loop. Activates a `machine.WDT` (timeout `WDT_TIMEOUT_MS`, 8 s) immediately before the loop — main-loop only, since AP/first-connect can legitimately wait; feeds it each tick and threads it through the reconnect helpers. Checks WiFi and MQTT connection before every `check_msg()` call; calls `update_device_pulses()` on every tick. Publishes a **heartbeat** (`publish_heartbeat`) every `PUBLISH_INTERVAL_S` (20 s) instead of re-announcing discovery, and runs `gc.collect()` after each publish.

### `mqttutils.py`
Everything MQTT-related plus security:
- `connect_mqtt(client_id, broker, port=8883, user=None, password=None)` — connects over **TLS** (`ssl=True`, `ssl_params={"cert_reqs": ussl.CERT_REQUIRED, "cadata": <systemconfig/ca.crt bytes>}`, see **MQTT TLS + Credentials** below) with the given dynsec `user`/`password`, subscribes to `smartboxes/{mac}/command`, `smartboxes/{mac}/config`, and `smartboxes/{mac}/ota`, stores the client globally so ACKs and discovery reuse the same connection. Refuses to connect (returns `None`) if `systemconfig/ca.crt` is missing — never falls back to unverified TLS or plaintext.
- `reconnect_mqtt()` — retries `connect_mqtt` up to `RECONNECT_ATTEMPTS` (12) times with `RECONNECT_DELAY_S` (10 s) between attempts, reusing the last-used `user`/`password` (module globals `_mqtt_user`/`_mqtt_password`)
- `load_ca_cert()` — reads `systemconfig/ca.crt` (PEM bytes, cached after first load, like `load_firmware_config()`)
- `message_callback(topic, msg)` — central router: `/config` → `_handle_config()`, `/command` → validates, applies security checks, calls `gpio_manager.set()`
- `_handle_config(payload)` — resets GPIO, rebuilds device map, saves to flash, sends ACK only if ≥1 device was initialised successfully
- `_send_config_ack()` — publishes `OK` to `smartboxes/{mac}/config/ack`
- `_send_device_command_ack(device_id)` — publishes `OK` to `smartboxes/{mac}/device/{deviceId}/executed`
- `publish_discovery(client_id)` — publishes the discovery payload **once on boot** via the persistent client; reads firmware version and box type from `systemconfig/firmware_config.json` (never hardcoded)
- `publish_heartbeat(client_id)` — publishes `{ "mac": ... }` to `smartboxes/{mac}/status` every `PUBLISH_INTERVAL_S`; the backend uses it for liveness (lastSeen/ONLINE) without triggering a config push
- `reconnect_mqtt(wdt=None)` — retries `connect_mqtt`; feeds the optional watchdog during the wait
- `load_device_config()` / `save_device_config(devices)` — read/write `userconfig/device_config.json`
- `load_firmware_config()` — reads `systemconfig/firmware_config.json` (cached in module global after first load)
- `_persist_mqtt_credentials(username, password)` — read-modify-write merge of `mqtt_username`/`mqtt_password` into `userconfig/client_config.json`, preserving existing WiFi/broker fields. The **only** place `client_config.json` is written outside `accesspoint.py`'s first-setup form — kept centralized here rather than duplicated across `main.py`/`accesspoint.py`.
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

One file per hardware type. All files are uploaded to every device; the correct one is selected at runtime by `main.py` using `sys.platform`. The active target is `xiao_esp32s3.py`; `pico2w.py` is parked (kept so Pico support can be revived without core changes). Each module exports a fixed interface:

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

The XIAO ESP32-S3 has 512 KB internal SRAM plus 8 MB PSRAM (the SPIRAM kernel image maps PSRAM into the MicroPython heap), so heap pressure is far lower than on the old Pico target — but the discipline stays: fragmentation, not total size, is what kills long-running MicroPython processes, and the same AppCode should remain portable to smaller boards.

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

1. **Install the MicroPython kernel** (XIAO ESP32-S3): flash the `.bin` from `setup/` with `esptool` (hold the BOOT button while plugging in to enter bootloader mode if needed)

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
   mpremote connect <port> cp ota.py :ota.py
   mpremote connect <port> cp hardware.py :hardware.py
   mpremote connect <port> cp networkutils.py :networkutils.py
   mpremote connect <port> cp accesspoint.py :accesspoint.py
   mpremote connect <port> cp accesspoint.html :accesspoint.html
   mpremote connect <port> cp -r boards :boards
   mpremote connect <port> cp -r lib :lib
   mpremote connect <port> cp systemconfig/accesspoint_config.json :systemconfig/accesspoint_config.json
   mpremote connect <port> cp systemconfig/firmware_config.json :systemconfig/firmware_config.json
   mpremote connect <port> cp systemconfig/ca.crt :systemconfig/ca.crt
   ```
   **Replace `systemconfig/ca.crt` with the real Dev-CA root for the target environment
   before flashing** — the copy checked into this repo is a throwaway placeholder (see
   the file's own doc entry above); a box flashed with the placeholder cannot verify (and
   therefore cannot connect to) any real broker.

### First Boot

On first boot, `main.py` checks for `userconfig/client_config.json`. If absent or incomplete, it starts the captive portal:

1. Phone/laptop connects to SSID from `systemconfig/accesspoint_config.json` (`SmartRange-Client-Setup`)
2. Opens browser → `192.168.4.1`
3. Fills in WiFi SSID, password, and broker IP → writes to `userconfig/client_config.json`
4. The box reboots and connects normally

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
`appVersion`, `configSchemaVersion`, and `capabilities` (App Code) come from `systemconfig/firmware_config.json`; `firmwareVersion` (the MicroPython kernel) comes from `os.uname().release`; box type comes from the board module.
```json
{ "mac": "aabbccddeeff", "appVersion": "1.0", "firmwareVersion": "micropython-1.23.0",
  "boxType": "xiao-esp32s3", "ip": "192.168.1.42",
  "configSchemaVersion": "1",
  "capabilities": { "GPIO": { "...": "..." }, "LED": { "...": "..." } } }
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
  ],
  "mqttUsername": "aabbccddeeff",
  "mqttPassword": "s3cr3t"
}
```
`device` is `"GPIO"` or `"LED"`. For GPIO, `command` is the pin number as a string. LED devices use the onboard LED. The firmware ignores any extra keys (e.g. a `delay_ms` still sent by an older backend).

`mqttUsername`/`mqttPassword` are **optional and camelCase** (wire-payload convention,
unlike `client_config.json`'s local `mqtt_username`/`mqtt_password` snake_case keys — see
below) and appear **only on the very first config push after a brand-new box's
registration** (Task D, `SmartBoxConfigPushService`). `mqttutils._handle_config()`
persists both fields into `userconfig/client_config.json` when present (via
`_persist_mqtt_credentials()`) and updates the module globals `_mqtt_user`/
`_mqtt_password` so the **next** reconnect/reboot uses them — see **MQTT TLS +
Credentials** below for why this isn't an immediate hot-swap.

### Command payload (Backend → `smartboxes/{mac}/command`)
```json
{ "command": "ON", "commandId": "<uuid>", "deviceId": "<uuid>", "signalDurationMs": 500 }
```
Valid `command` values: `ON`, `OFF`, `BLOCK`, `UNBLOCK`. `signalDurationMs` is an optional override for the per-device config value. (Delay is no longer handled by firmware — see Known Issues.)

---

## MQTT TLS + Credentials (decision, 2026-07-09 — Task C of the MQTT TLS + client auth initiative)

The broker (`smart-ground-deploy`'s Mosquitto, Task A) now requires TLS on port 8883 and
dynsec username/password auth (no anonymous access). `connect_mqtt()` connects with
`ssl=True`, `ssl_params={"cert_reqs": ussl.CERT_REQUIRED, "cadata": <systemconfig/ca.crt
bytes>}`, and the `user`/`password` kwargs — never falls back to plaintext or unverified
TLS. See `connect_mqtt`'s docstring in `mqttutils.py` and the `systemconfig/ca.crt` /
`userconfig/client_config.json` entries above for the file-level details.

**Verified vs. best-effort:** `MQTTClient(..., ssl=True, ssl_params={...})` is exactly
`umqtt.simple`'s documented `__init__` signature (`lib/umqtt/simple.py:10-11`), and its
`connect()` calls `ussl.wrap_socket(self.sock, **self.ssl_params)` (`lib/umqtt/simple.py:
59-61`) — both verified by reading the vendored source, not guessed. What is **not**
verified on real hardware: whether this project's exact pinned MicroPython build (kernel
v1.28.0, `ESP32_GENERIC_S3-SPIRAM_OCT`, per the Stack & Versions section) accepts `cadata`
as raw PEM bytes (what this code passes, i.e. the literal contents of `ca.crt`) versus
requiring DER, and whether `ussl.wrap_socket`'s parameter name is exactly `cadata` on that
build. This is implemented against the plan's own wording ("`ssl_params` mit gepinnter
Root-CA (`cadata`)") and is exactly what Phase 4's hardware verification must confirm —
tracked, not yet done.

**Credential flow:**
- `userconfig/client_config.json` gains two **optional** local-convention (snake_case)
  keys: `mqtt_username`, `mqtt_password`. `main.py`'s `load_config()` needs no change to
  read them (it already returns the whole dict); the `connect_mqtt(...)` call site passes
  `user=config.get('mqtt_username')`, `password=config.get('mqtt_password')`.
- A brand-new, factory-flashed box has neither key present → connects with
  `user=None, password=None`, which the hardened broker rejects. This is expected and
  **not fixed by this task** — see "Bootstrap credential — not yet implemented" below.
- When the backend delivers fresh credentials (the one-time `mqttUsername`/`mqttPassword`
  fields on a config push, camelCase, see the Config payload section above),
  `mqttutils._handle_config()` persists them via `_persist_mqtt_credentials()` (a
  read-modify-write merge, so existing WiFi/broker fields survive) and updates
  `_mqtt_user`/`_mqtt_password` module globals.
- **Reconnect-timing decision:** persisting does **not** trigger an immediate
  `reconnect_mqtt()`. `_handle_config()` runs inside the call stack of
  `client.check_msg()` → `wait_msg()` → `cb()` on the *currently active* socket;
  tearing that socket down and opening a new one from inside its own callback is a
  re-entrancy risk (the outer `wait_msg()`/`check_msg()` frame still expects to finish
  reading from the socket it started with). Instead, the new credentials take effect
  automatically on the **next natural reconnect** — a WiFi drop, a broker-initiated
  disconnect, or a reboot — because `reconnect_mqtt()` always reads the current
  `_mqtt_user`/`_mqtt_password` globals. The box keeps using its current (e.g. bootstrap)
  session for the remainder of the current connection, which is safe since nothing revokes
  that session mid-flight.

### Bootstrap credential — not yet implemented

The original plan (item 11) describes a factory-flashed **bootstrap credential** (a
dynsec `bootstrap` role: write-only discovery + own config-topic read) that a brand-new
box uses to authenticate its very first connection, before the backend has provisioned its
real identity. **This role does not exist yet** — Task A/B only created `backend` and
`smartbox` dynsec roles; creating `bootstrap` was explicitly out of scope for Task C (it
would touch `smart-ground-deploy/dynsec-init.sh`, a different repo). Concretely, today:
- An unprovisioned box (no `mqtt_username`/`mqtt_password` in `client_config.json`) cannot
  connect to the hardened broker at all, and therefore can never receive the first
  credentialed config push — there is currently no way for it to bootstrap itself.
- The `connect_mqtt(client_id, broker, port, user, password)` code path itself is correct
  and identical regardless of which of the three cases applies (absent/`None`, a manually
  provisioned bootstrap credential, or the box's own post-registration credential) — only
  the *values* differ, not the code.
- A real bootstrap story (the `bootstrap` dynsec role + a flash-time step that writes an
  initial `mqtt_username`/`mqtt_password` into `client_config.json`) needs its own
  follow-up plan item; do not invent one ad hoc.

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

> **Watchdog caveat:** once the main loop has started, `machine.WDT` is active and **cannot be stopped** (true on ESP32 and RP2 alike). So a `KeyboardInterrupt` in normal operation runs the cleanup but the box still resets ~`WDT_TIMEOUT_MS` (8 s) later — the "no reset" guarantee only fully holds on the AP/first-connect path, where the WDT isn't running yet. This limits REPL debugging after Ctrl-C: re-flash the kernel if you need an un-watched session. Relatedly, a very slow `MQTTClient.connect()` (broker unreachable >8 s) will be cut short by a WDT reset; this is intended recovery — equivalent to the reset the reconnect path already performs on failure.

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

### `systemconfig/firmware_config.json` (release metadata — ships with every App-Code OTA release)

Describes the App Code release; read when building the discovery payload. `app_version` is the App Code version reported as `appVersion` (the OTA target the backend compares against); `firmware_version` is retained for backward compatibility. The `boxType` field in the discovery payload comes from `board.BOX_TYPE`; `firmwareVersion` (the MicroPython kernel) comes from `os.uname()`, not this file:
```json
{
  "app_version": "1.0",
  "firmware_version": "1.0",
  "config_schema_version": "1",
  "capabilities": {
    "GPIO": {
      "directions": ["INPUT", "OUTPUT"],
      "commands": ["ON", "OFF"],
      "config_fields": { "signal_duration_ms": {"type": "int", "default": 0} }
    },
    "LED": {
      "directions": ["OUTPUT"],
      "commands": ["ON"],
      "config_fields": { "signal_duration_ms": {"type": "int", "default": 150} }
    }
  }
}
```

**This file is part of the App Code and ships with every OTA release** (decision, 2026-07-02 — replaces the former "never overwrite this file from the backend" rule, which protected the wrong tier). It carries per-release metadata (`app_version`, `config_schema_version`, `capabilities`); if an App-Code OTA did not update it, the box would announce the old `appVersion` forever and the backend's `(appVersion, boxType)` FirmwareConfig resolution would go stale. `tools/pack_ota.py` includes it in `DEFAULT_FILES` and refuses to pack a release whose version argument disagrees with `app_version` in this file. It is still never written at runtime — the config push only writes `userconfig/device_config.json`.

**What a release must never touch is `userconfig/`** (WiFi/broker credentials in `client_config.json`, the per-box `device_config.json`, `ota_state.json`). Enforced on both ends: the backend rejects uploaded release ZIPs containing `userconfig/` paths (`OtaArtifactStore`), and the firmware rejects OTA manifests containing them (`ota.py`, `download_app`).

> **Capability manifest + config schema versioning** (designed 2026-06-30, implemented on both ends: firmware announces the manifest; the backend's `SmartBoxDiscoveryHandler` upserts `FirmwareConfig` from it on every discovery).
> **Why:** the backend should learn what a box can do from the box itself (discovery payload), not from an admin manually seeding a `firmware_configs` row — that breaks for any box that wasn't provisioned through the backend's OTA path (factory-flashed, externally configured, returned hardware). The full `capabilities` object travels in the discovery payload alongside `appVersion`/`boxType` so the backend can upsert its registry on every boot, with no bootstrap gap.
>
> **`config_schema_version` exists to prevent silent corruption across AppCode upgrades.** `save_device_config()` writes this version into `device_config.json` alongside the backend-pushed device list. On boot, `main.py` compares the saved config's version with `firmware_config.json`'s; on mismatch the firmware discards the saved config instead of applying it with possibly-renamed/removed fields, and waits for the backend to push a fresh one. This catches the case where an OTA changes what a config field means without the box being aware its old config is now wrong.
>
> **`LED` is intentionally narrower than `GPIO`:** no `OFF` command, and it always targets `board.LED_PIN` (no pin config). An LED is technically just a GPIO output, but the onboard LED is being kept as a deliberate debug capability — easiest way to visually confirm MQTT communication during development/testing — not a general-purpose output. `ON` triggers a single pulse of `signal_duration_ms` (default 150 ms), reusing the same auto-off pulse mechanism `GpioManager` already uses for GPIO. Do not expose `LED` as an assignable production device type in the backend/UI; it should be labeled as debug-only.
>
> **BLOCK/UNBLOCK are not part of any capability's `commands` list.** They are security meta-commands handled directly in `mqttutils.message_callback`, independent of which capabilities a device has — this is intentional so a new capability can never accidentally ship without being blockable.

### `systemconfig/ca.crt` (pinned Dev-CA root — ships with every App-Code OTA release)

PEM-encoded root certificate of the Mosquitto broker's Dev-CA (see
`smart-ground-deploy/mosquitto/docker-entrypoint.sh`, Task A — the CA is generated per
environment/deployment). Loaded by `mqttutils.load_ca_cert()` and passed as
`ssl_params={"cadata": <raw file bytes>}` to `MQTTClient(..., ssl=True, ...)` in
`connect_mqtt()`. **Rotation runs entirely through App-Code OTA** — this file is never
written at runtime, and swapping it for a new/rotated CA is just another release, same as
bumping `firmware_config.json`. The version checked into this repo is a **throwaway
self-signed placeholder** (see the file's Subject CN) — replace it with the real
environment's `ca.crt` at provisioning/flash time; do not ship the placeholder to a real
device expecting to reach a real broker (`ussl.wrap_socket(cert_reqs=CERT_REQUIRED, ...)`
will simply refuse to verify the broker's actual server cert against it, and the box will
never connect). `tools/pack_ota.py`'s `DEFAULT_FILES` includes it alongside
`firmware_config.json`.

### `userconfig/client_config.json` (written by captive portal / config-push)

```json
{
  "client_network_ssid": "MyNetwork",
  "client_network_pw": "mypassword",
  "broker_ip": "192.168.1.100",
  "mqtt_username": "aabbccddeeff",
  "mqtt_password": "s3cr3t"
}
```
`broker_port` is optional (defaults to 8883, TLS). `mqtt_username`/`mqtt_password` are
optional (snake_case, matching this file's local convention — **not** the wire payload's
camelCase `mqttUsername`/`mqttPassword`, see below): absent on a freshly-flashed,
unprovisioned box (`connect_mqtt()` then connects with `user=None, password=None`, which
fails authentication against the hardened broker — expected until a bootstrap credential
or the box's own post-registration credential is present, see **MQTT TLS + Credentials**
below). Written either by `accesspoint.py`'s captive-portal form (WiFi/broker fields only,
full overwrite) or by `mqttutils._persist_mqtt_credentials()` (credential fields only,
merge — see below). Never hardcode these values. This file — like everything under
`userconfig/` — is never part of an OTA release; see the protection rule under
`systemconfig/firmware_config.json` above.

### `userconfig/device_config.json` (written by SmartBox after config push)

```json
{
  "config_schema_version": "1",
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
Overwritten on every config push; `config_schema_version` is copied from `firmware_config.json` at save time and checked on boot (mismatch → saved config discarded). Firmware metadata does **not** go here — it lives in `systemconfig/firmware_config.json`.

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
Press `^C` to trigger `KeyboardInterrupt`. If frozen, hold the BOOT button and replug to enter bootloader mode, then re-flash with `esptool`.

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

`tests/_stubs.py` installs fake `machine`/`micropython`/`network`/`umqtt.simple` modules into `sys.modules` and patches a **controllable clock** (`ticks_ms`/`ticks_add`/`ticks_diff`/`sleep_ms`, advanced via `_stubs.clock.advance(ms)`) onto the real `time` module, so timing is deterministic with no real sleeps. `tests/__init__.py` puts the repo root on `sys.path` and installs the stubs before any firmware import. Covered: scheduler timing + busy-reject, security routing (allowlist/admin block), config handling, discovery/heartbeat payloads, and the watchdog-fed sleep. Hardware-only paths (`main.py`, real GPIO/WDT/WiFi) are still verified manually on the XIAO ESP32-S3. `boards/test_board.py` provides a neutral board stub (`BOX_TYPE = "test-board"`, `LED_PIN = 0`). `tests/_stubs.py` registers it as `sys.modules["board"]` before any firmware module is imported, so `import board` in `hardware.py` and `mqttutils.py` resolves correctly under CPython.

**Cross-repo test dependency:** `tests/test_espnow_crypto.py` reads its fixture from
`../docs/espnow/crypto-test-vectors.json` — outside this repo, in the outer
`smart-ground` monorepo this checkout normally lives nested inside (see
`docs/espnow/crypto-test-vectors.json` in that monorepo; it is the single
canonical copy shared with the `smart-ground-node` Java tests, deliberately never
duplicated into this repo). A standalone clone of this repo (its real remote is
`github.com:Hynrek/smartground-firmware.git`) will not have that file — the
module detects this and skips with an explanatory message rather than failing.

## AppCode Variants — Splitting by Use Case (decision, 2026-06-30)

Today there is a single AppCode for all SmartBoxes. As new use cases emerge (e.g. an acoustic/microphone-based shot detector), the question of whether to keep one AppCode or split by use case was discussed and decided:

**Split only when the runtime model is genuinely incompatible, not by device-naming convenience.** The current main loop is synchronous polling at ~50ms ticks — fine for GPIO output (Werfer, LEDs), GPIO input (sensors, triggers), and anything event-driven at human timescales. It is **not** viable for high-bandwidth signal processing (e.g. audio sampling at kHz rates needs sub-millisecond servicing, which a 50ms poll loop cannot deliver).

Decided split:
- **`ESP32S3_IO`** — one AppCode for all simple GPIO use cases (Werfer, LED, sensors, triggers). Within it, use conditional/lazy module imports based on what `device_config.json` actually configures, so memory scales with what's deployed, not with everything the AppCode is capable of.
- **`ESP32S3_Audio`** (future, not yet started) — separate AppCode for microphone/acoustic capability, because it needs a fundamentally different runtime (interrupts/DMA, not a polling loop) and cannot coexist with the GPIO loop in the same process.

The `boxType` field already in the discovery payload is the mechanism that distinguishes these — `firmware_configs` is naturally keyed on `(appVersion, boxType)`, so `ESP32S3_IO` and `ESP32S3_Audio` are simply different box types with different capability manifests. No backend schema change is needed to support this split when it happens.

## Known Issues & Open Work

### Delay handling moved to the backend (firmware no longer applies `delay_ms`)
The firmware previously blocked the polling loop on `time.sleep_ms(delay_ms)` in `GpioManager.set()`. Delay is now entirely a backend concern: the firmware ignores the field and never delays. If a delayed-release / staggered-double requirement is confirmed, it is implemented backend-side (scheduling the command publish at send-time using `DeviceType.delaySignalDurationMs`, which is retained in the backend domain). Backend MQTT payloads should drop `delay_ms` (config) / `delaySignalDurationMs` (command) — tracked as a backend task.

### HIGH: Topic structure routes all devices to one topic
All commands arrive on `smartboxes/{mac}/command`. The intended future structure is `smartboxes/{mac}/device/{deviceId}/command` — one topic per device. Coordinate with the backend before changing.

---

## Code Review Checklist (Firmware-Specific)

Before merging any changes:

- [ ] Firmware runs on a physical XIAO ESP32-S3 without crashes
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
| **App Code** | the SmartBox `.py` logic (`main.py`, `mqttutils.py`, `ota.py`, `boards/`) plus the release metadata `systemconfig/firmware_config.json` | file-level: download → verify → stage → atomic swap + backup |
| **Firmware** | the MicroPython kernel image | native `esp32.Partition` A/B OTA (ESP-IDF bootloader auto-rollback) |

App Code OTA is the everyday path; Firmware OTA is rare (kernel bumps). Both share the MQTT control channel and the HTTP download transport. Firmware OTA relies on `esp32.Partition` A/B slots — one reason the parked Pico 2W target can't do kernel OTA (RP2350 needs physical BOOTSEL).

### `ota.py` module

All OTA logic lives here (the one module allowed to `import os`, for directory operations). Key functions:

- `handle_command(payload, publish_status, wdt, reset, live_root)` — entry from the MQTT router; orchestrates the APP path (download → verify → apply → `begin_probation` → reset) or the FIRMWARE path. Rejects a second command while one is in progress (`is_busy`).
- `download_app(base_url, manifest_sha256, wdt)` — streams `manifest.json` to `OTA_STAGING_DIR`, verifies it against the **MQTT-supplied hash** (the trust anchor, since per-file hashes live inside the HTTP-fetched manifest), then streams + per-file-SHA-256-verifies each file. Rejects path traversal (`..`) and protected `userconfig/` paths (device-owned state a release must never overwrite). Raises `OtaError` on any failure — **live code is never touched until everything is downloaded and verified.**
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

### OTA download path — CA-pinning investigation (Task C, 2026-07-09)

The MQTT TLS + client auth initiative (Task A/B/C) hardened the **MQTT control channel**
(port 8883, `systemconfig/ca.crt` pinned). The **OTA HTTP download path**
(`ota.py`'s `_default_http_stream` → `urequests.get(url)`, and `_do_firmware_update`'s
image download) is a **genuinely separate channel and was investigated, not changed**:

- The `url` a device downloads from comes from the backend's `OtaPublishService`
  (`smart-ground-backend`), whose `ota.base-url` property defaults to
  `http://localhost:8080` (`application.properties`/`application-h2.properties`) — **plain
  HTTP**, and it points at the backend's own embedded Spring Boot HTTP server (the same
  port serving the REST API), **not** at Mosquitto. No `server.ssl.*` properties exist
  anywhere in the backend config, so today this channel has no TLS at all — the Dev-CA
  pinned for the MQTT broker is unrelated to it and would not apply even if this path were
  TLS'd, since the backend's REST server would need its own (possibly different) server
  certificate/CA pairing.
- Because the URL is plain HTTP by default, `urequests.get()` needs no CA at all today —
  there's nothing to pin. If `ota.base-url` is ever changed to `https://...`, `ota.py`'s
  integrity model (MQTT-supplied `sha256` verifying every downloaded file, `OtaError` on
  mismatch, live code never touched until fully verified) already protects against a
  **tampered artifact**, but does **not** protect against **who you talk to** without
  cert verification (a MITM could still serve a *validly-hashed* response for a different
  request, or simply observe/deny traffic) — the same class of gap TLS closes on the MQTT
  side.
- **Not a trivial one-liner**, so left as a follow-up rather than implemented here:
  enabling TLS on this path would mean (1) deciding whether to TLS the backend's whole
  REST API (a much bigger, unrelated change) or stand up a separate download endpoint,
  (2) `urequests` (MicroPython's bundled minimal HTTP client) has limited/unverified
  support for passing a custom CA/cadata the way `ussl.wrap_socket()` does directly in
  `mqttutils.py` — this needs its own investigation before implementation, and (3) the
  trust anchor might not even be the same Dev-CA as Mosquitto's (different server, could
  be a different cert). Tracked as an open follow-up item, not implemented in Task C.

### Known OTA limitations

- **Health = "boots and runs", not "broker reachable".** `confirm_boot_healthy()` runs right after WiFi connects (before MQTT), so a temporarily unreachable broker does **not** trigger a false rollback of a healthy new version. The flip side: probation ends (backup deleted) once the box reaches the main path, so a new version that boots+runs but is functionally broken in a way that survives WiFi connect won't auto-rollback — always test a build on hardware before pushing it to a fleet.
- **Unreachable HTTP server during download → watchdog reset.** `urequests.get()` blocks on DNS/TCP connect with no watchdog feed. If the OTA URL host is unreachable, the connect timeout can exceed `WDT_TIMEOUT_MS` (8 s) and the WDT resets the box mid-OTA. This is safe recovery (same as an unreachable broker): `recover_interrupted_apply()` restores any half-applied state on the next boot; no `FAILED` status is published in this case.
- **Newly-added files are not crash-safe during apply.** `apply_app` only backs up files that already exist. A file the update *adds* (no live counterpart) has no backup, so a power loss while copying it leaves it partial. The previous code is fully restored, so this is safe **unless** the update adds a brand-new module that the restored old `main.py` imports — avoid OTA updates that both add a new imported module and depend on it in the same release, or split into two releases.

### Manual (cable) re-flash — still the recovery path

If a box is bricked (e.g. before OTA is deployed, or a kernel that won't boot): on the ESP32-S3, re-flash MicroPython over USB with `esptool`, then re-upload project files via `mpremote`/Thonny.

---

## ESP-NOW Crypto (`espnow_crypto.py`, added 2026-07-09 — Baustein A of the ESP-NOW migration)

`ucryptolib` (MicroPython's built-in crypto module) supports `MODE_ECB`, `MODE_CBC`, and
`MODE_CTR` — **no native GCM mode**. `espnow_crypto.py` builds AES-256-GCM itself: AES
encryption goes through `ucryptolib.aes(key, ucryptolib.MODE_ECB)` (hardware-accelerated,
one 16-byte block at a time), authentication (GHASH, GF(2^128) multiplication) is pure
Python — portable, and cheap enough at ESP-NOW's 250-byte frame ceiling (≤16 blocks) that
performance was not a design concern. `hmac_sha256`/`hkdf_extract`/`hkdf_expand` implement
RFC 5869 HKDF by hand (`hmac` is not in MicroPython's stdlib).

**Verified vs. best-effort** (same category as the MQTT TLS `cadata` note above): the pure
Python GHASH/counter-mode assembly and the HMAC/HKDF construction are verified — both are
tested against `docs/espnow/crypto-test-vectors.json`, cross-checked against Node.js's
native (OpenSSL-backed) `crypto` module and RFC 5869's published test vector. **Not yet
verified on real hardware:** that this pinned MicroPython build's `ucryptolib.MODE_ECB`
constant is exactly `1` and that `ucryptolib.aes(key, 1).encrypt(data)` behaves as
documented for 32-byte (AES-256) keys — host tests use a from-scratch pure-Python AES-256
stub (`tests/_stubs.py`) instead of the real module, since `ucryptolib` doesn't exist
under CPython. Tracked for Phase 1/2 hardware verification, not implemented here.

Do not call `ucryptolib.aes(key, MODE_CTR, iv=...)` directly for the keystream — GCM's
counter increments only the last 32 bits of the 16-byte counter block (`_inc32`), which is
not guaranteed to match how a generic `MODE_CTR` implementation increments its counter.
`espnow_crypto.py` sidesteps the ambiguity entirely by driving `MODE_ECB` one block at a
time under its own `_inc32`, matching the GCM spec exactly regardless of what `MODE_CTR`
does internally.
