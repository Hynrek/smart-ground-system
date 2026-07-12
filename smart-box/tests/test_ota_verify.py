from tests import _stubs
import os
import hashlib
import tempfile
import unittest
import ota


class OtaVerifyTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.path = os.path.join(self.dir, "blob.bin")
        self.data = b"hello smartbox" * 100
        with open(self.path, "wb") as f:
            f.write(self.data)
        self.good = hashlib.sha256(self.data).hexdigest()

    def test_verify_matches(self):
        self.assertTrue(ota.verify_file(self.path, self.good))

    def test_verify_rejects_wrong_hash(self):
        self.assertFalse(ota.verify_file(self.path, "00" * 32))

    def test_verify_missing_file_returns_false(self):
        self.assertFalse(ota.verify_file(self.path + ".nope", self.good))


if __name__ == "__main__":
    unittest.main()
