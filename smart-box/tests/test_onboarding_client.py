from tests import _stubs
import unittest
from unittest.mock import patch

import onboarding_client as oclient
import onboarding_codec
from frame_envelope import TYPE_HELLO, TYPE_ONBOARD_OFFER, pack_header

MAC_HEX = "aabbccddee01"
MAC_BYTES = bytes.fromhex(MAC_HEX)
NODE_MAC = bytes.fromhex("30aea41f2b3c")


def _offer_frame(box_nonce, dest_mac=MAC_BYTES, token=b"\x11" * 16, fingerprint=b"\x22" * 32,
                  ssid=b"SmartGround-Node-1", psk=b"provision-pw-123", url=b"https://192.168.4.1:8443"):
    return onboarding_codec.build_onboard_offer(
        dest_mac, NODE_MAC, 1, 1, box_nonce, token, fingerprint, ssid, psk, url)


class OnboardingClientHelloTest(unittest.TestCase):
    def setUp(self):
        self.sent = []
        oclient.radio_send = lambda dest, frame: self.sent.append((dest, frame))
        oclient.radio_poll = lambda: None
        self.client = oclient.OnboardingClient(MAC_HEX, "xiao-esp32s3", "1.0", "{}")

    def tearDown(self):
        oclient.radio_send = oclient._log_only_radio_send
        oclient.radio_poll = oclient._log_only_radio_poll

    def test_tick_sends_hello_immediately_then_backs_off(self):
        self.assertIsNone(self.client.tick(0))
        self.assertEqual(len(self.sent), 1)
        dest, frame = self.sent[0]
        self.assertEqual(dest, oclient.BROADCAST_MAC)
        self.assertEqual(frame[15], TYPE_HELLO)

        # Vor Ablauf des Intervalls: kein zweites HELLO
        self.assertIsNone(self.client.tick(500))
        self.assertEqual(len(self.sent), 1)

        # Nach dem ersten Intervall: zweites HELLO, Intervall wächst (Backoff)
        self.assertIsNone(self.client.tick(oclient.HELLO_INITIAL_INTERVAL_MS))
        self.assertEqual(len(self.sent), 2)

        third_due = oclient.HELLO_INITIAL_INTERVAL_MS + oclient.HELLO_INITIAL_INTERVAL_MS * oclient.HELLO_BACKOFF_FACTOR
        self.assertIsNone(self.client.tick(third_due))
        self.assertEqual(len(self.sent), 3)

    def test_tick_caps_backoff_at_max_interval(self):
        now = 0
        for _ in range(10):
            self.client.tick(now)
            now += oclient.HELLO_MAX_INTERVAL_MS + 1
        self.assertEqual(self.client._hello_interval_ms, oclient.HELLO_MAX_INTERVAL_MS)

    def test_each_hello_uses_a_fresh_nonce(self):
        self.client.tick(0)
        first_nonce = onboarding_codec.box_nonce_of(self.sent[0][1])
        self.client.tick(oclient.HELLO_INITIAL_INTERVAL_MS)
        second_nonce = onboarding_codec.box_nonce_of(self.sent[1][1])
        self.assertNotEqual(first_nonce, second_nonce)


class OnboardingClientOfferFilterTest(unittest.TestCase):
    def setUp(self):
        oclient.radio_send = lambda dest, frame: None
        self.client = oclient.OnboardingClient(MAC_HEX, "xiao-esp32s3", "1.0", "{}")
        self.client.tick(0)  # sendet HELLO, setzt _box_nonce
        self.sent_nonce = self.client._box_nonce

    def tearDown(self):
        oclient.radio_send = oclient._log_only_radio_send
        oclient.radio_poll = oclient._log_only_radio_poll

    def test_ignores_frame_of_wrong_type(self):
        frame = pack_header(MAC_BYTES, NODE_MAC, 1, 1, TYPE_HELLO) + b"\x00" * 8
        oclient.radio_poll = lambda: frame
        self.assertIsNone(self.client.tick(1))

    def test_ignores_offer_addressed_to_another_box(self):
        other_mac = bytes.fromhex("ffeeddccbbaa")
        frame = _offer_frame(self.sent_nonce, dest_mac=other_mac)
        oclient.radio_poll = lambda: frame
        self.assertIsNone(self.client.tick(1))

    def test_ignores_offer_with_stale_or_foreign_nonce(self):
        frame = _offer_frame(b"\x99" * 8)  # nicht die zuletzt gesendete Nonce
        oclient.radio_poll = lambda: frame
        self.assertIsNone(self.client.tick(1))


class OnboardingClientHandleOfferTest(unittest.TestCase):
    def setUp(self):
        oclient.radio_send = lambda dest, frame: None
        self.client = oclient.OnboardingClient(MAC_HEX, "xiao-esp32s3", "1.0", "{}")
        self.client.tick(0)
        self.nonce = self.client._box_nonce
        self.deactivated = []
        self._deactivate_patcher = patch.object(oclient.OnboardingClient, '_deactivate_sta',
                                                 side_effect=lambda *args: self.deactivated.append(True))
        self._deactivate_patcher.start()

    def tearDown(self):
        self._deactivate_patcher.stop()
        oclient.radio_send = oclient._log_only_radio_send
        oclient.radio_poll = oclient._log_only_radio_poll

    def test_ap_join_failure_returns_ap_join_failed_and_leaves_ap(self):
        frame = _offer_frame(self.nonce)
        oclient.radio_poll = lambda: frame
        with patch('onboarding_client.connect_wifi', return_value=False) as mock_join, \
             patch('onboarding_client.box_provisioning.discover_and_provision') as mock_provision:
            result = self.client.tick(1)

        mock_join.assert_called_once_with("SmartGround-Node-1", "provision-pw-123", timeout=oclient.AP_JOIN_TIMEOUT_S)
        mock_provision.assert_not_called()
        self.assertEqual(result.status, "ap_join_failed")
        self.assertTrue(self.deactivated)

    def test_provisioning_failure_returns_provisioning_failed_and_leaves_ap(self):
        frame = _offer_frame(self.nonce)
        oclient.radio_poll = lambda: frame
        with patch('onboarding_client.connect_wifi', return_value=True), \
             patch('onboarding_client.box_provisioning.discover_and_provision',
                   side_effect=RuntimeError("token abgelehnt")):
            result = self.client.tick(1)

        self.assertEqual(result.status, "provisioning_failed")
        self.assertIn("token abgelehnt", result.error)
        self.assertTrue(self.deactivated)

    def test_happy_path_provisions_and_leaves_ap(self):
        frame = _offer_frame(self.nonce, token=bytes.fromhex("deadbeefdeadbeefdeadbeefdeadbeef"))
        oclient.radio_poll = lambda: frame
        with patch('onboarding_client.connect_wifi', return_value=True) as mock_join, \
             patch('onboarding_client.box_provisioning.discover_and_provision',
                   return_value={"kBoxBase64": "a2V5"}) as mock_provision:
            result = self.client.tick(1)

        mock_provision.assert_called_once_with(
            MAC_HEX, "https://192.168.4.1:8443", "deadbeefdeadbeefdeadbeefdeadbeef",
            app_version="1.0", box_type="xiao-esp32s3", capabilities_json="{}")
        self.assertEqual(result.status, "provisioned")
        self.assertEqual(result.k_box_base64, "a2V5")
        self.assertTrue(self.deactivated)

    def test_after_handling_an_offer_hello_backoff_resets(self):
        frame = _offer_frame(self.nonce)
        oclient.radio_poll = lambda: frame
        with patch('onboarding_client.connect_wifi', return_value=False), \
             patch('onboarding_client.box_provisioning.discover_and_provision'):
            self.client.tick(1)
        self.assertEqual(self.client._hello_interval_ms, oclient.HELLO_INITIAL_INTERVAL_MS)
        self.assertEqual(self.client._next_hello_at_ms, 0)


if __name__ == "__main__":
    unittest.main()
