# Multi-MCU Board Abstraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a `boards/` module layer so the firmware auto-selects the correct hardware constants at runtime via `sys.platform`, eliminating the hardcoded `Pin("LED")` and enabling one codebase to run on Pico 2W and XIAO ESP32-S3 without reflashing.

**Architecture:** A new `boards/` directory holds one module per supported board type, each exporting a fixed interface (`BOX_TYPE`, `LED_PIN`, `WDT_TIMEOUT_MS`, `board_init()`). `main.py` selects the correct module at boot using a `sys.platform → board_name` map and registers it as `sys.modules["board"]`, so all other modules can do a plain `import board`. Host tests get a `boards/test_board.py` stub registered by `_stubs.py` before any firmware module is imported.

**Tech Stack:** MicroPython 1.23+, CPython (host tests via `unittest`), `sys.platform`, `sys.modules`

---

## File Map

| Action | Path | Responsibility |
|---|---|---|
| Create | `boards/pico2w.py` | Pico 2W hardware constants + init |
| Create | `boards/xiao_esp32s3.py` | XIAO ESP32-S3 hardware constants + init |
| Create | `boards/test_board.py` | Neutral stub for host tests |
| Modify | `tests/_stubs.py` | Register `board` stub before firmware imports |
| Modify | `hardware.py` | `import board`; use `board.LED_PIN` |
| Modify | `main.py` | Board selection block at top; use `board.WDT_TIMEOUT_MS` |
| Modify | `mqttutils.py` | Use `board.BOX_TYPE` in `publish_discovery`; drop `box_type` from firmware config fallback |
| Modify | `systemconfig/firmware_config.json` | Remove `box_type` field |
| Modify | `smart-box/CLAUDE.md` | Document new boards/ layer |

---

## Task 1: Create board modules (no tests — pure constants)

**Files:**
- Create: `boards/pico2w.py`
- Create: `boards/xiao_esp32s3.py`

- [ ] **Step 1: Create `boards/pico2w.py`**

```python
from micropython import const

BOX_TYPE       = "pico2w"
LED_PIN        = "LED"          # Pico-spezifischer benannter Pin
WDT_TIMEOUT_MS = const(8000)


def board_init():
    pass  # Kein spezielles Init nötig
```

- [ ] **Step 2: Create `boards/xiao_esp32s3.py`**

```python
from micropython import const
import esp

BOX_TYPE       = "xiao-esp32s3"
LED_PIN        = const(21)      # Onboard-LED des XIAO ESP32-S3
WDT_TIMEOUT_MS = const(8000)


def board_init():
    esp.osdebug(None)           # ESP32-Debug-Ausgabe auf UART unterdrücken
```

- [ ] **Step 3: Commit**

```bash
git add boards/pico2w.py boards/xiao_esp32s3.py
git commit -m "[firmware] add board modules for pico2w and xiao-esp32s3"
```

---

## Task 2: Add test board stub and wire it into the test harness

**Files:**
- Create: `boards/test_board.py`
- Modify: `tests/_stubs.py`

The test harness (`tests/__init__.py`) imports `_stubs` before any firmware module. `_stubs.py` must register `"board"` in `sys.modules` before `hardware` is imported, because `hardware.py` will do `import board` at module load time.

- [ ] **Step 1: Create `boards/test_board.py`**

```python
BOX_TYPE       = "test-board"
LED_PIN        = 0              # Neutraler Pin für CPython-Tests
WDT_TIMEOUT_MS = 8000


def board_init():
    pass
```

- [ ] **Step 2: Add board stub registration to `tests/_stubs.py`**

Append the following block at the **end** of `tests/_stubs.py` (after the `umqtt.simple` block):

```python
# --- board (auto-Selektion für Host-Tests) ---
import types as _types
_board_stub = _types.ModuleType("board")
_board_stub.BOX_TYPE       = "test-board"
_board_stub.LED_PIN        = 0
_board_stub.WDT_TIMEOUT_MS = 8000
_board_stub.board_init     = lambda: None
sys.modules["board"] = _board_stub
```

- [ ] **Step 3: Run existing tests — all must still pass**

```bash
python -m unittest discover -s tests -t . -v
```

Expected: all existing tests pass (no firmware module has changed yet, but `board` is now available).

- [ ] **Step 4: Commit**

```bash
git add boards/test_board.py tests/_stubs.py
git commit -m "[firmware] add test board stub and wire into test harness"
```

---

## Task 3: Update `hardware.py` to use board module for LED pin

**Files:**
- Modify: `hardware.py`

`hardware.py` is imported at module level throughout the test suite. The `board` stub is already registered by Task 2, so `import board` will resolve from `sys.modules["board"]` without touching the filesystem.

- [ ] **Step 1: Replace the hardcoded LED pin in `hardware.py`**

Change the top of `hardware.py`. Replace:

```python
import time
from machine import Pin
from micropython import const

# --- KONFIGURATION ---
# Onboard-LED einmalig definieren – bleibt für Statusanzeige verfügbar
led = Pin("LED", Pin.OUT)
```

With:

```python
import board
import time
from machine import Pin
from micropython import const

# --- KONFIGURATION ---
# Onboard-LED: Pin kommt aus dem Board-Modul (board.LED_PIN), nicht hardcodiert
led = Pin(board.LED_PIN, Pin.OUT)
```

Everything else in `hardware.py` stays unchanged.

- [ ] **Step 2: Run tests — all must still pass**

```bash
python -m unittest discover -s tests -t . -v
```

Expected: all tests pass. The `board` stub provides `LED_PIN = 0`, so `Pin(0, Pin.OUT)` is used in tests.

- [ ] **Step 3: Commit**

```bash
git add hardware.py
git commit -m "[firmware] hardware.py reads LED_PIN from board module"
```

---

## Task 4: Update `main.py` — board selection block + use `board.WDT_TIMEOUT_MS`

**Files:**
- Modify: `main.py`

`main.py` is not imported by the test suite (it runs directly on the device), so no test changes are needed here.

- [ ] **Step 1: Add board selection block at the very top of `main.py`**

Replace the current opening imports:

```python
import gc
import json
import machine
import time
import network
```

With:

```python
import sys

# Board-Modul anhand von sys.platform auswählen und als 'board' registrieren.
# Muss VOR allen anderen Firmware-Importen geschehen, da hardware.py 'board' beim
# Laden benötigt.
_PLATFORM_MAP = {"rp2": "pico2w", "esp32": "xiao_esp32s3"}
_board_name = _PLATFORM_MAP[sys.platform]
board = __import__("boards." + _board_name, None, None, [_board_name])
sys.modules["board"] = board
board.board_init()

import gc
import json
import machine
import time
import network
```

- [ ] **Step 2: Replace the `WDT_TIMEOUT_MS` constant in `main.py`**

In the `# --- KONFIGURATION ---` block, remove:

```python
WDT_TIMEOUT_MS       = 8000    # Watchdog-Timeout (nahe rp2-Maximum; Build-Limit beim Flashen prüfen)
```

- [ ] **Step 3: Update the WDT instantiation to use `board.WDT_TIMEOUT_MS`**

Change:

```python
wdt = machine.WDT(timeout=WDT_TIMEOUT_MS)
```

To:

```python
wdt = machine.WDT(timeout=board.WDT_TIMEOUT_MS)
```

- [ ] **Step 4: Commit**

```bash
git add main.py
git commit -m "[firmware] main.py selects board module via sys.platform at boot"
```

---

## Task 5: Update `mqttutils.py` — derive `boxType` from board module

**Files:**
- Modify: `mqttutils.py`
- Modify: `tests/test_publish.py`

`publish_discovery` currently reads `box_type` from `firmware_config.json`. After this task it reads `board.BOX_TYPE` instead. The `load_firmware_config` fallback dict also carries `box_type` — that field is removed.

- [ ] **Step 1: Write a failing test that asserts `boxType` comes from the board module**

In `tests/test_publish.py`, add to `PublishTest`:

```python
def test_discovery_box_type_from_board(self):
    import board as _board
    ok = mqttutils.publish_discovery(MAC)
    self.assertTrue(ok)
    _, payload = mqttutils._mqtt_client.published[-1]
    d = json.loads(payload)
    self.assertEqual(d["boxType"], _board.BOX_TYPE)
```

- [ ] **Step 2: Run the new test — it should pass already (or will after Step 3)**

```bash
python -m unittest tests.test_publish.PublishTest.test_discovery_box_type_from_board -v
```

If `boxType` currently comes from `firmware_config.json` (which still has `box_type: "pico2w"` in its fallback), the test will fail because the stub board returns `"test-board"`. That confirms the test is meaningful.

- [ ] **Step 3: Update `publish_discovery` in `mqttutils.py` to use `board.BOX_TYPE`**

At the top of `mqttutils.py`, add after the existing imports:

```python
import board as _board
```

In `publish_discovery`, replace:

```python
firmware = load_firmware_config()
...
box_type = firmware.get("box_type", "unknown")
```

The function currently looks like this (around line 396–425):

```python
def publish_discovery(client_id):
    ...
    firmware = load_firmware_config()
    firmware_version = firmware.get("firmware_version", "?")
    box_type = firmware.get("box_type", "?")
    ip = get_sta_ip() or "?"
    payload = json.dumps({
        "mac":             client_id,
        "firmwareVersion": firmware_version,
        "boxType":         box_type,
        "ip":              ip,
    })
```

Change it to:

```python
def publish_discovery(client_id):
    ...
    firmware = load_firmware_config()
    firmware_version = firmware.get("firmware_version", "?")
    ip = get_sta_ip() or "?"
    payload = json.dumps({
        "mac":             client_id,
        "firmwareVersion": firmware_version,
        "boxType":         _board.BOX_TYPE,
        "ip":              ip,
    })
```

- [ ] **Step 4: Remove `box_type` from the `load_firmware_config` fallback dict**

In `load_firmware_config`, the fallback dict (used when the file cannot be read) currently includes `"box_type"`. Remove that key. It looks like:

```python
return {
    "firmware_version": "0.6",
    "box_type": "pico2w",
    "supported_device_kinds": ["GPIO", "LED"],
    "supported_directions":   ["INPUT", "OUTPUT"],
}
```

Change to:

```python
return {
    "firmware_version":       "0.6",
    "supported_device_kinds": ["GPIO", "LED"],
    "supported_directions":   ["INPUT", "OUTPUT"],
}
```

Also remove the log line that prints `box_type` from `load_firmware_config` (the line referencing `data.get("box_type", "?")`).

- [ ] **Step 5: Run all tests — all must pass**

```bash
python -m unittest discover -s tests -t . -v
```

Expected: all tests pass including `test_discovery_box_type_from_board`.

- [ ] **Step 6: Commit**

```bash
git add mqttutils.py tests/test_publish.py
git commit -m "[firmware] publish_discovery reads boxType from board module"
```

---

## Task 6: Remove `box_type` from `firmware_config.json`

**Files:**
- Modify: `systemconfig/firmware_config.json`

- [ ] **Step 1: Update `firmware_config.json`**

Change from:

```json
{
  "firmware_version": "0.6",
  "box_type": "pico2w",
  "supported_device_kinds": ["GPIO", "LED"],
  "supported_directions": ["INPUT", "OUTPUT"]
}
```

To:

```json
{
  "firmware_version": "0.6",
  "supported_device_kinds": ["GPIO", "LED"],
  "supported_directions": ["INPUT", "OUTPUT"]
}
```

- [ ] **Step 2: Run all tests — all must still pass**

```bash
python -m unittest discover -s tests -t . -v
```

- [ ] **Step 3: Commit**

```bash
git add systemconfig/firmware_config.json
git commit -m "[firmware] remove box_type from firmware_config.json (now derived at runtime)"
```

---

## Task 7: Update CLAUDE.md

**Files:**
- Modify: `smart-box/CLAUDE.md`

- [ ] **Step 1: Add `boards/` to the Project Structure section**

In the `## Project Structure` file tree, add:

```
├── boards/
│   ├── pico2w.py                           # Board-Konstanten und Init für Raspberry Pi Pico 2W
│   ├── xiao_esp32s3.py                     # Board-Konstanten und Init für XIAO ESP32-S3
│   └── test_board.py                       # Neutraler Stub für Host-Tests
```

- [ ] **Step 2: Add a `### Board Selection` subsection under Module Responsibilities**

Add after the `### accesspoint.py` section:

```markdown
### `boards/` (board modules)

One file per supported hardware type. All files are uploaded to every device; the correct one is
selected at runtime by `main.py` using `sys.platform`. Each module exports a fixed interface:

| Symbol | Type | Description |
|---|---|---|
| `BOX_TYPE` | `str` | Canonical name sent in the discovery payload |
| `LED_PIN` | `int` or `str` | Passed to `machine.Pin()` for the onboard LED |
| `WDT_TIMEOUT_MS` | `int` | Watchdog timeout in milliseconds |
| `board_init()` | function | Called once at boot; no-op if nothing board-specific is needed |

`main.py` registers the selected module as `sys.modules["board"]` so other modules can do a plain
`import board`. Adding support for a new board = one new file in `boards/`, no changes to core logic.
```

- [ ] **Step 3: Update the `### main.py` responsibilities section**

Add to the `main.py` description:

> Selects the board module at the very top of the file via `sys.platform` → `boards/<name>.py` map, registers it as `sys.modules["board"]`, and calls `board.board_init()`. This happens before all other imports because `hardware.py` needs `board.LED_PIN` at module load time. Uses `board.WDT_TIMEOUT_MS` for the watchdog timeout.

- [ ] **Step 4: Update the `### hardware.py` section**

Replace mention of `led = Pin("LED", Pin.OUT)` with:

> Module-level `led = Pin(board.LED_PIN, Pin.OUT)` — pin comes from the board module, not hardcoded.

- [ ] **Step 5: Update `systemconfig/firmware_config.json` schema in CLAUDE.md**

Remove `"box_type"` from the documented JSON schema for `firmware_config.json`. Note that `boxType` in the discovery payload now comes from `board.BOX_TYPE`.

- [ ] **Step 6: Update the Host Tests section**

Add to the `## Host Tests` section:

> `boards/test_board.py` provides a neutral board stub (`BOX_TYPE = "test-board"`, `LED_PIN = 0`). `tests/_stubs.py` registers it as `sys.modules["board"]` before any firmware module is imported, so `import board` in `hardware.py` and `mqttutils.py` resolves correctly under CPython.

- [ ] **Step 7: Commit**

```bash
git add smart-box/CLAUDE.md
git commit -m "[firmware] update CLAUDE.md to document board module layer"
```

---

## Self-Review Notes

- **Spec coverage:** All seven spec requirements covered: board modules ✓, `sys.platform` auto-select ✓, `sys.modules` injection ✓, `hardware.py` LED pin ✓, `main.py` WDT ✓, `mqttutils` `BOX_TYPE` ✓, `firmware_config.json` cleanup ✓, test stub ✓, CLAUDE.md ✓.
- **No placeholders:** All steps contain exact file content and commands.
- **Type consistency:** `board.BOX_TYPE` (str), `board.LED_PIN` (int or str), `board.WDT_TIMEOUT_MS` (int) used consistently across Tasks 1–5.
- **Import alias:** `mqttutils.py` uses `import board as _board` to avoid shadowing any local `board` variable; this alias is used consistently in Task 5.
