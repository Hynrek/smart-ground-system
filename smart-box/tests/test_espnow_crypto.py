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


class AesGcmTest(unittest.TestCase):
    def test_encrypt_matches_all_fixture_vectors(self):
        vectors = _load_fixture()["aes256_gcm"]
        self.assertTrue(vectors)

        for v in vectors:
            key = bytes.fromhex(v["key"])
            iv = bytes.fromhex(v["iv"])
            aad = bytes.fromhex(v["aad"])
            plaintext = bytes.fromhex(v["plaintext"])
            expected = bytes.fromhex(v["ciphertext"]) + bytes.fromhex(v["tag"])

            actual = espnow_crypto.aes256_gcm_encrypt(key, iv, aad, plaintext)
            self.assertEqual(actual, expected, "vector " + v["name"])

    def test_decrypt_matches_all_fixture_vectors(self):
        vectors = _load_fixture()["aes256_gcm"]

        for v in vectors:
            key = bytes.fromhex(v["key"])
            iv = bytes.fromhex(v["iv"])
            aad = bytes.fromhex(v["aad"])
            ciphertext_and_tag = bytes.fromhex(v["ciphertext"]) + bytes.fromhex(v["tag"])
            expected_plaintext = bytes.fromhex(v["plaintext"])

            actual = espnow_crypto.aes256_gcm_decrypt(key, iv, aad, ciphertext_and_tag)
            self.assertEqual(actual, expected_plaintext, "vector " + v["name"])

    def test_decrypt_rejects_tampered_tag(self):
        v = _load_fixture()["aes256_gcm"][0]
        key = bytes.fromhex(v["key"])
        iv = bytes.fromhex(v["iv"])
        aad = bytes.fromhex(v["aad"])
        tampered = bytes.fromhex(v["ciphertext"]) + bytes(bytearray(bytes.fromhex(v["tag"])[:-1]) + bytearray([0x00]))

        with self.assertRaises(ValueError):
            espnow_crypto.aes256_gcm_decrypt(key, iv, aad, tampered)
