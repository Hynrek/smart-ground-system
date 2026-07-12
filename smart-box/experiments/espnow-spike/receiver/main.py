# ESP-NOW-Spike: EMPFAENGER — startet automatisch beim Boot (main.py).
# Lauscht und laesst die Onboard-LED 1 s aufleuchten, wenn ein CMD_LED-Frame
# an die eigene MAC adressiert ist.
# Upload: mpremote connect <port> cp receiver/main.py :main.py
# Self-contained — bewusst ohne Imports aus dem Spike-Ordner, damit pro Board
# genau eine Datei reicht. Nur fuer leere/frisch geflashte ESPs (ueberschreibt main.py).

import struct
import time
import network
from machine import Pin

# --- KONFIGURATION ---
CHANNEL = 1
LONG_RANGE = False  # True = 802.11-LR-Modus (mehr Reichweite, weniger Durchsatz) — auf ALLEN Boards gleich setzen!
LED_PIN = 21      # XIAO ESP32-S3 Onboard-LED
LED_ON = 0        # active-low
LED_OFF = 1
LED_DURATION_MS = 1000

# --- Frame-Format (identisch in sender/relay/receiver halten!) ---
# magic(3) | dst_mac(6) | src_mac(6) | frame_id(4) | ttl(1) | cmd(1) = 21 Bytes
MAGIC = b"SG1"
FMT = "<3s6s6sIBB"
FRAME_SIZE = struct.calcsize(FMT)
CMD_LED = 1


def mac_str(b):
    return ":".join("%02x" % x for x in b)


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
sta.disconnect()  # nicht mit einem AP verbinden — Kanal bleibt stabil
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
led = Pin(LED_PIN, Pin.OUT, value=LED_OFF)
print("Eigene MAC:", mac_str(my_mac), "| Kanal:", CHANNEL)
print("Empfaenger bereit — diese MAC beim Sender/Relay eintragen.")

while True:
    host, msg = e.recv(10000)  # 10 s Timeout, dann einfach weiter lauschen
    if msg is None:
        continue
    frame = unpack_frame(msg)
    if frame is None:
        print("Fremdes/kaputtes Paket von", mac_str(host), "ignoriert")
        continue
    dst, src, frame_id, ttl, cmd = frame
    if dst != my_mac:
        # Nicht fuer uns — im Spike nur loggen (Weiterleiten macht das Relay)
        print("Frame #%d nicht fuer uns (dst=%s), ignoriert" % (frame_id, mac_str(dst)))
        continue
    hops_hint = " (via Relay?)" if host != src else ""
    print("Frame #%d von %s, ttl=%d, cmd=%d%s" % (frame_id, mac_str(src), ttl, cmd, hops_hint))
    if cmd == CMD_LED:
        led.value(LED_ON)
        time.sleep_ms(LED_DURATION_MS)
        led.value(LED_OFF)
