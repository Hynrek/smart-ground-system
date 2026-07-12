from tests import _stubs
import json
import os
import tempfile
import unittest
import device_state


class DeviceConfigPersistenceTest(unittest.TestCase):
    def setUp(self):
        _stubs.clock.reset()
        device_state._firmware_config = None  # Firmware-Config-Cache leeren

    def tearDown(self):
        device_state._firmware_config = None

    def test_save_includes_schema_version(self):
        with tempfile.NamedTemporaryFile(suffix=".json", delete=False, mode='w') as f:
            tmp = f.name
        original = device_state.DEVICE_CONFIG_PATH
        try:
            device_state.DEVICE_CONFIG_PATH = tmp
            device_state.save_device_config([{"device_id": "a"}])
            with open(tmp, 'r') as f:
                data = json.load(f)
            self.assertIn("config_schema_version", data)
            self.assertIsInstance(data["config_schema_version"], str)
            self.assertEqual(len(data["devices"]), 1)
        finally:
            device_state.DEVICE_CONFIG_PATH = original
            os.unlink(tmp)

    def test_load_returns_dict_with_schema_version(self):
        data = {"config_schema_version": "1", "devices": [{"device_id": "a"}]}
        with tempfile.NamedTemporaryFile(suffix=".json", delete=False, mode='w') as f:
            json.dump(data, f)
            tmp = f.name
        original = device_state.DEVICE_CONFIG_PATH
        try:
            device_state.DEVICE_CONFIG_PATH = tmp
            result = device_state.load_device_config()
            self.assertIsNotNone(result)
            self.assertEqual(result["config_schema_version"], "1")
            self.assertEqual(len(result["devices"]), 1)
        finally:
            device_state.DEVICE_CONFIG_PATH = original
            os.unlink(tmp)

    def test_load_returns_none_on_missing_file(self):
        original = device_state.DEVICE_CONFIG_PATH
        device_state.DEVICE_CONFIG_PATH = "nonexistent_xyz_abc.json"
        try:
            result = device_state.load_device_config()
            self.assertIsNone(result)
        finally:
            device_state.DEVICE_CONFIG_PATH = original

    def test_save_load_roundtrip_preserves_schema_version(self):
        with tempfile.NamedTemporaryFile(suffix=".json", delete=False, mode='w') as f:
            tmp = f.name
        original = device_state.DEVICE_CONFIG_PATH
        try:
            device_state.DEVICE_CONFIG_PATH = tmp
            devs = [{"device_id": "b", "alias": "Werfer 1"}]
            device_state.save_device_config(devs)
            result = device_state.load_device_config()
            self.assertIsNotNone(result)
            self.assertEqual(result["devices"], devs)
            self.assertIn("config_schema_version", result)
        finally:
            device_state.DEVICE_CONFIG_PATH = original
            os.unlink(tmp)


class DeviceAuthorizationTest(unittest.TestCase):
    """
    Direkte Unit-Tests für die Autorisierungs-/Block-Logik aus device_state.py.
    Vorher wurde dieses Verhalten nur indirekt über mqttutils.message_callback
    getestet (tests/test_security.py, tests/test_config.py's ConfigTest) — beide
    sind mit der MQTT-Entfernung (Task 12) gelöscht worden. Diese Klasse deckt die
    reine device_state-Logik ab, ohne den (jetzt nicht mehr existierenden) MQTT-
    Transport dazwischenzuschalten.
    """

    def setUp(self):
        device_state._known_devices = set()
        device_state._blocked_devices = {}

    def test_update_known_devices_tracks_ids(self):
        devs = [{"device_id": "a"}, {"device_id": "b"}]
        device_state._update_known_devices(devs)
        self.assertEqual(device_state._known_devices, {"a", "b"})

    def test_update_known_devices_prunes_stale_admin_blocks(self):
        device_state._blocked_devices = {"old": device_state.ADMIN_BLOCK_TOKEN}
        device_state._known_devices = {"old"}
        device_state._update_known_devices([{"device_id": "new"}])
        self.assertNotIn("old", device_state._blocked_devices)
        self.assertEqual(device_state._known_devices, {"new"})

    def test_admin_block_unknown_device_fails(self):
        self.assertFalse(device_state._admin_block_device("ghost"))

    def test_admin_block_then_unblock_roundtrip(self):
        device_state._update_known_devices([{"device_id": "a"}])
        self.assertTrue(device_state._admin_block_device("a"))
        self.assertTrue(device_state._is_device_blocked("a"))
        self.assertTrue(device_state._admin_unblock_device("a"))
        self.assertFalse(device_state._is_device_blocked("a"))

    def test_unblock_unblocked_device_fails(self):
        device_state._update_known_devices([{"device_id": "a"}])
        self.assertFalse(device_state._admin_unblock_device("a"))

    def test_set_watchdog_stores_reference(self):
        sentinel = object()
        device_state.set_watchdog(sentinel)
        self.assertIs(device_state._wdt, sentinel)
        device_state.set_watchdog(None)


if __name__ == "__main__":
    unittest.main()
