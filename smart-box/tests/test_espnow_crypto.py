from tests import _stubs
import json
import os
import unittest

import espnow_crypto

_FIXTURE_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "..", "docs", "espnow", "crypto-test-vectors.json",
)


def _load_fixture():
    with open(_FIXTURE_PATH) as f:
        return json.load(f)


class HkdfTest(unittest.TestCase):
    def test_extract_and_expand_match_rfc5869_test_case_1(self):
        v = _load_fixture()["hkdf_sha256_rfc5869"][0]

        prk = espnow_crypto.hkdf_extract(bytes.fromhex(v["salt"]), bytes.fromhex(v["ikm"]))
        self.assertEqual(prk, bytes.fromhex(v["prk"]))

        okm = espnow_crypto.hkdf_expand(prk, bytes.fromhex(v["info"]), v["l"])
        self.assertEqual(okm, bytes.fromhex(v["okm"]))
