# Firmware: Capability Manifest & Config Schema Versioning

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the SmartBox firmware so every boot announces its full capability manifest (GPIO, LED) and config schema version in the discovery payload, and validates saved device config compatibility before applying it.

**Architecture:** `firmware_config.json` gains a `capabilities` block and `config_schema_version` field. `publish_discovery` includes these in the MQTT payload. `save_device_config` stamps the current schema version into `device_config.json`. On boot, `main.py` compares the saved schema version against the expected version and skips GPIO init on mismatch — forcing a fresh config push from the backend.

**Tech Stack:** MicroPython 1.23+, `umqtt.simple`, host tests under CPython via `tests/_stubs.py`

---

## File Map

| File | Change |
|---|---|
| `smart-box/systemconfig/firmware_config.json` | Add `capabilities`, `config_schema_version`; bump `app_version` to `1.0` |
| `smart-box/mqttutils.py` | `save_device_config` stamps schema version; `load_device_config` returns full dict; `publish_discovery` includes capabilities + configSchemaVersion |
| `smart-box/main.py` | Import `load_firmware_config`; schema compatibility check before GPIO init |
| `smart-box/tests/test_publish.py` | Add assertions for `capabilities` and `configSchemaVersion`; update version assertion |
| `smart-box/tests/test_config.py` | Add round-trip tests for schema version in save/load |

---

### Task 1: Update `firmware_config.json` with capability manifest

**Files:**
- Modify: `smart-box/systemconfig/firmware_config.json`

No tests for this task — it is a data file read by subsequent tasks.

- [ ] **Step 1: Replace the file contents**

```json
{
  "app_version": "1.0",
  "firmware_version": "1.0",
  "config_schema_version": "1",
  "capabilities": {
    "GPIO": {
      "directions": ["INPUT", "OUTPUT"],
      "commands": ["ON", "OFF"],
      "config_fields": {
        "signal_duration_ms": {"type": "int", "default": 0}
      }
    },
    "LED": {
      "directions": ["OUTPUT"],
      "commands": ["ON"],
      "config_fields": {
        "signal_duration_ms": {"type": "int", "default": 150}
      }
    }
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add smart-box/systemconfig/firmware_config.json
git commit -m "[firmware] Add capability manifest and config_schema_version to firmware_config.json"
```

---

### Task 2: Update `save_device_config` and `load_device_config`

**Files:**
- Modify: `smart-box/mqttutils.py` (lines 68–104)
- Modify: `smart-box/tests/test_config.py`

`save_device_config` reads `config_schema_version` from `firmware_config.json` and writes it alongside the device list. `load_device_config` now returns the full dict (`{"config_schema_version": "...", "devices": [...]}`) or `None` on error — the caller in `main.py` is updated in Task 4.

- [ ] **Step 1: Write the failing tests**

Add to `smart-box/tests/test_config.py`, below the existing imports:

```python
import os
import tempfile


class DeviceConfigPersistenceTest(unittest.TestCase):
    def setUp(self):
        _stubs.clock.reset()
        mqttutils._firmware_config = None  # Firmware-Config-Cache leeren

    def tearDown(self):
        mqttutils._firmware_config = None

    def test_save_includes_schema_version(self):
        with tempfile.NamedTemporaryFile(suffix=".json", delete=False, mode='w') as f:
            tmp = f.name
        original = mqttutils.DEVICE_CONFIG_PATH
        try:
            mqttutils.DEVICE_CONFIG_PATH = tmp
            mqttutils.save_device_config([{"device_id": "a"}])
            with open(tmp, 'r') as f:
                data = json.load(f)
            self.assertIn("config_schema_version", data)
            self.assertIsInstance(data["config_schema_version"], str)
            self.assertEqual(len(data["devices"]), 1)
        finally:
            mqttutils.DEVICE_CONFIG_PATH = original
            os.unlink(tmp)

    def test_load_returns_dict_with_schema_version(self):
        data = {"config_schema_version": "1", "devices": [{"device_id": "a"}]}
        with tempfile.NamedTemporaryFile(suffix=".json", delete=False, mode='w') as f:
            json.dump(data, f)
            tmp = f.name
        original = mqttutils.DEVICE_CONFIG_PATH
        try:
            mqttutils.DEVICE_CONFIG_PATH = tmp
            result = mqttutils.load_device_config()
            self.assertIsNotNone(result)
            self.assertEqual(result["config_schema_version"], "1")
            self.assertEqual(len(result["devices"]), 1)
        finally:
            mqttutils.DEVICE_CONFIG_PATH = original
            os.unlink(tmp)

    def test_load_returns_none_on_missing_file(self):
        original = mqttutils.DEVICE_CONFIG_PATH
        mqttutils.DEVICE_CONFIG_PATH = "nonexistent_xyz_abc.json"
        try:
            result = mqttutils.load_device_config()
            self.assertIsNone(result)
        finally:
            mqttutils.DEVICE_CONFIG_PATH = original

    def test_save_load_roundtrip_preserves_schema_version(self):
        with tempfile.NamedTemporaryFile(suffix=".json", delete=False, mode='w') as f:
            tmp = f.name
        original = mqttutils.DEVICE_CONFIG_PATH
        try:
            mqttutils.DEVICE_CONFIG_PATH = tmp
            devs = [{"device_id": "b", "alias": "Werfer 1"}]
            mqttutils.save_device_config(devs)
            result = mqttutils.load_device_config()
            self.assertIsNotNone(result)
            self.assertEqual(result["devices"], devs)
            self.assertIn("config_schema_version", result)
        finally:
            mqttutils.DEVICE_CONFIG_PATH = original
            os.unlink(tmp)
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd smart-box
python -m unittest tests.test_config.DeviceConfigPersistenceTest -v
```

Expected: FAIL — `save_device_config` doesn't write `config_schema_version` yet; `load_device_config` returns a list, not a dict.

- [ ] **Step 3: Implement the changes in `mqttutils.py`**

Replace `load_device_config` (lines 68–88):

```python
def load_device_config():
    """
    Lädt die zuletzt gespeicherte Gerätekonfiguration von der Flash-Disk.
    Gibt ein Dict {"config_schema_version": "...", "devices": [...]} zurück,
    oder None bei Fehler/fehlender Datei.
    """
    try:
        with open(DEVICE_CONFIG_PATH, 'r') as f:
            data = json.load(f)
            devices = data.get("devices", [])
            print("Gerätekonfiguration geladen ({} Gerät(e)).".format(len(devices)))
            return data
    except (OSError, ValueError) as e:
        print("Keine gespeicherte Gerätekonfiguration gefunden:", e)
        return None
```

Replace `save_device_config` (lines 91–104):

```python
def save_device_config(devices):
    """
    Speichert die aktiven Geräte als JSON auf der Flash-Disk.
    Schreibt config_schema_version aus firmware_config.json mit, damit
    der Boot-Supervisor beim nächsten Start die Kompatibilität prüfen kann.

    :param devices: Liste von Geräte-Dicts aus dem Backend-Config-Push.
    """
    firmware = load_firmware_config()
    schema_version = firmware.get("config_schema_version", "1")
    try:
        with open(DEVICE_CONFIG_PATH, 'w') as f:
            json.dump({"config_schema_version": schema_version, "devices": devices}, f)
        print("Gerätekonfiguration gespeichert ({} Gerät(e)).".format(len(devices)))
    except OSError as e:
        print("Fehler beim Speichern der Gerätekonfiguration:", e)
```

- [ ] **Step 4: Fix the existing `test_config.py` setUp mock**

`_handle_config` calls `save_device_config(devices)`. The existing mock `mqttutils.save_device_config = lambda devs: self._saved.append(devs)` captures only `devices`. Update the `ConfigTest.setUp` to keep that mock working (signature is unchanged — `devices` is still the only param):

No change needed — the mock signature matches. Verify by running all config tests:

```bash
python -m unittest tests.test_config -v
```

Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add smart-box/mqttutils.py smart-box/tests/test_config.py
git commit -m "[firmware] load_device_config returns full dict; save_device_config stamps config_schema_version"
```

---

### Task 3: Boot-time schema compatibility check in `main.py`

**Files:**
- Modify: `smart-box/main.py` (lines 25–27 imports, lines 70–77 GPIO init block)

No host tests for `main.py` — it is verified on hardware. The logic is simple: if the saved schema version does not match the expected version from `firmware_config.json`, skip GPIO init and wait for the backend to push a fresh config.

- [ ] **Step 1: Update the import line in `main.py`**

Replace line 25:
```python
from mqttutils import publish_discovery, publish_heartbeat, connect_mqtt, reconnect_mqtt, load_device_config, update_device_pulses
```
with:
```python
from mqttutils import publish_discovery, publish_heartbeat, connect_mqtt, reconnect_mqtt, load_device_config, load_firmware_config, update_device_pulses
```

- [ ] **Step 2: Replace the GPIO pre-load block in `main.py`**

Replace lines 70–77 (the `# Gespeicherte Gerätekonfiguration` block):

```python
# Gespeicherte Gerätekonfiguration laden und GPIO-Pins initialisieren.
# Läuft vor dem MQTT-Connect, damit die Box sofort einsatzbereit ist,
# auch wenn der Backend-Config-Push noch aussteht.
# Bei Schema-Inkompatibilität (OTA-Update mit neuer Config-Version) wird die
# gespeicherte Config verworfen und auf den Config-Push gewartet.
from hardware import gpio_manager
firmware_cfg = load_firmware_config()
expected_schema = firmware_cfg.get("config_schema_version", "1")
saved = load_device_config()
if saved and saved.get("config_schema_version") == expected_schema:
    saved_devices = saved.get("devices", [])
    if saved_devices:
        _update_known_devices(saved_devices)
        gpio_manager.setup(saved_devices)
        print("GPIO-Pins aus gespeicherter Config initialisiert.")
    else:
        print("Gespeicherte Config leer – warte auf Config-Push.")
else:
    print("Config-Schema inkompatibel oder fehlend – warte auf Config-Push.")
```

- [ ] **Step 3: Commit**

```bash
git add smart-box/main.py
git commit -m "[firmware] Boot-time schema version check before GPIO init"
```

---

### Task 4: Update `publish_discovery` to include capabilities and `configSchemaVersion`

**Files:**
- Modify: `smart-box/mqttutils.py` (`publish_discovery` function, lines 416–452)
- Modify: `smart-box/tests/test_publish.py`

- [ ] **Step 1: Write the failing tests**

Add to `smart-box/tests/test_publish.py`, inside `PublishTest`:

```python
def test_discovery_includes_capabilities(self):
    ok = mqttutils.publish_discovery(MAC)
    self.assertTrue(ok)
    _, payload = mqttutils._mqtt_client.published[-1]
    d = json.loads(payload)
    self.assertIn("capabilities", d)
    self.assertIn("GPIO", d["capabilities"])
    self.assertIn("LED", d["capabilities"])

def test_discovery_includes_config_schema_version(self):
    ok = mqttutils.publish_discovery(MAC)
    self.assertTrue(ok)
    _, payload = mqttutils._mqtt_client.published[-1]
    d = json.loads(payload)
    self.assertIn("configSchemaVersion", d)
    self.assertIsInstance(d["configSchemaVersion"], str)

def test_gpio_capability_has_expected_commands(self):
    ok = mqttutils.publish_discovery(MAC)
    self.assertTrue(ok)
    _, payload = mqttutils._mqtt_client.published[-1]
    d = json.loads(payload)
    gpio = d["capabilities"]["GPIO"]
    self.assertIn("ON", gpio["commands"])
    self.assertIn("OFF", gpio["commands"])

def test_led_capability_has_no_off_command(self):
    ok = mqttutils.publish_discovery(MAC)
    self.assertTrue(ok)
    _, payload = mqttutils._mqtt_client.published[-1]
    d = json.loads(payload)
    led = d["capabilities"]["LED"]
    self.assertNotIn("OFF", led["commands"])
    self.assertIn("ON", led["commands"])
```

Also update the existing `test_discovery_reports_app_and_firmware_version` assertion — `app_version` is now `"1.0"`:

```python
def test_discovery_reports_app_and_firmware_version(self):
    ok = mqttutils.publish_discovery(MAC)
    self.assertTrue(ok)
    _, payload = mqttutils._mqtt_client.published[-1]
    d = json.loads(payload)
    self.assertIn("appVersion", d)
    self.assertIn("firmwareVersion", d)
    self.assertEqual(d["appVersion"], "1.0")   # war "0.6"
    self.assertTrue(d["firmwareVersion"].startswith("micropython"))
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd smart-box
python -m unittest tests.test_publish -v
```

Expected: new tests FAIL with `KeyError: 'capabilities'` / `KeyError: 'configSchemaVersion'`; `test_discovery_reports_app_and_firmware_version` FAIL with `AssertionError: '0.6' != '1.0'`.

- [ ] **Step 3: Update `publish_discovery` in `mqttutils.py`**

Replace the `publish_discovery` function body (lines 416–452):

```python
def publish_discovery(client_id):
    """
    Veröffentlicht einmalig beim Boot eine Discovery-Nachricht über die bestehende
    MQTT-Verbindung. Enthält neben Version und Box-Typ auch das vollständige
    Capability-Manifest und die Config-Schema-Version, damit das Backend die
    Fähigkeiten der Box ohne manuelle Konfiguration lernt.

    Topic:   smartboxes/discovery
    Payload: { "mac", "appVersion", "configSchemaVersion", "capabilities",
               "firmwareVersion", "boxType", "ip" }
    """
    if _mqtt_client is None:
        print("MQTT-Verbindung nicht verfügbar – Discovery nicht möglich.")
        return False

    firmware = load_firmware_config()
    app_version = firmware.get("app_version", firmware.get("firmware_version", "unknown"))
    config_schema_version = firmware.get("config_schema_version", "1")
    capabilities = firmware.get("capabilities", {})

    try:
        kernel = "micropython-" + os.uname().release
    except Exception as e:
        print("Kernel-Version nicht lesbar:", e)
        kernel = "micropython-unknown"

    try:
        ip_address = network.WLAN(network.STA_IF).ifconfig()[0]
        payload = json.dumps({
            "mac":                 client_id,
            "appVersion":          app_version,
            "configSchemaVersion": config_schema_version,
            "capabilities":        capabilities,
            "firmwareVersion":     kernel,
            "boxType":             _board.BOX_TYPE,
            "ip":                  ip_address,
        })
        _mqtt_client.publish("smartboxes/discovery", payload)
        print("Discovery gesendet:", payload)
        return True
    except Exception as e:
        print("Discovery fehlgeschlagen:", e)
        return False
```

- [ ] **Step 4: Run all tests**

```bash
cd smart-box
python -m unittest discover -s tests -t . -v
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add smart-box/mqttutils.py smart-box/tests/test_publish.py
git commit -m "[firmware] Discovery payload includes capabilities and configSchemaVersion"
```

---

## Self-Review

**Spec coverage:**
- ✅ `firmware_config.json` extended with `capabilities` and `config_schema_version`
- ✅ Discovery payload includes full capability manifest and `configSchemaVersion`
- ✅ `device_config.json` stamped with `config_schema_version` on save
- ✅ Boot-time schema compatibility check in `main.py` — skips GPIO init on mismatch
- ✅ LED capability: `ON` only, `signal_duration_ms`, no `OFF` (declared in manifest)
- ✅ GPIO capability: `ON`/`OFF`, `signal_duration_ms`
- ✅ BLOCK/UNBLOCK not in capability manifest (they are implicit meta-commands)

**Placeholder scan:** none found.

**Type consistency:** `load_device_config` returns `dict | None` throughout. `main.py` consumes with `saved.get(...)` pattern. `test_config.py` mock (`lambda devs: ...`) unchanged — `_handle_config` still calls `save_device_config(devices)` with the list, not the full dict.
