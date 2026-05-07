# AI Instructions — client-node-pico-2w

## Project Overview
This project contains the firmware for a **Raspberry Pi Pico 2W** node that acts as a client in the Smart Ground system. The node connects to WiFi, communicates with a backend via MQTT, and publishes device discovery and sensor data.

---

## Hardware
- **Board:** Raspberry Pi Pico 2W
- **Onboard LED:** accessible via `Pin("LED", Pin.OUT)`
- **Wireless:** built-in CYW43 WiFi chip, managed via MicroPython's `network` module
- **No external peripherals** are assumed unless explicitly stated

---

## Language & Runtime
- **MicroPython only** — do not use CPython-specific libraries or syntax
- Target MicroPython version: **1.23+** (RP2350 / Pico 2W support)
- Allowed standard modules: `network`, `time`, `machine`, `json`, `sys`, `gc`
- MQTT: use `umqtt.simple` (preferred) with fallback to `umqtt.robust`
- Do **not** use `asyncio` unless explicitly requested; keep logic synchronous and polling-based

---

## Code Style & Conventions
- Use **German comments** for inline documentation (consistent with existing code)
- Variable and function names in **English**
- Configuration constants at the top of each file in a clearly marked `# --- KONFIGURATION ---` block
- Keep files small and focused — one responsibility per module
- Avoid external dependencies beyond what MicroPython ships with

---

## Safe Exit Requirement (CRITICAL)
**Every `main.py` or top-level entry-point script MUST follow this pattern:**

```python
import machine
import sys

try:
    # --- main logic here ---
    pass

except KeyboardInterrupt:
    print("Programm durch Benutzer beendet.")

except Exception as e:
    print("Kritischer Fehler:", e)
    time.sleep(5)
    machine.reset()  # Neustart bei nicht behebbarem Fehler

finally:
    # Ressourcen freigeben
    try:
        client.disconnect()
    except Exception:
        pass
    try:
        wlan.disconnect()
        wlan.active(False)
    except Exception:
        pass
    print("Aufräumen abgeschlossen.")
```

Rules:
- `KeyboardInterrupt` must always be caught and handled gracefully (clean shutdown, no reset)
- All other unhandled exceptions must trigger a `machine.reset()` after a short delay
- The `finally` block must always attempt to disconnect MQTT and deactivate WiFi
- Never use bare `except:` — always use `except Exception as e:` or specific exception types

---

## MQTT Conventions
- **Broker IP** is configured as a constant; do not hardcode it in logic functions
- **Topic structure:** `devices/discovery` for registration, `pico/control` for inbound commands
- Discovery payload schema:
  ```json
  { "clientId": "string", "name": "string", "ipAddress": "string" }
  ```
- Always call `client.check_msg()` in the main loop to process subscriptions
- Publish intervals should be configurable via a constant (e.g., `PUBLISH_INTERVAL_S = 100`)

---

## WiFi Conventions
- WiFi credentials (`SSID`, `PASSWORD`) are constants at the top of the file
- Connection must include a timeout or retry limit — never block indefinitely
- Check `wlan.isconnected()` before any network operation
- Log the assigned IP address after successful connection

---

## File Structure
```
client-node-pico-2w/
├── CLAUDE.md            # This file — AI instructions
├── main.py              # Entry point, deployed to Pico as main.py (always safe exit)
├── MQTTLogic.py         # MQTT connection, subscription, and publish logic
└── mqtt_discovery.py    # Device discovery helper (publish_discovery function)
```

- `main.py` is the only file that runs at boot; it imports from the other modules
- Helper modules (`MQTTLogic.py`, `mqtt_discovery.py`) must **not** contain top-level side effects when imported
- Guard any self-test code with `if __name__ == "__main__":`

---

## What to Avoid
- Do **not** use `uasyncio` / `asyncio` unless requested
- Do **not** use f-strings with complex expressions (MicroPython f-string support is limited)
- Do **not** `import os` for file I/O on the Pico unless explicitly needed
- Do **not** leave hardcoded credentials anywhere other than the `# --- KONFIGURATION ---` block
- Do **not** suppress exceptions silently — always `print()` the error before continuing or resetting
