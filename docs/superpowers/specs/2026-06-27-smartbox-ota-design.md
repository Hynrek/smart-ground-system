# SmartBox OTA Update — Design Spec

**Date:** 2026-06-27
**Status:** Approved design — ready for implementation planning
**Target hardware:** XIAO ESP32-S3 (`boards/xiao_esp32s3.py`), 8 MB flash + 8 MB PSRAM
**Scope:** Cable-free updates for SmartBox devices over the existing LAN

---

## Vocabulary (locked)

| Term | What it is | Form | Cable-free update path |
|---|---|---|---|
| **Firmware** | The MicroPython runtime / kernel | single `.bin` image | Native `esp32.Partition` A/B OTA (bootloader auto-rollback) |
| **App Code** | The SmartBox logic | `main.py`, `mqttutils.py`, `hardware.py`, `boards/`, `*.json` configs | File-level OTA into a staging dir + atomic swap + backup |

- **App Code OTA is the primary, everyday path.**
- **Firmware OTA is the secondary path** for rare kernel / MicroPython bumps.
- Both reuse the same control plane (MQTT) and data plane (HTTP), so Firmware OTA is an incremental addition, not a second project.

## Context & constraints

- **Deployment:** local LAN only. No guaranteed internet. Update bytes come from the Spring Boot backend on the same network.
- **Hardware switch:** the project is moving off the Raspberry Pi Pico 2W (RP2350) to the XIAO ESP32-S3. This removes the Pico's hard limits:
  - The RP2350 needs physical BOOTSEL to replace the kernel; the ESP32-S3 can be re-flashed over the network, so Firmware OTA becomes possible.
  - A/B rollback is a native ESP-IDF bootloader feature on ESP32 (`esp32.Partition`), so robust Firmware updates require little custom code.
  - The Pico's ~120 KB free-heap wall largely dissolves (PSRAM), though streaming-to-flash is kept as hygiene.
- **No internet release server.** The backend is the single source of update artifacts.

---

## Architecture: two planes

- **Control plane = MQTT** (reuses the existing persistent connection). Small messages only: the backend *tells* a box to update; the box *reports* progress.
- **Data plane = HTTP** (reuses the existing Spring Boot server). The box *pulls* the actual bytes over TCP and streams them to flash in a small fixed buffer.

The download URL is delivered **in the MQTT command payload** — nothing about the HTTP server is hardcoded on the box, consistent with the firmware's "never hardcode broker IP / credentials" rule.

---

## On-device design

### New module: `ota.py`

All OTA logic lives in one new single-responsibility module. `mqttutils.py` only routes the new `/ota` topic to `ota.handle_command(payload)` and stays lean.

Responsibilities:
- Parse + validate the OTA command.
- Download (App: manifest + files; Firmware: `.bin`) over HTTP, feeding the watchdog.
- Verify SHA-256.
- Stage, then atomically apply.
- Decide rollback (probation logic helpers).
- Publish status over MQTT.

### Boot supervisor (in `main.py`, runs first)

A small section at the very top of normal startup, before MQTT connects:

1. **Crash-recovery:** if marker `ota_pending` exists but `ota_applied` does not → power was lost mid-apply → restore from `/backup/`.
2. **Probation check:** if the probation flag is set, the freshly-applied version is on trial. Normal startup proceeds.
3. **Rollback trigger:** if the box has watchdog-reset `N` times during probation without confirming healthy → restore `/backup/` over live, clear probation, reboot into the previous version, then report `ROLLED_BACK` once reconnected.

### Health confirmation

Once App Code boots and successfully connects MQTT (first good heartbeat), it calls `ota_confirm()`:
- App path: clears the probation flag + reset counter, deletes `/backup/`, reports `APPLIED`.
- Firmware path: additionally calls `esp32.Partition.mark_app_valid_cancel_rollback()`.

This deliberately mirrors ESP-IDF semantics so the App and Firmware paths behave identically from the operator's point of view.

---

## MQTT topics (new)

| Direction | Topic | Payload |
|---|---|---|
| Backend → box | `smartboxes/{mac}/ota` | `{ "type":"APP"\|"FIRMWARE", "version":"0.7", "url":"http://{backend}/api/ota/app/0.7", "sha256":"...", "size":12345 }` |
| box → Backend | `smartboxes/{mac}/ota/status` | `{ "version":"0.7", "phase":"<PHASE>", "progress":0-100, "detail":"..." }` |

`<PHASE>` ∈ `DOWNLOADING`, `VERIFYING`, `APPLYING`, `APPLIED`, `FAILED`, `ROLLED_BACK`.

---

## App Code OTA flow (primary)

```
1. Backend publishes /ota (type=APP)
2. Box: busy-check (ignore if an OTA is already running) → report DOWNLOADING
3. Box GETs manifest.json → { appVersion, files: [ {path, sha256, size}, ... ] }
4. Box streams each file → /ota_staging/ , feeding the watchdog every chunk
5. Box verifies every file's sha256 → report VERIFYING
   ↳ any mismatch / missing file → abort, delete staging, LIVE CODE UNTOUCHED, report FAILED
6. APPLY (only after full verify):
   - write marker  ota_pending
   - move live files → /backup/ , move staging → live root
   - write marker  ota_applied ; set probation flag + reset-counter
7. machine.reset()
8. Boot supervisor + ota_confirm() decide APPLIED vs ROLLED_BACK (see above)
```

**Key safety property:** live App Code is never modified until the entire new version is downloaded *and* hash-verified. A dropped connection mid-download only leaves a discardable staging directory.

### Manifest schema (`GET /api/ota/app/{version}/manifest.json`)

```json
{
  "appVersion": "0.7",
  "files": [
    { "path": "main.py",        "sha256": "<hex>", "size": 4096 },
    { "path": "mqttutils.py",   "sha256": "<hex>", "size": 8192 },
    { "path": "boards/xiao_esp32s3.py", "sha256": "<hex>", "size": 512 }
  ]
}
```

Individual files are fetched from `GET /api/ota/app/{version}/files/{path}`.

---

## Firmware OTA flow (secondary, native)

Same `/ota` trigger (`type=FIRMWARE`) and the same HTTP transport, but the bytes land via the native partition API:

```
1. part = esp32.Partition(...).get_next_update()
2. stream the .bin over HTTP → part.writeblocks(...) , feeding the watchdog
3. verify sha256 over the written image
4. part.set_boot() ; machine.reset()
5. on healthy boot → Partition.mark_app_valid_cancel_rollback()  (else bootloader auto-rolls back)
```

Requires the MicroPython build to use an OTA partition layout (standard ESP32 builds ship one).

---

## Versioning (schema change — acceptable pre-v1.0)

Split the single version field into two; the discovery payload reports both so the backend can decide what (if anything) to push:

```json
{ "mac":"...", "appVersion":"0.6", "firmwareVersion":"micropython-1.23", "boxType":"xiao-esp32s3", "ip":"..." }
```

- `appVersion` — lives in `systemconfig/firmware_config.json` (renamed from / alongside the current `firmware_version`).
- `firmwareVersion` — derived from `os.uname()` (the MicroPython kernel version).

---

## Backend responsibilities

- Store App Code bundles per version (manifest + files + sha256); serve them over `/api/ota/...`.
- Optionally store Firmware `.bin` images per version analogously.
- Admin action (UI) to push an OTA command to one box or a fleet → publishes the `/ota` MQTT message with the correct URL + checksum.
- Subscribe to `/ota/status`; track each box's reported `appVersion` and current OTA `phase`; surface in the UI.
- Decide the target version by comparing the box's reported `appVersion` against the available bundle.

---

## Error handling & edge cases

| Case | Behavior |
|---|---|
| Watchdog during download | HTTP read loop feeds the WDT every chunk (existing `feed_sleep_ms` pattern); an 8 s timeout will not kill a multi-second download. |
| Memory | Small fixed read buffer; `gc.collect()` between files; never hold a whole file in RAM. |
| Concurrent OTA command | Busy-reject while one OTA is already in progress. |
| Checksum mismatch / partial download | Abort before APPLY; live code untouched; report `FAILED`. |
| Power loss mid-apply | Crash-recovery via `ota_pending` / `ota_applied` markers on next boot. |
| Bad new version (won't connect) | Probation auto-rollback after `N` watchdog resets without confirm. |
| Disk space | 8 MB flash is ample for staging + one backup copy. |

---

## Testing

- **Host tests (CPython, existing `tests/_stubs.py` approach):**
  - manifest parsing + validation
  - SHA-256 verification (good + tampered)
  - staging / atomic-swap logic against a temp dir
  - rollback-decision logic (probation counter, marker recovery)
  - `/ota` command routing + payload validation (incl. busy-reject)
  - status-payload construction for each phase
  - HTTP client and `esp32.Partition` API mocked
- **On real ESP32-S3 hardware (manual, as today):**
  - actual flash writes + reboot
  - native partition OTA for the Firmware path
  - a deliberate bad App update to confirm probation auto-rollback fires
  - a deliberate power-cut mid-apply to confirm marker crash-recovery

---

## Out of scope (for this iteration)

- Signed/authenticated update artifacts (LAN-only trust assumed for now; SHA-256 integrity only, not authenticity).
- Delta/differential updates (full-bundle replace is simple and the artifacts are tiny).
- Internet / hosted release server (backend is the sole artifact source).
- Per-file partial updates (always replace the full App Code set per version).
