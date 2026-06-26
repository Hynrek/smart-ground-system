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
        mqttutils.save_device_config = lambda devs: self._saved.append(devs)

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


if __name__ == "__main__":
    unittest.main()
