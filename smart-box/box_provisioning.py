"""
Ersetzt mqttutils.publish_discovery / publish_heartbeat / _persist_mqtt_credentials für
den box-api-Transport. Read-modify-write auf client_config.json bleibt (gleiche Begründung
wie zuvor: bestehende WiFi-Felder dürfen nicht verloren gehen).
"""
import json
import box_api_client

CLIENT_CONFIG_PATH = "userconfig/client_config.json"


def _read_config():
    try:
        with open(CLIENT_CONFIG_PATH, 'r') as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def _write_config(data):
    with open(CLIENT_CONFIG_PATH, 'w') as f:
        json.dump(data, f)


def discover_and_provision(mac_address, box_api_base_url, app_version="unknown",
                            firmware_version="unknown", box_type="unknown", capabilities_json="{}"):
    result = box_api_client.post_json(
        box_api_base_url + "/box-api/v1/discovery",
        {
            "macAddress": mac_address,
            "appVersion": app_version,
            "firmwareVersion": firmware_version,
            "boxType": box_type,
            "capabilitiesJson": capabilities_json,
        })
    data = _read_config()
    data['k_box_base64'] = result['kBoxBase64']
    _write_config(data)
    return result


def send_heartbeat(mac_address, box_api_base_url, status):
    box_api_client.post_json(
        box_api_base_url + "/box-api/v1/boxes/" + mac_address + "/status",
        {"status": status})
