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
        ota.begin_probation("APP", "0.7")
        st = ota._load_state()
        self.assertEqual(st["phase"], "probation")
        self.assertEqual(st["version"], "0.7")
        self.assertEqual(st["boot_attempts"], 0)

    def test_confirm_clears_probation_and_deletes_backup(self):
        ota.begin_probation("APP", "0.7")
        report = ota.confirm_boot_healthy()
        self.assertEqual(report, ("APPLIED", "0.7"))
        self.assertEqual(ota._load_state()["phase"], "idle")
        self.assertFalse(os.path.exists(ota.OTA_BACKUP_DIR))

    def test_confirm_noop_when_idle(self):
        self.assertIsNone(ota.confirm_boot_healthy())

    def test_probation_check_increments_until_rollback(self):
        ota.begin_probation("APP", "0.7")
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

    def test_rollback_leaves_one_shot_pending_report(self):
        ota.begin_probation("APP", "0.7")
        for _ in range(ota.MAX_PROBATION_BOOTS):
            ota.probation_check(live_root=self.root)
        # Nach dem Rollback liegt ein einmaliger Bericht vor
        first = ota.take_pending_report()
        self.assertEqual(first, ("ROLLED_BACK", "0.7"))
        # Er wird nur einmal geliefert
        self.assertIsNone(ota.take_pending_report())

    def test_take_pending_report_none_when_absent(self):
        self.assertIsNone(ota.take_pending_report())


if __name__ == "__main__":
    unittest.main()
