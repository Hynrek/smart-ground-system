"""Fake MicroPython-Module + steuerbare Uhr für Host-Tests (CPython)."""
import sys
import types
import time as _time

# --- Steuerbare Uhr (ersetzt time.ticks_* / sleep_ms) ---
class _Clock:
    def __init__(self):
        self.ms = 0
    def advance(self, d):
        self.ms += d
    def reset(self):
        self.ms = 0

clock = _Clock()

def _ticks_ms():
    return clock.ms

def _ticks_add(t, d):
    return t + d

def _ticks_diff(a, b):
    # MicroPython-Semantik: vorzeichenbehaftete Differenz modulo 2^30 (Rollover-sicher)
    diff = (a - b) & 0x3FFFFFFF
    if diff >= 0x20000000:
        diff -= 0x40000000
    return diff

def _sleep_ms(d):
    clock.advance(d)

def _sleep(s):
    clock.advance(int(s * 1000))

# Reale time-Modul-Attribute ergänzen statt ersetzen (bricht unittest nicht).
_time.ticks_ms = _ticks_ms
_time.ticks_add = _ticks_add
_time.ticks_diff = _ticks_diff
_time.sleep_ms = _sleep_ms
_time.sleep = _sleep

# --- machine ---
machine = types.ModuleType("machine")

class Pin:
    OUT = 1
    IN = 0
    def __init__(self, id, mode=None):
        self.id = id
        self._v = 0
        # Ungültige GPIO-Nummer simulieren (Pico 2W: 0..40)
        if isinstance(id, int) and id > 40:
            raise ValueError("ungueltiger Pin: {}".format(id))
    def value(self, v=None):
        if v is None:
            return self._v
        self._v = 1 if v else 0
    def toggle(self):
        self._v = 0 if self._v else 1

class WDT:
    def __init__(self, timeout=0):
        self.timeout = timeout
        self.fed = 0
    def feed(self):
        self.fed += 1

def _reset():
    raise SystemExit("machine.reset")

machine.Pin = Pin
machine.WDT = WDT
machine.reset = _reset
sys.modules["machine"] = machine

# --- micropython ---
micropython = types.ModuleType("micropython")
micropython.const = lambda x: x
sys.modules["micropython"] = micropython

# --- os.uname (für firmwareVersion-Reporting; restliche os-Funktionen bleiben echt) ---
import os as _os
class _Uname:
    # Reihenfolge wie MicroPython: (sysname, nodename, release, version, machine)
    sysname = "esp32"
    nodename = "esp32"
    release = "1.23.0"
    version = "v1.23.0 on 2025-01-01"
    machine = "Generic ESP32S3 module with ESP32S3"
if not hasattr(_os, "uname"):
    _os.uname = lambda: _Uname()

# --- network ---
network = types.ModuleType("network")

class WLAN:
    STA_IF = 0
    AP_IF = 1
    def __init__(self, iface=0):
        self.iface = iface
    def active(self, *a):
        return True
    def isconnected(self):
        return True
    def connect(self, *a):
        pass
    def config(self, what=None, **kw):
        if what == "mac":
            return b"\xaa\xbb\xcc\xdd\xee\xff"
        return None
    def ifconfig(self):
        return ("192.168.1.42", "255.255.255.0", "192.168.1.1", "192.168.1.1")
    def status(self):
        return 3

network.WLAN = WLAN
network.STA_IF = 0
network.AP_IF = 1
sys.modules["network"] = network

# --- umqtt.simple ---
umqtt = types.ModuleType("umqtt")
umqtt_simple = types.ModuleType("umqtt.simple")

class MQTTClient:
    def __init__(self, client_id, broker, port=1883, keepalive=0,
                 user=None, password=None, ssl=False, ssl_params=None):
        self.client_id = client_id
        self.broker = broker
        self.port = port
        self.user = user
        self.password = password
        self.ssl = ssl
        self.ssl_params = ssl_params
        self.published = []
        self.subscribed = []
        self.cb = None
        self.connected = False
    def set_callback(self, cb):
        self.cb = cb
    def connect(self):
        self.connected = True
    def subscribe(self, topic):
        self.subscribed.append(topic)
    def publish(self, topic, payload):
        self.published.append((topic, payload))
    def check_msg(self):
        pass
    def disconnect(self):
        self.connected = False

umqtt_simple.MQTTClient = MQTTClient
sys.modules["umqtt"] = umqtt
sys.modules["umqtt.simple"] = umqtt_simple

# --- ussl (TLS-Konstanten für connect_mqtt's ssl_params; wrap_socket wird auf dem Host
#     nie aufgerufen, da der MQTTClient-Stub oben keine echte Socket-Verbindung öffnet) ---
ussl = types.ModuleType("ussl")
ussl.CERT_NONE = 0
ussl.CERT_OPTIONAL = 1
ussl.CERT_REQUIRED = 2
sys.modules["ussl"] = ussl

# --- board (auto-Selektion für Host-Tests) ---
_board_stub = types.ModuleType("board")
_board_stub.BOX_TYPE       = "test-board"
_board_stub.LED_PIN        = 0
_board_stub.LED_ON         = 1
_board_stub.WDT_TIMEOUT_MS = 8000
_board_stub.board_init     = lambda: None
sys.modules["board"] = _board_stub

# --- esp32.Partition (Firmware-A/B-OTA; nur Logik wird auf dem Host getestet) ---
esp32 = types.ModuleType("esp32")

class Partition:
    RUNNING = 0
    # Klassen-Spies für Tests
    booted = []
    validated = []
    def __init__(self, which):
        self.which = which
    def get_next_update(self):
        return Partition(99)  # "andere" Partition
    def ioctl(self, cmd, arg):
        # 4 = Anzahl Blöcke, 5 = Blockgröße (vereinfachte Werte)
        return 4096 if cmd == 4 else 4096
    def writeblocks(self, block_num, buf):
        Partition.written = getattr(Partition, "written", 0) + len(buf)
    def set_boot(self):
        Partition.booted.append(self.which)
    @staticmethod
    def mark_app_valid_cancel_rollback():
        Partition.validated.append(True)

esp32.Partition = Partition
sys.modules["esp32"] = esp32

# --- ucryptolib (AES-256-ECB in reinem Python, nur fuer Host-Tests; echte Hardware nutzt
#     das mitgelieferte ucryptolib-Modul — siehe espnow_crypto.py, das dieselbe API ruft) ---
ucryptolib = types.ModuleType("ucryptolib")


def _gmul(a, b):
    p = 0
    for _ in range(8):
        if b & 1:
            p ^= a
        hi = a & 0x80
        a = (a << 1) & 0xFF
        if hi:
            a ^= 0x1B
        b >>= 1
    return p


def _build_sbox():
    inv = [0] * 256
    for x in range(1, 256):
        for y in range(1, 256):
            if _gmul(x, y) == 1:
                inv[x] = y
                break
    sbox = [0] * 256
    for x in range(256):
        b = inv[x]
        s = b
        for shift in (1, 2, 3, 4):
            s ^= ((b << shift) | (b >> (8 - shift))) & 0xFF
        s ^= 0x63
        sbox[x] = s
    return sbox


_SBOX = _build_sbox()

_RCON = [0x01]
for _ in range(13):
    _prev = _RCON[-1]
    _nxt = (_prev << 1) & 0xFF
    if _prev & 0x80:
        _nxt ^= 0x1B
    _RCON.append(_nxt)


def _key_expansion_256(key):
    Nk, Nr = 8, 14
    w = [list(key[4 * i:4 * i + 4]) for i in range(Nk)]
    for i in range(Nk, 4 * (Nr + 1)):
        temp = list(w[i - 1])
        if i % Nk == 0:
            temp = temp[1:] + temp[:1]
            temp = [_SBOX[b] for b in temp]
            temp[0] ^= _RCON[i // Nk - 1]
        elif i % Nk == 4:
            temp = [_SBOX[b] for b in temp]
        w.append([a ^ b for a, b in zip(w[i - Nk], temp)])
    return w


def _add_round_key(state, w, round_):
    for c in range(4):
        word = w[round_ * 4 + c]
        for r in range(4):
            state[r][c] ^= word[r]


def _sub_bytes(state):
    for r in range(4):
        for c in range(4):
            state[r][c] = _SBOX[state[r][c]]


def _shift_rows(state):
    for r in range(1, 4):
        state[r] = state[r][r:] + state[r][:r]


def _mix_columns(state):
    for c in range(4):
        col = [state[r][c] for r in range(4)]
        state[0][c] = _gmul(col[0], 2) ^ _gmul(col[1], 3) ^ col[2] ^ col[3]
        state[1][c] = col[0] ^ _gmul(col[1], 2) ^ _gmul(col[2], 3) ^ col[3]
        state[2][c] = col[0] ^ col[1] ^ _gmul(col[2], 2) ^ _gmul(col[3], 3)
        state[3][c] = _gmul(col[0], 3) ^ col[1] ^ col[2] ^ _gmul(col[3], 2)


def _aes256_encrypt_block(key, block):
    Nr = 14
    w = _key_expansion_256(key)
    state = [[block[r + 4 * c] for c in range(4)] for r in range(4)]
    _add_round_key(state, w, 0)
    for rnd in range(1, Nr):
        _sub_bytes(state)
        _shift_rows(state)
        _mix_columns(state)
        _add_round_key(state, w, rnd)
    _sub_bytes(state)
    _shift_rows(state)
    _add_round_key(state, w, Nr)
    return bytes(state[r][c] for c in range(4) for r in range(4))


class _Aes:
    """Fake ucryptolib.aes — nur MODE_ECB, verarbeitet Vielfache von 16 Byte block-fuer-block."""

    def __init__(self, key, mode, iv=None):
        if mode != ucryptolib.MODE_ECB:
            raise NotImplementedError("Host-Stub unterstuetzt nur MODE_ECB")
        if len(key) != 32:
            raise ValueError("nur AES-256 unterstuetzt (32-Byte-Key)")
        self._key = key

    def encrypt(self, data):
        if len(data) % 16 != 0:
            raise ValueError("Blocklaenge muss ein Vielfaches von 16 sein")
        out = bytearray()
        for i in range(0, len(data), 16):
            out += _aes256_encrypt_block(self._key, data[i:i + 16])
        return bytes(out)


ucryptolib.MODE_ECB = 1
ucryptolib.aes = _Aes
sys.modules["ucryptolib"] = ucryptolib
