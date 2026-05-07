import network
import time

RECONNECT_ATTEMPTS = 12
RECONNECT_DELAY_S = 10

def get_mac_address():
    wlan = network.WLAN(network.STA_IF)
    wlan.active(True)
    mac = wlan.config('mac')
    return ''.join('{:02x}'.format(b) for b in mac)


def connect_wifi(ssid, password, timeout=20):
    print('Try to connect to:', ssid)
    wlan = network.WLAN(network.STA_IF)
    wlan.active(True)
    wlan.connect(ssid, password)
    
    for _ in range(timeout):
        print('Status', wlan.status())
        if wlan.isconnected():
            print("Connected to Wi-Fi!")
            print('IP Address:', wlan.ifconfig()[0])
            return True
        time.sleep(1)
    
    return False

def get_sta_ip():
    wlan = network.WLAN(network.STA_IF)
    return wlan.ifconfig()[0] if wlan.isconnected() else None

def reconnect_wifi(ssid, pw):
    """Versucht WiFi wiederherzustellen. Gibt True bei Erfolg zurück."""
    for attempt in range(RECONNECT_ATTEMPTS):
        print("WiFi Wiederverbindung, Versuch", attempt + 1)
        if connect_wifi(ssid, pw):
            return True
        time.sleep(RECONNECT_DELAY_S)
    return False