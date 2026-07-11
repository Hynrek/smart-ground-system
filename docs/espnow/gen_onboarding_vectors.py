"""Erzeugt die kanonischen Onboarding-Frame-Testvektoren (docs/espnow/onboarding-test-vectors.json).

Layout siehe docs/superpowers/plans/2026-07-11-smartbox-onboarding-frame-contract.md.
Diese Datei ist die *einzige* Quelle der Golden-Bytes: sowohl der Java- (smart-ground-node)
als auch der MicroPython-Test (smart-box) prueft gegen ihre Ausgabe.
"""
import json
import struct

BROADCAST_MAC = bytes.fromhex("ffffffffffff")
BOX_MAC = bytes.fromhex("24a1600b1c2d")
NODE_MAC = bytes.fromhex("30aea41f2b3c")
BOX_NONCE = bytes.fromhex("1122334455667788")            # 8 B
TOKEN = bytes.fromhex("000102030405060708090a0b0c0d0e0f")  # 16 B
FINGERPRINT = bytes(range(32))                            # 32 B (SHA-256 des Node-Zerts, hier Dummy)
AP_SSID = "SmartGround-Node-1".encode("utf-8")
AP_PSK = "provision-pw-123".encode("utf-8")
BOX_API_URL = "https://192.168.4.1:8443".encode("utf-8")

TYPE_HELLO = 0x20
TYPE_ONBOARD_OFFER = 0x21


def pack_header(dest_mac, src_mac, frame_id, ttl, type_):
    return dest_mac + src_mac + struct.pack("<H", frame_id) + bytes([ttl, type_])


def build_hello():
    return pack_header(BROADCAST_MAC, BOX_MAC, 1, 1, TYPE_HELLO) + BOX_NONCE


def _lv(b):
    return bytes([len(b)]) + b


def build_onboard_offer():
    header = pack_header(BOX_MAC, NODE_MAC, 1, 1, TYPE_ONBOARD_OFFER)
    body = BOX_NONCE + TOKEN + FINGERPRINT + _lv(AP_SSID) + _lv(AP_PSK) + _lv(BOX_API_URL)
    return header + body


def h(b):
    return b.hex()


vectors = {
    "constants": {
        "broadcast_mac": h(BROADCAST_MAC), "box_mac": h(BOX_MAC), "node_mac": h(NODE_MAC),
        "box_nonce": h(BOX_NONCE), "token": h(TOKEN), "fingerprint": h(FINGERPRINT),
        "ap_ssid_utf8": AP_SSID.decode(), "ap_ssid_hex": h(AP_SSID),
        "ap_psk_utf8": AP_PSK.decode(), "ap_psk_hex": h(AP_PSK),
        "box_api_url_utf8": BOX_API_URL.decode(), "box_api_url_hex": h(BOX_API_URL),
    },
    "hello": {
        "dest_mac": h(BROADCAST_MAC), "src_mac": h(BOX_MAC), "frame_id": 1, "ttl": 1,
        "type": TYPE_HELLO, "box_nonce": h(BOX_NONCE), "frame": h(build_hello()),
    },
    "onboard_offer": {
        "dest_mac": h(BOX_MAC), "src_mac": h(NODE_MAC), "frame_id": 1, "ttl": 1,
        "type": TYPE_ONBOARD_OFFER, "echo_nonce": h(BOX_NONCE), "token": h(TOKEN),
        "fingerprint": h(FINGERPRINT), "ssid": h(AP_SSID), "psk": h(AP_PSK), "url": h(BOX_API_URL),
        "frame": h(build_onboard_offer()),
    },
}

if __name__ == "__main__":
    import os
    out = os.path.join(os.path.dirname(__file__), "onboarding-test-vectors.json")
    with open(out, "w") as f:
        json.dump(vectors, f, indent=2)
        f.write("\n")
    print("wrote", out)
    print("hello.frame       =", vectors["hello"]["frame"])
    print("onboard_offer.frame len(bytes) =", len(build_onboard_offer()))
