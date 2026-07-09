"""AES-256-GCM (via ucryptolib) + HKDF-SHA256 fuer das ESP-NOW-Funksegment (ADR-002/ADR-003).

MicroPythons ucryptolib kennt keinen nativen GCM-Modus — dieses Modul baut ihn aus
ucryptolib.aes(key, MODE_ECB) (Hardware-beschleunigt) plus einer reinen Python-GHASH-
Implementierung zusammen. Siehe docs/superpowers/specs/2026-07-09-espnow-protocol-
contracts-design.md fuer das Frame-Format, das diese Funktionen bedienen.
"""
import hashlib

import ucryptolib

_MODE_ECB = ucryptolib.MODE_ECB


def hmac_sha256(key, msg):
    block_size = 64
    if len(key) > block_size:
        key = hashlib.sha256(key).digest()
    key = key + bytes(block_size - len(key))
    o_key_pad = bytes(b ^ 0x5C for b in key)
    i_key_pad = bytes(b ^ 0x36 for b in key)
    inner = hashlib.sha256(i_key_pad + msg).digest()
    return hashlib.sha256(o_key_pad + inner).digest()


def hkdf_extract(salt, ikm):
    return hmac_sha256(salt, ikm)


def hkdf_expand(prk, info, length):
    n = (length + 31) // 32
    t = b""
    okm = b""
    for i in range(1, n + 1):
        t = hmac_sha256(prk, t + info + bytes([i]))
        okm += t
    return okm[:length]
