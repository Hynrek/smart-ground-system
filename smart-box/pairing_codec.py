"""Baut und parst die Pairing-Frames DISCOVER/OFFER/CONFIRM unter K_Box.

ADR-003, docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md Abschnitt 3.
"""
import espnow_crypto
from frame_envelope import HEADER_SIZE, TYPE_PAIR_CONFIRM, TYPE_PAIR_DISCOVER, TYPE_PAIR_OFFER, pack_header

MIC_LENGTH = 16
SESSION_KEY_INFO = b"smart-ground-espnow-session"
SESSION_KEY_LENGTH = 32


def _mic(key, message):
    return espnow_crypto.hmac_sha256(key, message)[:MIC_LENGTH]


def build_discover(dest_mac, src_mac, frame_id, ttl, box_uuid, nonce_b, k_box):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_PAIR_DISCOVER)
    body = box_uuid + nonce_b
    mic = _mic(k_box, header + body)
    return header + body + mic


def verify_discover(frame, k_box):
    header = frame[:HEADER_SIZE]
    body = frame[HEADER_SIZE:-MIC_LENGTH]
    mic = frame[-MIC_LENGTH:]
    expected = _mic(k_box, header + body)
    return mic == expected


def box_uuid_of(discover_frame):
    return discover_frame[HEADER_SIZE:HEADER_SIZE + 16]


def nonce_b_of(discover_frame):
    return discover_frame[HEADER_SIZE + 16:HEADER_SIZE + 24]


def build_offer(dest_mac, src_mac, frame_id, ttl, radio_id, channel, nonce_n, nonce_b, k_box):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_PAIR_OFFER)
    gcm_nonce = bytes(4) + nonce_n
    ciphertext_and_tag = espnow_crypto.aes256_gcm_encrypt(k_box, gcm_nonce, header, nonce_b)
    body = bytes([radio_id, channel]) + nonce_n + ciphertext_and_tag
    return header + body


def nonce_b_from_offer(offer_frame, k_box):
    header = offer_frame[:HEADER_SIZE]
    nonce_n = offer_frame[HEADER_SIZE + 2:HEADER_SIZE + 10]
    ciphertext_and_tag = offer_frame[HEADER_SIZE + 10:]
    gcm_nonce = bytes(4) + nonce_n
    return espnow_crypto.aes256_gcm_decrypt(k_box, gcm_nonce, header, ciphertext_and_tag)


def radio_id_of(offer_frame):
    return offer_frame[HEADER_SIZE]


def channel_of(offer_frame):
    return offer_frame[HEADER_SIZE + 1]


def nonce_n_of_offer(offer_frame):
    return offer_frame[HEADER_SIZE + 2:HEADER_SIZE + 10]


def build_confirm(dest_mac, src_mac, frame_id, ttl, nonce_n, k_box):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_PAIR_CONFIRM)
    mic = _mic(k_box, header + nonce_n)
    return header + nonce_n + mic


def verify_confirm(frame, k_box):
    header = frame[:HEADER_SIZE]
    nonce_n = frame[HEADER_SIZE:-MIC_LENGTH]
    mic = frame[-MIC_LENGTH:]
    expected = _mic(k_box, header + nonce_n)
    return mic == expected


def nonce_n_of_confirm(confirm_frame):
    return confirm_frame[HEADER_SIZE:-MIC_LENGTH]


def derive_session_key(k_box, nonce_b, nonce_n):
    salt = nonce_b + nonce_n
    prk = espnow_crypto.hkdf_extract(salt, k_box)
    return espnow_crypto.hkdf_expand(prk, SESSION_KEY_INFO, SESSION_KEY_LENGTH)
