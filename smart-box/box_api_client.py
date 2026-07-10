"""
HTTPS-Client für box-api (Node), mit gepinntem CA-Root. Ersatz für mqttutils' MQTT-TLS-
Verbindung: gleiches Pinning-Prinzip (CA aus systemconfig/, nie zur Laufzeit geschrieben),
anderer Transport.
"""
import json
import urequests

CA_CERT_PATH = "systemconfig/node_ca.crt"

_ca_cert_data = None


def _load_ca_cert():
    global _ca_cert_data
    if _ca_cert_data is not None:
        return _ca_cert_data
    try:
        with open(CA_CERT_PATH, 'rb') as f:
            data = f.read()
            _ca_cert_data = data
            return data
    except OSError as e:
        print("Node-CA-Root nicht gefunden:", e)
        return None


def post_json(url, payload_dict):
    ca = _load_ca_cert()
    response = urequests.post(
        url,
        data=json.dumps(payload_dict),
        headers={"Content-Type": "application/json"},
        ssl_params={"cadata": ca} if ca else {})
    try:
        return json.loads(response.text)
    finally:
        response.close()


def get_bytes(url):
    ca = _load_ca_cert()
    response = urequests.get(url, ssl_params={"cadata": ca} if ca else {})
    try:
        return response.content
    finally:
        response.close()
