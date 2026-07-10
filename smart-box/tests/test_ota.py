from tests import _stubs
import unittest
from unittest.mock import patch
import ota


class TestOtaBoxApiDownload(unittest.TestCase):
    def test_download_uses_box_api_client_get_bytes(self):
        chunks = []
        with patch('ota.box_api_client.get_bytes', return_value=b'manifest-bytes') as mock_get:
            ota._default_http_stream(
                'https://node.local:8443/box-api/v1/ota/app/1.0.0/manifest.json',
                chunks.append)

        args, _ = mock_get.call_args
        self.assertEqual(args[0], 'https://node.local:8443/box-api/v1/ota/app/1.0.0/manifest.json')
        self.assertEqual(b"".join(chunks), b'manifest-bytes')


if __name__ == '__main__':
    unittest.main()
