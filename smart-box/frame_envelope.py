"""Klartext-Routing-Header und Duplikat-Erkennung fuer ESP-NOW-Frames.

Layout (16 Byte, siehe docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md
Abschnitt 1): dest_mac(6) ‖ src_mac(6) ‖ frame_id(2, uint16 LE) ‖ ttl(1) ‖ type(1).
"""
import struct

HEADER_SIZE = 16

TYPE_PAIR_DISCOVER = 0x01
TYPE_PAIR_OFFER = 0x02
TYPE_PAIR_CONFIRM = 0x03
TYPE_DISCOVERY = 0x10
TYPE_CONFIG = 0x11
TYPE_CONFIG_ACK = 0x12
TYPE_COMMAND = 0x13
TYPE_EXECUTED = 0x14
TYPE_HEARTBEAT = 0x15
TYPE_HELLO = 0x20
TYPE_ONBOARD_OFFER = 0x21


def pack_header(dest_mac, src_mac, frame_id, ttl, type_):
    return dest_mac + src_mac + struct.pack("<H", frame_id) + bytes([ttl, type_])


def unpack_header(data):
    dest_mac = data[0:6]
    src_mac = data[6:12]
    frame_id = struct.unpack("<H", data[12:14])[0]
    ttl = data[14]
    type_ = data[15]
    return dest_mac, src_mac, frame_id, ttl, type_


class SeenCache:
    """Duplikat-/Storm-Unterdrueckung: (src_mac, frame_id) verfaellt nach window_ms."""

    def __init__(self, window_ms):
        self.window_ms = window_ms
        self._seen = {}

    def is_duplicate(self, src_mac, frame_id, now_ms):
        key = (bytes(src_mac), frame_id)
        last = self._seen.get(key)
        duplicate = last is not None and (now_ms - last) < self.window_ms
        self._seen[key] = now_ms
        expired = [k for k, t in self._seen.items() if (now_ms - t) >= self.window_ms]
        for k in expired:
            del self._seen[k]
        return duplicate
