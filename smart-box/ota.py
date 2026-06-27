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


class OtaError(Exception):
    """Fehler während eines OTA-Vorgangs (Download/Verify/Apply)."""
    pass


# --- HTTP-Seam: auf dem Gerät über urequests, in Tests ersetzbar ---

def _default_http_stream(url, on_chunk, wdt=None):
    import urequests
    r = urequests.get(url)
    try:
        if r.status_code != 200:
            raise OSError("HTTP {} für {}".format(r.status_code, url))
        sock = r.raw
        while True:
            chunk = sock.read(CHUNK_SIZE)
            if not chunk:
                break
            on_chunk(chunk)
            if wdt is not None:
                wdt.feed()
    finally:
        r.close()


http_stream = _default_http_stream    # in Tests überschreibbar


def _ensure_dir(path):
    """Legt ein Verzeichnis (rekursiv) an, falls es fehlt."""
    parts = path.split("/")
    cur = ""
    for p in parts:
        if not p:
            continue
        cur = cur + "/" + p if cur else p
        try:
            os.mkdir(cur)
        except OSError:
            pass  # existiert bereits


def _ensure_parent(path):
    """Stellt sicher, dass das Elternverzeichnis einer Datei existiert."""
    idx = path.rfind("/")
    if idx > 0:
        _ensure_dir(path[:idx])


def _rmtree(path):
    """Löscht eine Datei oder ein Verzeichnis rekursiv (best effort)."""
    try:
        mode = os.stat(path)[0]
    except OSError:
        return
    if mode & 0x4000:  # Verzeichnis (S_IFDIR)
        for name in os.listdir(path):
            _rmtree(path + "/" + name)
        try:
            os.rmdir(path)
        except OSError:
            pass
    else:
        try:
            os.remove(path)
        except OSError:
            pass


def _download_to(url, dest, wdt=None):
    """Streamt eine URL in eine Datei; legt das Elternverzeichnis an."""
    _ensure_parent(dest)
    f = open(dest, "wb")
    try:
        http_stream(url, lambda c: f.write(c), wdt)
    finally:
        f.close()


def download_app(base_url, manifest_sha256="", wdt=None):
    """
    Lädt Manifest + alle App-Code-Dateien nach OTA_STAGING_DIR herunter und
    verifiziert sie. Wirft OtaError bei Fehler.

    base_url: z.B. "http://host/api/ota/app/0.7"
              Manifest:  {base_url}/manifest.json
              Dateien:   {base_url}/files/{path}
    manifest_sha256: erwarteter SHA-256 des Manifests (über MQTT geliefert,
              Vertrauensanker). Leer = Manifest-Prüfung überspringen.

    Gibt das Manifest-Dict zurück.
    """
    _rmtree(OTA_STAGING_DIR)
    _ensure_dir(OTA_STAGING_DIR)

    manifest_path = OTA_STAGING_DIR + "/manifest.json"
    try:
        _download_to(base_url + "/manifest.json", manifest_path, wdt)
    except Exception as e:
        raise OtaError("Manifest-Download fehlgeschlagen: {}".format(e))

    # Manifest-Integrität gegen den über MQTT gelieferten Hash prüfen
    if manifest_sha256 and not verify_file(manifest_path, manifest_sha256):
        raise OtaError("Manifest-SHA-256 stimmt nicht")

    try:
        with open(manifest_path, "r") as f:
            manifest = json.load(f)
    except (OSError, ValueError) as e:
        raise OtaError("Manifest nicht lesbar: {}".format(e))

    files = manifest.get("files", [])
    if not files:
        raise OtaError("Manifest enthält keine Dateien")

    for entry in files:
        rel = entry.get("path", "")
        expected = entry.get("sha256", "")
        if not rel:
            raise OtaError("Manifest-Eintrag ohne 'path'")
        # Pfad-Traversal verhindern (Datei darf das Staging-Verzeichnis nicht verlassen)
        if ".." in rel or rel.startswith("/"):
            raise OtaError("Ungültiger Pfad im Manifest: {}".format(rel))
        if not expected:
            raise OtaError("Manifest-Eintrag ohne 'sha256' für {}".format(rel))
        dest = OTA_STAGING_DIR + "/" + rel
        try:
            _download_to(base_url + "/files/" + rel, dest, wdt)
        except Exception as e:
            raise OtaError("Download fehlgeschlagen für {}: {}".format(rel, e))
        if not verify_file(dest, expected):
            raise OtaError("SHA-256 stimmt nicht für {}".format(rel))
        gc.collect()

    return manifest
