# Multi-MCU Board Abstraction — Design Spec

**Date:** 2026-06-26  
**Status:** Approved  
**Scope:** `smart-box` firmware only; backend follow-up noted but not specified here.

---

## Problem

The firmware is currently tied to the Raspberry Pi Pico 2W via one hardcoded assumption: `Pin("LED", Pin.OUT)` in `hardware.py`. Adding support for the XIAO ESP32-S3 (and future boards) requires a strategy that keeps one shared codebase while allowing per-board hardware differences.

---

## Goals

- One firmware codebase runs on all supported boards — no per-board forks or branches.
- Adding a new board requires only a new file, no changes to core logic.
- The board type is auto-detected at runtime — no manual flash-time configuration.
- The backend receives `boxType` in the discovery payload and can use it to send board-appropriate GPIO config.

---

## Non-Goals

- No tiebreaker/override mechanism for multiple boards within the same `sys.platform` family (deferred until needed).
- No backend implementation (GPIO profile per board type is a backend task).
- No OTA or remote board switching.

---

## Approach: Board Module per Hardware Type

### Directory Structure

```
smart-box/
└── boards/
    ├── pico2w.py
    ├── xiao_esp32s3.py
    └── test_board.py       # CPython host-test stub
```

All board files are uploaded to every device. The firmware selects the correct one at runtime using `sys.platform`.

### Board Module Interface

Every board module must export:

| Symbol | Type | Description |
|---|---|---|
| `BOX_TYPE` | `str` | Canonical name sent in discovery payload (e.g. `"pico2w"`) |
| `LED_PIN` | `int` or `str` | Passed directly to `machine.Pin()` |
| `WDT_TIMEOUT_MS` | `int` | Watchdog timeout in milliseconds |
| `board_init()` | `function` | Called once at boot; no-op if nothing board-specific needed |

Example — `boards/pico2w.py`:
```python
from micropython import const

BOX_TYPE       = "pico2w"
LED_PIN        = "LED"
WDT_TIMEOUT_MS = const(8000)

def board_init():
    pass
```

Example — `boards/xiao_esp32s3.py`:
```python
from micropython import const
import esp

BOX_TYPE       = "xiao-esp32s3"
LED_PIN        = const(21)
WDT_TIMEOUT_MS = const(8000)

def board_init():
    esp.osdebug(None)
```

---

## Boot Sequence

Board selection happens at the very top of `main.py`, before any other import — because `hardware.py` needs `LED_PIN` at import time.

```python
import sys
_PLATFORM_MAP = {"rp2": "pico2w", "esp32": "xiao_esp32s3"}
_board_name = _PLATFORM_MAP[sys.platform]
_board = __import__("boards." + _board_name, None, None, [_board_name], 0)
sys.modules["board"] = _board   # als 'board' registrieren, damit andere Module importieren können
_board.board_init()
```

After this block, any module can do `import board` and receive the already-selected board module — including `hardware.py`, which runs at import time and cannot accept arguments.

**Import order in `main.py`:**
1. `sys`, `board` selection, `board.board_init()`
2. `hardware` (consumes `board.LED_PIN`)
3. All other firmware modules

---

## Changes to Existing Modules

### `hardware.py`
- Remove `led = Pin("LED", Pin.OUT)` hardcode.
- Add `import board` at the top; use `board.LED_PIN`: `led = Pin(board.LED_PIN, Pin.OUT)`.
- Remove `WDT_TIMEOUT_MS` constant (moves to board module).

### `main.py`
- Add board selection block at top (see Boot Sequence above).
- Replace `WDT_TIMEOUT_MS` local constant with `board.WDT_TIMEOUT_MS`.
- Remove the "nahe rp2-Maximum" comment (no longer relevant; limit is board-specific).

### `mqttutils.py` — `publish_discovery`
- Replace `firmware_config["box_type"]` with `board.BOX_TYPE`.
- No other changes.

### `systemconfig/firmware_config.json`
- Remove `box_type` field (now derived at runtime from board module).
- Keep: `firmware_version`, `supported_device_kinds`, `supported_directions`.

---

## Testing

### Host Tests (`tests/`)

- Add `boards/test_board.py`:
  ```python
  BOX_TYPE       = "test-board"
  LED_PIN        = 0
  WDT_TIMEOUT_MS = 8000

  def board_init():
      pass
  ```
- In `tests/_stubs.py`: set `sys.platform = "test"` and add `"test": "test_board"` to the platform map so auto-detection resolves correctly under CPython.
- All existing tests pass without modification — board module is transparent to MQTT, security, and config logic.

### Physical Verification
Hardware-specific paths (`Pin("LED")` vs `Pin(21)`, ESP32 `board_init` side effects) are verified manually on each physical board after flashing.

---

## Backend Integration (Out of Scope — Noted for Alignment)

The discovery payload shape is unchanged:
```json
{ "mac": "aabbccddeeff", "firmwareVersion": "0.6", "boxType": "xiao-esp32s3", "ip": "..." }
```

`boxType` is now derived from `board.BOX_TYPE` instead of `firmware_config.json` — no contract change.

The backend should use `boxType` to maintain a per-board hardware profile (valid GPIO pins, capabilities) and validate device assignments at config-push time. This is a separate backend task.

---

## CLAUDE.md Updates Required

After implementation, update `smart-box/CLAUDE.md` to reflect:
- New `boards/` directory and its interface contract
- `sys.platform`-based auto-detection replacing `firmware_config.json` `box_type`
- `hardware.py` no longer owns `WDT_TIMEOUT_MS` — it comes from the board module
- `firmware_config.json` schema change (no `box_type` field)
- Host test stub pattern for board modules
