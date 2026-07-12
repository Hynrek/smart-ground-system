"""
Geräteeigener Zustand ohne MQTT-Abhängigkeit: Firmware-/Geräte-Config-Persistenz auf
Flash, autorisierte Geräte-IDs (Security-Allowlist) und Admin-Block/Unblock.

Diese Funktionen lagen bisher in mqttutils.py, waren dort aber nur örtlich
mitgeführt, nicht inhaltlich an MQTT gekoppelt (Task 12, MQTT-Ausbau). Mit der
Löschung von mqttutils.py brauchen sie ein neues, transport-unabhängiges Zuhause.
`load_firmware_config` und `update_device_pulses` sind aus demselben Grund
mitgezogen worden, obwohl sie im ursprünglichen Task-Plan nicht auf der
Verschiebungsliste standen: mqttutils.py wird komplett gelöscht, und main.py
importiert beide weiterhin – sie mussten also irgendwo hin, und keine der beiden
hat MQTT-Bezug.
"""
import json
from hardware import gpio_manager

# --- KONFIGURATION ---
FIRMWARE_CONFIG_PATH = "systemconfig/firmware_config.json"   # Release-Metadaten (app_version, capabilities) – wird nur per App-Code-OTA aktualisiert
DEVICE_CONFIG_PATH   = "userconfig/device_config.json"       # Dynamisch – aktive Geräte vom Backend
ADMIN_BLOCK_TOKEN    = "ADMIN"  # Token für manuelle Admin-Blockierung (keine Auto-Freigabe)

# --- Modul-globaler Zustand ---
_known_devices = set()  # Autorisierte Geräte-IDs
_blocked_devices = {}   # Admin-blockierte Geräte: device_id -> ADMIN_BLOCK_TOKEN
_wdt = None             # vom main gesetzt, für OTA-Download-Watchdog-Feeding

# Firmware-Konfiguration einmalig beim Modulstart laden – Version ändert sich nie zur Laufzeit
_firmware_config = None


def load_firmware_config():
    """
    Lädt die Firmware-Konfiguration aus dem systemconfig-Ordner.
    Diese Datei beschreibt das App-Code-Release (Version, Config-Schema-Version,
    Capabilities). Sie wird beim Provisionieren geflasht und danach nur durch
    App-Code-OTA-Releases aktualisiert — nie zur Laufzeit beschrieben.
    Gibt ein Dict zurück, oder {} bei Fehler/fehlender Datei.

    Format:
        { "app_version": "1.0",
          "firmware_version": "1.0",
          "config_schema_version": "1",
          "capabilities": { "GPIO": {...}, "LED": {...} } }
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
    """Reicht den Watchdog aus main.py hierher, damit OTA ihn füttern kann."""
    global _wdt
    _wdt = wdt


def update_device_pulses():
    """
    Aktualisiert alle aktiven WERFER-Pulse. Sollte regelmäßig aus der Main-Loop
    aufgerufen werden.
    """
    gpio_manager.tick()


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
