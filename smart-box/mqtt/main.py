import gc
import json
import machine
import time
import network

from networkutils import connect_wifi, reconnect_wifi, get_mac_address
from accesspoint import start_ap
from mqttutils import publish_discovery, connect_mqtt, reconnect_mqtt, load_device_config, update_device_pulses
from mqttutils import _update_known_devices

# --- KONFIGURATION ---
DEFAULT_NODE_NAME    = "SMART_RANGE_NODE"
CONFIG_PATH          = "userconfig/client_config.json"
PUBLISH_INTERVAL_S   = 20      # Sekunden zwischen Discovery-Nachrichten


def load_config():
    """Lädt die gespeicherte Netzwerkkonfiguration aus der JSON-Datei."""
    try:
        with open(CONFIG_PATH, 'r') as f:
            return json.load(f)
    except (OSError, ValueError) as e:
        print("Konfiguration nicht geladen:", e)
        return None


client = None

try:
    config = load_config()

    # Unvollständiges Profil -> Setup-Modus starten
    if not config or not config.get('client_network_ssid') or not config.get('broker_ip'):
        print("Kein gültiges WiFi-Profil. Starte Access Point für Einrichtung...")
        start_ap()

    else:
        # WiFi verbinden
        if not connect_wifi(config['client_network_ssid'], config['client_network_pw']):
            print("WiFi-Verbindung fehlgeschlagen. Starte Access Point für Einrichtung...")
            start_ap()

        else:
            CLIENT_ID = get_mac_address()

            # Gespeicherte Gerätekonfiguration laden und GPIO-Pins initialisieren.
            # Läuft vor dem MQTT-Connect, damit die Box sofort einsatzbereit ist,
            # auch wenn der Backend-Config-Push noch aussteht.
            from hardware import gpio_manager
            saved_devices = load_device_config()
            if saved_devices:
                _update_known_devices(saved_devices)  # Sicherheit: bekannte Geräte initialisieren
                gpio_manager.setup(saved_devices)
                print("GPIO-Pins aus gespeicherter Config initialisiert.")
            else:
                print("Keine gespeicherte Gerätekonfiguration – warte auf Config-Push.")

            # MQTT verbinden
            client = connect_mqtt(CLIENT_ID, config['broker_ip'])
            if not client:
                print("MQTT-Verbindung fehlgeschlagen. Neustart in 5 Sekunden...")
                time.sleep(5)
                machine.reset()

            # Discovery senden → Backend erkennt die Box und pusht Config
            publish_discovery(config['broker_ip'], 1883, CLIENT_ID)
            last_publish = time.time()

            # WLAN-Objekt einmalig vor der Schleife anlegen – nicht bei jedem Tick neu erzeugen
            wlan = network.WLAN(network.STA_IF)

            # Hauptschleife: MQTT-Nachrichten und Discovery kooperativ verarbeiten
            while True:

                # WiFi-Verbindung prüfen und bei Bedarf wiederherstellen
                if not wlan.isconnected():
                    print("WiFi-Verbindung verloren. Wiederverbindung wird versucht...")
                    if not reconnect_wifi(config['client_network_ssid'], config['client_network_pw']):
                        print("WiFi Wiederverbindung fehlgeschlagen. Neustart...")
                        machine.reset()
                    client = reconnect_mqtt()
                    if not client:
                        print("MQTT Wiederverbindung fehlgeschlagen. Neustart...")
                        machine.reset()

                # MQTT-Nachrichten nicht-blockierend abrufen
                try:
                    client.check_msg()
                except Exception as e:
                    print("MQTT-Fehler:", e)
                    client = reconnect_mqtt()
                    if not client:
                        print("MQTT nicht wiederherstellbar. Neustart...")
                        machine.reset()

                # Aktualisiere WERFER-Pulse (auto-off nach Duration)
                update_device_pulses()

                # Discovery periodisch erneut veröffentlichen
                now = time.time()
                if now - last_publish >= PUBLISH_INTERVAL_S:
                    publish_discovery(config['broker_ip'], 1883, CLIENT_ID)
                    last_publish = now
                    gc.collect()  # Heap aufräumen nach periodischer Aktivität

                time.sleep_ms(50)

except KeyboardInterrupt:
    print("Programm durch Benutzer beendet.")

except Exception as e:
    print("Kritischer Fehler:", e)
    time.sleep(5)
    machine.reset()

finally:
    try:
        if client:
            client.disconnect()
    except Exception as e:
        print("MQTT-Trennung fehlgeschlagen:", e)
    try:
        # Eigene WLAN-Instanz anlegen – nicht auf äussere Variable verlassen,
        # die im Fehlerfall noch nicht definiert sein könnte.
        network.WLAN(network.STA_IF).active(False)
    except Exception:
        pass
    try:
        network.WLAN(network.AP_IF).active(False)
    except Exception:
        pass
    print("Aufräumen abgeschlossen.")
