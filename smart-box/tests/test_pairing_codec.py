from tests import _stubs
import json
import os
import unittest

import pairing_codec

_FIXTURE_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "..", "docs", "espnow", "pairing-test-vectors.json",
)

if not os.path.isfile(_FIXTURE_PATH):
    raise unittest.SkipTest(
        "Pairing-Test-Vektoren nicht gefunden unter " + _FIXTURE_PATH +
        " — dieses Modul braucht das smart-box-Repo als Sub-Checkout im"
        " smart-ground-Monorepo. Bei einem eigenstaendigen Checkout von"
        " smartground-firmware werden diese Tests uebersprungen."
    )


def _load_fixture():
    with open(_FIXTURE_PATH) as f:
        return json.load(f)


class PairDiscoverTest(unittest.TestCase):
    def test_build_discover_matches_fixture_frame(self):
        f = _load_fixture()
        v = f["pair_discover"]
        k_box = bytes.fromhex(f["constants"]["k_box"])

        frame = pairing_codec.build_discover(
            bytes.fromhex(v["dest_mac"]), bytes.fromhex(v["src_mac"]), v["frame_id"], v["ttl"],
            bytes.fromhex(v["box_uuid"]), bytes.fromhex(v["nonce_b"]), k_box,
        )

        self.assertEqual(frame, bytes.fromhex(v["frame"]))

    def test_verify_discover_accepts_fixture_rejects_tampered(self):
        f = _load_fixture()
        v = f["pair_discover"]
        k_box = bytes.fromhex(f["constants"]["k_box"])
        frame = bytes.fromhex(v["frame"])

        self.assertTrue(pairing_codec.verify_discover(frame, k_box))

        tampered = bytearray(frame)
        tampered[-1] ^= 0x01
        self.assertFalse(pairing_codec.verify_discover(bytes(tampered), k_box))

    def test_box_uuid_of_and_nonce_b_of_extract_fixture_values(self):
        v = _load_fixture()["pair_discover"]
        frame = bytes.fromhex(v["frame"])

        self.assertEqual(pairing_codec.box_uuid_of(frame), bytes.fromhex(v["box_uuid"]))
        self.assertEqual(pairing_codec.nonce_b_of(frame), bytes.fromhex(v["nonce_b"]))


class PairOfferTest(unittest.TestCase):
    def test_build_offer_matches_fixture_frame(self):
        f = _load_fixture()
        v = f["pair_offer"]
        k_box = bytes.fromhex(f["constants"]["k_box"])

        frame = pairing_codec.build_offer(
            bytes.fromhex(v["dest_mac"]), bytes.fromhex(v["src_mac"]), v["frame_id"], v["ttl"],
            v["radio_id"], v["channel"], bytes.fromhex(v["nonce_n"]), bytes.fromhex(v["plaintext_nonce_b"]), k_box,
        )

        self.assertEqual(frame, bytes.fromhex(v["frame"]))

    def test_nonce_b_from_offer_decrypts_fixture_frame(self):
        f = _load_fixture()
        v = f["pair_offer"]
        k_box = bytes.fromhex(f["constants"]["k_box"])
        frame = bytes.fromhex(v["frame"])

        self.assertEqual(pairing_codec.nonce_b_from_offer(frame, k_box), bytes.fromhex(v["plaintext_nonce_b"]))
        self.assertEqual(pairing_codec.radio_id_of(frame), v["radio_id"])
        self.assertEqual(pairing_codec.channel_of(frame), v["channel"])
        self.assertEqual(pairing_codec.nonce_n_of_offer(frame), bytes.fromhex(v["nonce_n"]))

    def test_nonce_b_from_offer_rejects_tampered_tag(self):
        f = _load_fixture()
        v = f["pair_offer"]
        k_box = bytes.fromhex(f["constants"]["k_box"])
        tampered = bytearray(bytes.fromhex(v["frame"]))
        tampered[-1] ^= 0x01

        with self.assertRaises(ValueError):
            pairing_codec.nonce_b_from_offer(bytes(tampered), k_box)


class PairConfirmTest(unittest.TestCase):
    def test_build_confirm_matches_fixture_frame(self):
        f = _load_fixture()
        v = f["pair_confirm"]
        k_box = bytes.fromhex(f["constants"]["k_box"])

        frame = pairing_codec.build_confirm(
            bytes.fromhex(v["dest_mac"]), bytes.fromhex(v["src_mac"]), v["frame_id"], v["ttl"],
            bytes.fromhex(v["nonce_n"]), k_box,
        )

        self.assertEqual(frame, bytes.fromhex(v["frame"]))

    def test_verify_confirm_accepts_fixture_and_nonce_n_of_confirm_extracts_it(self):
        f = _load_fixture()
        v = f["pair_confirm"]
        k_box = bytes.fromhex(f["constants"]["k_box"])
        frame = bytes.fromhex(v["frame"])

        self.assertTrue(pairing_codec.verify_confirm(frame, k_box))
        self.assertEqual(pairing_codec.nonce_n_of_confirm(frame), bytes.fromhex(v["nonce_n"]))


class SessionKeyTest(unittest.TestCase):
    def test_derive_session_key_matches_fixture(self):
        f = _load_fixture()
        v = f["session_key"]
        k_box = bytes.fromhex(f["constants"]["k_box"])
        nonce_b = bytes.fromhex(f["constants"]["nonce_b"])
        nonce_n = bytes.fromhex(f["constants"]["nonce_n"])

        k_s = pairing_codec.derive_session_key(k_box, nonce_b, nonce_n)

        self.assertEqual(k_s, bytes.fromhex(v["k_s"]))
