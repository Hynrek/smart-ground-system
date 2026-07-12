import unittest
from unittest.mock import patch
import box_provisioning


class TestBoxProvisioning(unittest.TestCase):
    def test_discover_and_provision_persists_k_box(self):
        fake_response = {"kBoxBase64": "ZmFrZS1rLWJveA==", "provisioned": True}
        written = {}

        def fake_persist(data):
            written.update(data)

        with patch('box_provisioning.box_api_client.post_json', return_value=fake_response), \
             patch('box_provisioning._read_config', return_value={}), \
             patch('box_provisioning._write_config', side_effect=fake_persist):
            result = box_provisioning.discover_and_provision(
                'AA:BB:CC:DD:EE:01', 'https://node.local:8443')

        self.assertEqual(result['kBoxBase64'], 'ZmFrZS1rLWJveA==')
        self.assertEqual(written['k_box_base64'], 'ZmFrZS1rLWJveA==')

    def test_send_heartbeat_posts_status(self):
        with patch('box_provisioning.box_api_client.post_json', return_value={}) as mock_post:
            box_provisioning.send_heartbeat('AA:BB:CC:DD:EE:01', 'https://node.local:8443', 'idle')

        args, _ = mock_post.call_args
        self.assertEqual(args[0], 'https://node.local:8443/box-api/v1/boxes/AA:BB:CC:DD:EE:01/status')
        self.assertEqual(args[1], {'status': 'idle'})


if __name__ == '__main__':
    unittest.main()
