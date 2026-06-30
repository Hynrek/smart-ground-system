import gc
import json
import os
import network
from hardware import led, gpio_manager, feed_sleep_ms
import board as _board

# --- KONFIGURATION ---
RECONNECT_ATTEMPTS   = 12
RECONNECT_DELAY_S    = 10
FIRMWARE_CONFIG_PATH = "systemconfig/firmware_config.json"   # Statisch – beschreibt die Fähigkeiten der Box
DEVICE_CONFIG_PATH   = "userconfig/device_config.json"       # Dynamisch – aktive Geräte vom Backend
ADMIN_BLOCK_TOKEN    = "ADMIN"  # Token für manuelle Admin-Blockierung (keine Auto-Freigabe)

try:
    from umqtt.simple import MQTTClient
except Exception:
    try:
        from umqtt.robust import MQTTClient
    except Exception as e:
        print("MQTT-Bibliothek nicht verfügbar:", e)
        MQTTClient = None

# --- Modul-globaler Zustand ---
_client_id   = None
_mqtt_broker = None
_mqtt_port   = 1883
_mqtt_client = None     # Aktive persistente MQTT-Verbindung
_known_devices = set()  # Autorisierte Geräte-IDs
_blocked_devices = {}   # Admin-blockierte Geräte: device_id -> ADMIN_BLOCK_TOKEN
_wdt = None             # vom main gesetzt, für OTA-Download-Watchdog-Feeding

# Firmware-Konfiguration einmalig beim Modulstart laden – Version ändert sich nie zur Laufzeit
_firmware_config = None


# =============================================================================
# Config-Persistenz
# =============================================================================

def load_firmware_config():
    """
    Lädt die statische Firmware-Konfiguration aus dem systemconfig-Ordner.
    Diese Datei wird mit der Firmware geflasht und beschreibt die Fähigkeiten der Box
    (Version, unterstützte Gerätearten und Richtungen).
    Gibt ein Dict zurück, oder {} bei Fehler/fehlender Datei.

    Format:
        { "firmware_version": "0.6",
          "supported_device_kinds": ["GPIO", "LED"],
          "supported_directions": ["INPUT", "OUTPUT"] }
    """
    global _firmware_config
    if _firmware_config is not None:
        return _firmware_config
    try:
        with open(FIRMWARE_CONFIG_PATH, 'r') as f:
            data = json.load(f)
            print("Firmware-Konfiguration geladen: v{}".format(
                data.get("firmware_version", "?")))
            _firmware_config = data
            return _firmware_config
    except (OSError, ValueError) as e:
        print("Firmware-Konfiguration nicht gefunden:", e)
        return {}


def load_device_config():
    """
    Lädt die zuletzt gespeicherte Gerätekonfiguration von der Flash-Disk.
    Gibt ein Dict {"config_schema_version": "...", "devices": [...]} zurück,
    oder None bei Fehler/fehlender Datei.
    """
    try:
        with open(DEVICE_CONFIG_PATH, 'r') as f:
            data = json.load(f)
            devices = data.get("devices", [])
            print("Gerätekonfiguration geladen ({} Gerät(e)).".format(len(devices)))
            return data
    except (OSError, ValueError) as e:
        print("Keine gespeicherte Gerätekonfiguration gefunden:", e)
        return None


def save_device_config(devices):
    """
    Speichert die aktiven Geräte als JSON auf der Flash-Disk.
    Schreibt config_schema_version aus firmware_config.json mit, damit
    der Boot-Supervisor beim nächsten Start die Kompatibilität prüfen kann.

    :param devices: Liste von Geräte-Dicts aus dem Backend-Config-Push.
    """
    firmware = load_firmware_config()
    schema_version = firmware.get("config_schema_version", "1")
    try:
        with open(DEVICE_CONFIG_PATH, 'w') as f:
            json.dump({"config_schema_version": schema_version, "devices": devices}, f)
        print("Gerätekonfiguration gespeichert ({} Gerät(e)).".format(len(devices)))
    except OSError as e:
        print("Fehler beim Speichern der Gerätekonfiguration:", e)


def set_watchdog(wdt):
    """Reicht den Watchdog aus main.py ins MQTT-Modul, damit OTA ihn füttern kann."""
    global _wdt
    _wdt = wdt


# =============================================================================
# MQTT-Callbacks
# =============================================================================

# =============================================================================
# Sicherheitslogik - Befehls-Blocking
# =============================================================================

def _update_known_devices(devices):
    """
    Aktualisiert die Liste autorisierter Geräte aus der Config.
    Entfernt Admin-Blockierungen für nicht mehr konfigurierte Geräte,
    damit _blocked_devices nicht unbegrenzt wächst.
    """
    global _known_devices
    new_ids = set()
    for dev in devices:
        device_id = dev.get("device_id", "")
        if device_id:
            new_ids.add(device_id)

    # Einträge für nicht mehr konfigurierte Geräte bereinigen
    stale = _known_devices - new_ids
    for device_id in stale:
        _blocked_devices.pop(device_id, None)

    _known_devices = new_ids
    print("Autorisierte Geräte aktualisiert: {} Gerät(e)".format(len(_known_devices)))


def _is_device_blocked(device_id):
    """Prüft, ob ein Gerät administrativ blockiert ist (kein Auto-Block mehr)."""
    return _blocked_devices.get(device_id) == ADMIN_BLOCK_TOKEN


def _admin_block_device(device_id):
    """
    Blockiert ein Gerät auf Anforderung des Backends (z.B. Wartung, Befüllung).
    Die Blockierung läuft NICHT automatisch ab — nur via UNBLOCK freizugeben.
    """
    if device_id not in _known_devices:
        print("Fehler: Unbekanntes Gerät kann nicht blockiert werden: device={}".format(device_id))
        return False

    _blocked_devices[device_id] = ADMIN_BLOCK_TOKEN
    print("Gerät administrativ blockiert: device={}".format(device_id))
    return True


def _admin_unblock_device(device_id):
    """
    Hebt die Blockierung eines Geräts auf (Backend-Anforderung).
    """
    if device_id not in _blocked_devices:
        print("Warnung: Gerät ist nicht blockiert: device={}".format(device_id))
        return False

    block_info = _blocked_devices[device_id]
    if block_info != ADMIN_BLOCK_TOKEN:
        print("Warnung: Gerät hat keine Admin-Blockierung: device={}".format(device_id))
        return False

    _blocked_devices.pop(device_id)
    print("Gerät entsperrt: device={}".format(device_id))
    return True


# =============================================================================
# Config-Callbacks
# =============================================================================

def _handle_config(payload_bytes):
    """
    Verarbeitet eine Config-Payload vom Backend.

    Payload-Schema (JSON):
        { "devices": [ { "device_id", "alias", "direction", "device",
                         "command", "signal_duration_ms", "blocked" }, ... ] }

    Firmware-Informationen (Version, Box-Typ, Fähigkeiten) sind in
    systemconfig/firmware_config.json hinterlegt und werden vom Backend NICHT überschrieben.

    Schritte:
      1. JSON parsen
      2. Gerätelist extrahieren
      3. GPIO-Manager neu aufbauen
      4. Nur die Geräteliste auf Flash speichern
      5. ACK an Backend senden
    """
    try:
        data = json.loads(payload_bytes)
    except ValueError as e:
        print("Config-Payload konnte nicht geparst werden:", e)
        return

    devices = data.get("devices", [])
    del data
    print("Config-Push empfangen mit {} Gerät(en).".format(len(devices)))

    # Autorisierte Geräte aktualisieren (Security)
    _update_known_devices(devices)

    # GPIO-Pins neu initialisieren
    gpio_manager.reset()
    gpio_manager.setup(devices)

    # ACK nur senden, wenn mindestens ein Gerät erfolgreich initialisiert wurde
    initialized = len(gpio_manager.known_ids())
    if initialized == 0 and len(devices) > 0:
        print("Config-ACK verweigert: kein Gerät erfolgreich initialisiert.")
        return

    # Nur Geräteliste auf Flash sichern – keine Firmware-Infos
    save_device_config(devices)

    # ACK an Backend publizieren
    _send_config_ack()


def _send_config_ack():
    """Sendet eine Config-ACK-Nachricht an das Backend über die bestehende Verbindung."""
    if _mqtt_client is None or _client_id is None:
        return
    ack_topic = "smartboxes/{}/config/ack".format(_client_id)
    try:
        _mqtt_client.publish(ack_topic, b"OK")
        print("Config-ACK gesendet auf:", ack_topic)
    except Exception as e:
        print("Config-ACK Fehler:", e)


def _send_device_command_ack(device_id):
    """Sendet eine Device-Command-ACK-Nachricht an das Backend über die bestehende Verbindung."""
    if _mqtt_client is None or _client_id is None:
        return
    ack_topic = "smartboxes/{}/device/{}/executed".format(_client_id, device_id)
    try:
        _mqtt_client.publish(ack_topic, b"OK")
        print("Device-ACK gesendet auf:", ack_topic)
    except Exception as e:
        print("Device-ACK Fehler:", e)


def message_callback(topic, msg):
    """
    Zentraler MQTT-Message-Handler mit Sicherheits-Blocking.

    Unterstützte Topics:
      smartboxes/{mac}/config   → Config-Push vom Backend
      smartboxes/{mac}/command  → Steuerbefehl für ein Gerät

    Befehlsformat (JSON):
        { "command": "ON"|"OFF", "commandId": "...", "deviceId": "<uuid>" }

    SICHERHEIT:
      - Nur bekannte (konfigurierte) Geräte werden akzeptiert (Allowlist)
      - Administrativ blockierte Geräte werden ignoriert (BLOCK/UNBLOCK)
      - ON wird ignoriert, solange das Gerät beschäftigt ist (laufender Puls)
    """
    topic_str = topic.decode() if isinstance(topic, bytes) else topic
    print("Nachricht empfangen: Topic={}".format(topic_str))

    try:
        if topic_str.endswith("/config"):
            _handle_config(msg)
            return

        if topic_str.endswith("/ota"):
            import ota
            ota.handle_command(
                msg,
                lambda phase, version, progress=0, detail="":
                    publish_ota_status(_client_id, phase, version, progress, detail),
                _wdt)
            return

        # --- Befehl parsen ---
        try:
            data = json.loads(msg)
            command = data.get("command", "").upper()
            device_id = data.get("deviceId", None)
            signal_duration_ms = data.get("signalDurationMs", None)
            del data
        except (ValueError, AttributeError) as e:
            print("Fehler: Ungültiges JSON-Format:", e)
            return

        # --- Befehlsvalidierung ---
        if not device_id:
            print("Fehler: deviceId erforderlich, Befehl blockiert")
            return

        if command not in ("ON", "OFF", "BLOCK", "UNBLOCK"):
            print("Fehler: Ungültiger Befehl '{}', blockiert".format(command))
            return

        # --- Admin-Befehle (BLOCK/UNBLOCK) ---
        if command == "BLOCK":
            if _admin_block_device(device_id):
                _send_device_command_ack(device_id)
            return

        if command == "UNBLOCK":
            if _admin_unblock_device(device_id):
                _send_device_command_ack(device_id)
            return

        # --- Sicherheits-Checks für ON/OFF ---
        if device_id not in _known_devices:
            print("Fehler: Unbekanntes Gerät blockiert: device={}".format(device_id))
            return

        if _is_device_blocked(device_id):
            print("Fehler: Gerät blockiert: device={}".format(device_id))
            return

        # --- Befehl ausführen (ON/OFF) ---
        success = False
        if command == "ON":
            success = gpio_manager.set(device_id, 1, signal_duration_ms)
        elif command == "OFF":
            success = gpio_manager.set(device_id, 0, signal_duration_ms)

        # ACK nur bei tatsächlicher Annahme (busy/blockiert -> success False, kein ACK)
        if success:
            _send_device_command_ack(device_id)
            print("Befehl erfolgreich: device={} command={}".format(device_id, command))

    finally:
        gc.collect()


# =============================================================================
# MQTT-Verbindung
# =============================================================================

def connect_mqtt(client_id, mqtt_broker, port=1883):
    """
    Verbindet mit dem MQTT-Broker und abonniert die gerätespezifischen Topics.
    Speichert den aktiven Client global, damit ACK- und Discovery-Publishes ihn
    wiederverwenden können (keine ephemeren Verbindungen).

    Abonnierte Topics:
      - smartboxes/{mac}/command  → Steuerbefehle
      - smartboxes/{mac}/config   → Config-Push vom Backend

    Gibt den verbundenen MQTTClient zurück, oder None bei Fehler.
    """
    global _client_id, _mqtt_broker, _mqtt_port, _mqtt_client

    if MQTTClient is None:
        print("MQTT-Bibliothek nicht verfügbar – Verbindung nicht möglich.")
        return None

    _client_id   = client_id
    _mqtt_broker = mqtt_broker
    _mqtt_port   = port

    command_topic = "smartboxes/{}/command".format(client_id)
    config_topic  = "smartboxes/{}/config".format(client_id)
    ota_topic     = "smartboxes/{}/ota".format(client_id)

    try:
        client = MQTTClient(client_id, mqtt_broker, port=port, keepalive=60)
        client.set_callback(message_callback)
        # connect() blockiert (umqtt.simple kennt kein socket_timeout). Bei einem sehr
        # langsamen/nicht erreichbaren Broker (>WDT_TIMEOUT_MS) setzt der Watchdog die Box
        # zurück. Das ist gewollte Recovery – identisch zum Reset bei Verbindungsfehler.
        client.connect()
        client.subscribe(command_topic)
        client.subscribe(config_topic)
        client.subscribe(ota_topic)
        _mqtt_client = client  # Globale Referenz für ACK/Discovery-Publishes
        print("MQTT verbunden. Abonniert:", command_topic, ",", config_topic, "und", ota_topic)
        return client
    except Exception as e:
        print("MQTT-Verbindung fehlgeschlagen:", e)
        _mqtt_client = None
        return None


def reconnect_mqtt(wdt=None):
    """
    Stellt die MQTT-Verbindung mit den zuletzt verwendeten Parametern wieder her.
    Füttert den optionalen Watchdog während der Wartezeit.
    """
    for attempt in range(RECONNECT_ATTEMPTS):
        print("MQTT Wiederverbindung, Versuch", attempt + 1)
        client = connect_mqtt(_client_id, _mqtt_broker, _mqtt_port)
        if client:
            return client
        feed_sleep_ms(RECONNECT_DELAY_S * 1000, wdt)
    return None


def update_device_pulses():
    """
    Aktualisiert alle aktiven WERFER-Pulse. Sollte regelmäßig aus der Main-Loop
    aufgerufen werden.
    """
    gpio_manager.tick()


def publish_discovery(client_id):
    """
    Veröffentlicht einmalig beim Boot eine Discovery-Nachricht über die bestehende
    MQTT-Verbindung, damit das Backend die Box erkennt und einen Config-Push auslöst.
    Firmware-Version kommt aus systemconfig/firmware_config.json, Box-Typ aus dem board-Modul.

    Topic:   smartboxes/discovery
    Payload: { "mac", "appVersion", "firmwareVersion", "boxType", "ip" }
    """
    if _mqtt_client is None:
        print("MQTT-Verbindung nicht verfügbar – Discovery nicht möglich.")
        return False

    firmware = load_firmware_config()
    app_version = firmware.get("app_version", firmware.get("firmware_version", "unknown"))

    try:
        kernel = "micropython-" + os.uname().release
    except Exception as e:
        print("Kernel-Version nicht lesbar:", e)
        kernel = "micropython-unknown"

    try:
        ip_address = network.WLAN(network.STA_IF).ifconfig()[0]
        payload = json.dumps({
            "mac":             client_id,
            "appVersion":      app_version,
            "firmwareVersion": kernel,
            "boxType":         _board.BOX_TYPE,
            "ip":              ip_address,
        })
        _mqtt_client.publish("smartboxes/discovery", payload)
        print("Discovery gesendet:", payload)
        return True
    except Exception as e:
        print("Discovery fehlgeschlagen:", e)
        return False


def publish_heartbeat(client_id):
    """
    Veröffentlicht einen Heartbeat über die bestehende MQTT-Verbindung. Das Backend
    (SmartBoxStatusHandler) aktualisiert damit lastSeen + ONLINE, OHNE einen Config-Push
    auszulösen. Ersetzt das frühere periodische Discovery.

    Topic:   smartboxes/{mac}/status
    Payload: { "mac": "..." }
    """
    if _mqtt_client is None:
        print("MQTT-Verbindung nicht verfügbar – Heartbeat nicht möglich.")
        return False
    try:
        topic = "smartboxes/{}/status".format(client_id)
        payload = json.dumps({"mac": client_id})
        _mqtt_client.publish(topic, payload)
        print("Heartbeat gesendet:", topic)
        return True
    except Exception as e:
        print("Heartbeat fehlgeschlagen:", e)
        return False


def publish_ota_status(client_id, phase, version, progress=0, detail=""):
    """
    Meldet den OTA-Fortschritt an das Backend.

    Topic:   smartboxes/{mac}/ota/status
    Payload: { "version", "phase", "progress", "detail" }
    """
    if _mqtt_client is None:
        print("MQTT-Verbindung nicht verfügbar – OTA-Status nicht möglich.")
        return False
    try:
        topic = "smartboxes/{}/ota/status".format(client_id)
        payload = json.dumps({"version": version, "phase": phase,
                              "progress": progress, "detail": detail})
        _mqtt_client.publish(topic, payload)
        print("OTA-Status gesendet:", phase, version)
        return True
    except Exception as e:
        print("OTA-Status Fehler:", e)
        return False
