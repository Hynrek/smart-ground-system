import network
import socket
import time
import json
import machine

from hardware import led_on

# --- KONFIGURATION ---
SYSTEM_CONFIG_PATH = "systemconfig/accesspoint_config.json"
USER_CONFIG_PATH = "userconfig/client_config.json"
HTML_PATH = "accesspoint.html"


def _url_decode(s):
    """Einfaches URL-Decoding: ersetzt '+' durch Leerzeichen und '%XX' durch das Zeichen."""
    s = s.replace('+', ' ')
    result = []
    i = 0
    while i < len(s):
        if s[i] == '%' and i + 2 < len(s):
            try:
                result.append(chr(int(s[i+1:i+3], 16)))
                i += 3
                continue
            except ValueError:
                pass
        result.append(s[i])
        i += 1
    return ''.join(result)


# Nur erlaubte Schlüssel übernehmen – verhindert Injection fremder Felder.
# box_api_base_url ersetzt broker_ip/broker_port: main.py's Boot-Gate prüft seit dem
# box-api-Umbau nur noch client_network_ssid + box_api_base_url (kein MQTT-Broker mehr).
ALLOWED_KEYS = ('client_network_ssid', 'client_network_pw', 'box_api_base_url')


def extract_setup_params(query_string):
    """Parst den Query-String und filtert auf ALLOWED_KEYS."""
    raw = {}
    for pair in query_string.split('&'):
        if '=' in pair:
            key, value = pair.split('=', 1)
            raw[_url_decode(key)] = _url_decode(value)
    return {k: raw[k] for k in ALLOWED_KEYS if k in raw}


def validate_setup_params(params):
    """Prüft Pflichtfelder. Gibt eine Fehlermeldung zurück oder None wenn gültig."""
    if not params.get('client_network_ssid') or not params.get('box_api_base_url'):
        return "Fehler: SSID und box-api-URL sind Pflichtfelder."
    return None


def start_ap():
    """Startet den Access Point und einen einfachen HTTP-Server für die Ersteinrichtung."""
    print("Aktiviere Access Point...")
    setup_config = None
    html = None

    try:
        with open(SYSTEM_CONFIG_PATH, 'r') as f:
            setup_config = json.load(f)
    except (OSError, ValueError) as e:
        print("Access Point Konfiguration nicht geladen:", e)
        return

    try:
        with open(HTML_PATH, 'r') as f:
            html = f.read()
    except (OSError, ValueError) as e:
        print("Access Point HTML nicht geladen:", e)
        html = "<html><body><h1>Fehler: Setup-Seite nicht gefunden.</h1></body></html>"

    wlan = network.WLAN(network.AP_IF)
    wlan.config(essid=setup_config['accesspoint_ssid'], password=setup_config['accesspoint_pass'])
    wlan.active(True)

    # Status-LED: dauerhaft EIN signalisiert Access-Point-Modus
    led_on()

    print("Access Point aktiv.")
    print("Netzwerkname:", setup_config['accesspoint_ssid'])
    print("IP-Adresse:", wlan.ifconfig()[0])

    addr = socket.getaddrinfo('0.0.0.0', 80)[0][-1]
    s = socket.socket()
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(addr)
    s.listen(1)

    try:
        while True:
            cl, _ = s.accept()
            try:
                request = cl.recv(1024).decode('utf-8')

                if '/save?' in request:
                    # Query-String aus der ersten Zeile der HTTP-Anfrage extrahieren
                    try:
                        query_string = request.split('/save?')[1].split(' ')[0]
                        params = extract_setup_params(query_string)

                        # Pflichtfelder prüfen bevor auf Flash geschrieben wird
                        error = validate_setup_params(params)
                        if error:
                            cl.send('HTTP/1.1 400 Bad Request\r\nContent-Type: text/html\r\n\r\n' + error)
                        else:
                            with open(USER_CONFIG_PATH, 'w') as f:
                                json.dump(params, f)

                            cl.send('HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\nEinstellungen gespeichert! Neustart...')
                            cl.close()
                            time.sleep(2)
                            machine.reset()
                    except Exception as e:
                        print("Fehler beim Speichern der Konfiguration:", e)
                        cl.send('HTTP/1.1 500 Internal Server Error\r\nContent-Type: text/html\r\n\r\nFehler beim Speichern.')

                else:
                    cl.send('HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n' + html)

            except Exception as e:
                print("HTTP-Anfragefehler:", e)
            finally:
                cl.close()
    finally:
        s.close()
