import json
import machine
import time
import network

from networkutils import connect_wifi, reconnect_wifi
from accesspoint import start_ap
from webserver import create_control_server
from mqttutils import publish_discovery, connect_mqtt, reconnect_mqtt

# --- KONFIGURATION ---
CLIENT_ID = None
DEFAULT_NAME = "SMART_RANGE_NODE"

PUBLISH_INTERVAL_S = 60   # Sekunden zwischen Discovery-Nachrichten
RECONNECT_DELAY_S = 10      # Wartezeit zwischen Wiederverbindungsversuchen
RECONNECT_ATTEMPTS = 12     # Maximale Wiederverbindungsversuche


def load_saved_config():
    try:
        with open('./userconfig/client_config.json', 'r') as f:
            return json.load(f)
    except (OSError, ValueError) as e:
        print("Konfiguration nicht geladen:", e)
        return None

client = None

if __name__ == "__main__":
    try:
        config = load_saved_config()

        if config and config.get('client_network_ssid') and config.get('client_network_pw'):
            if connect_wifi(config['client_network_ssid'], config['client_network_pw']):
                network.hostname(node_name)
                serve_pending = create_control_server()

                # Hauptschleife
                while True:
                    # WiFi-Verbindung prüfen und bei Bedarf wiederherstellen
                    wlan = network.WLAN(network.STA_IF)
                    if not wlan.isconnected():
                        print("WiFi-Verbindung verloren. Wiederverbindung wird versucht...")
                        if not reconnect_wifi(config['client_network_ssid'], config['client_network_pw']):
                            print("WiFi Wiederverbindung fehlgeschlagen. Neustart...")
                            machine.reset()
                        client = reconnect_mqtt()
                        if not client:
                            print("MQTT Wiederverbindung fehlgeschlagen. Neustart...")
                            machine.reset()
                    # HTTP-Anfragen nicht-blockierend verarbeiten
                    serve_pending()
        # Kein gespeichertes Profil oder WiFi-Verbindung fehlgeschlagen -> Setup-Modus
        print("Kein gültiges WiFi-Profil. Starte Access Point für Einrichtung...")
        start_ap()

    except KeyboardInterrupt:
        print("Programm durch Benutzer beendet.")
    except Exception as e:
        print("Kritischer Fehler:", e)
        time.sleep(5)
        machine.reset()
    finally:
        try:
            wlan = network.WLAN(network.STA_IF)
            wlan.active(False)
        except Exception:
            pass
        try:
            wlan = network.WLAN(network.AP_IF)
            wlan.active(False)
        except Exception:
            pass
        print("Aufräumen abgeschlossen.")
