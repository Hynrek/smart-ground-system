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
    def __init__(self, client_id, broker, port=1883, keepalive=0):
        self.client_id = client_id
        self.broker = broker
        self.port = port
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

# --- board (auto-Selektion für Host-Tests) ---
_board_stub = types.ModuleType("board")
_board_stub.BOX_TYPE       = "test-board"
_board_stub.LED_PIN        = 0
_board_stub.WDT_TIMEOUT_MS = 8000
_board_stub.board_init     = lambda: None
sys.modules["board"] = _board_stub
