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
from box_provisioning import discover_and_provision, send_heartbeat
import box_api_client
from mqttutils import load_device_config, load_firmware_config, update_device_pulses
from mqttutils import _update_known_devices, set_watchdog
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


try:
    # Status-LED: 3x blinken signalisiert erfolgreichen Boot
    status_blink()

    config = load_config()

    # Unvollständiges Profil -> Setup-Modus starten.
    # broker_ip wird nicht mehr geprüft (kein MQTT-Broker mehr) – stattdessen ist
    # box_api_base_url das Pflichtfeld für den HTTPS-Transport zum Node-box-api.
    # HINWEIS: accesspoint.py (Captive Portal) schreibt box_api_base_url noch nicht in
    # client_config.json – das ist ausserhalb des Umfangs dieser Aufgabe (siehe main.py
    # / box_provisioning.py / tests/test_box_provisioning.py im Task-Brief) und muss vor
    # dem produktiven Einsatz des Captive-Portal-Flows nachgezogen werden.
    if not config or not config.get('client_network_ssid') or not config.get('box_api_base_url'):
        print("Kein gültiges WiFi-/box-api-Profil. Starte Access Point für Einrichtung...")
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

            # box-api verbinden: Discovery + Provisioning in einem einzigen HTTPS-Call
            # (ersetzt sowohl den früheren MQTT-Connect als auch das separate
            # publish_discovery() – discover_and_provision() deckt beides ab, siehe
            # box_provisioning.py). box_api_base_url ist Pflichtfeld (siehe Config-Check
            # oben); firmware_version bleibt "unknown" (Default von discover_and_provision) –
            # der bisherige Kernel-String (os.uname().release) würde einen neuen os-Import
            # in main.py erfordern, was ausserhalb dieser Aufgabe liegt und hier bewusst
            # nicht nachgezogen wird.
            app_version = firmware_cfg.get("app_version", firmware_cfg.get("firmware_version", "unknown"))
            capabilities_json = json.dumps(firmware_cfg.get("capabilities", {}))
            box_api_base_url = config['box_api_base_url']

            try:
                discover_and_provision(CLIENT_ID, box_api_base_url,
                                        app_version=app_version,
                                        box_type=board.BOX_TYPE,
                                        capabilities_json=capabilities_json)
            except Exception as e:
                print("box-api Discovery/Provisioning fehlgeschlagen. Neustart in 5 Sekunden...", e)
                time.sleep(5)
                machine.reset()

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
            # OTA-Statusmeldung entfällt mit MQTT — kein Ersatz in #7, siehe node-channel (#4).
            # box-api's Status-Endpunkt (Task 7) meldet nur generischen Box-Status, keinen
            # OTA-spezifischen Fortschritt (phase/progress/detail); ein Ersatz ist nicht
            # Teil dieser Aufgabe (Task 11) und wird von Task 12 entschieden/entfernt. Bis
            # dahin werden die hier ermittelten Berichte nur geloggt, nicht übertragen –
            # kein toter Call gegen einen nicht mehr existierenden MQTT-Client.
            if _ota_confirm:
                print("OTA-Bericht (nicht übertragen, kein box-api-Ersatz):", _ota_confirm)
            if _ota_pending:
                print("OTA-Bericht (nicht übertragen, kein box-api-Ersatz):", _ota_pending)

            # Hauptschleife: Heartbeat kooperativ verarbeiten (kein MQTT-Polling mehr)
            while True:
                wdt.feed()

                # WiFi-Verbindung prüfen und bei Bedarf wiederherstellen. Kein MQTT-Reconnect
                # mehr danach – es existiert keine MQTT-Verbindung, die wiederhergestellt
                # werden müsste; box-api-Aufrufe (Heartbeat) laufen ohnehin je Tick neu über
                # HTTPS und scheitern/gelingen unabhängig vom WLAN-Zustand des vorigen Ticks.
                if not wlan.isconnected():
                    print("WiFi-Verbindung verloren. Wiederverbindung wird versucht...")
                    if not reconnect_wifi(config['client_network_ssid'], config['client_network_pw'], wdt):
                        print("WiFi Wiederverbindung fehlgeschlagen. Neustart...")
                        machine.reset()

                # Aktualisiere WERFER-Pulse (auto-off nach Duration)
                update_device_pulses()

                # Heartbeat periodisch senden (Liveness ohne Config-Push). send_heartbeat()
                # selbst fängt keine Fehler ab (siehe box_provisioning.py) – hier lokal
                # abfangen, damit ein vorübergehend nicht erreichbares box-api (z.B. Node
                # kurz offline) nicht denselben Effekt hat wie ein Absturz (Watchdog-Reset).
                # Entspricht dem bisherigen Verhalten von publish_heartbeat(), das Fehler
                # intern loggte und False zurückgab statt zu eskalieren.
                now = time.time()
                if now - last_publish >= PUBLISH_INTERVAL_S:
                    try:
                        send_heartbeat(CLIENT_ID, box_api_base_url, "idle")
                    except Exception as e:
                        print("Heartbeat fehlgeschlagen:", e)
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
    # Kein MQTT-Client mehr zu trennen (HTTPS/box-api ist zustandslos je Call) – der
    # frühere "if client: client.disconnect()"-Schritt entfällt ersatzlos.
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
