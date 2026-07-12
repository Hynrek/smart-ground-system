"""Baut und parst die unauthentifizierten Onboarding-Frames HELLO und ONBOARD_OFFER.

Kein MIC, kein AES-GCM — eine fabrikneue Box hat noch kein K_Box (siehe
docs/superpowers/specs/2026-07-11-smartbox-coupling-design.md). Cross-Language-Gegenstueck
zu smart-ground-node's OnboardingCodec.java; beide gegen docs/espnow/onboarding-test-vectors.json.

HELLO         = header(16) ‖ box_nonce(8)
ONBOARD_OFFER = header(16) ‖ echo_nonce(8) ‖ token(16) ‖ fingerprint(32)
                ‖ ssid_len(1) ‖ ssid ‖ psk_len(1) ‖ psk ‖ url_len(1) ‖ url
"""
from frame_envelope import HEADER_SIZE, TYPE_HELLO, TYPE_ONBOARD_OFFER, pack_header

NONCE_LENGTH = 8
TOKEN_LENGTH = 16
FINGERPRINT_LENGTH = 32
_VARFIELDS_START = HEADER_SIZE + NONCE_LENGTH + TOKEN_LENGTH + FINGERPRINT_LENGTH


def build_hello(dest_mac, src_mac, frame_id, ttl, box_nonce):
    return pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_HELLO) + box_nonce


def box_nonce_of(hello_frame):
    return hello_frame[HEADER_SIZE:HEADER_SIZE + NONCE_LENGTH]


def _lv(field):
    if len(field) > 255:
        raise ValueError("Feld zu lang fuer 1-Byte-Laengenpraefix: %d" % len(field))
    return bytes([len(field)]) + field


def build_onboard_offer(dest_mac, src_mac, frame_id, ttl, echo_nonce, token, fingerprint, ssid, psk, url):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_ONBOARD_OFFER)
    body = echo_nonce + token + fingerprint + _lv(ssid) + _lv(psk) + _lv(url)
    return header + body


def echo_nonce_of(offer_frame):
    start = HEADER_SIZE
    return offer_frame[start:start + NONCE_LENGTH]


def token_of(offer_frame):
    start = HEADER_SIZE + NONCE_LENGTH
    return offer_frame[start:start + TOKEN_LENGTH]


def fingerprint_of(offer_frame):
    start = HEADER_SIZE + NONCE_LENGTH + TOKEN_LENGTH
    return offer_frame[start:start + FINGERPRINT_LENGTH]


def _var_field_at(frame, index):
    pos = _VARFIELDS_START
    for _ in range(index):
        pos += 1 + frame[pos]
    length = frame[pos]
    return frame[pos + 1:pos + 1 + length]


def ssid_of(offer_frame):
    return _var_field_at(offer_frame, 0)


def psk_of(offer_frame):
    return _var_field_at(offer_frame, 1)


def url_of(offer_frame):
    return _var_field_at(offer_frame, 2)
