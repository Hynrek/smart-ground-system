from tests import _stubs
import json
import unittest
import mqttutils


class OtaRoutingTest(unittest.TestCase):
    def setUp(self):
        mqttutils._mqtt_client = _stubs.umqtt_simple.MQTTClient("box", "broker")
        mqttutils._client_id = "aabbccddeeff"

    def test_publish_ota_status_payload(self):
        ok = mqttutils.publish_ota_status("aabbccddeeff", "DOWNLOADING", "0.7", 10, "x")
        self.assertTrue(ok)
        topic, payload = mqttutils._mqtt_client.published[-1]
        self.assertEqual(topic, "smartboxes/aabbccddeeff/ota/status")
        d = json.loads(payload)
        self.assertEqual(d["phase"], "DOWNLOADING")
        self.assertEqual(d["version"], "0.7")
        self.assertEqual(d["progress"], 10)

    def test_ota_topic_routes_to_handler(self):
        called = {}
        import ota
        orig = ota.handle_command
        ota.handle_command = lambda *a, **k: called.setdefault("hit", True)
        try:
            mqttutils.message_callback(b"smartboxes/aabbccddeeff/ota",
                                       b'{"type":"APP","version":"0.7","url":"http://x/y"}')
        finally:
            ota.handle_command = orig
        self.assertTrue(called.get("hit"))


if __name__ == "__main__":
    unittest.main()
