import gc
import json
import machine
import time
import network

from networkutils import connect_wifi, reconnect_wifi, get_mac_address
from accesspoint import start_ap
from hardware import status_blink
from mqttutils import publish_discovery, publish_heartbeat, connect_mqtt, reconnect_mqtt, load_device_config, update_device_pulses
from mqttutils import _update_known_devices

# --- KONFIGURATION ---
DEFAULT_NODE_NAME    = "SMART_RANGE_NODE"
CONFIG_PATH          = "userconfig/client_config.json"
PUBLISH_INTERVAL_S   = 20      # Sekunden zwischen Heartbeat-Nachrichten
WDT_TIMEOUT_MS       = 8000    # Watchdog-Timeout (nahe rp2-Maximum; Build-Limit beim Flashen prüfen)


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
    # Status-LED: 3x blinken signalisiert erfolgreichen Boot
    status_blink()

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

            # MQTT verbinden (broker_port aus Config respektieren, Default 1883)
            try:
                broker_port = int(config.get('broker_port') or 1883)
            except (ValueError, TypeError):
                broker_port = 1883

            client = connect_mqtt(CLIENT_ID, config['broker_ip'], broker_port)
            if not client:
                print("MQTT-Verbindung fehlgeschlagen. Neustart in 5 Sekunden...")
                time.sleep(5)
                machine.reset()

            # Discovery EINMALIG beim Boot → Backend erkennt Box und pusht Config
            publish_discovery(CLIENT_ID)
            last_publish = time.time()

            # WLAN-Objekt einmalig vor der Schleife anlegen – nicht bei jedem Tick neu erzeugen
            wlan = network.WLAN(network.STA_IF)

            # Watchdog erst im Normalbetrieb aktivieren – AP-/Erstverbindung darf lange dauern.
            # ACHTUNG: Auf dem RP2 lässt sich der WDT nicht mehr stoppen. Ab hier setzt auch ein
            # KeyboardInterrupt (Ctrl-C) die Box nach spätestens WDT_TIMEOUT_MS zurück, weil im
            # finally-Block niemand mehr feed() aufruft – REPL-Debugging ist dadurch begrenzt.
            wdt = machine.WDT(timeout=WDT_TIMEOUT_MS)

            # Hauptschleife: MQTT-Nachrichten und Heartbeat kooperativ verarbeiten
            while True:
                wdt.feed()

                # WiFi-Verbindung prüfen und bei Bedarf wiederherstellen
                if not wlan.isconnected():
                    print("WiFi-Verbindung verloren. Wiederverbindung wird versucht...")
                    if not reconnect_wifi(config['client_network_ssid'], config['client_network_pw'], wdt):
                        print("WiFi Wiederverbindung fehlgeschlagen. Neustart...")
                        machine.reset()
                    client = reconnect_mqtt(wdt)
                    if not client:
                        print("MQTT Wiederverbindung fehlgeschlagen. Neustart...")
                        machine.reset()

                # MQTT-Nachrichten nicht-blockierend abrufen
                try:
                    client.check_msg()
                except Exception as e:
                    print("MQTT-Fehler:", e)
                    client = reconnect_mqtt(wdt)
                    if not client:
                        print("MQTT nicht wiederherstellbar. Neustart...")
                        machine.reset()

                # Aktualisiere WERFER-Pulse (auto-off nach Duration)
                update_device_pulses()

                # Heartbeat periodisch senden (Liveness ohne Config-Push)
                now = time.time()
                if now - last_publish >= PUBLISH_INTERVAL_S:
                    publish_heartbeat(CLIENT_ID)
                    last_publish = now
                    gc.collect()  # Heap aufräumen nach periodischer Aktivität

                time.sleep_ms(50)

except KeyboardInterrupt:
    # Sauberes Herunterfahren ohne expliziten Reset. Hinweis: Sobald der Watchdog läuft
    # (Normalbetrieb), setzt er die Box nach dem Aufräumen dennoch zurück – der RP2-WDT
    # kann nicht gestoppt werden. Im AP-/Erstverbindungs-Pfad (kein WDT) gilt "kein Reset".
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
