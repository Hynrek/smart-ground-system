# smart-box — MicroPython Firmware Development Guide

## Project Overview

The Smart Box firmware runs on the **Seeed XIAO ESP32-S3** (the current and only supported target) and manages physical devices (GPIO outputs, LEDs, sensors). It announces itself and its liveness to `smart-ground-node`'s **box-api** over HTTPS, handles the initial WiFi access-point setup flow, and can update itself over the LAN (OTA, also fetched via box-api). The board abstraction in `boards/` remains — `boards/pico2w.py` is parked (Raspberry Pi Pico 2W is no longer an active target) and re-adding a board is one new file, no core changes.

> **MQTT removed (sub-project #7, `erledigt`).** `mqttutils.py` — the module that used to own the MQTT connection, message routing, security checks, and config persistence — is **deleted**. Discovery/provisioning and heartbeat now go through `box_api_client.py` + `box_provisioning.py` (HTTPS, CA-pinned) against `smart-ground-node`'s box-api; OTA downloads route through `box_api_client.get_bytes`; non-MQTT device state (allowlist, admin-block, firmware/device-config persistence) lives in `device_state.py`. There is currently **no command/config-push channel at all** — the Hub's command dispatch and config push both return `501` pending the `node-channel` sub-project (#4), and this firmware has no code path that would receive either even if the Hub could send them. See "SmartBox Integration" in `../smart-ground-hub/CLAUDE.md` and the box-api section in `../smart-ground-node/CLAUDE.md`.

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
- Changes to box-api request/response shapes (must stay in sync with `smart-ground-node`'s controllers)
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
- **box-api transport**: `urequests` over HTTPS, wrapped by `box_api_client.py` (CA-pinned via `systemconfig/node_ca.crt`) — see **box-api Client** below. `lib/umqtt/` and MQTT are gone.
- **No asyncio**: Synchronous polling loop only

## Language & Runtime

- **MicroPython 1.23+** only — no CPython-specific syntax or libraries
- Allowed stdlib modules: `network`, `time`, `machine`, `json`, `sys`, `gc`, `os` (only in `ota.py`), `hashlib` (`ota.py`, `espnow_crypto.py`), `struct` (`frame_envelope.py`)
- box-api transport: `urequests`, used directly by `box_api_client.py` (`post_json`/`get_bytes`, HTTPS with a pinned CA)
- OTA HTTP download uses `box_api_client.get_bytes` (itself `urequests`-backed) via `ota.py`'s `_default_http_stream`
- No `asyncio` / `uasyncio` — all logic is synchronous and polling-based
- No `import os` for **file I/O** — use `open()` directly. Exception: `ota.py` imports `os` for **directory operations** (`mkdir`/`listdir`/`rename`/`remove`/`stat`) that `open()` cannot do. Neither is plain file read/write.
- Do not import modules you don't use — every imported module stays in RAM for the lifetime of the process

---

## Project Structure

```
smart-box/
├── main.py                             # Entry point; auto-runs on boot (incl. OTA boot supervisor)
├── box_api_client.py                   # CA-pinned HTTPS client for box-api (post_json/get_bytes) — replaces mqttutils' MQTT connection
├── box_provisioning.py                  # Discovery/provisioning + heartbeat over box-api — replaces publish_discovery/publish_heartbeat
├── device_state.py                      # Firmware/device-config persistence + security allowlist/admin-block — non-MQTT parts salvaged from mqttutils.py
├── ota.py                              # OTA: download/verify/stage/apply App Code + esp32.Partition firmware update (downloads via box_api_client)
├── espnow_crypto.py                    # AES-256-GCM (ucryptolib ECB + hand-rolled GHASH/CTR) + HKDF-SHA256 (ESP-NOW pairing/session keys)
├── frame_envelope.py                   # Klartext-Routing-Header (pack/unpack) + Duplikat-Erkennung (SeenCache) fuer ESP-NOW-Frames
├── pairing_codec.py                    # Baut/parst DISCOVER/OFFER/CONFIRM unter K_Box + leitet K_S ab (ESP-NOW-Pairing, ADR-003)
├── hardware.py                         # GpioManager class + onboard LED
├── networkutils.py                     # WiFi connect/reconnect helpers
├── accesspoint.py                      # Captive portal for first-time WiFi setup
├── accesspoint.html                    # HTML served by the captive portal
├── boards/
│   ├── xiao_esp32s3.py                 # Board-Konstanten und Init für XIAO ESP32-S3 (aktuelles Ziel)
│   ├── pico2w.py                       # Pico 2W — geparkt, kein aktives Ziel mehr
│   └── test_board.py                   # Neutraler Stub für Host-Tests
├── lib/
│   └── umqtt/                          # Vendored umqtt.simple — VESTIGIAL, nothing imports it any more (mqttutils.py deleted); not yet removed from the tree
├── systemconfig/
│   ├── accesspoint_config.json         # AP SSID/password (read-only at runtime)
│   ├── firmware_config.json            # App version + capability manifest + config schema version (read-only at runtime)
│   ├── ca.crt                          # Former Mosquitto Dev-CA root — vestigial, no longer loaded by anything (MQTT removed)
│   └── node_ca.crt                     # Pinned Node CA root (PEM), used by box_api_client.py for HTTPS to box-api (not yet present in this checkout — see box-api Client below)
├── userconfig/
│   ├── client_config.json              # WiFi credentials + box_api_base_url (+ legacy mqtt_username/mqtt_password fields — see below)
│   ├── device_config.json              # Active device/GPIO config + config_schema_version (written after each config push — dormant, no config-push transport exists, #7)
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
Selects the board module at the very top of the file via `sys.platform` → `boards/<name>.py` map, registers it as `sys.modules["board"]`, and calls `board.board_init()`. This happens before all other imports because `hardware.py` needs `board.LED_PIN` at module load time. Uses `board.WDT_TIMEOUT_MS` for the watchdog timeout. Loads `userconfig/client_config.json`; falls back to `start_ap()` if absent or incomplete — the required fields are now `client_network_ssid` and `box_api_base_url` (the former `broker_ip` check is gone, since there is no broker). Pre-loads GPIO from the last-saved `userconfig/device_config.json` (if its `config_schema_version` matches the current firmware's) before doing anything over the network, so the box is immediately usable even if a fresh config push never arrives. Connects WiFi, runs the OTA boot supervisor (`recover_interrupted_apply` → `probation_check` → `confirm_boot_healthy`/`take_pending_report`), then calls `box_provisioning.discover_and_provision()` — a single HTTPS call that covers what used to be two separate MQTT steps (`connect_mqtt` + `publish_discovery`). Runs the main polling loop: feeds the watchdog, checks/reconnects WiFi, calls `update_device_pulses()`, and sends a **heartbeat** (`box_provisioning.send_heartbeat`) every `PUBLISH_INTERVAL_S` (20 s) via box-api instead of MQTT. There is **no message-receive loop of any kind** — no `check_msg()` equivalent — because there is no inbound channel (command dispatch/config push are `501` on the Hub side; nothing on the box would handle them if they arrived).

> **Known gap (not this task's scope):** `accesspoint.py`'s captive-portal form does not yet write `box_api_base_url` into `client_config.json` — only `box_provisioning.py`/`main.py` consume that field so far. The captive-portal flow needs a follow-up before it can provision a box end-to-end in production.

### `box_api_client.py` (replaces MQTT connection handling)
CA-pinned HTTPS client for talking to `smart-ground-node`'s box-api — the direct analogue of `mqttutils.connect_mqtt`'s TLS pinning, just over HTTPS instead of MQTT-over-TLS:
- `_load_ca_cert()` — reads `systemconfig/node_ca.crt` (PEM bytes, cached after first load; returns `None` if missing, same graceful-degradation shape as the old `load_ca_cert()`)
- `post_json(url, payload_dict)` — POSTs JSON with `ssl_params={"cadata": ca}` when a CA is loaded; parses and returns the JSON response
- `get_bytes(url)` — GETs and returns raw response bytes (used by `ota.py` for manifest/file/firmware downloads)

Unlike the old MQTT client, box-api calls are **stateless per-call** — there is no persistent connection to keep alive, reconnect, or explicitly disconnect.

### `box_provisioning.py` (replaces `publish_discovery`/`publish_heartbeat`/`_persist_mqtt_credentials`)
- `discover_and_provision(mac_address, box_api_base_url, app_version, firmware_version, box_type, capabilities_json)` — `POST {box_api_base_url}/box-api/v1/discovery`; on success, persists the returned `kBoxBase64` into `userconfig/client_config.json` under `k_box_base64` (read-modify-write merge, same reasoning as the old `_persist_mqtt_credentials`: existing WiFi fields must survive)
- `send_heartbeat(mac_address, box_api_base_url, status)` — `POST {box_api_base_url}/box-api/v1/boxes/{mac}/status` with `{"status": status}`; does **not** catch its own exceptions — callers (`main.py`'s loop) are expected to catch and log, matching the old `publish_heartbeat`'s fail-soft behavior at the call site rather than inside the function

### `device_state.py` (non-MQTT parts salvaged from the deleted `mqttutils.py`)
Firmware/device-config persistence and the security allowlist/admin-block model — none of this was ever MQTT-specific, it just used to live in `mqttutils.py` alongside the MQTT code:
- `load_firmware_config()` — reads `systemconfig/firmware_config.json` (cached in module global after first load)
- `load_device_config()` / `save_device_config(devices)` — read/write `userconfig/device_config.json`, stamping `config_schema_version` from `firmware_config.json` on save
- `update_device_pulses()` — thin wrapper around `gpio_manager.tick()`
- `_update_known_devices(devices)` — rebuilds `_known_devices` set; prunes stale `_blocked_devices` entries
- `_is_device_blocked`, `_admin_block_device`, `_admin_unblock_device` — the allowlist/admin-block model described in **Security Model** below

> **These security functions are currently dormant.** `main.py` only imports `load_device_config`, `load_firmware_config`, `update_device_pulses`, `_update_known_devices`, and `set_watchdog` — not `_is_device_blocked`/`_admin_block_device`/`_admin_unblock_device`, because there is no command-receive path left to call them from (the old `message_callback` router is gone with `mqttutils.py`, and nothing has replaced it). The allowlist is still populated on boot from the last saved device config, but nothing currently checks it before actuating a device, since there is no way to actuate a device remotely at all right now. This will need to be re-wired once `node-channel` (#4) delivers a real command-receive path.

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
- `get_mac_address()` — returns 12-char lowercase hex MAC string used to identify the box in every box-api call
- `connect_wifi(ssid, password, timeout=20, wdt=None)` — `ticks_ms`-deadline poll loop honoring `timeout` seconds; toggles the onboard LED every `CONNECTING_TOGGLE_MS` (250 ms) while waiting, feeds the optional watchdog, and calls `led_off()` on success; returns `True` on success
- `reconnect_wifi(ssid, pw, wdt=None)` — retries up to `RECONNECT_ATTEMPTS` (12) times with `RECONNECT_DELAY_S` (10 s) between attempts, feeding the optional watchdog during each wait (chunked via `feed_sleep_ms`)
- `get_sta_ip()` — returns current IP or `None`

### `accesspoint.py`
Captive portal for first-time WiFi setup. Runs in AP mode (SSID/password from `systemconfig/accesspoint_config.json`), serves `accesspoint.html`, writes validated credentials to `userconfig/client_config.json`. Query-string parsing/filtering and Pflichtfeld-validation live in the standalone, host-testable `extract_setup_params(query_string)` / `validate_setup_params(params)` functions (see `tests/test_accesspoint.py`). Only accepts keys `client_network_ssid`, `client_network_pw`, `box_api_base_url`. Requires `client_network_ssid` and `box_api_base_url` — matching `main.py`'s boot-completeness gate (see below); rejects with 400 otherwise. Reboots after saving. Keep entirely separate from box-api logic — it runs as an alternative boot path. Calls `led_on()` (solid LED) before entering the socket loop to signal AP mode.

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

The onboard LED signals the boot/connection phase so the box can be diagnosed without a serial console. All signals occur **before** box-api discovery/heartbeat calls begin, so they never conflict with box-controllable LED devices.

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

1. **Call `gc.collect()` after every network round-trip** — done after every heartbeat send in `main.py`'s loop, and after each OTA download step in `ota.py` (formerly done in `message_callback`'s `finally` block, which no longer exists)

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
   mpremote connect <port> cp box_api_client.py :box_api_client.py
   mpremote connect <port> cp box_provisioning.py :box_provisioning.py
   mpremote connect <port> cp device_state.py :device_state.py
   mpremote connect <port> cp ota.py :ota.py
   mpremote connect <port> cp hardware.py :hardware.py
   mpremote connect <port> cp networkutils.py :networkutils.py
   mpremote connect <port> cp accesspoint.py :accesspoint.py
   mpremote connect <port> cp accesspoint.html :accesspoint.html
   mpremote connect <port> cp -r boards :boards
   mpremote connect <port> cp systemconfig/accesspoint_config.json :systemconfig/accesspoint_config.json
   mpremote connect <port> cp systemconfig/firmware_config.json :systemconfig/firmware_config.json
   mpremote connect <port> cp systemconfig/node_ca.crt :systemconfig/node_ca.crt
   ```
   **Replace `systemconfig/node_ca.crt` with the real CA root for the target `smart-ground-node` deployment
   before flashing** — this file is not committed as a placeholder the way the old `systemconfig/ca.crt`
   was (it does not exist in this checkout yet); a box flashed without a matching CA cannot verify (and
   therefore cannot reach) any real box-api. `lib/umqtt/` is no longer needed and does not need to be
   uploaded to new boxes.

### First Boot

On first boot, `main.py` checks for `userconfig/client_config.json`. If absent or incomplete, it starts the captive portal:

1. Phone/laptop connects to SSID from `systemconfig/accesspoint_config.json` (`SmartRange-Client-Setup`)
2. Opens browser → `192.168.4.1`
3. Fills in WiFi SSID, password, and the box-api base URL → writes to `userconfig/client_config.json`
4. The box reboots and connects normally

---

## box-api Endpoints & Payload Schemas

The SmartBox MAC address (12-char lowercase hex) is used to identify the box in every box-api call. There are no MQTT topics any more — these are plain HTTPS requests against `smart-ground-node`, described in full (server-side) in `../smart-ground-node/CLAUDE.md`'s "box-api" section.

| Direction | Endpoint | Purpose |
|---|---|---|
| SmartBox → Node | `POST /box-api/v1/discovery` | Discovery + provisioning in one call (sent once per boot) |
| SmartBox → Node | `POST /box-api/v1/boxes/{mac}/status` | Heartbeat / liveness — sent every 20 s |
| SmartBox → Node | `GET /box-api/v1/ota/app/{version}/manifest.json`, `/files/{path}` | App-Code OTA artifact download (proxied from the Hub) |
| SmartBox → Node | `GET /box-api/v1/ota/firmware/{version}` | Firmware image OTA download (proxied from the Hub) |

**Gone, with no replacement:** the former `smartboxes/{mac}/config` (config push), `smartboxes/{mac}/command` (device command), `smartboxes/{mac}/device/{deviceId}/executed` (command ACK), and `smartboxes/{mac}/ota` (OTA trigger) all had no MQTT successor — they required the Backend to push something *to* the box, and there is currently no channel for that at all (Hub-side stubs return `501`; see `../smart-ground-hub/CLAUDE.md`'s "SmartBox Integration" section). A future `node-channel` (#4) is expected to reintroduce these as some new mechanism, not necessarily shaped the same way.

### Discovery/provisioning request (`box_provisioning.discover_and_provision` → `POST /box-api/v1/discovery`)
`app_version` and `capabilities_json` (App Code) come from `systemconfig/firmware_config.json`; `firmware_version` defaults to `"unknown"` (see the `main.py` note above — reading the real MicroPython kernel string would need a new `os` import not currently made); `box_type` comes from the board module.
```json
{ "macAddress": "aabbccddeeff", "appVersion": "1.0", "firmwareVersion": "unknown",
  "boxType": "xiao-esp32s3", "capabilitiesJson": "{\"GPIO\": {...}, \"LED\": {...}}" }
```
Response: `{ "kBoxBase64": "...", "wasNewlyProvisioned": true }` — `kBoxBase64` is persisted into `userconfig/client_config.json`'s `k_box_base64` field.

### Status/heartbeat request (`box_provisioning.send_heartbeat` → `POST /box-api/v1/boxes/{mac}/status`)
```json
{ "status": "idle" }
```
Sent every `PUBLISH_INTERVAL_S` (20 s) from the main loop; used by the Node for liveness (`lastSeenAt`/`lastStatus` on its `BoxRecord`) — this is a Node-local registry, separate from the Hub's `SmartBox.lastSeen`/status fields, which nothing currently updates (no channel from Node → Hub for this yet).

---

## box-api Client (CA-pinned HTTPS — replaces "MQTT TLS + Credentials")

> **MQTT TLS + dynsec credentials removed (#7).** This section used to describe `mqttutils.py`'s TLS setup (`ssl_params={"cadata": <systemconfig/ca.crt bytes>}` on a `MQTTClient`) and its credential-provisioning flow (dynsec `mqtt_username`/`mqtt_password`, persisted via the now-deleted `_persist_mqtt_credentials()`), plus a documented gap: no `bootstrap` dynsec role existed, so a factory-flashed box had no way to authenticate its very first MQTT connection. **All of that is gone, and the gap is closed, not worked around:**
>
> - **CA pinning carries forward unchanged in spirit**, just over HTTPS instead of MQTT-over-TLS: `box_api_client.py`'s `_load_ca_cert()` reads `systemconfig/node_ca.crt` and passes it as `ssl_params={"cadata": ...}` to `urequests`, never falling back to an unverified connection — same principle as the old `connect_mqtt()`, different transport. See the `box_api_client.py` entry in **Module Responsibilities** above.
> - **The bootstrap-credential gap does not exist for box-api**, by construction: `POST /box-api/v1/discovery` is a box's very first call and requires no pre-existing credential of any kind — the Node hands back a `kBoxBase64` in the response to that same call (see **box-api Endpoints & Payload Schemas** above). There is no equivalent of "a brand-new box can't authenticate its first message" to solve, because discovery/provisioning *is* the first message and needs no prior identity.
> - **Not yet hardware-verified:** whether this project's pinned MicroPython build's `urequests`/`ussl` accepts `cadata` as raw PEM bytes the same way the old MQTT path did is unconfirmed on real hardware — same category of open item the old MQTT section flagged for `ussl.wrap_socket`, now applying to `urequests` instead.

---

## Security Model

> **Currently dormant (#7).** These checks used to run inside `mqttutils.message_callback`, the MQTT command router — deleted along with everything else MQTT. The functions still exist in `device_state.py` (`_is_device_blocked`, `_admin_block_device`, `_admin_unblock_device`), and the allowlist (`_known_devices`) is still populated from the saved device config on every boot, but **nothing currently calls them**: there is no command-receive path of any kind on the box right now (see `main.py`'s description in Module Responsibilities). The model below describes what the checks do and is expected to apply again, unchanged in spirit, once `node-channel` (#4) delivers a real command-receive path — it is not currently enforced because there is nothing to enforce it against.

The intended checks, when a command-receive path exists:

1. **Allowlist** — `deviceId` must be in `_known_devices` (populated from the last saved device config). Unknown IDs are rejected and dropped (no ACK).
2. **Admin block** — `BLOCK` sets a permanent (`ADMIN_BLOCK_TOKEN`) block; only `UNBLOCK` removes it. No auto-expiry.
3. `BLOCK`/`UNBLOCK` bypass the ON/OFF security path and do not require the device to be in `_known_devices` for UNBLOCK.

Operational (not security): an ON command for a device whose pulse is still running is ignored (**busy-reject**, in `GpioManager.set`); OFF always cancels. This replaced the former per-command rate limiter.

There is **no rate limiter and no auto-block**. They were removed deliberately: the rate limiter compared against second-resolution `time.time()` (so its 0.1 s window never worked) and fed an auto-block that, in practice, locked legitimate devices on bursts/misconfig while giving unknown IDs (already rejected by the allowlist) an unbounded-growth memory vector. Do not reintroduce them; the allowlist + admin block + busy-reject are the model. Do not otherwise bypass or weaken these checks whenever they are wired back up.

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
    # Kein MQTT-Client mehr zu trennen (HTTPS/box-api ist zustandslos je Call) —
    # der frühere "if client: client.disconnect()"-Schritt entfällt ersatzlos.
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
- `finally` → deactivate both WLAN interfaces (no MQTT client to disconnect any more — box-api calls are stateless per-call, so there is nothing persistent to tear down)
- Always create a fresh `network.WLAN()` instance in `finally` — do not rely on outer variables that may not be defined if the error happened early

> **Watchdog caveat:** once the main loop has started, `machine.WDT` is active and **cannot be stopped** (true on ESP32 and RP2 alike). So a `KeyboardInterrupt` in normal operation runs the cleanup but the box still resets ~`WDT_TIMEOUT_MS` (8 s) later — the "no reset" guarantee only fully holds on the AP/first-connect path, where the WDT isn't running yet. This limits REPL debugging after Ctrl-C: re-flash the kernel if you need an un-watched session. Relatedly, a very slow box-api HTTPS call (Node unreachable >8 s) will be cut short by a WDT reset; this is intended recovery — equivalent to the reset the reconnect path already performs on failure.

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
> **`LED` is intentionally narrower than `GPIO`:** no `OFF` command, and it always targets `board.LED_PIN` (no pin config). An LED is technically just a GPIO output, but the onboard LED is being kept as a deliberate debug capability — easiest way to visually confirm connectivity during development/testing — not a general-purpose output. `ON` triggers a single pulse of `signal_duration_ms` (default 150 ms), reusing the same auto-off pulse mechanism `GpioManager` already uses for GPIO. Do not expose `LED` as an assignable production device type in the backend/UI; it should be labeled as debug-only.
>
> **BLOCK/UNBLOCK are not part of any capability's `commands` list.** They are security meta-commands intended to be handled independently of which capabilities a device has, so a new capability can never accidentally ship without being blockable. Previously handled directly in `mqttutils.message_callback`; the functions still exist (`device_state._admin_block_device`/`_admin_unblock_device`) but are currently dormant — see **Security Model** above.

### `systemconfig/ca.crt` (former Mosquitto Dev-CA root — vestigial, superseded by `node_ca.crt`)

PEM-encoded root certificate of the **former** Mosquitto broker's Dev-CA. **Nothing loads this file any more** — `mqttutils.load_ca_cert()`, its only reader, is deleted along with the rest of MQTT. The file itself has not been removed from the tree/repo as part of this docs task; treat it as dead weight, safe to delete whenever someone gets to the vendored-`lib/umqtt` cleanup too.

### `systemconfig/node_ca.crt` (pinned Node CA root — replaces `ca.crt`)

PEM-encoded root certificate for `smart-ground-node`'s box-api HTTPS endpoint. Loaded by `box_api_client._load_ca_cert()` and passed as `ssl_params={"cadata": <raw file bytes>}` to `urequests` in `post_json`/`get_bytes`. **Rotation runs entirely through App-Code OTA**, same convention as the old `ca.crt`: this file is never written at runtime, and swapping it for a new/rotated CA is just another release. This file **does not exist yet in this checkout** (see the Running & Flashing note above) — provisioning a real box requires generating/obtaining it from the Node's dev keystore (`smart-ground-node/src/main/resources/node-dev-keystore.p12`) and placing it here before flashing; there is no throwaway placeholder checked in for it the way there was for `ca.crt`. `tools/pack_ota.py`'s `DEFAULT_FILES` has not yet been updated to include it — tracked as a gap, not addressed by this docs task.

### `userconfig/client_config.json` (written by captive portal / box-api provisioning)

```json
{
  "client_network_ssid": "MyNetwork",
  "client_network_pw": "mypassword",
  "box_api_base_url": "https://192.168.4.1:8443",
  "k_box_base64": "base64-encoded-32-byte-key",
  "mqtt_username": "aabbccddeeff",
  "mqtt_password": "s3cr3t"
}
```
`box_api_base_url` replaces the former `broker_ip`/`broker_port` fields (decision,
2026-07-10, sub-project #7 — MQTT-Ausbau/box-api): `main.py`'s boot-completeness gate
requires `client_network_ssid` and `box_api_base_url`; it no longer checks any
broker field. `k_box_base64` is written by `box_provisioning.discover_and_provision()`
after a successful `POST /box-api/v1/discovery` — the ESP-NOW pairing key for this box,
persisted so it survives reboots (see `smart-ground-node/CLAUDE.md`'s "K_Box generation
semantics"). `mqtt_username`/`mqtt_password` are legacy holdovers from the deleted MQTT-era
credential flow (see **box-api Client** above) — nothing in the current boot path reads or
writes them any more (the function that used to, `mqttutils._persist_mqtt_credentials()`,
no longer exists); they are documented here only because an on-disk file from before this
plan may still carry them. Written either by `accesspoint.py`'s captive-portal form
(`client_network_ssid`, `client_network_pw`, `box_api_base_url` — full overwrite, though see
the known gap in `main.py`'s doc entry above: the portal doesn't write `box_api_base_url`
yet) or by `box_provisioning.discover_and_provision()` (`k_box_base64` only, merge).
Never hardcode these values. This file — like everything under `userconfig/` — is never
part of an OTA release; see the protection rule under `systemconfig/firmware_config.json`
above.

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
Overwritten on every config push (mechanism kept in `device_state.save_device_config`, but currently unreachable — there is no config-push transport, #7); `config_schema_version` is copied from `firmware_config.json` at save time and checked on boot (mismatch → saved config discarded). Firmware metadata does **not** go here — it lives in `systemconfig/firmware_config.json`.

---

## Conventions

### Code Style

| Convention | Rule |
|---|---|
| Comments | German inline comments (`# Kommentar`) |
| Identifiers | English (`device_id`, `discover_and_provision`, `gpio_manager`) |
| Config constants | Top of each file under a `# --- KONFIGURATION ---` block |
| f-strings | Simple only — avoid complex expressions inside `{}` (MicroPython support is limited) |
| Error handling | Always `except Exception as e:` + `print(...)` — never silent bare `except:` |
| Module size | Small and single-responsibility |

### What to Avoid

- ❌ Do not use `asyncio` / `uasyncio`
- ❌ Do not hardcode credentials, firmware version, or `box_api_base_url` outside the config files or `# --- KONFIGURATION ---` block
- ❌ Do not suppress exceptions silently
- ❌ Do not put firmware metadata in `userconfig/device_config.json`
- ❌ Do not bypass the security checks in `device_state.py` once a command-receive path exists to call them
- ❌ Do not add CPython-only dependencies

---

## Troubleshooting

### Board doesn't respond in REPL
Press `^C` to trigger `KeyboardInterrupt`. If frozen, hold the BOOT button and replug to enter bootloader mode, then re-flash with `esptool`.

### Memory errors around box-api calls
Ensure `gc.collect()` is called after every heartbeat/discovery round-trip and OTA download step (see Memory Budget above). Check `gc.mem_free()` in REPL.

### WiFi connection fails
Check `userconfig/client_config.json`. If the file is missing or `client_network_ssid`/`box_api_base_url` are empty, the captive portal starts automatically. Debug in REPL:
```python
import network; wlan = network.WLAN(network.STA_IF); wlan.active(True); wlan.connect('ssid', 'pw'); print(wlan.status())
```

### Device doesn't trigger on command
There is currently **no remote command path at all** (#7 — command dispatch is `501` on the Hub, and this firmware has no message-receive loop to act on one even if it arrived; see **Security Model** and the `main.py` entry in Module Responsibilities). This is expected today, not a bug — it will need `node-channel` (#4) before remote triggering works again. To sanity-check the hardware/config independent of any remote channel:
1. Verify the GPIO pin number in `userconfig/device_config.json` (loaded from the last saved config on boot, if `config_schema_version` matches)
2. Manually trigger in REPL: `from machine import Pin; p = Pin(15, Pin.OUT); p.value(1)`

---

## Host Tests

Logic that doesn't need real hardware is unit-tested under CPython. From `smart-box/`:

```bash
python -m unittest discover -s tests -t . -v
```

`tests/_stubs.py` installs fake `machine`/`micropython`/`network` modules into `sys.modules` and patches a **controllable clock** (`ticks_ms`/`ticks_add`/`ticks_diff`/`sleep_ms`, advanced via `_stubs.clock.advance(ms)`) onto the real `time` module, so timing is deterministic with no real sleeps. It also still installs a fake `umqtt.simple` — **vestigial**, nothing under test imports it any more since `mqttutils.py` was deleted; not yet cleaned up as part of this docs task. `tests/__init__.py` puts the repo root on `sys.path` and installs the stubs before any firmware import. Covered: scheduler timing + busy-reject, security routing (allowlist/admin block, currently exercised directly against `device_state.py` rather than via any message-router integration test), config handling, discovery/heartbeat payloads (against `box_provisioning.py`), and the watchdog-fed sleep. Hardware-only paths (`main.py`, real GPIO/WDT/WiFi, real HTTPS) are still verified manually on the XIAO ESP32-S3. `boards/test_board.py` provides a neutral board stub (`BOX_TYPE = "test-board"`, `LED_PIN = 0`). `tests/_stubs.py` registers it as `sys.modules["board"]` before any firmware module is imported, so `import board` in `hardware.py` resolves correctly under CPython.

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
The firmware previously blocked the polling loop on `time.sleep_ms(delay_ms)` in `GpioManager.set()`. Delay is now entirely a backend concern: the firmware ignores the field and never delays. If a delayed-release / staggered-double requirement is confirmed, it would be implemented backend-side (scheduling the command publish at send-time using `DeviceType.delaySignalDurationMs`, which is retained in the backend domain) — but there is currently no command-publish mechanism of any kind for it to hook into (see #7 below). Tracked as a backend task, blocked on `node-channel` (#4).

### RESOLVED (#7): topic structure question is moot — there are no topics any more
The former concern ("all commands arrive on `smartboxes/{mac}/command`; the intended future structure is `smartboxes/{mac}/device/{deviceId}/command`") does not apply to whatever replaces command dispatch — MQTT topics no longer exist in this stack at all. Whatever shape `node-channel` (#4) takes, per-device addressing will need to be reconsidered from scratch rather than by picking a finer-grained topic string.

### No command/config-push channel at all (sub-project #7, tracked for node-channel #4)
`mqttutils.py` is deleted and nothing replaces its command-receive/config-push role. The Hub's `DeviceController.sendDeviceCommand`, `RangePositionService.sendPositionCommand`, and `SmartBoxController.pushSmartBoxConfig` all return `501`. This firmware has no message-receive loop, no allowlist enforcement point, and no config-apply trigger reachable from the network — `device_state.py`'s security functions and `GpioManager.set()` are only reachable from host tests today. This is the single biggest functional gap left by MQTT removal and is explicitly out of scope for this plan; closing it is `node-channel` (#4)'s job.

---

## Code Review Checklist (Firmware-Specific)

Before merging any changes:

- [ ] Firmware runs on a physical XIAO ESP32-S3 without crashes
- [ ] `gc.collect()` called after every box-api round-trip and OTA download step
- [ ] No unbounded lists or string concatenation in loops
- [ ] `micropython.const()` used for integer constants
- [ ] Safe-exit pattern in `main.py` with `try/except/finally`
- [ ] Configuration constants in `# --- KONFIGURATION ---` block at top of file
- [ ] German inline comments, English identifiers and docstrings
- [ ] No hardcoded credentials, firmware version, or box-api URL
- [ ] Free heap > 30 KB under normal operation
- [ ] Security checks in `device_state.py` not bypassed once a command-receive path exists to enforce them against

---

## OTA Updates (cable-free, over the LAN)

The firmware can update itself over the network — no cable needed. Two distinct things can be updated, with deliberately different mechanisms:

| Term | What it is | Mechanism |
|---|---|---|
| **App Code** | the SmartBox `.py` logic (`main.py`, `box_api_client.py`, `box_provisioning.py`, `device_state.py`, `ota.py`, `boards/`) plus the release metadata `systemconfig/firmware_config.json` | file-level: download → verify → stage → atomic swap + backup |
| **Firmware** | the MicroPython kernel image | native `esp32.Partition` A/B OTA (ESP-IDF bootloader auto-rollback) |

App Code OTA is the everyday path; Firmware OTA is rare (kernel bumps). Firmware OTA relies on `esp32.Partition` A/B slots — one reason the parked Pico 2W target can't do kernel OTA (RP2350 needs physical BOOTSEL). **Downloads now go through box-api** (`box_api_client.get_bytes`, HTTPS, CA-pinned via `systemconfig/node_ca.crt`) instead of a plain-HTTP URL published over MQTT — see **Downloads via box-api (resolved)** below. **There is no trigger mechanism at all right now** — see the "No command/config-push channel" item in Known Issues & Open Work.

### `ota.py` module

All OTA logic lives here (the one module allowed to `import os`, for directory operations). Key functions:

- `handle_command(payload, publish_status, wdt, reset, live_root)` — orchestrates the APP path (download → verify → apply → `begin_probation` → reset) or the FIRMWARE path. Rejects a second command while one is in progress (`is_busy`). **Currently only exercised by host tests** (`tests/test_ota_handle.py`) — nothing in `main.py` calls it, since there is no trigger channel to call it from (the former MQTT router is gone).
- `download_app(base_url, manifest_sha256, wdt)` — streams `manifest.json` to `OTA_STAGING_DIR` via `box_api_client.get_bytes` (formerly `_default_http_stream` → `urequests.get(url)` directly), verifies it against `manifest_sha256` (the trust anchor — whoever calls `handle_command` is expected to supply this out-of-band; there is currently no live caller, so this is a contract enforced only in tests today), then streams + per-file-SHA-256-verifies each file. Rejects path traversal (`..`) and protected `userconfig/` paths (device-owned state a release must never overwrite). Raises `OtaError` on any failure — **live code is never touched until everything is downloaded and verified.**
- `verify_file(path, expected_hex)` — streaming SHA-256 (manual hex; MicroPython `hashlib` has no `hexdigest()`).
- `apply_app(manifest, live_root)` — writes a `pending` marker (file list), backs up each live file to `OTA_BACKUP_DIR`, copies staging over live, writes an `applied` marker, cleans up.
- `recover_interrupted_apply(live_root)` — boot-time: if `pending` exists without `applied` (power lost mid-apply), restores from backup.
- `probation_check(live_root)` — boot-time: counts probation boots; after `MAX_PROBATION_BOOTS` without a healthy confirm, restores the backup and leaves a one-shot `pending_report`. The new version gets the boots before that to prove itself.
- `confirm_boot_healthy()` — called after WiFi connects and box-api discovery succeeds (formerly "after a successful MQTT connect"); ends probation, deletes the backup, returns `("APPLIED", version)`. For FIRMWARE also calls `Partition.mark_app_valid_cancel_rollback()`.
- `take_pending_report()` — returns and clears the one-shot `ROLLED_BACK` report after a rollback reboot. **Not currently transmitted anywhere** — `main.py` only logs these reports (see the boot flow note below); there is no `smartboxes/{mac}/ota/status`-equivalent channel to publish them on, and box-api's status endpoint only carries a generic `status` string, not OTA phase/progress/detail.
- HTTP is the replaceable seam `http_stream` (real impl is `box_api_client.get_bytes`; host tests inject a fake).

`http_stream`/`esp32.Partition`/real flash writes are verified manually on the ESP32-S3; all other logic is host-tested under CPython via `tests/_stubs.py` (which now also stubs `esp32.Partition` and `os.uname`).

### Boot flow (in `main.py`, before the watchdog starts)

`ota.recover_interrupted_apply()` → `ota.probation_check()` (on rollback: reset into the restored old version) → connect WiFi → `box_provisioning.discover_and_provision()` → `set_watchdog(wdt)` → `ota.confirm_boot_healthy()` / `ota.take_pending_report()` (both just **logged**, not transmitted — see above).

### Downloads via box-api (resolved, Task 10/12 — formerly "OTA download path — CA-pinning investigation")

The former open item here — the OTA HTTP download path had no TLS/CA-pinning story, unlike the MQTT control channel — is **resolved by this plan, not merely investigated further**: OTA downloads (App-Code manifest/files and firmware images) now go through `box_api_client.get_bytes`, which is CA-pinned via `systemconfig/node_ca.crt` (see **box-api Client** above) exactly like every other box-api call. There is no longer a separate plain-HTTP path with its own trust story — `urequests`'s ability to pass a custom CA (`ssl_params={"cadata": ...}`) is exercised the same way for OTA downloads as for discovery/heartbeat, and is demonstrated working in this codebase (not merely asserted) via `box_api_client.py`'s use in `ota.py`. The artifacts themselves are served by `smart-ground-node`'s `BoxOtaController`, which proxies them from the Hub's `OtaDownloadController` — see `smart-ground-node/CLAUDE.md`'s box-api section and `smart-ground-hub/CLAUDE.md`'s SmartBox Integration section.

### Known OTA limitations

- **Health = "boots and runs", not "reachable Node".** `confirm_boot_healthy()` runs right after WiFi connects and discovery succeeds, so a temporarily unreachable Node does **not** trigger a false rollback of a healthy new version. The flip side: probation ends (backup deleted) once the box reaches the main path, so a new version that boots+runs but is functionally broken in a way that survives WiFi connect won't auto-rollback — always test a build on hardware before pushing it to a fleet.
- **Unreachable HTTPS server during download → watchdog reset.** `box_api_client.get_bytes` (via `urequests`) blocks on DNS/TCP connect with no watchdog feed. If the box-api host is unreachable, the connect timeout can exceed `WDT_TIMEOUT_MS` (8 s) and the WDT resets the box mid-OTA. This is safe recovery: `recover_interrupted_apply()` restores any half-applied state on the next boot; no `FAILED` status is published in this case (there is no status channel to publish it on any more — see `take_pending_report()` above).
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

**Verified vs. best-effort** (same category as the box-api Client `cadata` note above): the pure
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
