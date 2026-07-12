# ESP-NOW-Spike: RELAY (Hop) — startet automatisch beim Boot (main.py).
# Leitet Frames weiter, die nicht an die eigene MAC adressiert sind.
# Statisch konfigurierter naechster Hop (ADR-002: kein Mesh in v1),
# TTL-Dekrement und Seen-Cache gegen Duplikate/Schleifen.
# Upload: mpremote connect <port> cp relay/main.py :main.py
# Self-contained — pro Board reicht genau eine Datei. Nur fuer leere ESPs.

import struct
import network

# --- KONFIGURATION ---
CHANNEL = 1
LONG_RANGE = False  # True = 802.11-LR-Modus (mehr Reichweite, weniger Durchsatz) — auf ALLEN Boards gleich setzen!
NEXT_HOP = "aa:bb:cc:dd:ee:ff"  # wohin weitergeleitet wird (Empfaenger oder naechstes Relay)
SEEN_MAX = 64  # Seen-Cache begrenzen (Speicherdisziplin)

# --- Frame-Format (identisch in sender/relay/receiver halten!) ---
MAGIC = b"SG1"
FMT = "<3s6s6sIBB"
FRAME_SIZE = struct.calcsize(FMT)


def mac_bytes(s):
    s = s.replace(":", "").replace("-", "").lower()
    return bytes(int(s[i:i + 2], 16) for i in range(0, 12, 2))


def mac_str(b):
    return ":".join("%02x" % x for x in b)


def pack_frame(dst, src, frame_id, ttl, cmd):
    return struct.pack(FMT, MAGIC, dst, src, frame_id, ttl, cmd)


def unpack_frame(msg):
    if msg is None or len(msg) != FRAME_SIZE:
        return None
    magic, dst, src, frame_id, ttl, cmd = struct.unpack(FMT, msg)
    if magic != MAGIC:
        return None
    return dst, src, frame_id, ttl, cmd


# --- Funk-Init ---
sta = network.WLAN(network.STA_IF)
sta.active(True)
sta.disconnect()
try:
    sta.config(channel=CHANNEL)
except Exception as ex:
    print("Kanal setzen fehlgeschlagen:", ex)

# Long-Range-Modus: proprietaeres 802.11-LR-PHY von Espressif — nur ESP32 untereinander,
# alle Teilnehmer muessen denselben Modus fahren, sonst hoeren sie sich nicht
if LONG_RANGE:
    try:
        sta.config(protocol=network.MODE_LR)
        print("Long-Range-Modus (802.11 LR) aktiv")
    except Exception as ex:
        print("LR-Modus nicht verfuegbar:", ex)

import espnow
e = espnow.ESPNow()
e.active(True)

my_mac = sta.config("mac")
next_hop_mac = mac_bytes(NEXT_HOP)
e.add_peer(next_hop_mac)
print("Eigene MAC:", mac_str(my_mac), "| Kanal:", CHANNEL)
print("Relay bereit — leite weiter an", NEXT_HOP)

seen = []  # (src, frame_id) der zuletzt gesehenen Frames

while True:
    host, msg = e.recv(10000)
    if msg is None:
        continue
    frame = unpack_frame(msg)
    if frame is None:
        continue
    dst, src, frame_id, ttl, cmd = frame

    if dst == my_mac:
        # Im Spike hat das Relay selbst keine Geraetefunktion — nur loggen
        print("Frame #%d ist an das Relay selbst adressiert — keine Aktion" % frame_id)
        continue

    key = (src, frame_id)
    if key in seen:
        print("Frame #%d von %s: Duplikat, verworfen" % (frame_id, mac_str(src)))
        continue
    seen.append(key)
    if len(seen) > SEEN_MAX:
        seen.pop(0)

    if ttl <= 1:
        print("Frame #%d: TTL abgelaufen, verworfen" % frame_id)
        continue

    fwd = pack_frame(dst, src, frame_id, ttl - 1, cmd)
    try:
        ok = e.send(next_hop_mac, fwd)
        print("Frame #%d von %s -> %s weitergeleitet (ttl %d->%d) | MAC-ACK: %s"
              % (frame_id, mac_str(src), NEXT_HOP, ttl, ttl - 1, ok))
    except Exception as ex:
        print("Frame #%d Weiterleitung fehlgeschlagen:" % frame_id, ex)
