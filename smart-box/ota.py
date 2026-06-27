import gc
import json
import os
import hashlib

# --- KONFIGURATION ---
OTA_STAGING_DIR    = "ota_staging"            # temporäres Verzeichnis für heruntergeladene Dateien
OTA_BACKUP_DIR     = "ota_backup"             # Sicherungskopie der vorherigen App-Code-Dateien
OTA_MARKER_PENDING = "ota_pending"            # Marker: Apply hat begonnen
OTA_MARKER_APPLIED = "ota_applied"            # Marker: Apply abgeschlossen
OTA_STATE_PATH     = "userconfig/ota_state.json"
CHUNK_SIZE         = 1024                      # Lesepuffer für Streaming-Download
MAX_PROBATION_BOOTS = 3                        # Boot-Versuche, bevor automatisch zurückgerollt wird
_VALID_TYPES       = ("APP", "FIRMWARE")

# --- Modul-globaler Zustand ---
_busy = False


def is_busy():
    """True, solange ein OTA-Vorgang läuft (verhindert parallele Updates)."""
    return _busy


def parse_command(payload_bytes):
    """
    Parst und validiert eine /ota-Befehls-Payload.

    Erwartet JSON:
        { "type": "APP"|"FIRMWARE", "version": "...", "url": "...",
          "sha256": "<hex>", "size": <int> }

    Gibt ein normalisiertes Dict zurück. Wirft ValueError bei ungültiger Payload.

    # sha256 ist der Hash des herunterzuladenden Manifests (APP) bzw. Images (FIRMWARE); er wird beim Download geprüft, nicht hier. Optional in diesem Parser.
    """
    try:
        data = json.loads(payload_bytes)
    except (ValueError, TypeError) as e:
        raise ValueError("OTA-Payload ist kein gültiges JSON: {}".format(e))

    ota_type = data.get("type", "")
    version  = data.get("version", "")
    url      = data.get("url", "")
    sha256   = data.get("sha256", "")

    if ota_type not in _VALID_TYPES:
        raise ValueError("Ungültiger OTA-Typ: {}".format(ota_type))
    if not version:
        raise ValueError("OTA-Befehl: 'version' fehlt oder leer")
    if not url:
        raise ValueError("OTA-Befehl: 'url' fehlt oder leer")

    return {
        "type": ota_type,
        "version": version,
        "url": url,
        "sha256": sha256,
        "size": data.get("size", 0),
    }


def verify_file(path, expected_hex):
    """
    Berechnet den SHA-256 einer Datei im Streaming-Verfahren und vergleicht ihn
    mit dem erwarteten Hex-Digest. Gibt True/False zurück, niemals Exception nach aussen.
    """
    if not expected_hex:
        return False
    try:
        h = hashlib.sha256()
        with open(path, "rb") as f:
            while True:
                chunk = f.read(CHUNK_SIZE)
                if not chunk:
                    break
                h.update(chunk)
        digest = "".join("{:02x}".format(b) for b in h.digest())
        return digest == expected_hex
    except OSError as e:
        print("Verify fehlgeschlagen für {}: {}".format(path, e))
        return False
    finally:
        gc.collect()
