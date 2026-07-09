import sys

# Board-Modul anhand von sys.platform auswählen und als 'board' registrieren.
# Muss VOR allen anderen Firmware-Importen geschehen, da hardware.py 'board' beim
# Laden benötigt.
_PLATFORM_MAP = {"rp2": "pico2w", "esp32": "xiao_esp32s3"}
_board_name = _PLATFORM_MAP.get(sys.platform)
if _board_name is None:
    print("Unbekannte Plattform:", sys.platform)
    import machine as _machine
    _machine.reset()
board = __import__("boards." + _board_name, None, None, [_board_name])
sys.modules["board"] = board
board.board_init()

import gc
import json
import machine
import time
import network

from networkutils import connect_wifi, reconnect_wifi, get_mac_address
from accesspoint import start_ap
from hardware import status_blink
from mqttutils import publish_discovery, publish_heartbeat, connect_mqtt, reconnect_mqtt, load_device_config, load_firmware_config, update_device_pulses
from mqttutils import _update_known_devices, set_watchdog, publish_ota_status
import ota

# --- KONFIGURATION ---
DEFAULT_NODE_NAME    = "SMART_RANGE_NODE"
CONFIG_PATH          = "userconfig/client_config.json"
PUBLISH_INTERVAL_S   = 20      # Sekunden zwischen Heartbeat-Nachrichten


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
            # Bei Schema-Inkompatibilität (OTA-Update mit neuer Config-Version) wird die
            # gespeicherte Config verworfen und auf den Config-Push gewartet.
            from hardware import gpio_manager
            firmware_cfg = load_firmware_config()
            expected_schema = firmware_cfg.get("config_schema_version", "1")
            saved = load_device_config()
            if saved and saved.get("config_schema_version") == expected_schema:
                saved_devices = saved.get("devices", [])
                if saved_devices:
                    _update_known_devices(saved_devices)
                    gpio_manager.setup(saved_devices)
                    print("GPIO-Pins aus gespeicherter Config initialisiert.")
                else:
                    print("Gespeicherte Config leer – warte auf Config-Push.")
            else:
                print("Config-Schema inkompatibel oder fehlend – warte auf Config-Push.")

            # --- OTA Boot-Supervisor (läuft VOR dem Watchdog, direkt nach WiFi-Connect) ---
            # 1. Unterbrochenen Apply (Stromverlust) aus Backup wiederherstellen
            ota.recover_interrupted_apply()
            # 2. Probezeit prüfen: zu viele Fehlversuche → automatischer Rollback.
            #    Bei Rollback liegt die alte Version wieder auf Flash; sofort neu starten,
            #    damit sie auch im RAM läuft. Der ROLLED_BACK-Bericht folgt nach dem
            #    nächsten Connect via ota.take_pending_report().
            if ota.probation_check():
                print("OTA-Rollback durchgeführt – Neustart in die alte Version...")
                time.sleep(2)
                machine.reset()
            # 3. Boot gilt als gesund, sobald WiFi steht und die Box den Hauptpfad erreicht.
            #    Bewusst NICHT an MQTT/Broker gekoppelt: Broker-Erreichbarkeit ist Infrastruktur,
            #    kein Firmware-Gesundheitssignal – sonst würde ein vorübergehend nicht erreichbarer
            #    Broker eine gesunde neue Version fälschlich zurückrollen. Berichte werden gehalten
            #    und nach dem MQTT-Connect gesendet.
            _ota_confirm = ota.confirm_boot_healthy()      # beendet Probezeit, ("APPLIED", v) oder None
            _ota_pending = ota.take_pending_report()       # ("ROLLED_BACK", v) nach Reboot oder None

            # MQTT verbinden (broker_port aus Config respektieren, Default 8883/TLS).
            # mqtt_username/mqtt_password sind optional: fehlen sie (unprovisionierte Box,
            # kein Bootstrap-Credential geflasht), verbindet sich connect_mqtt() mit
            # user=None/password=None, was gegen den gehärteten Broker fehlschlägt – das
            # ist erwartetes Verhalten, siehe smart-box/CLAUDE.md "Bootstrap credential".
            try:
                broker_port = int(config.get('broker_port') or 8883)
            except (ValueError, TypeError):
                broker_port = 8883

            client = connect_mqtt(CLIENT_ID, config['broker_ip'], broker_port,
                                   user=config.get('mqtt_username'),
                                   password=config.get('mqtt_password'))
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
            # KeyboardInterrupt (Ctrl-C) die Box nach spätestens board.WDT_TIMEOUT_MS zurück, weil im
            # finally-Block niemand mehr feed() aufruft – REPL-Debugging ist dadurch begrenzt.
            wdt = machine.WDT(timeout=board.WDT_TIMEOUT_MS)

            # OTA-Modul den Watchdog geben (für Download-Feeding)
            set_watchdog(wdt)
            # Die im Boot-Supervisor ermittelten OTA-Berichte jetzt (MQTT steht) senden
            if _ota_confirm:
                publish_ota_status(CLIENT_ID, _ota_confirm[0], _ota_confirm[1])
            if _ota_pending:
                publish_ota_status(CLIENT_ID, _ota_pending[0], _ota_pending[1])

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
