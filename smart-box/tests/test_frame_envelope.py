from tests import _stubs
import json
import os
import struct
import unittest

import frame_envelope

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


class HeaderCodecTest(unittest.TestCase):
    def test_pack_header_matches_discover_fixture(self):
        v = _load_fixture()["pair_discover"]
        packed = frame_envelope.pack_header(
            bytes.fromhex(v["dest_mac"]), bytes.fromhex(v["src_mac"]),
            v["frame_id"], v["ttl"], v["type"],
        )
        self.assertEqual(packed, bytes.fromhex(v["header"]))

    def test_unpack_header_roundtrips_all_three_fixture_headers(self):
        fixture = _load_fixture()
        for key, expected_type in (
            ("pair_discover", frame_envelope.TYPE_PAIR_DISCOVER),
            ("pair_offer", frame_envelope.TYPE_PAIR_OFFER),
            ("pair_confirm", frame_envelope.TYPE_PAIR_CONFIRM),
        ):
            v = fixture[key]
            dest_mac, src_mac, frame_id, ttl, type_ = frame_envelope.unpack_header(bytes.fromhex(v["header"]))
            self.assertEqual(dest_mac, bytes.fromhex(v["dest_mac"]))
            self.assertEqual(src_mac, bytes.fromhex(v["src_mac"]))
            self.assertEqual(frame_id, v["frame_id"])
            self.assertEqual(ttl, v["ttl"])
            self.assertEqual(type_, expected_type)


class SeenCacheTest(unittest.TestCase):
    MAC_A = b"\x01\x02\x03\x04\x05\x06"
    MAC_B = b"\x0a\x0b\x0c\x0d\x0e\x0f"

    def test_first_sighting_is_not_duplicate(self):
        cache = frame_envelope.SeenCache(5000)
        self.assertFalse(cache.is_duplicate(self.MAC_A, 1, 1000))

    def test_repeat_within_window_is_duplicate(self):
        cache = frame_envelope.SeenCache(5000)
        cache.is_duplicate(self.MAC_A, 1, 1000)
        self.assertTrue(cache.is_duplicate(self.MAC_A, 1, 1000 + 4999))

    def test_repeat_after_window_expires_is_not_duplicate(self):
        cache = frame_envelope.SeenCache(5000)
        cache.is_duplicate(self.MAC_A, 1, 1000)
        self.assertFalse(cache.is_duplicate(self.MAC_A, 1, 1000 + 5000))

    def test_different_frame_id_same_mac_is_not_duplicate(self):
        cache = frame_envelope.SeenCache(5000)
        cache.is_duplicate(self.MAC_A, 1, 1000)
        self.assertFalse(cache.is_duplicate(self.MAC_A, 2, 1000))

    def test_same_frame_id_different_mac_is_not_duplicate(self):
        cache = frame_envelope.SeenCache(5000)
        cache.is_duplicate(self.MAC_A, 1, 1000)
        self.assertFalse(cache.is_duplicate(self.MAC_B, 1, 1000))
