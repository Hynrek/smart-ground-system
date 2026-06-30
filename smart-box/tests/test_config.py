from tests import _stubs
import json
import unittest
import mqttutils
import hardware

MAC = "aabbccddeeff"
CFG_TOPIC = ("smartboxes/%s/config" % MAC).encode()
CFG_ACK = "smartboxes/%s/config/ack" % MAC


def _cfg(devices):
    return json.dumps({"devices": devices}).encode()


class ConfigTest(unittest.TestCase):
    def setUp(self):
        _stubs.clock.reset()
        mqttutils._known_devices = set()
        mqttutils._blocked_devices = {}
        mqttutils._client_id = MAC
        mqttutils._mqtt_client = _stubs.umqtt_simple.MQTTClient("box", "broker")
        hardware.gpio_manager.reset()
        # Flash-Schreiben in Tests unterdrücken
        self._saved = []
        self._real_save_device_config = mqttutils.save_device_config
        mqttutils.save_device_config = lambda devs: self._saved.append(devs)

    def tearDown(self):
        mqttutils.save_device_config = self._real_save_device_config

    def _acks(self):
        return [t for (t, _) in mqttutils._mqtt_client.published if t == CFG_ACK]

    def test_valid_config_builds_map_and_acks(self):
        devs = [{"device_id": "a", "alias": "d", "device": "GPIO", "direction": "OUTPUT",
                 "command": "15", "signal_duration_ms": 500, "blocked": False}]
        mqttutils.message_callback(CFG_TOPIC, _cfg(devs))
        self.assertEqual(hardware.gpio_manager.known_ids(), ["a"])
        self.assertEqual(mqttutils._known_devices, {"a"})
        self.assertEqual(len(self._acks()), 1)
        self.assertEqual(len(self._saved), 1)

    def test_empty_config_clears_and_acks(self):
        mqttutils.message_callback(CFG_TOPIC, _cfg([]))
        self.assertEqual(hardware.gpio_manager.known_ids(), [])
        self.assertEqual(len(self._acks()), 1)        # leere Config quittiert (Reset)

    def test_all_devices_fail_no_ack(self):
        # Pin > 40 lässt den Stub-Pin scheitern -> kein Gerät initialisiert
        devs = [{"device_id": "a", "alias": "d", "device": "GPIO", "direction": "OUTPUT",
                 "command": "99", "signal_duration_ms": 0, "blocked": False}]
        mqttutils.message_callback(CFG_TOPIC, _cfg(devs))
        self.assertEqual(hardware.gpio_manager.known_ids(), [])
        self.assertEqual(self._acks(), [])            # kein erfolgreiches Gerät -> kein ACK
        self.assertEqual(self._saved, [])

    def test_stale_admin_block_pruned_on_reconfig(self):
        mqttutils._blocked_devices = {"old": mqttutils.ADMIN_BLOCK_TOKEN}
        mqttutils._known_devices = {"old"}
        devs = [{"device_id": "new", "alias": "d", "device": "GPIO", "direction": "OUTPUT",
                 "command": "15", "signal_duration_ms": 0, "blocked": False}]
        mqttutils.message_callback(CFG_TOPIC, _cfg(devs))
        self.assertNotIn("old", mqttutils._blocked_devices)


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


if __name__ == "__main__":
    unittest.main()
