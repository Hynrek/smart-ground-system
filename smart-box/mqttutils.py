import gc
import json
import time
import network
from hardware import led, gpio_manager

# --- KONFIGURATION ---
RECONNECT_ATTEMPTS   = 12
RECONNECT_DELAY_S    = 10
FIRMWARE_CONFIG_PATH = "systemconfig/firmware_config.json"   # Statisch – beschreibt die Fähigkeiten der Box
DEVICE_CONFIG_PATH   = "userconfig/device_config.json"       # Dynamisch – aktive Geräte vom Backend
RATE_LIMIT_S         = 0.1    # Min. Sekunden zwischen Befehlen pro Gerät
MAX_FAILED_ATTEMPTS  = 5      # Max. gescheiterte Versuche bevor Blocking
BLOCK_DURATION_S     = 300    # Blockierungszeit nach MAX_FAILED_ATTEMPTS (5 Min)
ADMIN_BLOCK_TOKEN    = "ADMIN"  # Spezial-Token für manuelle Admin-Blockierung (keine Auto-Freigabe)

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
_known_devices = set()  # Authorized device IDs
_last_command_time = {}  # Rate limiting: device_id -> last_command_timestamp
_failed_commands = {}  # Tracking failed commands: device_id -> count
_blocked_devices = {}  # Blocked devices: device_id -> unblock_timestamp

# Firmware-Konfiguration einmalig beim Modulstart laden – Version ändert sich nie zur Laufzeit
_firmware_config = None


# =============================================================================
# Config-Persistenz
# =============================================================================

def load_firmware_config():
    """
    Lädt die statische Firmware-Konfiguration aus dem systemconfig-Ordner.
    Diese Datei wird mit der Firmware geflasht und beschreibt die Fähigkeiten der Box
    (Version, Box-Typ, unterstützte Gerätearten und Richtungen).
    Gibt ein Dict zurück, oder {} bei Fehler/fehlender Datei.

    Format:
        { "firmware_version": "0.6", "box_type": "pico2w",
          "supported_device_kinds": ["GPIO", "LED"],
          "supported_directions": ["INPUT", "OUTPUT"] }
    """
    global _firmware_config
    if _firmware_config is not None:
        return _firmware_config
    try:
        with open(FIRMWARE_CONFIG_PATH, 'r') as f:
            data = json.load(f)
            print("Firmware-Konfiguration geladen: v{} ({})".format(
                data.get("firmware_version", "?"), data.get("box_type", "?")))
            _firmware_config = data
            return _firmware_config
    except (OSError, ValueError) as e:
        print("Firmware-Konfiguration nicht gefunden:", e)
        return {}


def load_device_config():
    """
    Lädt die zuletzt gespeicherte Gerätekonfiguration von der Flash-Disk.
    Diese Datei wird vom Backend via MQTT-Config-Push geschrieben und enthält
    ausschliesslich die aktiven Geräte dieser SmartBox.
    Gibt eine Liste von Geräte-Dicts zurück, oder [] bei Fehler/fehlender Datei.

    Format:
        { "devices": [ { "device_id": "...", "alias": "...", "device": "GPIO",
                         "direction": "OUTPUT", "command": "15",
                         "signal_duration_ms": 500, "delay_ms": null, "blocked": false }, ... ] }
    """
    try:
        with open(DEVICE_CONFIG_PATH, 'r') as f:
            data = json.load(f)
            devices = data.get("devices", [])
            print("Gerätekonfiguration geladen ({} Gerät(e)).".format(len(devices)))
            return devices
    except (OSError, ValueError) as e:
        print("Keine gespeicherte Gerätekonfiguration gefunden:", e)
        return []


def save_device_config(devices):
    """
    Speichert die aktiven Geräte als JSON auf der Flash-Disk.
    Firmware-Informationen gehören NICHT in diese Datei – sie liegen in
    systemconfig/firmware_config.json.

    :param devices: Liste von Geräte-Dicts aus dem Backend-Config-Push.
    """
    try:
        with open(DEVICE_CONFIG_PATH, 'w') as f:
            json.dump({"devices": devices}, f)
        print("Gerätekonfiguration gespeichert ({} Gerät(e)).".format(len(devices)))
    except OSError as e:
        print("Fehler beim Speichern der Gerätekonfiguration:", e)


# =============================================================================
# MQTT-Callbacks
# =============================================================================

# =============================================================================
# Sicherheitslogik - Befehls-Blocking
# =============================================================================

def _update_known_devices(devices):
    """
    Aktualisiert die Liste bekannter (autorisierter) Geräte aus der Config.
    Nur diese Geräte dürfen Befehle empfangen.
    Entfernt veraltete Einträge aus Rate-Limit- und Fehlerzähler-Dicts,
    damit diese bei wechselnden Geräte-IDs nicht unbegrenzt wachsen.
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
        _last_command_time.pop(device_id, None)
        _failed_commands.pop(device_id, None)

    _known_devices = new_ids
    print("Autorisierte Geräte aktualisiert: {} Gerät(e)".format(len(_known_devices)))


def _is_device_blocked(device_id):
    """
    Prüft, ob ein Gerät aktuell blockiert ist.
    - Admin-Blockierung hat kein Ablaufdatum (manuell freizugeben)
    - Auto-Blockierung läuft ab nach BLOCK_DURATION_S
    """
    if device_id not in _blocked_devices:
        return False

    block_info = _blocked_devices[device_id]

    # Admin-Blockierung (manuell von Backend) → läuft nicht ab
    if block_info == ADMIN_BLOCK_TOKEN:
        return True

    # Auto-Blockierung (nach fehlgeschlagenen Versuchen) → läuft ab
    if time.time() >= block_info:
        _blocked_devices.pop(device_id)
        print("Auto-Blockierung aufgehoben: device={}".format(device_id))
        return False
    return True


def _check_rate_limit(device_id):
    """
    Prüft, ob der Befehl Rate-Limit-Anforderungen erfüllt.
    Gibt True zurück, wenn der Befehl akzeptiert werden kann.
    """
    now = time.time()
    last_time = _last_command_time.get(device_id, 0)
    if now - last_time < RATE_LIMIT_S:
        print("Rate-Limit überschritten: device={}".format(device_id))
        return False
    _last_command_time[device_id] = now
    return True


def _record_failed_attempt(device_id):
    """
    Registriert einen gescheiterten Befehlsversuch.
    Nach MAX_FAILED_ATTEMPTS wird das Gerät blockiert.
    """
    count = _failed_commands.get(device_id, 0) + 1
    _failed_commands[device_id] = count
    print("Gescheiterter Versuch für device={}: {}/{}".format(
        device_id, count, MAX_FAILED_ATTEMPTS))

    if count >= MAX_FAILED_ATTEMPTS:
        _blocked_devices[device_id] = time.time() + BLOCK_DURATION_S
        print("Gerät blockiert: device={} Duration={}s".format(
            device_id, BLOCK_DURATION_S))


def _clear_failed_attempts(device_id):
    """
    Setzt den Fehlerzähler zurück nach erfolgreichem Befehl.
    """
    _failed_commands.pop(device_id, None)


def _admin_block_device(device_id):
    """
    Blockiert ein Gerät auf Anforderung des Backends (z.B. Wartung, Befüllung).
    Die Blockierung läuft NICHT automatisch ab — nur via UNBLOCK freizugeben.
    """
    if device_id not in _known_devices:
        print("Fehler: Unbekanntes Gerät kann nicht blockiert werden: device={}".format(device_id))
        return False

    _blocked_devices[device_id] = ADMIN_BLOCK_TOKEN
    _clear_failed_attempts(device_id)
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
    _clear_failed_attempts(device_id)
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
                         "command", "signal_duration_ms", "delay_ms", "blocked" }, ... ] }

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
      - Nur bekannte (konfigurierte) Geräte werden akzeptiert
      - Blockierte Geräte werden ignoriert
      - Rate-Limiting pro Gerät
      - Fehlgeschlagene Versuche werden gezählt
    """
    topic_str = topic.decode() if isinstance(topic, bytes) else topic
    print("Nachricht empfangen: Topic={}".format(topic_str))

    try:
        if topic_str.endswith("/config"):
            _handle_config(msg)
            return

        # --- Befehl parsen ---
        try:
            data = json.loads(msg)
            command = data.get("command", "").upper()
            device_id = data.get("deviceId", None)
            signal_duration_ms = data.get("signalDurationMs", None)
            delay_signal_duration_ms = data.get("delaySignalDurationMs", None)
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
            success = _admin_block_device(device_id)
            if success:
                _send_device_command_ack(device_id)
            return

        if command == "UNBLOCK":
            success = _admin_unblock_device(device_id)
            if success:
                _send_device_command_ack(device_id)
            return

        # --- Sicherheits-Checks für ON/OFF ---
        if device_id not in _known_devices:
            print("Fehler: Unbekanntes Gerät blockiert: device={}".format(device_id))
            _record_failed_attempt(device_id)
            return

        if _is_device_blocked(device_id):
            print("Fehler: Gerät blockiert: device={}".format(device_id))
            return

        if not _check_rate_limit(device_id):
            _record_failed_attempt(device_id)
            return

        # --- Befehl ausführen (ON/OFF) ---
        success = False
        if command == "ON":
            success = gpio_manager.set(device_id, 1, signal_duration_ms, delay_signal_duration_ms)
        elif command == "OFF":
            success = gpio_manager.set(device_id, 0, signal_duration_ms, delay_signal_duration_ms)

        if success:
            _clear_failed_attempts(device_id)
            _send_device_command_ack(device_id)
            print("Befehl erfolgreich: device={} command={}".format(device_id, command))
        else:
            _record_failed_attempt(device_id)

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

    try:
        client = MQTTClient(client_id, mqtt_broker, port=port, keepalive=60)
        client.set_callback(message_callback)
        client.connect()
        client.subscribe(command_topic)
        client.subscribe(config_topic)
        _mqtt_client = client  # Globale Referenz für ACK/Discovery-Publishes
        print("MQTT verbunden. Abonniert:", command_topic, "und", config_topic)
        return client
    except Exception as e:
        print("MQTT-Verbindung fehlgeschlagen:", e)
        _mqtt_client = None
        return None


def reconnect_mqtt():
    """
    Versucht die MQTT-Verbindung mit den zuletzt verwendeten Parametern wiederherzustellen.
    """
    for attempt in range(RECONNECT_ATTEMPTS):
        print("MQTT Wiederverbindung, Versuch", attempt + 1)
        client = connect_mqtt(_client_id, _mqtt_broker, _mqtt_port)
        if client:
            return client
        time.sleep(RECONNECT_DELAY_S)
    return None


def update_device_pulses():
    """
    Aktualisiert alle aktiven WERFER-Pulse. Sollte regelmäßig aus der Main-Loop
    aufgerufen werden.
    """
    gpio_manager.update_pulses()


def publish_discovery(broker, port, client_id):
    """
    Veröffentlicht eine Discovery-Nachricht über die bestehende MQTT-Verbindung,
    damit das Backend die Box erkennt und automatisch einen Config-Push auslöst.

    Die Firmware-Version wird aus systemconfig/firmware_config.json gelesen –
    sie wird NIE hartcodiert.

    Topic:   smartboxes/discovery
    Payload: { "mac": "...", "firmwareVersion": "...", "boxType": "...", "ip": "..." }
    """
    if _mqtt_client is None:
        print("MQTT-Verbindung nicht verfügbar – Discovery nicht möglich.")
        return False

    firmware = load_firmware_config()
    firmware_version = firmware.get("firmware_version", "unknown")
    box_type = firmware.get("box_type", "unknown")

    try:
        ip_address = network.WLAN(network.STA_IF).ifconfig()[0]
        payload = json.dumps({
            "mac":             client_id,
            "firmwareVersion": firmware_version,
            "boxType":         box_type,
            "ip":              ip_address,
        })
        _mqtt_client.publish("smartboxes/discovery", payload)
        print("Discovery gesendet:", payload)
        return True
    except Exception as e:
        print("Discovery fehlgeschlagen:", e)
        return False
