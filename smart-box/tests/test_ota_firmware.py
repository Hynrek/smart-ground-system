from tests import _stubs
import os
import json
import hashlib
import shutil
import tempfile
import unittest
import ota


class OtaFirmwareTest(unittest.TestCase):
    def setUp(self):
        ota._busy = False
        # OTA-Zustand isolieren, damit begin_probation nicht ins echte userconfig/ schreibt
        self.root = tempfile.mkdtemp()
        ota.OTA_STATE_PATH = os.path.join(self.root, "ota_state.json")
        _stubs.esp32.Partition.booted = []
        _stubs.esp32.Partition.written = 0
        self.statuses = []
        self.reset_called = []
        # Stream liefert ein paar Bytes "Firmware"
        ota.http_stream = lambda url, on_chunk, wdt=None: on_chunk(b"\x00" * 2048)

    def tearDown(self):
        ota._busy = False
        shutil.rmtree(self.root, ignore_errors=True)

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

    def test_firmware_verifies_image_hash_and_boots(self):
        body = b"\x00" * 2048
        ota.http_stream = lambda url, on_chunk, wdt=None: on_chunk(body)
        cmd = {"type": "FIRMWARE", "version": "mp-1.24",
               "url": "http://srv/api/ota/firmware/mp-1.24.bin",
               "sha256": hashlib.sha256(body).hexdigest(), "size": 2048}
        ota._do_firmware_update(cmd, self._publish, wdt=None,
                                reset=lambda: self.reset_called.append(True))
        self.assertTrue(_stubs.esp32.Partition.booted)
        self.assertTrue(self.reset_called)

    def test_firmware_rejects_bad_image_hash(self):
        body = b"\x00" * 2048
        ota.http_stream = lambda url, on_chunk, wdt=None: on_chunk(body)
        cmd = {"type": "FIRMWARE", "version": "mp-1.24",
               "url": "http://srv/api/ota/firmware/mp-1.24.bin",
               "sha256": "00" * 32, "size": 2048}
        with self.assertRaises(ota.OtaError):
            ota._do_firmware_update(cmd, self._publish, wdt=None,
                                    reset=lambda: self.reset_called.append(True))
        # set_boot und reset dürfen bei Hash-Fehler NICHT aufgerufen worden sein
        self.assertFalse(_stubs.esp32.Partition.booted)
        self.assertFalse(self.reset_called)


if __name__ == "__main__":
    unittest.main()
