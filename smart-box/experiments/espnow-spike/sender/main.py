# ESP-NOW-Spike: SENDER — startet automatisch beim Boot (main.py).
# Schickt alle 5 s an JEDEN konfigurierten Empfaenger einen LED-Befehl (Fan-out).
# DESTINATIONS: dest_mac -> next_hop_mac
#   Direkt-Szenario:  next_hop_mac == dest_mac (Empfaenger direkt erreichbar)
#   Hop-Szenario:     next_hop_mac == MAC des Relays, dest_mac == MAC des Empfaengers
# Upload: mpremote connect <port> cp sender/main.py :main.py
# Self-contained — pro Board reicht genau eine Datei. Nur fuer leere ESPs.

import struct
import time
import network

# --- KONFIGURATION ---
CHANNEL = 1
LONG_RANGE = False  # True = 802.11-LR-Modus (mehr Reichweite, weniger Durchsatz) — auf ALLEN Boards gleich setzen!
# Jeder Eintrag: finales Ziel -> erster Funk-Hop (Empfaenger direkt ODER ein Relay davor)
DESTINATIONS = {
    "aa:bb:cc:dd:ee:ff": "aa:bb:cc:dd:ee:ff",  # Beispiel: Empfaenger 1, direkt
    # "11:22:33:44:55:66": "11:22:33:44:55:66",  # Beispiel: Empfaenger 2, direkt
    # "77:88:99:aa:bb:cc": "dd:ee:ff:00:11:22",  # Beispiel: Empfaenger 3, via Relay
}
SEND_INTERVAL_S = 5
TTL = 3  # erlaubt bis zu 2 Relays dazwischen (ADR-002: max. TTL 3)

# --- Frame-Format (identisch in sender/relay/receiver halten!) ---
MAGIC = b"SG1"
FMT = "<3s6s6sIBB"
CMD_LED = 1


def mac_bytes(s):
    s = s.replace(":", "").replace("-", "").lower()
    return bytes(int(s[i:i + 2], 16) for i in range(0, 12, 2))


def mac_str(b):
    return ":".join("%02x" % x for x in b)


def pack_frame(dst, src, frame_id, ttl, cmd):
    return struct.pack(FMT, MAGIC, dst, src, frame_id, ttl, cmd)


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
# routes: Liste von (dest_str, dest_mac, next_hop_mac) — next_hop_mac je Ziel, aber nur einmal als Peer registriert
routes = []
registered_peers = set()
for dest_str, next_hop_str in DESTINATIONS.items():
    next_hop_mac = mac_bytes(next_hop_str)
    if next_hop_str not in registered_peers:
        e.add_peer(next_hop_mac)
        registered_peers.add(next_hop_str)
    routes.append((dest_str, mac_bytes(dest_str), next_hop_mac, next_hop_str))

print("Eigene MAC:", mac_str(my_mac), "| Kanal:", CHANNEL)
print("Sende alle %d s an %d Ziel(e)" % (SEND_INTERVAL_S, len(routes)))

frame_id = 0
while True:
    for dest_str, dest_mac, next_hop_mac, next_hop_str in routes:
        frame_id += 1
        frame = pack_frame(dest_mac, my_mac, frame_id, TTL, CMD_LED)
        try:
            ok = e.send(next_hop_mac, frame)  # True = MAC-Layer-ACK vom naechsten Hop
            print("Frame #%d -> %s (via %s) | MAC-ACK: %s" % (frame_id, dest_str, next_hop_str, ok))
        except Exception as ex:
            print("Frame #%d Sendefehler:" % frame_id, ex)
    time.sleep(SEND_INTERVAL_S)
