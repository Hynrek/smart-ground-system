from tests import _stubs
import json
import unittest
import mqttutils

MAC = "aabbccddeeff"


class PublishTest(unittest.TestCase):
    def setUp(self):
        _stubs.clock.reset()
        mqttutils._mqtt_client = _stubs.umqtt_simple.MQTTClient("box", "broker")

    def test_discovery_payload(self):
        ok = mqttutils.publish_discovery(MAC)
        self.assertTrue(ok)
        topic, payload = mqttutils._mqtt_client.published[-1]
        self.assertEqual(topic, "smartboxes/discovery")
        d = json.loads(payload)
        self.assertEqual(d["mac"], MAC)
        self.assertIn("firmwareVersion", d)
        self.assertIn("boxType", d)
        self.assertIn("ip", d)

    def test_heartbeat_payload(self):
        ok = mqttutils.publish_heartbeat(MAC)
        self.assertTrue(ok)
        topic, payload = mqttutils._mqtt_client.published[-1]
        self.assertEqual(topic, "smartboxes/%s/status" % MAC)
        self.assertEqual(json.loads(payload), {"mac": MAC})

    def test_discovery_no_client_returns_false(self):
        mqttutils._mqtt_client = None
        self.assertFalse(mqttutils.publish_discovery(MAC))

    def test_heartbeat_no_client_returns_false(self):
        mqttutils._mqtt_client = None
        self.assertFalse(mqttutils.publish_heartbeat(MAC))

    def test_discovery_signature_has_only_client_id(self):
        import inspect
        self.assertEqual(list(inspect.signature(mqttutils.publish_discovery).parameters),
                         ["client_id"])

    def test_discovery_box_type_from_board(self):
        import board as _board
        ok = mqttutils.publish_discovery(MAC)
        self.assertTrue(ok)
        _, payload = mqttutils._mqtt_client.published[-1]
        d = json.loads(payload)
        self.assertEqual(d["boxType"], _board.BOX_TYPE)

    def test_discovery_reports_app_and_firmware_version(self):
        ok = mqttutils.publish_discovery(MAC)
        self.assertTrue(ok)
        _, payload = mqttutils._mqtt_client.published[-1]
        d = json.loads(payload)
        # appVersion kommt aus firmware_config.json, firmwareVersion aus os.uname()
        self.assertIn("appVersion", d)
        self.assertIn("firmwareVersion", d)
        self.assertTrue(d["firmwareVersion"].startswith("micropython"))


if __name__ == "__main__":
    unittest.main()
