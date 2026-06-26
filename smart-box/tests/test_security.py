from tests import _stubs
import json
import unittest
import mqttutils
import hardware

MAC = "aabbccddeeff"
CMD_TOPIC = ("smartboxes/%s/command" % MAC).encode()
EXEC_PREFIX = "smartboxes/%s/device/" % MAC


def _cmd(command, device_id, signal=None):
    d = {"command": command, "commandId": "c1", "deviceId": device_id}
    if signal is not None:
        d["signalDurationMs"] = signal
    return json.dumps(d).encode()


def _gpio(device_id, pin, duration=0):
    return {
        "device_id": device_id, "alias": "d", "device": "GPIO",
        "direction": "OUTPUT", "command": str(pin),
        "signal_duration_ms": duration, "blocked": False,
    }


class SecurityTest(unittest.TestCase):
    def setUp(self):
        _stubs.clock.reset()
        mqttutils._known_devices = set()
        mqttutils._blocked_devices = {}
        mqttutils._client_id = MAC
        mqttutils._mqtt_client = _stubs.umqtt_simple.MQTTClient("box", "broker")
        hardware.gpio_manager.reset()

    def _exec_acks(self):
        return [t for (t, _) in mqttutils._mqtt_client.published if t.startswith(EXEC_PREFIX)]

    def _arm(self, device_id, pin=15, duration=0):
        devs = [_gpio(device_id, pin, duration)]
        mqttutils._update_known_devices(devs)
        hardware.gpio_manager.setup(devs)

    def test_unknown_device_rejected_no_ack(self):
        mqttutils.message_callback(CMD_TOPIC, _cmd("ON", "ghost"))
        self.assertEqual(self._exec_acks(), [])

    def test_known_on_acks(self):
        self._arm("a")
        mqttutils.message_callback(CMD_TOPIC, _cmd("ON", "a"))
        self.assertEqual(len(self._exec_acks()), 1)

    def test_busy_on_not_acked_twice(self):
        self._arm("a", duration=500)
        mqttutils.message_callback(CMD_TOPIC, _cmd("ON", "a"))
        mqttutils.message_callback(CMD_TOPIC, _cmd("ON", "a"))  # busy
        self.assertEqual(len(self._exec_acks()), 1)

    def test_admin_block_then_on_ignored(self):
        self._arm("a")
        mqttutils.message_callback(CMD_TOPIC, _cmd("BLOCK", "a"))
        before = len(self._exec_acks())
        mqttutils.message_callback(CMD_TOPIC, _cmd("ON", "a"))
        self.assertEqual(len(self._exec_acks()), before)   # ON erzeugt kein neues ACK

    def test_unblock_restores(self):
        self._arm("a")
        mqttutils.message_callback(CMD_TOPIC, _cmd("BLOCK", "a"))
        mqttutils.message_callback(CMD_TOPIC, _cmd("UNBLOCK", "a"))
        n = len(self._exec_acks())
        mqttutils.message_callback(CMD_TOPIC, _cmd("ON", "a"))
        self.assertEqual(len(self._exec_acks()), n + 1)

    def test_no_autoblock_on_repeated_unknown(self):
        for _ in range(6):
            mqttutils.message_callback(CMD_TOPIC, _cmd("ON", "ghost"))
        self.assertEqual(self._exec_acks(), [])
        self.assertNotIn("ghost", mqttutils._blocked_devices)  # keine Auto-Blockierung

    def test_rate_limit_artifacts_removed(self):
        for name in ("_check_rate_limit", "_record_failed_attempt",
                     "_clear_failed_attempts", "_last_command_time", "_failed_commands"):
            self.assertFalse(hasattr(mqttutils, name), "soll entfernt sein: " + name)


if __name__ == "__main__":
    unittest.main()
