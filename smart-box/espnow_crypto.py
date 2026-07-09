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


def _xor(a, b):
    return bytes(x ^ y for x, y in zip(a, b))


def _inc32(block):
    # GCM-Spec: nur die letzten 4 Byte (big-endian) inkrementieren, Rest bleibt fix.
    counter = int.from_bytes(block[12:16], "big")
    counter = (counter + 1) % (1 << 32)
    return block[:12] + counter.to_bytes(4, "big")


def _gf_mult(x, y):
    # Multiplikation in GF(2^128), Reduktionspolynom aus NIST SP 800-38D.
    z = 0
    v = y
    for i in range(128):
        bit = (x >> (127 - i)) & 1
        if bit:
            z ^= v
        lsb = v & 1
        v >>= 1
        if lsb:
            v ^= (0xE1 << 120)
    return z


def _pad16(data):
    r = len(data) % 16
    return data if r == 0 else data + bytes(16 - r)


def _ghash(h, aad, ciphertext):
    h_int = int.from_bytes(h, "big")
    blocks = _pad16(aad) + _pad16(ciphertext)
    y = 0
    for i in range(0, len(blocks), 16):
        block = blocks[i:i + 16]
        x = y ^ int.from_bytes(block, "big")
        y = _gf_mult(x, h_int)
    length_block = (len(aad) * 8).to_bytes(8, "big") + (len(ciphertext) * 8).to_bytes(8, "big")
    x = y ^ int.from_bytes(length_block, "big")
    y = _gf_mult(x, h_int)
    return y.to_bytes(16, "big")


def _aes_ecb_block(key, block):
    return ucryptolib.aes(key, _MODE_ECB).encrypt(block)


def aes256_gcm_encrypt(key, iv, aad, plaintext):
    h = _aes_ecb_block(key, bytes(16))
    j0 = iv + b"\x00\x00\x00\x01"
    counter = _inc32(j0)
    ciphertext = bytearray()
    for i in range(0, len(plaintext), 16):
        keystream = _aes_ecb_block(key, counter)
        chunk = plaintext[i:i + 16]
        ciphertext += _xor(chunk, keystream[:len(chunk)])
        counter = _inc32(counter)
    ciphertext = bytes(ciphertext)
    s = _ghash(h, aad, ciphertext)
    e_j0 = _aes_ecb_block(key, j0)
    tag = _xor(s, e_j0)
    return ciphertext + tag


def aes256_gcm_decrypt(key, iv, aad, ciphertext_and_tag):
    ciphertext = ciphertext_and_tag[:-16]
    expected_tag = ciphertext_and_tag[-16:]
    h = _aes_ecb_block(key, bytes(16))
    s = _ghash(h, aad, ciphertext)
    j0 = iv + b"\x00\x00\x00\x01"
    e_j0 = _aes_ecb_block(key, j0)
    tag = _xor(s, e_j0)
    if tag != expected_tag:
        raise ValueError("AES-GCM: Tag stimmt nicht ueberein")
    counter = _inc32(j0)
    plaintext = bytearray()
    for i in range(0, len(ciphertext), 16):
        keystream = _aes_ecb_block(key, counter)
        chunk = ciphertext[i:i + 16]
        plaintext += _xor(chunk, keystream[:len(chunk)])
        counter = _inc32(counter)
    return bytes(plaintext)
