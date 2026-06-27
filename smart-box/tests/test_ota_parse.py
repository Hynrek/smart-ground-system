from tests import _stubs
import json
import unittest
import ota


class OtaParseTest(unittest.TestCase):
    def _cmd(self, **over):
        base = {"type": "APP", "version": "0.7",
                "url": "http://10.0.0.1/api/ota/app/0.7",
                "sha256": "ab" * 32, "size": 100}
        base.update(over)
        return json.dumps(base).encode()

    def test_parse_valid_app_command(self):
        cmd = ota.parse_command(self._cmd())
        self.assertEqual(cmd["type"], "APP")
        self.assertEqual(cmd["version"], "0.7")
        self.assertEqual(cmd["url"], "http://10.0.0.1/api/ota/app/0.7")

    def test_parse_rejects_unknown_type(self):
        with self.assertRaises(ValueError):
            ota.parse_command(self._cmd(type="BOGUS"))

    def test_parse_rejects_missing_url(self):
        with self.assertRaises(ValueError):
            ota.parse_command(self._cmd(url=""))

    def test_parse_rejects_bad_json(self):
        with self.assertRaises(ValueError):
            ota.parse_command(b"not json")

    def test_is_busy_default_false(self):
        self.assertFalse(ota.is_busy())


if __name__ == "__main__":
    unittest.main()
