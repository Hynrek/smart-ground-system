"""Baut und parst die Betriebs-Frames (CONFIG, CONFIG_ACK, COMMAND, EXECUTED, HEARTBEAT) unter K_S.

DISCOVERY (mit Capability-Codierung) folgt als Erweiterung dieses Moduls (siehe Baustein-C-Folgeaufgabe).
docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md Abschnitt 4,
docs/superpowers/specs/2026-07-10-espnow-operational-frames-design.md.
"""
import espnow_crypto
from frame_envelope import (
    HEADER_SIZE,
    TYPE_CONFIG,
    TYPE_CONFIG_ACK,
    TYPE_COMMAND,
    TYPE_DISCOVERY,
    TYPE_EXECUTED,
    TYPE_HEARTBEAT,
    pack_header,
)

COUNTER_NONCE_LENGTH = 4


def _wrap(header, counter_nonce, plaintext, k_s):
    gcm_nonce = bytes(8) + counter_nonce
    ciphertext_and_tag = espnow_crypto.aes256_gcm_encrypt(k_s, gcm_nonce, header, plaintext)
    return header + counter_nonce + ciphertext_and_tag


def _unwrap(frame, k_s):
    header = frame[:HEADER_SIZE]
    counter_nonce = frame[HEADER_SIZE:HEADER_SIZE + COUNTER_NONCE_LENGTH]
    ciphertext_and_tag = frame[HEADER_SIZE + COUNTER_NONCE_LENGTH:]
    gcm_nonce = bytes(8) + counter_nonce
    return espnow_crypto.aes256_gcm_decrypt(k_s, gcm_nonce, header, ciphertext_and_tag)


def counter_nonce_of(frame):
    return frame[HEADER_SIZE:HEADER_SIZE + COUNTER_NONCE_LENGTH]


def build_config(dest_mac, src_mac, frame_id, ttl, counter_nonce, k_s, device_id, device_index, device_count,
                  alias, device_type, direction, command, signal_duration_ms, blocked):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_CONFIG)
    alias_bytes = alias.encode("utf-8")
    command_bytes = command.encode("utf-8")
    plaintext = (
        device_id
        + bytes([device_index, device_count, len(alias_bytes)])
        + alias_bytes
        + bytes([device_type, direction, len(command_bytes)])
        + command_bytes
        + bytes([signal_duration_ms & 0xFF, (signal_duration_ms >> 8) & 0xFF])
        + bytes([1 if blocked else 0])
    )
    return _wrap(header, counter_nonce, plaintext, k_s)


def parse_config(frame, k_s):
    p = _unwrap(frame, k_s)
    pos = 0
    device_id = p[pos:pos + 16]
    pos += 16
    device_index = p[pos]
    pos += 1
    device_count = p[pos]
    pos += 1
    alias_len = p[pos]
    pos += 1
    alias = p[pos:pos + alias_len].decode("utf-8")
    pos += alias_len
    device_type = p[pos]
    pos += 1
    direction = p[pos]
    pos += 1
    command_len = p[pos]
    pos += 1
    command = p[pos:pos + command_len].decode("utf-8")
    pos += command_len
    signal_duration_ms = p[pos] | (p[pos + 1] << 8)
    pos += 2
    blocked = p[pos] != 0
    return {
        "device_id": device_id, "device_index": device_index, "device_count": device_count,
        "alias": alias, "device_type": device_type, "direction": direction, "command": command,
        "signal_duration_ms": signal_duration_ms, "blocked": blocked,
    }


def build_config_ack(dest_mac, src_mac, frame_id, ttl, counter_nonce, k_s):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_CONFIG_ACK)
    return _wrap(header, counter_nonce, b"", k_s)


def verify_config_ack(frame, k_s):
    try:
        _unwrap(frame, k_s)
        return True
    except ValueError:
        return False


def build_command(dest_mac, src_mac, frame_id, ttl, counter_nonce, k_s, command_id, device_id, command,
                   signal_duration_ms):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_COMMAND)
    plaintext = command_id + device_id + bytes([command]) + bytes(
        [signal_duration_ms & 0xFF, (signal_duration_ms >> 8) & 0xFF])
    return _wrap(header, counter_nonce, plaintext, k_s)


def parse_command(frame, k_s):
    p = _unwrap(frame, k_s)
    command_id = p[0:16]
    device_id = p[16:32]
    command = p[32]
    signal_duration_ms = p[33] | (p[34] << 8)
    return {"command_id": command_id, "device_id": device_id, "command": command,
            "signal_duration_ms": signal_duration_ms}


def build_executed(dest_mac, src_mac, frame_id, ttl, counter_nonce, k_s, command_id, device_id):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_EXECUTED)
    plaintext = command_id + device_id
    return _wrap(header, counter_nonce, plaintext, k_s)


def parse_executed(frame, k_s):
    p = _unwrap(frame, k_s)
    return {"command_id": p[0:16], "device_id": p[16:32]}


def build_heartbeat(dest_mac, src_mac, frame_id, ttl, counter_nonce, k_s):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_HEARTBEAT)
    return _wrap(header, counter_nonce, b"", k_s)


def verify_heartbeat(frame, k_s):
    try:
        _unwrap(frame, k_s)
        return True
    except ValueError:
        return False


def build_discovery(dest_mac, src_mac, frame_id, ttl, counter_nonce, k_s, app_version_major, app_version_minor,
                     config_schema_version, box_type, device_types):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_DISCOVERY)
    box_type_bytes = box_type.encode("utf-8")
    plaintext = bytes([app_version_major, app_version_minor, config_schema_version, len(box_type_bytes)])
    plaintext += box_type_bytes
    plaintext += bytes([len(device_types)])
    for dt in device_types:
        fields = dt["config_fields"]
        plaintext += bytes([dt["device_type_id"], dt["directions_bitmask"], dt["commands_bitmask"], len(fields)])
        for field in fields:
            default_bytes = field["default_bytes"]
            plaintext += bytes([field["field_id"], field["type_id"], len(default_bytes)])
            plaintext += default_bytes
    return _wrap(header, counter_nonce, plaintext, k_s)


def parse_discovery(frame, k_s):
    p = _unwrap(frame, k_s)
    pos = 0
    app_version_major = p[pos]
    pos += 1
    app_version_minor = p[pos]
    pos += 1
    config_schema_version = p[pos]
    pos += 1
    box_type_len = p[pos]
    pos += 1
    box_type = p[pos:pos + box_type_len].decode("utf-8")
    pos += box_type_len
    device_type_count = p[pos]
    pos += 1
    device_types = []
    for _ in range(device_type_count):
        device_type_id = p[pos]
        pos += 1
        directions_bitmask = p[pos]
        pos += 1
        commands_bitmask = p[pos]
        pos += 1
        field_count = p[pos]
        pos += 1
        fields = []
        for _ in range(field_count):
            field_id = p[pos]
            pos += 1
            type_id = p[pos]
            pos += 1
            default_len = p[pos]
            pos += 1
            default_bytes = p[pos:pos + default_len]
            pos += default_len
            fields.append({"field_id": field_id, "type_id": type_id, "default_bytes": default_bytes})
        device_types.append({
            "device_type_id": device_type_id, "directions_bitmask": directions_bitmask,
            "commands_bitmask": commands_bitmask, "config_fields": fields,
        })
    return {
        "app_version_major": app_version_major, "app_version_minor": app_version_minor,
        "config_schema_version": config_schema_version, "box_type": box_type, "device_types": device_types,
    }
