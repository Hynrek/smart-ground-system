from tests import _stubs
import json
import os
import unittest

import operational_codec

_FIXTURE_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "..", "docs", "espnow", "operational-test-vectors.json",
)

if not os.path.isfile(_FIXTURE_PATH):
    raise unittest.SkipTest(
        "Betriebs-Frame-Test-Vektoren nicht gefunden unter " + _FIXTURE_PATH +
        " — dieses Modul braucht das smart-box-Repo als Sub-Checkout im"
        " smart-ground-Monorepo. Bei einem eigenstaendigen Checkout von"
        " smartground-firmware werden diese Tests uebersprungen."
    )


def _load_fixture():
    with open(_FIXTURE_PATH) as f:
        return json.load(f)


class ConfigTest(unittest.TestCase):
    def test_build_config_matches_fixture_frame(self):
        f = _load_fixture()
        v = f["config"]
        d = v["decoded"]
        k_s = bytes.fromhex(f["constants"]["k_s"])

        frame = operational_codec.build_config(
            bytes.fromhex(v["dest_mac"]), bytes.fromhex(v["src_mac"]), v["frame_id"], v["ttl"],
            bytes.fromhex(v["counter_nonce"]), k_s,
            bytes.fromhex(d["device_id"]), d["device_index"], d["device_count"], d["alias"],
            d["device_type"], d["direction"], d["command"], d["signal_duration_ms"], d["blocked"],
        )

        self.assertEqual(frame, bytes.fromhex(v["frame"]))

    def test_parse_config_extracts_fixture_fields(self):
        f = _load_fixture()
        v = f["config"]
        d = v["decoded"]
        k_s = bytes.fromhex(f["constants"]["k_s"])
        frame = bytes.fromhex(v["frame"])

        body = operational_codec.parse_config(frame, k_s)

        self.assertEqual(body["device_id"], bytes.fromhex(d["device_id"]))
        self.assertEqual(body["device_index"], d["device_index"])
        self.assertEqual(body["device_count"], d["device_count"])
        self.assertEqual(body["alias"], d["alias"])
        self.assertEqual(body["device_type"], d["device_type"])
        self.assertEqual(body["direction"], d["direction"])
        self.assertEqual(body["command"], d["command"])
        self.assertEqual(body["signal_duration_ms"], d["signal_duration_ms"])
        self.assertEqual(body["blocked"], d["blocked"])


class ConfigAckTest(unittest.TestCase):
    def test_build_config_ack_matches_fixture_and_verify_accepts(self):
        f = _load_fixture()
        v = f["config_ack"]
        k_s = bytes.fromhex(f["constants"]["k_s"])

        frame = operational_codec.build_config_ack(
            bytes.fromhex(v["dest_mac"]), bytes.fromhex(v["src_mac"]), v["frame_id"], v["ttl"],
            bytes.fromhex(v["counter_nonce"]), k_s,
        )

        self.assertEqual(frame, bytes.fromhex(v["frame"]))
        self.assertTrue(operational_codec.verify_config_ack(frame, k_s))

    def test_verify_config_ack_rejects_tampered_tag(self):
        f = _load_fixture()
        v = f["config_ack"]
        k_s = bytes.fromhex(f["constants"]["k_s"])
        tampered = bytearray(bytes.fromhex(v["frame"]))
        tampered[-1] ^= 0x01

        self.assertFalse(operational_codec.verify_config_ack(bytes(tampered), k_s))


class CommandTest(unittest.TestCase):
    def test_build_command_matches_fixture_frame(self):
        f = _load_fixture()
        v = f["command"]
        d = v["decoded"]
        k_s = bytes.fromhex(f["constants"]["k_s"])

        frame = operational_codec.build_command(
            bytes.fromhex(v["dest_mac"]), bytes.fromhex(v["src_mac"]), v["frame_id"], v["ttl"],
            bytes.fromhex(v["counter_nonce"]), k_s,
            bytes.fromhex(d["command_id"]), bytes.fromhex(d["device_id"]), d["command"], d["signal_duration_ms"],
        )

        self.assertEqual(frame, bytes.fromhex(v["frame"]))

    def test_parse_command_extracts_fixture_fields(self):
        f = _load_fixture()
        v = f["command"]
        d = v["decoded"]
        k_s = bytes.fromhex(f["constants"]["k_s"])
        frame = bytes.fromhex(v["frame"])

        body = operational_codec.parse_command(frame, k_s)

        self.assertEqual(body["command_id"], bytes.fromhex(d["command_id"]))
        self.assertEqual(body["device_id"], bytes.fromhex(d["device_id"]))
        self.assertEqual(body["command"], d["command"])
        self.assertEqual(body["signal_duration_ms"], d["signal_duration_ms"])


class ExecutedTest(unittest.TestCase):
    def test_build_executed_matches_fixture_frame(self):
        f = _load_fixture()
        v = f["executed"]
        d = v["decoded"]
        k_s = bytes.fromhex(f["constants"]["k_s"])

        frame = operational_codec.build_executed(
            bytes.fromhex(v["dest_mac"]), bytes.fromhex(v["src_mac"]), v["frame_id"], v["ttl"],
            bytes.fromhex(v["counter_nonce"]), k_s,
            bytes.fromhex(d["command_id"]), bytes.fromhex(d["device_id"]),
        )

        self.assertEqual(frame, bytes.fromhex(v["frame"]))

    def test_parse_executed_extracts_fixture_fields(self):
        f = _load_fixture()
        v = f["executed"]
        d = v["decoded"]
        k_s = bytes.fromhex(f["constants"]["k_s"])
        frame = bytes.fromhex(v["frame"])

        body = operational_codec.parse_executed(frame, k_s)

        self.assertEqual(body["command_id"], bytes.fromhex(d["command_id"]))
        self.assertEqual(body["device_id"], bytes.fromhex(d["device_id"]))


class HeartbeatTest(unittest.TestCase):
    def test_build_heartbeat_matches_fixture_and_verify_accepts(self):
        f = _load_fixture()
        v = f["heartbeat"]
        k_s = bytes.fromhex(f["constants"]["k_s"])

        frame = operational_codec.build_heartbeat(
            bytes.fromhex(v["dest_mac"]), bytes.fromhex(v["src_mac"]), v["frame_id"], v["ttl"],
            bytes.fromhex(v["counter_nonce"]), k_s,
        )

        self.assertEqual(frame, bytes.fromhex(v["frame"]))
        self.assertTrue(operational_codec.verify_heartbeat(frame, k_s))


class CounterNonceTest(unittest.TestCase):
    def test_counter_nonce_of_extracts_fixture_value(self):
        v = _load_fixture()["command"]
        frame = bytes.fromhex(v["frame"])

        self.assertEqual(operational_codec.counter_nonce_of(frame), bytes.fromhex(v["counter_nonce"]))
