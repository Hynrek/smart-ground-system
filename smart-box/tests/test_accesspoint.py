from tests import _stubs
import unittest
import accesspoint


class ExtractSetupParamsTest(unittest.TestCase):
    def test_extracts_box_api_base_url(self):
        qs = "client_network_ssid=Home&client_network_pw=pw&box_api_base_url=https%3A%2F%2F192.168.4.1%3A8443"
        params = accesspoint.extract_setup_params(qs)
        self.assertEqual(params["box_api_base_url"], "https://192.168.4.1:8443")

    def test_ignores_unknown_keys(self):
        qs = "client_network_ssid=Home&evil_key=1"
        params = accesspoint.extract_setup_params(qs)
        self.assertNotIn("evil_key", params)


class ValidateSetupParamsTest(unittest.TestCase):
    def test_valid_params_pass(self):
        params = {"client_network_ssid": "Home", "box_api_base_url": "https://192.168.4.1:8443"}
        self.assertIsNone(accesspoint.validate_setup_params(params))

    def test_missing_ssid_fails(self):
        params = {"box_api_base_url": "https://192.168.4.1:8443"}
        self.assertIsNotNone(accesspoint.validate_setup_params(params))

    def test_missing_box_api_base_url_fails(self):
        params = {"client_network_ssid": "Home"}
        self.assertIsNotNone(accesspoint.validate_setup_params(params))


if __name__ == "__main__":
    unittest.main()
