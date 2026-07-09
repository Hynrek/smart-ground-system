"""Host tests for TLS + credential wiring in mqttutils.py (Task C)."""
from tests import _stubs
import json
import os
import tempfile
import unittest
import mqttutils

MAC = "aabbccddeeff"
CFG_TOPIC = ("smartboxes/%s/config" % MAC).encode()


def _cfg(devices, mqtt_username=None, mqtt_password=None):
    payload = {"devices": devices}
    if mqtt_username is not None:
        payload["mqttUsername"] = mqtt_username
    if mqtt_password is not None:
        payload["mqttPassword"] = mqtt_password
    return json.dumps(payload).encode()


class ConnectMqttTlsTest(unittest.TestCase):
    def setUp(self):
        mqttutils._ca_cert_data = None
        mqttutils._mqtt_client = None
        mqttutils._mqtt_user = None
        mqttutils._mqtt_password = None
        self._orig_ca_path = mqttutils.CA_CERT_PATH

    def tearDown(self):
        mqttutils.CA_CERT_PATH = self._orig_ca_path
        mqttutils._ca_cert_data = None

    def test_connect_mqtt_defaults_to_tls_port_8883(self):
        client = mqttutils.connect_mqtt(MAC, "broker.local", user="u", password="p")
        self.assertIsNotNone(client)
        self.assertEqual(client.port, 8883)

    def test_connect_mqtt_passes_ssl_and_credentials_through(self):
        client = mqttutils.connect_mqtt(MAC, "broker.local", user="boxuser", password="boxpass")
        self.assertIsNotNone(client)
        self.assertTrue(client.ssl)
        self.assertEqual(client.user, "boxuser")
        self.assertEqual(client.password, "boxpass")

    def test_connect_mqtt_pins_ca_cert_as_cadata(self):
        client = mqttutils.connect_mqtt(MAC, "broker.local", user="u", password="p")
        self.assertIsNotNone(client)
        self.assertIn("cadata", client.ssl_params)
        self.assertIsInstance(client.ssl_params["cadata"], (bytes, bytearray))
        self.assertTrue(client.ssl_params["cadata"].startswith(b"-----BEGIN CERTIFICATE-----"))

    def test_connect_mqtt_requires_cert_verification(self):
        import ussl
        client = mqttutils.connect_mqtt(MAC, "broker.local", user="u", password="p")
        self.assertIsNotNone(client)
        self.assertEqual(client.ssl_params["cert_reqs"], ussl.CERT_REQUIRED)

    def test_connect_mqtt_refuses_without_ca_file(self):
        mqttutils.CA_CERT_PATH = "nonexistent_ca_xyz.crt"
        mqttutils._ca_cert_data = None
        client = mqttutils.connect_mqtt(MAC, "broker.local", user="u", password="p")
        self.assertIsNone(client)
        self.assertIsNone(mqttutils._mqtt_client)

    def test_connect_mqtt_with_no_credentials_still_connects_stub(self):
        # Unprovisionierte Box: user/password=None wird durchgereicht (schlägt gegen den
        # echten gehärteten Broker fehl, aber der Code-Pfad selbst darf nicht crashen).
        client = mqttutils.connect_mqtt(MAC, "broker.local")
        self.assertIsNotNone(client)
        self.assertIsNone(client.user)
        self.assertIsNone(client.password)

    def test_reconnect_mqtt_reuses_stored_credentials(self):
        mqttutils.connect_mqtt(MAC, "broker.local", user="boxuser", password="boxpass")
        client = mqttutils.reconnect_mqtt()
        self.assertIsNotNone(client)
        self.assertEqual(client.user, "boxuser")
        self.assertEqual(client.password, "boxpass")
        self.assertTrue(client.ssl)


class CredentialPersistenceTest(unittest.TestCase):
    def setUp(self):
        _stubs.clock.reset()
        mqttutils._known_devices = set()
        mqttutils._blocked_devices = {}
        mqttutils._client_id = MAC
        mqttutils._mqtt_client = _stubs.umqtt_simple.MQTTClient("box", "broker")
        mqttutils._mqtt_user = "old_user"
        mqttutils._mqtt_password = "old_pass"
        import hardware
        hardware.gpio_manager.reset()

        # Flash-Schreiben der Geräteliste unterdrücken (wie in test_config.py)
        self._real_save_device_config = mqttutils.save_device_config
        mqttutils.save_device_config = lambda devs: None

        # client_config.json auf eine temporäre Datei mit vorhandenem WiFi/Broker-Profil
        # umleiten, um zu prüfen, dass der Merge diese Felder nicht überschreibt.
        fd, self._tmp_path = tempfile.mkstemp(suffix=".json")
        os.close(fd)
        with open(self._tmp_path, 'w') as f:
            json.dump({"client_network_ssid": "MyNet", "client_network_pw": "secret",
                       "broker_ip": "192.168.1.100"}, f)
        self._orig_client_config_path = mqttutils.CLIENT_CONFIG_PATH
        mqttutils.CLIENT_CONFIG_PATH = self._tmp_path

    def tearDown(self):
        mqttutils.save_device_config = self._real_save_device_config
        mqttutils.CLIENT_CONFIG_PATH = self._orig_client_config_path
        mqttutils._mqtt_user = None
        mqttutils._mqtt_password = None
        try:
            os.unlink(self._tmp_path)
        except OSError:
            pass

    def _devs(self):
        return [{"device_id": "a", "alias": "d", "device": "GPIO", "direction": "OUTPUT",
                 "command": "15", "signal_duration_ms": 500, "blocked": False}]

    def test_config_push_with_credentials_persists_and_merges(self):
        mqttutils.message_callback(
            CFG_TOPIC, _cfg(self._devs(), mqtt_username="aabbccddeeff", mqtt_password="s3cr3t"))

        with open(self._tmp_path, 'r') as f:
            data = json.load(f)

        self.assertEqual(data["mqtt_username"], "aabbccddeeff")
        self.assertEqual(data["mqtt_password"], "s3cr3t")
        # Bestehende WiFi-/Broker-Felder bleiben erhalten (Merge, kein Overwrite)
        self.assertEqual(data["client_network_ssid"], "MyNet")
        self.assertEqual(data["client_network_pw"], "secret")
        self.assertEqual(data["broker_ip"], "192.168.1.100")

    def test_config_push_with_credentials_updates_module_globals(self):
        mqttutils.message_callback(
            CFG_TOPIC, _cfg(self._devs(), mqtt_username="newuser", mqtt_password="newpass"))
        self.assertEqual(mqttutils._mqtt_user, "newuser")
        self.assertEqual(mqttutils._mqtt_password, "newpass")

    def test_config_push_without_credentials_does_not_touch_client_config(self):
        before = os.stat(self._tmp_path).st_mtime_ns
        with open(self._tmp_path, 'r') as f:
            before_data = json.load(f)

        mqttutils.message_callback(CFG_TOPIC, _cfg(self._devs()))

        with open(self._tmp_path, 'r') as f:
            after_data = json.load(f)
        after = os.stat(self._tmp_path).st_mtime_ns

        self.assertEqual(before_data, after_data)
        self.assertEqual(before, after)
        # Modul-Globals unverändert (kein versehentliches Löschen bestehender Credentials)
        self.assertEqual(mqttutils._mqtt_user, "old_user")
        self.assertEqual(mqttutils._mqtt_password, "old_pass")

    def test_config_push_with_only_username_does_not_persist(self):
        # Beide Felder müssen vorhanden sein - ein Teil-Payload wird ignoriert statt
        # ein halbes Credential-Paar zu speichern.
        with open(self._tmp_path, 'r') as f:
            before_data = json.load(f)

        mqttutils.message_callback(
            CFG_TOPIC, _cfg(self._devs(), mqtt_username="onlyuser"))

        with open(self._tmp_path, 'r') as f:
            after_data = json.load(f)
        self.assertEqual(before_data, after_data)
        self.assertNotIn("mqtt_username", after_data)


if __name__ == "__main__":
    unittest.main()
