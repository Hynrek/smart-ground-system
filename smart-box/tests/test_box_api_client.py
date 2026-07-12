# smart-box/tests/test_box_api_client.py
import unittest
from unittest.mock import patch, MagicMock
import box_api_client


class TestBoxApiClient(unittest.TestCase):
    def test_post_json_sends_https_request_with_pinned_ca(self):
        fake_response = MagicMock()
        fake_response.text = '{"provisioned": true, "kBoxBase64": "AAAA"}'
        fake_response.status_code = 200

        with patch('box_api_client._load_ca_cert', return_value=b'fake-ca-bytes'), \
             patch('urequests.post', return_value=fake_response) as mock_post:
            result = box_api_client.post_json(
                'https://node.local:8443/box-api/v1/discovery',
                {'macAddress': 'AA:BB:CC:DD:EE:01'})

        self.assertEqual(result['provisioned'], True)
        self.assertEqual(result['kBoxBase64'], 'AAAA')
        args, kwargs = mock_post.call_args
        self.assertEqual(args[0], 'https://node.local:8443/box-api/v1/discovery')
        self.assertIn('cadata', kwargs.get('ssl_params', {}))

    def test_get_bytes_returns_raw_content(self):
        fake_response = MagicMock()
        fake_response.content = b'\x01\x02\x03'
        fake_response.status_code = 200

        with patch('box_api_client._load_ca_cert', return_value=b'fake-ca-bytes'), \
             patch('urequests.get', return_value=fake_response):
            result = box_api_client.get_bytes('https://node.local:8443/box-api/v1/ota/firmware/1.0.0')

        self.assertEqual(result, b'\x01\x02\x03')


if __name__ == '__main__':
    unittest.main()
