from tests import _stubs
import os
import json
import hashlib
import shutil
import tempfile
import unittest
import ota


class OtaDownloadTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        ota.OTA_STAGING_DIR = os.path.join(self.dir, "ota_staging")
        self.files = {"main.py": b"print('v7')\n",
                      "boards/xiao_esp32s3.py": b"BOX_TYPE='x'\n"}
        manifest = {"appVersion": "0.7", "files": []}
        for path, body in self.files.items():
            manifest["files"].append(
                {"path": path,
                 "sha256": hashlib.sha256(body).hexdigest(),
                 "size": len(body)})
        self.manifest = manifest
        self._holder = {"b": json.dumps(manifest).encode()}
        self.manifest_sha = hashlib.sha256(self._holder["b"]).hexdigest()

        files = self.files
        holder = self._holder
        def fake_stream(url, on_chunk, wdt=None):
            if url.endswith("/manifest.json"):
                on_chunk(holder["b"])
                return
            for path, body in files.items():
                if url.endswith("/files/" + path):
                    on_chunk(body)
                    return
            raise OSError("404 " + url)
        ota.http_stream = fake_stream

    def tearDown(self):
        shutil.rmtree(self.dir, ignore_errors=True)

    def test_download_verifies_manifest_hash_and_files(self):
        manifest = ota.download_app("http://srv/api/ota/app/0.7", self.manifest_sha, wdt=None)
        self.assertEqual(manifest["appVersion"], "0.7")
        for path, body in self.files.items():
            with open(os.path.join(ota.OTA_STAGING_DIR, path), "rb") as f:
                self.assertEqual(f.read(), body)

    def test_download_raises_on_manifest_hash_mismatch(self):
        with self.assertRaises(ota.OtaError):
            ota.download_app("http://srv/api/ota/app/0.7", "00" * 32, wdt=None)

    def test_download_skips_manifest_check_when_no_hash(self):
        manifest = ota.download_app("http://srv/api/ota/app/0.7", "", wdt=None)
        self.assertEqual(manifest["appVersion"], "0.7")

    def test_download_raises_on_file_hash_mismatch(self):
        self.manifest["files"][0]["sha256"] = "00" * 32
        self._holder["b"] = json.dumps(self.manifest).encode()
        new_sha = hashlib.sha256(self._holder["b"]).hexdigest()
        with self.assertRaises(ota.OtaError):
            ota.download_app("http://srv/api/ota/app/0.7", new_sha, wdt=None)

    def test_download_rejects_path_traversal(self):
        self.manifest["files"].append({"path": "../evil.py", "sha256": "ab" * 32, "size": 1})
        self._holder["b"] = json.dumps(self.manifest).encode()
        new_sha = hashlib.sha256(self._holder["b"]).hexdigest()
        with self.assertRaises(ota.OtaError):
            ota.download_app("http://srv/api/ota/app/0.7", new_sha, wdt=None)

    def test_download_rejects_userconfig_path(self):
        # userconfig/ is device-owned state (WiFi credentials, ota_state) and
        # must never be overwritten by a release
        body = b"{}"
        self.files["userconfig/client_config.json"] = body
        self.manifest["files"].append(
            {"path": "userconfig/client_config.json",
             "sha256": hashlib.sha256(body).hexdigest(),
             "size": len(body)})
        self._holder["b"] = json.dumps(self.manifest).encode()
        new_sha = hashlib.sha256(self._holder["b"]).hexdigest()
        with self.assertRaises(ota.OtaError):
            ota.download_app("http://srv/api/ota/app/0.7", new_sha, wdt=None)

    def test_download_rejects_missing_file_sha(self):
        self.manifest["files"].append({"path": "extra.py", "size": 1})
        self._holder["b"] = json.dumps(self.manifest).encode()
        new_sha = hashlib.sha256(self._holder["b"]).hexdigest()
        with self.assertRaises(ota.OtaError):
            ota.download_app("http://srv/api/ota/app/0.7", new_sha, wdt=None)


if __name__ == "__main__":
    unittest.main()
