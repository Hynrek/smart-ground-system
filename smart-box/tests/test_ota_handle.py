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
        self._holder = {"b": json.dumps(self.manifest).encode()}
        self.manifest_sha = hashlib.sha256(self._holder["b"]).hexdigest()
        body = self.body
        holder = self._holder

        def fake_stream(url, on_chunk, wdt=None):
            if url.endswith("/manifest.json"):
                on_chunk(holder["b"])
                return
            if url.endswith("/files/main.py"):
                on_chunk(body)
                return
            raise OSError("404 " + url)
        ota.http_stream = fake_stream
        # existierende Live-Datei
        with open(os.path.join(self.root, "main.py"), "wb") as f:
            f.write(b"OLD\n")
        self.statuses = []
        self.reset_called = []

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)
        ota._busy = False  # globalen Zustand für andere Testmodule zurücksetzen

    def _publish(self, phase, version, progress=0, detail=""):
        self.statuses.append((phase, version))

    def _cmd(self, **over):
        base = {"type": "APP", "version": "0.7", "url": "http://srv/api/ota/app/0.7",
                "sha256": self.manifest_sha, "size": 0}
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
        # Datei-Hash im Manifest verfälschen, Manifest-Hash entsprechend neu setzen,
        # damit die Manifest-Prüfung passiert und erst die Datei-Prüfung scheitert.
        self.manifest["files"][0]["sha256"] = "00" * 32
        self._holder["b"] = json.dumps(self.manifest).encode()
        bad_cmd = self._cmd(sha256=hashlib.sha256(self._holder["b"]).hexdigest())
        ota.handle_command(bad_cmd, self._publish, wdt=None,
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
