"""Onboarding-Frame-Codec gegen die geteilten Golden-Vektoren (Cross-Language mit smart-ground-node)."""
import json
import os
import unittest

import onboarding_codec as oc
from frame_envelope import TYPE_HELLO, TYPE_ONBOARD_OFFER

FIXTURE = os.path.join(os.path.dirname(__file__), "..", "..", "docs", "espnow", "onboarding-test-vectors.json")


def _load():
    with open(FIXTURE) as f:
        return json.load(f)


@unittest.skipUnless(os.path.exists(FIXTURE),
                     "onboarding-test-vectors.json fehlt (Standalone-Firmware-Clone ohne Monorepo-docs)")
class TestOnboardingCodec(unittest.TestCase):

    def setUp(self):
        self.v = _load()

    def test_build_hello_matches_fixture(self):
        h = self.v["hello"]
        frame = oc.build_hello(bytes.fromhex(h["dest_mac"]), bytes.fromhex(h["src_mac"]),
                               h["frame_id"], h["ttl"], bytes.fromhex(h["box_nonce"]))
        self.assertEqual(frame, bytes.fromhex(h["frame"]))
        self.assertEqual(frame[15], TYPE_HELLO)

    def test_box_nonce_of(self):
        h = self.v["hello"]
        self.assertEqual(oc.box_nonce_of(bytes.fromhex(h["frame"])), bytes.fromhex(h["box_nonce"]))

    def test_build_onboard_offer_matches_fixture(self):
        o = self.v["onboard_offer"]
        frame = oc.build_onboard_offer(
            bytes.fromhex(o["dest_mac"]), bytes.fromhex(o["src_mac"]), o["frame_id"], o["ttl"],
            bytes.fromhex(o["echo_nonce"]), bytes.fromhex(o["token"]), bytes.fromhex(o["fingerprint"]),
            bytes.fromhex(o["ssid"]), bytes.fromhex(o["psk"]), bytes.fromhex(o["url"]))
        self.assertEqual(frame, bytes.fromhex(o["frame"]))
        self.assertEqual(frame[15], TYPE_ONBOARD_OFFER)

    def test_onboard_offer_accessors(self):
        o = self.v["onboard_offer"]
        frame = bytes.fromhex(o["frame"])
        self.assertEqual(oc.echo_nonce_of(frame), bytes.fromhex(o["echo_nonce"]))
        self.assertEqual(oc.token_of(frame), bytes.fromhex(o["token"]))
        self.assertEqual(oc.fingerprint_of(frame), bytes.fromhex(o["fingerprint"]))
        self.assertEqual(oc.ssid_of(frame), bytes.fromhex(o["ssid"]))
        self.assertEqual(oc.psk_of(frame), bytes.fromhex(o["psk"]))
        self.assertEqual(oc.url_of(frame), bytes.fromhex(o["url"]))


if __name__ == "__main__":
    unittest.main()
