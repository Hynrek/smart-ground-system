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
