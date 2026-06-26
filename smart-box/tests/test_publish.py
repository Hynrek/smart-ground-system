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


if __name__ == "__main__":
    unittest.main()
