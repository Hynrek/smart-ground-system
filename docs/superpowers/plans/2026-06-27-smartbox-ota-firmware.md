# SmartBox OTA — Firmware Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the SmartBox firmware the ability to update its own **App Code** (and, secondarily, the **MicroPython Firmware** image) over the LAN with no cable, triggered by MQTT and pulled over HTTP, with verify-before-apply and automatic rollback.

**Architecture:** A new single-responsibility module `ota.py` holds all OTA logic. The MQTT layer (`mqttutils.py`) only *routes* the new `smartboxes/{mac}/ota` topic into `ota.handle_command(...)` and exposes a `publish_ota_status(...)` helper. The boot sequence (`main.py`) calls three testable `ota` functions (interrupted-apply recovery, probation check, healthy-boot confirm). The HTTP fetch and the `esp32.Partition` API are module-level seams in `ota.py` that host tests replace with fakes, so all logic is unit-tested under CPython exactly like the existing `tests/_stubs.py` setup; the real fetch/partition/flash paths are verified manually on the ESP32-S3.

**Tech Stack:** MicroPython 1.23+ (XIAO ESP32-S3), `hashlib` (sha256), `os` (directory ops), `urequests` (HTTP, device-only), `esp32.Partition` (firmware A/B), CPython `unittest` for host tests.

**Spec:** `docs/superpowers/specs/2026-06-27-smartbox-ota-design.md`

---

## Conventions for this plan (read once)

- German inline comments (`# Kommentar`), English identifiers — match the existing firmware.
- Config constants go in a `# --- KONFIGURATION ---` block at the top of each file.
- Run **all** host tests from `smart-box/` with:
  `python -m unittest discover -s tests -t . -v`
- Run a **single** test with e.g.:
  `python -m unittest tests.test_ota_parse -v`
- `tests/__init__.py` installs `_stubs.py` before any firmware import — every new test module starts with `from tests import _stubs`.
- **Deliberate exception to the "no `import os`" rule:** OTA needs directory operations (`rename`, `listdir`, `remove`, `mkdir`, `stat`) that `open()` cannot do. `ota.py` is the *only* module allowed to `import os`. This is recorded in `smart-box/CLAUDE.md` in the final task.

## File structure (what each file owns)

- Create `smart-box/ota.py` — all OTA logic: command parsing, download, sha256 verify, staging, atomic apply, markers, probation/rollback state, firmware-partition wrapper, status orchestration. HTTP + partition are replaceable module attributes.
- Modify `smart-box/mqttutils.py` — subscribe to `/ota`, route it to `ota.handle_command`, add `publish_ota_status`, add `set_watchdog`; split version reporting in `publish_discovery`.
- Modify `smart-box/main.py` — call `ota.recover_interrupted_apply()` + `ota.probation_check()` before the watchdog starts; call `mqttutils.set_watchdog(wdt)`; call `ota.confirm_boot_healthy(...)` after the first successful MQTT connect.
- Modify `smart-box/systemconfig/firmware_config.json` — add `app_version`.
- Modify `smart-box/tests/_stubs.py` — add an `esp32` fake (Partition) and an `os`-temp-dir test helper note; add a `uname` to the existing setup.
- Create test modules under `smart-box/tests/`: `test_ota_parse.py`, `test_ota_verify.py`, `test_ota_download.py`, `test_ota_apply.py`, `test_ota_rollback.py`, `test_ota_handle.py`, `test_ota_firmware.py`, plus additions to `test_publish.py`.
- Modify `smart-box/CLAUDE.md` — document the OTA module, topics, version split, and the `import os` exception.

## Data shapes (used across tasks — keep names identical)

OTA command (`smartboxes/{mac}/ota` payload):
```json
{ "type": "APP", "version": "0.7", "url": "http://192.168.1.100/api/ota/app/0.7", "sha256": "<hex-of-manifest>", "size": 12345 }
```

Manifest (`GET {url}/manifest.json`):
```json
{ "appVersion": "0.7",
  "files": [ { "path": "main.py", "sha256": "<hex>", "size": 4096 } ] }
```

OTA state file `userconfig/ota_state.json`:
```json
{ "phase": "idle", "version": "0.7", "type": "APP", "boot_attempts": 0 }
```
`phase` ∈ `"idle"`, `"probation"`. Status phases published to `/ota/status`: `DOWNLOADING`, `VERIFYING`, `APPLYING`, `APPLIED`, `FAILED`, `ROLLED_BACK`.

---

## Task 1: Version split (appVersion vs firmwareVersion)

**Files:**
- Modify: `smart-box/systemconfig/firmware_config.json`
- Modify: `smart-box/mqttutils.py` (`publish_discovery`, ~line 397-426)
- Modify: `smart-box/tests/_stubs.py` (add `uname` to the `machine`/`os` stub area)
- Modify: `smart-box/tests/test_publish.py`

- [ ] **Step 1: Add `os.uname` to the host stubs**

In `smart-box/tests/_stubs.py`, after the `micropython` block (around line 80), add a fake `os.uname` so the host can read a kernel version. Append:

```python
# --- os.uname (für firmwareVersion-Reporting; restliche os-Funktionen bleiben echt) ---
import os as _os
class _Uname:
    # Reihenfolge wie MicroPython: (sysname, nodename, release, version, machine)
    sysname = "esp32"
    nodename = "esp32"
    release = "1.23.0"
    version = "v1.23.0 on 2025-01-01"
    machine = "Generic ESP32S3 module with ESP32S3"
if not hasattr(_os, "uname"):
    _os.uname = lambda: _Uname()
```

- [ ] **Step 2: Write the failing test**

Add to `smart-box/tests/test_publish.py` inside `PublishTest`:

```python
def test_discovery_reports_app_and_firmware_version(self):
    ok = mqttutils.publish_discovery(MAC)
    self.assertTrue(ok)
    _, payload = mqttutils._mqtt_client.published[-1]
    d = json.loads(payload)
    # appVersion kommt aus firmware_config.json, firmwareVersion aus os.uname()
    self.assertIn("appVersion", d)
    self.assertIn("firmwareVersion", d)
    self.assertTrue(d["firmwareVersion"].startswith("micropython"))
```

- [ ] **Step 3: Run it to verify it fails**

Run: `python -m unittest tests.test_publish -v`
Expected: FAIL — `KeyError`/`assertIn` fails because `appVersion` is missing.

- [ ] **Step 4: Add `app_version` to the config file**

Replace `smart-box/systemconfig/firmware_config.json` contents with:

```json
{
  "app_version": "0.6",
  "firmware_version": "0.6",
  "supported_device_kinds": ["GPIO", "LED"],
  "supported_directions": ["INPUT", "OUTPUT"]
}
```

(`firmware_version` is kept for backward compatibility during migration; `app_version` is the new App Code version.)

- [ ] **Step 5: Update `publish_discovery`**

In `smart-box/mqttutils.py`, add `import os` is NOT needed here — read uname via the `os` module already available through the stub on host and the real module on device. At the top of `mqttutils.py` add `import os` under the existing imports (line 1-5 area). Then change the body of `publish_discovery` (the `firmware = ...` / `payload = ...` block) to:

```python
    firmware = load_firmware_config()
    app_version = firmware.get("app_version", firmware.get("firmware_version", "unknown"))

    try:
        kernel = "micropython-" + os.uname().release
    except Exception:
        kernel = "micropython-unknown"

    try:
        ip_address = network.WLAN(network.STA_IF).ifconfig()[0]
        payload = json.dumps({
            "mac":             client_id,
            "appVersion":      app_version,
            "firmwareVersion": kernel,
            "boxType":         _board.BOX_TYPE,
            "ip":              ip_address,
        })
        _mqtt_client.publish("smartboxes/discovery", payload)
        print("Discovery gesendet:", payload)
        return True
    except Exception as e:
        print("Discovery fehlgeschlagen:", e)
        return False
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `python -m unittest tests.test_publish -v`
Expected: PASS (existing `test_discovery_payload` still asserts `firmwareVersion` present — still true; new test passes).

- [ ] **Step 7: Commit**

```bash
git add smart-box/systemconfig/firmware_config.json smart-box/mqttutils.py smart-box/tests/_stubs.py smart-box/tests/test_publish.py
git commit -m "[firmware] Split discovery into appVersion + firmwareVersion"
```

---

## Task 2: `ota.parse_command` + busy flag

**Files:**
- Create: `smart-box/ota.py`
- Create: `smart-box/tests/test_ota_parse.py`

- [ ] **Step 1: Write the failing test**

Create `smart-box/tests/test_ota_parse.py`:

```python
from tests import _stubs
import json
import unittest
import ota


class OtaParseTest(unittest.TestCase):
    def _cmd(self, **over):
        base = {"type": "APP", "version": "0.7",
                "url": "http://10.0.0.1/api/ota/app/0.7",
                "sha256": "ab" * 32, "size": 100}
        base.update(over)
        return json.dumps(base).encode()

    def test_parse_valid_app_command(self):
        cmd = ota.parse_command(self._cmd())
        self.assertEqual(cmd["type"], "APP")
        self.assertEqual(cmd["version"], "0.7")
        self.assertEqual(cmd["url"], "http://10.0.0.1/api/ota/app/0.7")

    def test_parse_rejects_unknown_type(self):
        with self.assertRaises(ValueError):
            ota.parse_command(self._cmd(type="BOGUS"))

    def test_parse_rejects_missing_url(self):
        with self.assertRaises(ValueError):
            ota.parse_command(self._cmd(url=""))

    def test_parse_rejects_bad_json(self):
        with self.assertRaises(ValueError):
            ota.parse_command(b"not json")

    def test_is_busy_default_false(self):
        self.assertFalse(ota.is_busy())


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run it to verify it fails**

Run: `python -m unittest tests.test_ota_parse -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'ota'`.

- [ ] **Step 3: Create `ota.py` with constants, busy flag, and `parse_command`**

Create `smart-box/ota.py`:

```python
import gc
import json
import os
import hashlib

# --- KONFIGURATION ---
OTA_STAGING_DIR    = "ota_staging"            # temporäres Verzeichnis für heruntergeladene Dateien
OTA_BACKUP_DIR     = "ota_backup"             # Sicherungskopie der vorherigen App-Code-Dateien
OTA_MARKER_PENDING = "ota_pending"            # Marker: Apply hat begonnen
OTA_MARKER_APPLIED = "ota_applied"            # Marker: Apply abgeschlossen
OTA_STATE_PATH     = "userconfig/ota_state.json"
CHUNK_SIZE         = 1024                      # Lesepuffer für Streaming-Download
MAX_PROBATION_BOOTS = 3                        # Boot-Versuche, bevor automatisch zurückgerollt wird
_VALID_TYPES       = ("APP", "FIRMWARE")

# --- Modul-globaler Zustand ---
_busy = False


def is_busy():
    """True, solange ein OTA-Vorgang läuft (verhindert parallele Updates)."""
    return _busy


def parse_command(payload_bytes):
    """
    Parst und validiert eine /ota-Befehls-Payload.

    Erwartet JSON:
        { "type": "APP"|"FIRMWARE", "version": "...", "url": "...",
          "sha256": "<hex>", "size": <int> }

    Gibt ein normalisiertes Dict zurück. Wirft ValueError bei ungültiger Payload.
    """
    try:
        data = json.loads(payload_bytes)
    except (ValueError, TypeError) as e:
        raise ValueError("OTA-Payload ist kein gültiges JSON: {}".format(e))

    ota_type = data.get("type", "")
    version  = data.get("version", "")
    url      = data.get("url", "")
    sha256   = data.get("sha256", "")

    if ota_type not in _VALID_TYPES:
        raise ValueError("Ungültiger OTA-Typ: {}".format(ota_type))
    if not version or not url:
        raise ValueError("OTA-Befehl benötigt 'version' und 'url'")

    return {
        "type": ota_type,
        "version": version,
        "url": url,
        "sha256": sha256,
        "size": data.get("size", 0),
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `python -m unittest tests.test_ota_parse -v`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add smart-box/ota.py smart-box/tests/test_ota_parse.py
git commit -m "[firmware] Add ota module skeleton with command parsing"
```

---

## Task 3: `ota.verify_file` (streaming sha256)

**Files:**
- Modify: `smart-box/ota.py`
- Create: `smart-box/tests/test_ota_verify.py`

- [ ] **Step 1: Write the failing test**

Create `smart-box/tests/test_ota_verify.py`:

```python
from tests import _stubs
import os
import hashlib
import tempfile
import unittest
import ota


class OtaVerifyTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.path = os.path.join(self.dir, "blob.bin")
        self.data = b"hello smartbox" * 100
        with open(self.path, "wb") as f:
            f.write(self.data)
        self.good = hashlib.sha256(self.data).hexdigest()

    def test_verify_matches(self):
        self.assertTrue(ota.verify_file(self.path, self.good))

    def test_verify_rejects_wrong_hash(self):
        self.assertFalse(ota.verify_file(self.path, "00" * 32))

    def test_verify_missing_file_returns_false(self):
        self.assertFalse(ota.verify_file(self.path + ".nope", self.good))


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run it to verify it fails**

Run: `python -m unittest tests.test_ota_verify -v`
Expected: FAIL — `AttributeError: module 'ota' has no attribute 'verify_file'`.

- [ ] **Step 3: Implement `verify_file`**

Append to `smart-box/ota.py`:

```python
def verify_file(path, expected_hex):
    """
    Berechnet den SHA-256 einer Datei im Streaming-Verfahren und vergleicht ihn
    mit dem erwarteten Hex-Digest. Gibt True/False zurück, niemals Exception nach aussen.
    """
    if not expected_hex:
        return False
    try:
        h = hashlib.sha256()
        with open(path, "rb") as f:
            while True:
                chunk = f.read(CHUNK_SIZE)
                if not chunk:
                    break
                h.update(chunk)
        digest = "".join("{:02x}".format(b) for b in h.digest())
        return digest == expected_hex
    except OSError as e:
        print("Verify fehlgeschlagen für {}: {}".format(path, e))
        return False
    finally:
        gc.collect()
```

> Note: `hashlib.digest()` returns bytes on both CPython and MicroPython; we hex-encode manually because MicroPython `hashlib` has no `hexdigest()`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `python -m unittest tests.test_ota_verify -v`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add smart-box/ota.py smart-box/tests/test_ota_verify.py
git commit -m "[firmware] Add streaming sha256 file verification to ota"
```

---

## Task 4: `ota.download_app` (manifest + files, injectable HTTP)

**Files:**
- Modify: `smart-box/ota.py`
- Create: `smart-box/tests/test_ota_download.py`

- [ ] **Step 1: Write the failing test**

Create `smart-box/tests/test_ota_download.py`:

```python
from tests import _stubs
import os
import json
import hashlib
import shutil
import tempfile
import unittest
import ota


class OtaDownloadTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        ota.OTA_STAGING_DIR = os.path.join(self.dir, "ota_staging")
        # Server-Inhalte vorbereiten: zwei Dateien + Manifest
        self.files = {"main.py": b"print('v7')\n",
                      "boards/xiao_esp32s3.py": b"BOX_TYPE='x'\n"}
        manifest = {"appVersion": "0.7", "files": []}
        for path, body in self.files.items():
            manifest["files"].append(
                {"path": path,
                 "sha256": hashlib.sha256(body).hexdigest(),
                 "size": len(body)})
        self.manifest = manifest

        # HTTP-Seams durch Fakes ersetzen
        def fake_get_json(url):
            return json.loads(json.dumps(self.manifest))  # Kopie
        def fake_stream(url, on_chunk, wdt=None):
            # url endet mit dem Datei-Pfad
            for path, body in self.files.items():
                if url.endswith("/files/" + path):
                    on_chunk(body)
                    return
            raise OSError("404 " + url)
        ota.http_get_json = fake_get_json
        ota.http_stream = fake_stream

    def tearDown(self):
        shutil.rmtree(self.dir, ignore_errors=True)

    def test_download_writes_and_verifies_all_files(self):
        manifest = ota.download_app("http://srv/api/ota/app/0.7", wdt=None)
        self.assertEqual(manifest["appVersion"], "0.7")
        for path, body in self.files.items():
            staged = os.path.join(ota.OTA_STAGING_DIR, path)
            with open(staged, "rb") as f:
                self.assertEqual(f.read(), body)

    def test_download_raises_on_hash_mismatch(self):
        self.manifest["files"][0]["sha256"] = "00" * 32
        with self.assertRaises(ota.OtaError):
            ota.download_app("http://srv/api/ota/app/0.7", wdt=None)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run it to verify it fails**

Run: `python -m unittest tests.test_ota_download -v`
Expected: FAIL — `AttributeError: module 'ota' has no attribute 'OtaError'` / `download_app`.

- [ ] **Step 3: Implement the HTTP seams, `OtaError`, dir helpers, and `download_app`**

Append to `smart-box/ota.py`:

```python
class OtaError(Exception):
    """Fehler während eines OTA-Vorgangs (Download/Verify/Apply)."""
    pass


# --- HTTP-Seams: auf dem Gerät über urequests, in Tests ersetzbar ---

def _default_http_get_json(url):
    import urequests
    r = urequests.get(url)
    try:
        return r.json()
    finally:
        r.close()


def _default_http_stream(url, on_chunk, wdt=None):
    import urequests
    r = urequests.get(url)
    try:
        sock = r.raw
        while True:
            chunk = sock.read(CHUNK_SIZE)
            if not chunk:
                break
            on_chunk(chunk)
            if wdt is not None:
                wdt.feed()
    finally:
        r.close()


http_get_json = _default_http_get_json    # in Tests überschreibbar
http_stream   = _default_http_stream      # in Tests überschreibbar


def _ensure_dir(path):
    """Legt ein Verzeichnis (rekursiv) an, falls es fehlt."""
    parts = path.split("/")
    cur = ""
    for p in parts:
        if not p:
            continue
        cur = cur + "/" + p if cur else p
        try:
            os.mkdir(cur)
        except OSError:
            pass  # existiert bereits


def _ensure_parent(path):
    """Stellt sicher, dass das Elternverzeichnis einer Datei existiert."""
    idx = path.rfind("/")
    if idx > 0:
        _ensure_dir(path[:idx])


def _rmtree(path):
    """Löscht eine Datei oder ein Verzeichnis rekursiv (best effort)."""
    try:
        mode = os.stat(path)[0]
    except OSError:
        return
    if mode & 0x4000:  # Verzeichnis (S_IFDIR)
        for name in os.listdir(path):
            _rmtree(path + "/" + name)
        try:
            os.rmdir(path)
        except OSError:
            pass
    else:
        try:
            os.remove(path)
        except OSError:
            pass


def download_app(base_url, wdt=None):
    """
    Lädt Manifest + alle App-Code-Dateien nach OTA_STAGING_DIR herunter und
    verifiziert jede Datei per SHA-256. Wirft OtaError bei Fehler.

    base_url: z.B. "http://host/api/ota/app/0.7"
              Manifest:  {base_url}/manifest.json
              Dateien:   {base_url}/files/{path}

    Gibt das Manifest-Dict zurück.
    """
    _rmtree(OTA_STAGING_DIR)
    _ensure_dir(OTA_STAGING_DIR)

    try:
        manifest = http_get_json(base_url + "/manifest.json")
    except Exception as e:
        raise OtaError("Manifest-Download fehlgeschlagen: {}".format(e))

    files = manifest.get("files", [])
    if not files:
        raise OtaError("Manifest enthält keine Dateien")

    for entry in files:
        rel = entry.get("path", "")
        expected = entry.get("sha256", "")
        if not rel:
            raise OtaError("Manifest-Eintrag ohne 'path'")
        dest = OTA_STAGING_DIR + "/" + rel
        _ensure_parent(dest)
        try:
            f = open(dest, "wb")
            try:
                http_stream(base_url + "/files/" + rel,
                            lambda c: f.write(c), wdt)
            finally:
                f.close()
        except Exception as e:
            raise OtaError("Download fehlgeschlagen für {}: {}".format(rel, e))
        if not verify_file(dest, expected):
            raise OtaError("SHA-256 stimmt nicht für {}".format(rel))
        gc.collect()

    return manifest
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `python -m unittest tests.test_ota_download -v`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add smart-box/ota.py smart-box/tests/test_ota_download.py
git commit -m "[firmware] Add staged app-code download with per-file verify"
```

---

## Task 5: `ota.apply_app` + `ota.recover_interrupted_apply` (markers + atomic-ish swap)

**Files:**
- Modify: `smart-box/ota.py`
- Create: `smart-box/tests/test_ota_apply.py`

- [ ] **Step 1: Write the failing test**

Create `smart-box/tests/test_ota_apply.py`:

```python
from tests import _stubs
import os
import shutil
import tempfile
import unittest
import ota


class OtaApplyTest(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp()
        ota.OTA_STAGING_DIR    = os.path.join(self.root, "ota_staging")
        ota.OTA_BACKUP_DIR     = os.path.join(self.root, "ota_backup")
        ota.OTA_MARKER_PENDING = os.path.join(self.root, "ota_pending")
        ota.OTA_MARKER_APPLIED = os.path.join(self.root, "ota_applied")
        self._live = {}
        # Live-Datei vorhanden
        self._write(os.path.join(self.root, "main.py"), b"OLD\n")
        # Staging-Datei vorhanden
        ota._ensure_dir(ota.OTA_STAGING_DIR)
        self._write(os.path.join(ota.OTA_STAGING_DIR, "main.py"), b"NEW\n")

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def _write(self, path, body):
        with open(path, "wb") as f:
            f.write(body)

    def _read(self, path):
        with open(path, "rb") as f:
            return f.read()

    def test_apply_swaps_live_and_backs_up(self):
        ota.apply_app({"files": [{"path": "main.py"}]}, live_root=self.root)
        self.assertEqual(self._read(os.path.join(self.root, "main.py")), b"NEW\n")
        self.assertEqual(self._read(os.path.join(ota.OTA_BACKUP_DIR, "main.py")), b"OLD\n")
        # Marker aufgeräumt nach erfolgreichem Apply
        self.assertFalse(os.path.exists(ota.OTA_MARKER_PENDING))

    def test_recover_restores_backup_when_apply_interrupted(self):
        # Simuliere Absturz: Backup geschrieben, pending-Marker gesetzt, live korrupt
        ota._ensure_dir(ota.OTA_BACKUP_DIR)
        self._write(os.path.join(ota.OTA_BACKUP_DIR, "main.py"), b"OLD\n")
        self._write(os.path.join(self.root, "main.py"), b"HALF")
        self._write(ota.OTA_MARKER_PENDING, b"main.py\n")
        recovered = ota.recover_interrupted_apply(live_root=self.root)
        self.assertTrue(recovered)
        self.assertEqual(self._read(os.path.join(self.root, "main.py")), b"OLD\n")
        self.assertFalse(os.path.exists(ota.OTA_MARKER_PENDING))

    def test_recover_noop_when_no_pending_marker(self):
        self.assertFalse(ota.recover_interrupted_apply(live_root=self.root))


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run it to verify it fails**

Run: `python -m unittest tests.test_ota_apply -v`
Expected: FAIL — `apply_app` / `recover_interrupted_apply` missing.

- [ ] **Step 3: Implement `apply_app` and `recover_interrupted_apply`**

Append to `smart-box/ota.py`:

```python
def _copy_file(src, dst):
    """Kopiert eine Datei in CHUNK_SIZE-Blöcken (rename geht nicht über alle FS-Grenzen)."""
    _ensure_parent(dst)
    with open(src, "rb") as fin:
        with open(dst, "wb") as fout:
            while True:
                chunk = fin.read(CHUNK_SIZE)
                if not chunk:
                    break
                fout.write(chunk)


def _write_marker(path, text):
    with open(path, "w") as f:
        f.write(text)


def apply_app(manifest, live_root=""):
    """
    Aktiviert die in OTA_STAGING_DIR liegende, bereits verifizierte App-Code-Version:
      1. pending-Marker schreiben (Liste der betroffenen Dateien)
      2. jede Live-Datei nach OTA_BACKUP_DIR sichern
      3. jede Staging-Datei über die Live-Datei kopieren
      4. applied-Marker schreiben, dann beide Marker + Staging aufräumen

    live_root erlaubt Tests, eine andere Wurzel zu verwenden (auf dem Gerät: "" = FS-Wurzel).
    """
    paths = [e.get("path", "") for e in manifest.get("files", []) if e.get("path")]

    _rmtree(OTA_BACKUP_DIR)
    _ensure_dir(OTA_BACKUP_DIR)
    _write_marker(OTA_MARKER_PENDING, "\n".join(paths))

    def _live(p):
        return (live_root + "/" + p) if live_root else p

    # Schritt 2: vorhandene Live-Dateien sichern
    for p in paths:
        live = _live(p)
        try:
            os.stat(live)
        except OSError:
            continue  # Datei existiert noch nicht (Neuzugang) – nichts zu sichern
        _copy_file(live, OTA_BACKUP_DIR + "/" + p)

    # Schritt 3: Staging über Live kopieren
    for p in paths:
        _copy_file(OTA_STAGING_DIR + "/" + p, _live(p))

    # Schritt 4: Abschluss markieren und aufräumen
    _write_marker(OTA_MARKER_APPLIED, "ok")
    _rmtree(OTA_STAGING_DIR)
    try:
        os.remove(OTA_MARKER_PENDING)
    except OSError:
        pass
    try:
        os.remove(OTA_MARKER_APPLIED)
    except OSError:
        pass


def recover_interrupted_apply(live_root=""):
    """
    Wird beim Boot aufgerufen, BEVOR der Watchdog läuft. Wenn ein pending-Marker
    ohne applied-Marker existiert, wurde ein Apply durch Stromverlust unterbrochen:
    die gesicherten Backup-Dateien werden über die (evtl. korrupten) Live-Dateien
    zurückgespielt. Gibt True zurück, wenn eine Wiederherstellung stattfand.
    """
    try:
        os.stat(OTA_MARKER_PENDING)
    except OSError:
        return False  # kein unterbrochener Apply

    try:
        os.stat(OTA_MARKER_APPLIED)
        applied = True
    except OSError:
        applied = False

    if applied:
        # Apply war eigentlich fertig, nur die Marker blieben liegen → nur aufräumen
        _cleanup_markers()
        return False

    # Backup-Dateien zurückspielen
    def _live(p):
        return (live_root + "/" + p) if live_root else p
    try:
        with open(OTA_MARKER_PENDING, "r") as f:
            paths = [ln.strip() for ln in f.read().split("\n") if ln.strip()]
    except OSError:
        paths = []
    for p in paths:
        backup = OTA_BACKUP_DIR + "/" + p
        try:
            os.stat(backup)
        except OSError:
            continue
        _copy_file(backup, _live(p))

    _cleanup_markers()
    print("OTA: unterbrochener Apply wiederhergestellt ({} Datei(en)).".format(len(paths)))
    return True


def _cleanup_markers():
    for m in (OTA_MARKER_PENDING, OTA_MARKER_APPLIED):
        try:
            os.remove(m)
        except OSError:
            pass
```

> Design note: copy-then-remove instead of `rename` because backups and live may straddle directory levels and MicroPython's littlefs `rename` semantics across nested paths are less predictable than an explicit copy. Files are tiny, so the cost is negligible. The `pending` marker lists the exact files, so recovery is deterministic.

- [ ] **Step 4: Run tests to verify they pass**

Run: `python -m unittest tests.test_ota_apply -v`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add smart-box/ota.py smart-box/tests/test_ota_apply.py
git commit -m "[firmware] Add app-code apply with backup + crash recovery"
```

---

## Task 6: Probation state + rollback decision (`probation_check`, `confirm_boot_healthy`, `begin_probation`)

**Files:**
- Modify: `smart-box/ota.py`
- Create: `smart-box/tests/test_ota_rollback.py`

- [ ] **Step 1: Write the failing test**

Create `smart-box/tests/test_ota_rollback.py`:

```python
from tests import _stubs
import os
import shutil
import tempfile
import unittest
import ota


class OtaRollbackTest(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp()
        ota.OTA_STATE_PATH = os.path.join(self.root, "ota_state.json")
        ota.OTA_BACKUP_DIR = os.path.join(self.root, "ota_backup")
        ota.OTA_MARKER_PENDING = os.path.join(self.root, "ota_pending")
        ota.OTA_MARKER_APPLIED = os.path.join(self.root, "ota_applied")
        # eine Backup-Datei, damit Rollback etwas zurückspielen kann
        ota._ensure_dir(ota.OTA_BACKUP_DIR)
        with open(os.path.join(ota.OTA_BACKUP_DIR, "main.py"), "wb") as f:
            f.write(b"OLD\n")
        with open(os.path.join(self.root, "main.py"), "wb") as f:
            f.write(b"NEW\n")

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def test_begin_probation_writes_state(self):
        ota.begin_probation("APP", "0.7", ["main.py"])
        st = ota._load_state()
        self.assertEqual(st["phase"], "probation")
        self.assertEqual(st["version"], "0.7")
        self.assertEqual(st["boot_attempts"], 0)

    def test_confirm_clears_probation_and_deletes_backup(self):
        ota.begin_probation("APP", "0.7", ["main.py"])
        report = ota.confirm_boot_healthy()
        self.assertEqual(report, ("APPLIED", "0.7"))
        self.assertEqual(ota._load_state()["phase"], "idle")
        self.assertFalse(os.path.exists(ota.OTA_BACKUP_DIR))

    def test_confirm_noop_when_idle(self):
        self.assertIsNone(ota.confirm_boot_healthy())

    def test_probation_check_increments_until_rollback(self):
        ota.begin_probation("APP", "0.7", ["main.py"])
        # erste zwei Boots: weiter in Probation, kein Rollback
        self.assertIsNone(ota.probation_check(live_root=self.root))
        self.assertIsNone(ota.probation_check(live_root=self.root))
        # dritter Boot überschreitet MAX_PROBATION_BOOTS → Rollback
        report = ota.probation_check(live_root=self.root)
        self.assertEqual(report, ("ROLLED_BACK", "0.7"))
        # Live wurde aus Backup zurückgespielt, Zustand idle
        with open(os.path.join(self.root, "main.py"), "rb") as f:
            self.assertEqual(f.read(), b"OLD\n")
        self.assertEqual(ota._load_state()["phase"], "idle")


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run it to verify it fails**

Run: `python -m unittest tests.test_ota_rollback -v`
Expected: FAIL — state functions missing.

- [ ] **Step 3: Implement state + probation logic**

Append to `smart-box/ota.py`:

```python
def _load_state():
    """Liest userconfig/ota_state.json. Default: idle, wenn nicht vorhanden."""
    try:
        with open(OTA_STATE_PATH, "r") as f:
            return json.load(f)
    except (OSError, ValueError):
        return {"phase": "idle", "version": "", "type": "", "boot_attempts": 0}


def _save_state(state):
    try:
        with open(OTA_STATE_PATH, "w") as f:
            json.dump(state, f)
    except OSError as e:
        print("OTA-Zustand konnte nicht gespeichert werden:", e)


def begin_probation(ota_type, version, paths):
    """Markiert eine frisch angewandte Version als 'auf Probe'. Vor dem Reboot aufrufen."""
    _save_state({"phase": "probation", "type": ota_type,
                 "version": version, "boot_attempts": 0})


def confirm_boot_healthy():
    """
    Wird nach erfolgreichem MQTT-Connect aufgerufen. Beendet die Probezeit:
    Backup löschen, Zustand auf idle. Gibt ("APPLIED", version) zurück, wenn eine
    Probezeit bestätigt wurde, sonst None.
    Für FIRMWARE zusätzlich esp32.Partition.mark_app_valid_cancel_rollback().
    """
    st = _load_state()
    if st.get("phase") != "probation":
        return None
    version = st.get("version", "")
    if st.get("type") == "FIRMWARE":
        try:
            import esp32
            esp32.Partition.mark_app_valid_cancel_rollback()
        except Exception as e:
            print("Partition-Bestätigung fehlgeschlagen:", e)
    _rmtree(OTA_BACKUP_DIR)
    _save_state({"phase": "idle", "version": version,
                 "type": st.get("type", ""), "boot_attempts": 0})
    print("OTA: Version {} bestätigt (gesund).".format(version))
    return ("APPLIED", version)


def probation_check(live_root=""):
    """
    Wird beim Boot aufgerufen (vor dem Watchdog), NACH recover_interrupted_apply().
    Zählt Boot-Versuche während der Probezeit. Übersteigt der Zähler
    MAX_PROBATION_BOOTS, wird automatisch auf die Backup-Version zurückgerollt.
    Gibt ("ROLLED_BACK", version) zurück, wenn zurückgerollt wurde, sonst None.
    """
    st = _load_state()
    if st.get("phase") != "probation":
        return None

    st["boot_attempts"] = st.get("boot_attempts", 0) + 1
    if st["boot_attempts"] <= MAX_PROBATION_BOOTS:
        _save_state(st)
        print("OTA: Probe-Boot {}/{}".format(st["boot_attempts"], MAX_PROBATION_BOOTS))
        return None

    # Zu viele Fehlversuche → Rollback auf Backup
    version = st.get("version", "")
    paths = []
    try:
        for name in os.listdir(OTA_BACKUP_DIR):
            paths.append(name)
    except OSError:
        pass
    _restore_backup(live_root)
    _save_state({"phase": "idle", "version": version,
                 "type": st.get("type", ""), "boot_attempts": 0})
    print("OTA: automatischer Rollback nach {} Fehlversuchen.".format(MAX_PROBATION_BOOTS))
    return ("ROLLED_BACK", version)


def _restore_backup(live_root=""):
    """Spielt alle Dateien aus OTA_BACKUP_DIR rekursiv über die Live-Dateien zurück."""
    def _walk(rel):
        full = OTA_BACKUP_DIR + ("/" + rel if rel else "")
        try:
            mode = os.stat(full)[0]
        except OSError:
            return
        if mode & 0x4000:
            for name in os.listdir(full):
                _walk(rel + "/" + name if rel else name)
        else:
            dst = (live_root + "/" + rel) if live_root else rel
            _copy_file(full, dst)
    _walk("")
    _rmtree(OTA_BACKUP_DIR)
```

> Note: `_restore_backup` walks the backup tree so nested paths (`boards/...`) are restored too; the flat `os.listdir` in `probation_check` is only used to log/version, not to drive restore.

- [ ] **Step 4: Run tests to verify they pass**

Run: `python -m unittest tests.test_ota_rollback -v`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add smart-box/ota.py smart-box/tests/test_ota_rollback.py
git commit -m "[firmware] Add probation state and automatic rollback decision"
```

---

## Task 7: `ota.handle_command` orchestration (APP path + status phases)

**Files:**
- Modify: `smart-box/ota.py`
- Create: `smart-box/tests/test_ota_handle.py`

- [ ] **Step 1: Write the failing test**

Create `smart-box/tests/test_ota_handle.py`:

```python
from tests import _stubs
import os
import json
import hashlib
import shutil
import tempfile
import unittest
import ota


class OtaHandleTest(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp()
        ota.OTA_STAGING_DIR = os.path.join(self.root, "ota_staging")
        ota.OTA_BACKUP_DIR  = os.path.join(self.root, "ota_backup")
        ota.OTA_MARKER_PENDING = os.path.join(self.root, "ota_pending")
        ota.OTA_MARKER_APPLIED = os.path.join(self.root, "ota_applied")
        ota.OTA_STATE_PATH  = os.path.join(self.root, "ota_state.json")
        ota._busy = False
        self.body = b"print('v7')\n"
        self.manifest = {"appVersion": "0.7", "files": [
            {"path": "main.py", "sha256": hashlib.sha256(self.body).hexdigest(),
             "size": len(self.body)}]}
        ota.http_get_json = lambda url: json.loads(json.dumps(self.manifest))
        def fake_stream(url, on_chunk, wdt=None):
            on_chunk(self.body)
        ota.http_stream = fake_stream
        # existierende Live-Datei
        with open(os.path.join(self.root, "main.py"), "wb") as f:
            f.write(b"OLD\n")
        self.statuses = []
        self.reset_called = []

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def _publish(self, phase, version, progress=0, detail=""):
        self.statuses.append((phase, version))

    def _cmd(self, **over):
        base = {"type": "APP", "version": "0.7", "url": "http://srv/api/ota/app/0.7",
                "sha256": "", "size": 0}
        base.update(over)
        return json.dumps(base).encode()

    def test_app_update_happy_path_stages_applies_and_reboots(self):
        ota.handle_command(self._cmd(), self._publish, wdt=None,
                           reset=lambda: self.reset_called.append(True),
                           live_root=self.root)
        phases = [p for p, _ in self.statuses]
        self.assertEqual(phases, ["DOWNLOADING", "VERIFYING", "APPLYING"])
        # Staging über Live kopiert
        with open(os.path.join(self.root, "main.py"), "rb") as f:
            self.assertEqual(f.read(), self.body)
        # Probation gesetzt + Reboot ausgelöst
        self.assertEqual(ota._load_state()["phase"], "probation")
        self.assertTrue(self.reset_called)

    def test_app_update_reports_failed_on_bad_hash(self):
        self.manifest["files"][0]["sha256"] = "00" * 32
        ota.handle_command(self._cmd(), self._publish, wdt=None,
                           reset=lambda: self.reset_called.append(True),
                           live_root=self.root)
        self.assertIn("FAILED", [p for p, _ in self.statuses])
        # Live unverändert, kein Reboot
        with open(os.path.join(self.root, "main.py"), "rb") as f:
            self.assertEqual(f.read(), b"OLD\n")
        self.assertFalse(self.reset_called)

    def test_busy_rejects_second_command(self):
        ota._busy = True
        ota.handle_command(self._cmd(), self._publish, wdt=None,
                           reset=lambda: self.reset_called.append(True),
                           live_root=self.root)
        self.assertFalse(self.reset_called)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run it to verify it fails**

Run: `python -m unittest tests.test_ota_handle -v`
Expected: FAIL — `handle_command` missing / wrong signature.

- [ ] **Step 3: Implement `handle_command`**

Append to `smart-box/ota.py`:

```python
def _default_reset():
    import machine
    machine.reset()


def handle_command(payload_bytes, publish_status, wdt=None,
                   reset=_default_reset, live_root=""):
    """
    Einstiegspunkt aus dem MQTT-Router. Verarbeitet einen /ota-Befehl:
      APP:      download → verify → apply → begin_probation → reset
      FIRMWARE: siehe _do_firmware_update (Task 9)

    publish_status(phase, version, progress=0, detail="") meldet den Fortschritt
    zurück (injiziert, damit ota.py nicht von mqttutils abhängt → kein Importzyklus).
    Bei Fehler wird FAILED gemeldet und der Live-Code bleibt unangetastet.
    """
    global _busy
    if _busy:
        print("OTA bereits aktiv – Befehl ignoriert.")
        return
    try:
        cmd = parse_command(payload_bytes)
    except ValueError as e:
        print("OTA-Befehl ungültig:", e)
        return

    _busy = True
    version = cmd["version"]
    try:
        if cmd["type"] == "APP":
            publish_status("DOWNLOADING", version, 0, "")
            manifest = download_app(cmd["url"], wdt)
            publish_status("VERIFYING", version, 50, "")
            # download_app hat bereits jede Datei verifiziert; hier nur Phasenmeldung
            publish_status("APPLYING", version, 80, "")
            paths = [e.get("path", "") for e in manifest.get("files", []) if e.get("path")]
            apply_app(manifest, live_root)
            begin_probation("APP", version, paths)
            reset()
        elif cmd["type"] == "FIRMWARE":
            _do_firmware_update(cmd, publish_status, wdt, reset)
    except OtaError as e:
        print("OTA fehlgeschlagen:", e)
        publish_status("FAILED", version, 0, str(e))
        _rmtree(OTA_STAGING_DIR)
    except Exception as e:
        print("OTA unerwarteter Fehler:", e)
        publish_status("FAILED", version, 0, str(e))
        _rmtree(OTA_STAGING_DIR)
    finally:
        _busy = False
        gc.collect()
```

Also add a temporary stub for the firmware path so the module imports cleanly before Task 9 implements it. Append:

```python
def _do_firmware_update(cmd, publish_status, wdt=None, reset=_default_reset):
    """Platzhalter – vollständig implementiert in Task 9."""
    raise OtaError("Firmware-OTA noch nicht implementiert")
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `python -m unittest tests.test_ota_handle -v`
Expected: PASS (3 tests).

- [ ] **Step 5: Run the full suite**

Run: `python -m unittest discover -s tests -t . -v`
Expected: PASS (all existing + new tests).

- [ ] **Step 6: Commit**

```bash
git add smart-box/ota.py smart-box/tests/test_ota_handle.py
git commit -m "[firmware] Add ota.handle_command orchestration for app updates"
```

---

## Task 8: MQTT wiring (subscribe `/ota`, route it, status publisher, watchdog setter)

**Files:**
- Modify: `smart-box/mqttutils.py`
- Modify: `smart-box/tests/test_publish.py` (status publisher) and a new routing test

- [ ] **Step 1: Write the failing tests**

Create `smart-box/tests/test_ota_routing.py`:

```python
from tests import _stubs
import json
import unittest
import mqttutils


class OtaRoutingTest(unittest.TestCase):
    def setUp(self):
        mqttutils._mqtt_client = _stubs.umqtt_simple.MQTTClient("box", "broker")
        mqttutils._client_id = "aabbccddeeff"

    def test_publish_ota_status_payload(self):
        ok = mqttutils.publish_ota_status("aabbccddeeff", "DOWNLOADING", "0.7", 10, "x")
        self.assertTrue(ok)
        topic, payload = mqttutils._mqtt_client.published[-1]
        self.assertEqual(topic, "smartboxes/aabbccddeeff/ota/status")
        d = json.loads(payload)
        self.assertEqual(d["phase"], "DOWNLOADING")
        self.assertEqual(d["version"], "0.7")
        self.assertEqual(d["progress"], 10)

    def test_ota_topic_routes_to_handler(self):
        called = {}
        import ota
        orig = ota.handle_command
        ota.handle_command = lambda *a, **k: called.setdefault("hit", True)
        try:
            mqttutils.message_callback(b"smartboxes/aabbccddeeff/ota",
                                       b'{"type":"APP","version":"0.7","url":"http://x/y"}')
        finally:
            ota.handle_command = orig
        self.assertTrue(called.get("hit"))


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run it to verify it fails**

Run: `python -m unittest tests.test_ota_routing -v`
Expected: FAIL — `publish_ota_status` missing; `/ota` not routed.

- [ ] **Step 3: Add `set_watchdog`, `publish_ota_status`, subscribe + route**

In `smart-box/mqttutils.py`:

(a) Add a module global near the others (around line 27):

```python
_wdt = None            # vom main gesetzt, für OTA-Download-Watchdog-Feeding
```

(b) Add a setter after `save_device_config` (around line 103):

```python
def set_watchdog(wdt):
    """Reicht den Watchdog aus main.py ins MQTT-Modul, damit OTA ihn füttern kann."""
    global _wdt
    _wdt = wdt
```

(c) In `connect_mqtt`, add the OTA subscription. After `config_topic = ...` (line 355) add:

```python
    ota_topic = "smartboxes/{}/ota".format(client_id)
```

and after `client.subscribe(config_topic)` (line 365) add:

```python
        client.subscribe(ota_topic)
```

and update the print to mention it (optional).

(d) In `message_callback`, route `/ota` at the top of the `try` (right after the `/config` branch, around line 270):

```python
        if topic_str.endswith("/ota"):
            import ota
            ota.handle_command(
                msg,
                lambda phase, version, progress=0, detail="":
                    publish_ota_status(_client_id, phase, version, progress, detail),
                _wdt)
            return
```

(e) Add the status publisher near `publish_heartbeat` (end of file):

```python
def publish_ota_status(client_id, phase, version, progress=0, detail=""):
    """
    Meldet den OTA-Fortschritt an das Backend.

    Topic:   smartboxes/{mac}/ota/status
    Payload: { "version", "phase", "progress", "detail" }
    """
    if _mqtt_client is None:
        print("MQTT-Verbindung nicht verfügbar – OTA-Status nicht möglich.")
        return False
    try:
        topic = "smartboxes/{}/ota/status".format(client_id)
        payload = json.dumps({"version": version, "phase": phase,
                              "progress": progress, "detail": detail})
        _mqtt_client.publish(topic, payload)
        print("OTA-Status gesendet:", phase, version)
        return True
    except Exception as e:
        print("OTA-Status Fehler:", e)
        return False
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `python -m unittest tests.test_ota_routing -v`
Expected: PASS (2 tests).

- [ ] **Step 5: Run the full suite**

Run: `python -m unittest discover -s tests -t . -v`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add smart-box/mqttutils.py smart-box/tests/test_ota_routing.py
git commit -m "[firmware] Wire /ota subscribe, routing and status publishing"
```

---

## Task 9: Firmware OTA via `esp32.Partition` (hardware-verified, stub-tested wrapper)

**Files:**
- Modify: `smart-box/ota.py` (replace the `_do_firmware_update` placeholder)
- Modify: `smart-box/tests/_stubs.py` (add `esp32.Partition` fake)
- Create: `smart-box/tests/test_ota_firmware.py`

- [ ] **Step 1: Add an `esp32` fake to the stubs**

Append to `smart-box/tests/_stubs.py`:

```python
# --- esp32.Partition (Firmware-A/B-OTA; nur Logik wird auf dem Host getestet) ---
esp32 = types.ModuleType("esp32")

class Partition:
    RUNNING = 0
    # Klassen-Spies für Tests
    booted = []
    validated = []
    def __init__(self, which):
        self.which = which
    def get_next_update(self):
        return Partition(99)  # "andere" Partition
    def ioctl(self, cmd, arg):
        # 4 = Anzahl Blöcke, 5 = Blockgröße (vereinfachte Werte)
        return 4096 if cmd == 4 else 4096
    def writeblocks(self, block_num, buf):
        Partition.written = getattr(Partition, "written", 0) + len(buf)
    def set_boot(self):
        Partition.booted.append(self.which)
    @staticmethod
    def mark_app_valid_cancel_rollback():
        Partition.validated.append(True)

esp32.Partition = Partition
sys.modules["esp32"] = esp32
```

- [ ] **Step 2: Write the failing test**

Create `smart-box/tests/test_ota_firmware.py`:

```python
from tests import _stubs
import json
import unittest
import ota


class OtaFirmwareTest(unittest.TestCase):
    def setUp(self):
        ota._busy = False
        _stubs.esp32.Partition.booted = []
        _stubs.esp32.Partition.written = 0
        self.statuses = []
        self.reset_called = []
        # Stream liefert ein paar Bytes "Firmware"
        ota.http_stream = lambda url, on_chunk, wdt=None: on_chunk(b"\x00" * 2048)

    def _publish(self, phase, version, progress=0, detail=""):
        self.statuses.append(phase)

    def test_firmware_writes_partition_sets_boot_and_resets(self):
        cmd = {"type": "FIRMWARE", "version": "mp-1.24",
               "url": "http://srv/api/ota/firmware/mp-1.24.bin",
               "sha256": "", "size": 2048}
        ota._do_firmware_update(cmd, self._publish, wdt=None,
                                reset=lambda: self.reset_called.append(True))
        self.assertTrue(_stubs.esp32.Partition.written >= 2048)
        self.assertTrue(_stubs.esp32.Partition.booted)   # set_boot wurde aufgerufen
        self.assertTrue(self.reset_called)
        self.assertIn("APPLYING", self.statuses)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 3: Replace the placeholder `_do_firmware_update`**

In `smart-box/ota.py`, replace the placeholder body with:

```python
def _do_firmware_update(cmd, publish_status, wdt=None, reset=_default_reset):
    """
    Schreibt ein neues MicroPython-Image in die inaktive OTA-Partition und bootet hinein.
    Die ESP-IDF-Bootloader-Logik rollt automatisch zurück, falls die neue Firmware sich
    beim nächsten Boot nicht via mark_app_valid_cancel_rollback() bestätigt
    (das macht confirm_boot_healthy() nach erfolgreichem MQTT-Connect).

    Hardware-Pfad: auf dem ESP32-S3 manuell verifiziert. Host-Tests prüfen nur die
    Aufruf-Reihenfolge über den esp32-Stub.
    """
    import esp32
    version = cmd["version"]
    publish_status("DOWNLOADING", version, 0, "")

    part = esp32.Partition(esp32.Partition.RUNNING).get_next_update()
    block_size = part.ioctl(5, 0) or 4096

    publish_status("APPLYING", version, 50, "")
    state = {"block": 0, "buf": bytearray()}

    def _on_chunk(chunk):
        state["buf"].extend(chunk)
        # In Blockgrösse-Einheiten schreiben
        while len(state["buf"]) >= block_size:
            part.writeblocks(state["block"], bytes(state["buf"][:block_size]))
            del state["buf"][:block_size]
            state["block"] += 1
            if wdt is not None:
                wdt.feed()

    http_stream(cmd["url"], _on_chunk, wdt)

    # Restpuffer auf Blockgrösse auffüllen und schreiben
    if state["buf"]:
        pad = block_size - (len(state["buf"]) % block_size)
        if pad != block_size:
            state["buf"].extend(b"\xff" * pad)
        part.writeblocks(state["block"], bytes(state["buf"]))

    part.set_boot()
    # Probezeit für die Firmware aktivieren (Bestätigung via confirm_boot_healthy)
    begin_probation("FIRMWARE", version, [])
    reset()
```

> Hardware note: on the real ESP32-S3, verify the sha256 of the written image by reading partitions back if feasible, and confirm the MicroPython build uses an OTA partition table. The host test only locks the call sequence (write → set_boot → reset).

- [ ] **Step 4: Run tests to verify they pass**

Run: `python -m unittest tests.test_ota_firmware -v`
Expected: PASS (1 test).

- [ ] **Step 5: Run the full suite**

Run: `python -m unittest discover -s tests -t . -v`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add smart-box/ota.py smart-box/tests/_stubs.py smart-box/tests/test_ota_firmware.py
git commit -m "[firmware] Add esp32.Partition firmware OTA path"
```

---

## Task 10: `main.py` boot wiring (recovery, probation, watchdog setter, confirm)

**Files:**
- Modify: `smart-box/main.py`

> This task touches the hardware-only boot path. There is no host test for `main.py` (consistent with the existing project: `main.py` is verified manually on the Pico/ESP32). The logic it calls is already covered by Tasks 5-7. Keep the changes minimal and exactly as below.

- [ ] **Step 1: Import ota and run boot-time recovery/probation before the watchdog**

In `smart-box/main.py`, add `import ota` to the import block (after line 25-26). Then, inside the `else:` branch where WiFi connected, **before** `client = connect_mqtt(...)` (around line 78), add the boot supervisor. Place it right after the GPIO-from-saved-config block (after line 76):

```python
            # --- OTA Boot-Supervisor (läuft VOR dem Watchdog) ---
            # 1. Unterbrochenen Apply (Stromverlust) aus Backup wiederherstellen
            ota.recover_interrupted_apply()
            # 2. Probezeit prüfen: zu viele Fehlversuche → automatischer Rollback
            _ota_boot_report = ota.probation_check()
```

- [ ] **Step 2: Pass the watchdog to the MQTT module and confirm a healthy boot**

After the watchdog is created (`wdt = machine.WDT(...)`, line 101), add:

```python
            # OTA-Modul den Watchdog geben (für Download-Feeding)
            from mqttutils import set_watchdog, publish_ota_status
            set_watchdog(wdt)

            # Frisch angewandte Version als gesund bestätigen (beendet Probezeit)
            _confirm = ota.confirm_boot_healthy()
            if _confirm:
                publish_ota_status(CLIENT_ID, _confirm[0], _confirm[1])
            # Falls beim Boot zurückgerollt wurde, jetzt (nach MQTT-Connect) melden
            if _ota_boot_report:
                publish_ota_status(CLIENT_ID, _ota_boot_report[0], _ota_boot_report[1])
```

> Placement detail: `publish_ota_status` needs the MQTT client, which exists after `connect_mqtt` (line 84) and `publish_discovery` (line 91). The watchdog is created at line 101, which is after the connect — so this block (steps 2) sits correctly after line 101 and after a live MQTT connection. `_ota_boot_report` was computed in step 1 before connect and is reported here once the link is up.

- [ ] **Step 3: Manual hardware verification (no host test)**

On a real XIAO ESP32-S3:
1. Flash the firmware, confirm normal boot + MQTT connect (discovery shows `appVersion` + `firmwareVersion`).
2. Publish a valid APP `/ota` command pointing at a backend bundle; confirm `DOWNLOADING → VERIFYING → APPLYING` statuses, reboot, then `APPLIED`.
3. Publish an APP bundle whose code fails to connect MQTT; confirm the box reboots `MAX_PROBATION_BOOTS` times then reports `ROLLED_BACK` and runs the old code.
4. Pull power during APPLY; confirm next boot recovers from backup (old code runs).

- [ ] **Step 4: Commit**

```bash
git add smart-box/main.py
git commit -m "[firmware] Wire OTA boot supervisor, watchdog and health-confirm into main"
```

---

## Task 11: Documentation (CLAUDE.md)

**Files:**
- Modify: `smart-box/CLAUDE.md`

- [ ] **Step 1: Replace the "Firmware Updates" section**

In `smart-box/CLAUDE.md`, replace the final "## Firmware Updates" section (lines ~469-477) with an OTA description that covers: the App Code vs Firmware vocabulary, the `/ota` + `/ota/status` topics, the manifest schema, the `ota.py` module responsibilities, the staging/backup/probation/rollback flow, and the deliberate `import os` exception (only `ota.py` may import `os`, for directory operations). Also update the module list near the top and the MQTT topics table to include `/ota` and `/ota/status`, and update the discovery payload example to include `appVersion`.

- [ ] **Step 2: Verify the full suite once more**

Run: `python -m unittest discover -s tests -t . -v`
Expected: PASS (all tests).

- [ ] **Step 3: Commit**

```bash
git add smart-box/CLAUDE.md
git commit -m "[firmware] Document OTA update mechanism in CLAUDE.md"
```

---

## Out of scope (this plan)

- The **backend** side (artifact storage, `GET /api/ota/...` endpoints, the admin push action, status tracking, UI) — its own plan.
- Signed/authenticated artifacts (SHA-256 integrity only for now; LAN trust assumed).
- Delta updates (always full-bundle replace).
- Real `urequests` streaming robustness (chunked transfer/redirects) — verified on hardware; host tests inject fakes.

## Backend contract this firmware assumes (hand-off to the backend plan)

- MQTT publish `smartboxes/{mac}/ota` with `{ type, version, url, sha256, size }`.
- MQTT subscribe `smartboxes/{mac}/ota/status` for `{ version, phase, progress, detail }`.
- HTTP `GET {url}/manifest.json` → `{ appVersion, files:[{path,sha256,size}] }`.
- HTTP `GET {url}/files/{path}` → raw file bytes.
- For firmware: HTTP `GET {url}` → raw `.bin` bytes.
- Discovery now reports `appVersion` (decide target) and `firmwareVersion`.
```
