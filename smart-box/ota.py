import gc
import json
import os
import hashlib
import box_api_client

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


# --- HTTP-Seam: auf dem Gerät über box_api_client (HTTPS, gepinnte Node-CA), in Tests ersetzbar ---
# Ersetzt die frühere direkte urequests-Verbindung gegen den Hub: der Download läuft jetzt
# über box_api_client.get_bytes() gegen das Node-box-api (/box-api/v1/ota/...), siehe
# BoxOtaController. get_bytes() liefert den kompletten Inhalt auf einmal (kein Streaming-
# Socket wie zuvor bei urequests) – hier in CHUNK_SIZE-Stücke zerlegt, damit die
# nachgelagerte Pipeline (Datei schreiben, Hash/Flash-Block-Verarbeitung, Watchdog-Feeding)
# unverändert bleibt.

def _default_http_stream(url, on_chunk, wdt=None):
    data = box_api_client.get_bytes(url)
    for i in range(0, len(data), CHUNK_SIZE):
        on_chunk(data[i:i + CHUNK_SIZE])
        if wdt is not None:
            wdt.feed()


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

    base_url: z.B. "https://node.local:8443/box-api/v1/ota/app/1.0.0" (Node-box-api,
              siehe BoxOtaController)
              Manifest:  {base_url}/manifest.json
              Dateien:   {base_url}/files/{path}
    manifest_sha256: erwarteter SHA-256 des Manifests (Vertrauensanker, geliefert vom
              OTA-Trigger). Leer = Manifest-Prüfung überspringen.

    Gibt das Manifest-Dict zurück.
    """
    _rmtree(OTA_STAGING_DIR)
    _ensure_dir(OTA_STAGING_DIR)

    manifest_path = OTA_STAGING_DIR + "/manifest.json"
    try:
        _download_to(base_url + "/manifest.json", manifest_path, wdt)
    except Exception as e:
        raise OtaError("Manifest-Download fehlgeschlagen: {}".format(e))

    # Manifest-Integrität gegen den vom OTA-Trigger gelieferten Hash prüfen
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
        # Geräteeigener Zustand (WLAN-Zugangsdaten, device_config, ota_state) darf
        # von einem Release niemals überschrieben werden
        if rel.startswith("userconfig/"):
            raise OtaError("Geschützter Pfad im Manifest: {}".format(rel))
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


def _copy_file(src, dst):
    """Kopiert eine Datei in CHUNK_SIZE-Blöcken (rename geht nicht über alle FS-Grenzen)."""
    _ensure_parent(dst)
    with open(src, "rb") as fin:
        with open(dst, "wb") as fout:
            while True:
                chunk = fin.read(CHUNK_SIZE)
                if not chunk:
                    break
                fout.write(chunk)


def _write_marker(path, text):
    with open(path, "w") as f:
        f.write(text)


def apply_app(manifest, live_root=""):
    """
    Aktiviert die in OTA_STAGING_DIR liegende, bereits verifizierte App-Code-Version:
      1. pending-Marker schreiben (Liste der betroffenen Dateien)
      2. jede Live-Datei nach OTA_BACKUP_DIR sichern
      3. jede Staging-Datei über die Live-Datei kopieren
      4. applied-Marker schreiben, dann beide Marker + Staging aufräumen

    live_root erlaubt Tests, eine andere Wurzel zu verwenden (auf dem Gerät: "" = FS-Wurzel).
    """
    paths = [e.get("path", "") for e in manifest.get("files", []) if e.get("path")]

    _rmtree(OTA_BACKUP_DIR)
    _ensure_dir(OTA_BACKUP_DIR)
    _write_marker(OTA_MARKER_PENDING, "\n".join(paths))

    def _live(p):
        return (live_root + "/" + p) if live_root else p

    # Schritt 2: vorhandene Live-Dateien sichern
    for p in paths:
        live = _live(p)
        try:
            os.stat(live)
        except OSError:
            continue  # Datei existiert noch nicht (Neuzugang) – nichts zu sichern
        _copy_file(live, OTA_BACKUP_DIR + "/" + p)

    # Schritt 3: Staging über Live kopieren
    for p in paths:
        _copy_file(OTA_STAGING_DIR + "/" + p, _live(p))

    # Schritt 4: Abschluss markieren und aufräumen
    _write_marker(OTA_MARKER_APPLIED, "ok")
    _rmtree(OTA_STAGING_DIR)
    try:
        os.remove(OTA_MARKER_PENDING)
    except OSError:
        pass
    try:
        os.remove(OTA_MARKER_APPLIED)
    except OSError:
        pass


def recover_interrupted_apply(live_root=""):
    """
    Wird beim Boot aufgerufen, BEVOR der Watchdog läuft. Wenn ein pending-Marker
    ohne applied-Marker existiert, wurde ein Apply durch Stromverlust unterbrochen:
    die gesicherten Backup-Dateien werden über die (evtl. korrupten) Live-Dateien
    zurückgespielt. Gibt True zurück, wenn eine Wiederherstellung stattfand.
    """
    try:
        os.stat(OTA_MARKER_PENDING)
    except OSError:
        _cleanup_markers()  # evtl. verwaisten applied-Marker aufräumen
        return False  # kein unterbrochener Apply

    try:
        os.stat(OTA_MARKER_APPLIED)
        applied = True
    except OSError:
        applied = False

    if applied:
        # Apply war eigentlich fertig, nur die Marker blieben liegen → nur aufräumen
        _cleanup_markers()
        return False

    # Backup-Dateien zurückspielen
    def _live(p):
        return (live_root + "/" + p) if live_root else p
    try:
        with open(OTA_MARKER_PENDING, "r") as f:
            paths = [ln.strip() for ln in f.read().split("\n") if ln.strip()]
    except OSError:
        paths = []
    for p in paths:
        backup = OTA_BACKUP_DIR + "/" + p
        try:
            os.stat(backup)
        except OSError:
            continue
        _copy_file(backup, _live(p))

    _cleanup_markers()
    print("OTA: unterbrochener Apply wiederhergestellt ({} Datei(en)).".format(len(paths)))
    return True


def _cleanup_markers():
    for m in (OTA_MARKER_PENDING, OTA_MARKER_APPLIED):
        try:
            os.remove(m)
        except OSError:
            pass


def _load_state():
    """Liest userconfig/ota_state.json. Default: idle, wenn nicht vorhanden."""
    try:
        with open(OTA_STATE_PATH, "r") as f:
            return json.load(f)
    except (OSError, ValueError):
        return {"phase": "idle", "version": "", "type": "", "boot_attempts": 0}


def _save_state(state):
    try:
        with open(OTA_STATE_PATH, "w") as f:
            json.dump(state, f)
    except OSError as e:
        print("OTA-Zustand konnte nicht gespeichert werden:", e)


def begin_probation(ota_type, version):
    """Markiert eine frisch angewandte Version als 'auf Probe'. Vor dem Reboot aufrufen."""
    _save_state({"phase": "probation", "type": ota_type,
                 "version": version, "boot_attempts": 0})


def confirm_boot_healthy():
    """
    Wird nach erfolgreichem MQTT-Connect aufgerufen. Beendet die Probezeit:
    Backup löschen, Zustand auf idle. Gibt ("APPLIED", version) zurück, wenn eine
    Probezeit bestätigt wurde, sonst None.
    Für FIRMWARE zusätzlich esp32.Partition.mark_app_valid_cancel_rollback().
    """
    st = _load_state()
    if st.get("phase") != "probation":
        return None
    version = st.get("version", "")
    if st.get("type") == "FIRMWARE":
        try:
            import esp32
            esp32.Partition.mark_app_valid_cancel_rollback()
        except Exception as e:
            print("Partition-Bestätigung fehlgeschlagen:", e)
    _rmtree(OTA_BACKUP_DIR)
    _save_state({"phase": "idle", "version": version,
                 "type": st.get("type", ""), "boot_attempts": 0})
    print("OTA: Version {} bestätigt (gesund).".format(version))
    return ("APPLIED", version)


def probation_check(live_root=""):
    """
    Wird beim Boot aufgerufen (vor dem Watchdog), NACH recover_interrupted_apply().
    Zählt Boot-Versuche während der Probezeit. Übersteigt der Zähler
    MAX_PROBATION_BOOTS, wird automatisch auf die Backup-Version zurückgerollt.
    Gibt ("ROLLED_BACK", version) zurück, wenn zurückgerollt wurde, sonst None.
    """
    st = _load_state()
    if st.get("phase") != "probation":
        return None

    st["boot_attempts"] = st.get("boot_attempts", 0) + 1
    if st["boot_attempts"] < MAX_PROBATION_BOOTS:
        _save_state(st)
        print("OTA: Probe-Boot {}/{}".format(st["boot_attempts"], MAX_PROBATION_BOOTS))
        return None

    # Zu viele Fehlversuche → Rollback auf Backup. Die alte Version liegt danach wieder
    # auf der Flash-Disk; der Aufrufer (main) startet neu, damit sie auch im RAM läuft.
    # Der ROLLED_BACK-Bericht wird als Einmal-Bericht hinterlegt und nach dem nächsten
    # erfolgreichen MQTT-Connect von der wiederhergestellten Version gesendet.
    version = st.get("version", "")
    _restore_backup(live_root)
    _save_state({"phase": "idle", "version": version,
                 "type": st.get("type", ""), "boot_attempts": 0,
                 "pending_report": ["ROLLED_BACK", version]})
    print("OTA: automatischer Rollback nach {} Fehlversuchen.".format(MAX_PROBATION_BOOTS))
    return ("ROLLED_BACK", version)


def take_pending_report():
    """
    Gibt einen einmaligen, über einen Reboot hinweg gespeicherten Statusbericht zurück
    (aktuell: ROLLED_BACK nach automatischem Rollback) und löscht ihn aus dem Zustand.
    Gibt (phase, version) zurück oder None. Nach erfolgreichem MQTT-Connect aufrufen.
    """
    st = _load_state()
    rep = st.get("pending_report")
    if not rep:
        return None
    st.pop("pending_report", None)
    _save_state(st)
    return (rep[0], rep[1])


def _restore_backup(live_root=""):
    """Spielt alle Dateien aus OTA_BACKUP_DIR rekursiv über die Live-Dateien zurück."""
    def _walk(rel):
        full = OTA_BACKUP_DIR + ("/" + rel if rel else "")
        try:
            mode = os.stat(full)[0]
        except OSError:
            return
        if mode & 0x4000:
            for name in os.listdir(full):
                _walk(rel + "/" + name if rel else name)
        else:
            dst = (live_root + "/" + rel) if live_root else rel
            _copy_file(full, dst)
    _walk("")
    _rmtree(OTA_BACKUP_DIR)


def _default_reset():
    import machine
    machine.reset()


def handle_command(payload_bytes, publish_status, wdt=None,
                   reset=_default_reset, live_root=""):
    """
    Einstiegspunkt für einen OTA-Befehl (Auslöser/Transport ist nicht Teil dieses Moduls,
    siehe Aufrufer). Verarbeitet einen /ota-Befehl:
      APP:      download → verify → apply → begin_probation → reset
      FIRMWARE: siehe _do_firmware_update (Task 9)

    publish_status(phase, version, progress=0, detail="") meldet den Fortschritt
    zurück (injiziert, damit ota.py von keinem konkreten Transport-Modul abhängt →
    kein Importzyklus).
    Bei Fehler wird FAILED gemeldet und der Live-Code bleibt unangetastet.
    """
    global _busy
    if _busy:
        print("OTA bereits aktiv – Befehl ignoriert.")
        return
    try:
        cmd = parse_command(payload_bytes)
    except ValueError as e:
        print("OTA-Befehl ungültig:", e)
        return

    _busy = True
    version = cmd["version"]
    try:
        if cmd["type"] == "APP":
            publish_status("DOWNLOADING", version, 0, "")
            # cmd["sha256"] ist der über den OTA-Trigger gelieferte Manifest-Hash (Vertrauensanker)
            manifest = download_app(cmd["url"], cmd["sha256"], wdt)
            publish_status("VERIFYING", version, 50, "")
            # download_app hat bereits jede Datei verifiziert; hier nur Phasenmeldung
            publish_status("APPLYING", version, 80, "")
            apply_app(manifest, live_root)
            begin_probation("APP", version)
            reset()
        elif cmd["type"] == "FIRMWARE":
            _do_firmware_update(cmd, publish_status, wdt, reset)
    except OtaError as e:
        print("OTA fehlgeschlagen:", e)
        publish_status("FAILED", version, 0, str(e))
        # Nur das Staging-Verzeichnis verwerfen. Marker (pending/applied) werden bewusst
        # NICHT entfernt: falls der Fehler während apply_app NACH dem Überschreiben einer
        # Live-Datei auftrat, ist der pending-Marker genau das, was beim nächsten Boot die
        # Wiederherstellung aus dem Backup auslöst.
        _rmtree(OTA_STAGING_DIR)
    except Exception as e:
        print("OTA unerwarteter Fehler:", e)
        publish_status("FAILED", version, 0, str(e))
        _rmtree(OTA_STAGING_DIR)
    finally:
        _busy = False
        gc.collect()


def _do_firmware_update(cmd, publish_status, wdt=None, reset=_default_reset):
    """
    Schreibt ein neues MicroPython-Image in die inaktive OTA-Partition und bootet hinein.
    Die ESP-IDF-Bootloader-Logik rollt automatisch zurück, falls die neue Firmware sich
    beim nächsten Boot nicht via mark_app_valid_cancel_rollback() bestätigt
    (das macht confirm_boot_healthy() nach erfolgreichem MQTT-Connect).

    Hardware-Pfad: auf dem ESP32-S3 manuell verifiziert. Host-Tests prüfen nur die
    Aufruf-Reihenfolge über den esp32-Stub.
    """
    import esp32
    version = cmd["version"]
    expected = cmd.get("sha256", "")
    publish_status("DOWNLOADING", version, 0, "")

    part = esp32.Partition(esp32.Partition.RUNNING).get_next_update()
    block_size = part.ioctl(5, 0) or 4096

    publish_status("APPLYING", version, 50, "")
    state = {"block": 0, "buf": bytearray()}
    h = hashlib.sha256()  # Hash über die tatsächlich empfangenen Image-Bytes (ohne Padding)

    def _on_chunk(chunk):
        h.update(chunk)
        state["buf"].extend(chunk)
        # In Blockgrösse-Einheiten schreiben
        while len(state["buf"]) >= block_size:
            part.writeblocks(state["block"], bytes(state["buf"][:block_size]))
            del state["buf"][:block_size]
            state["block"] += 1
            if wdt is not None:
                wdt.feed()

    http_stream(cmd["url"], _on_chunk, wdt)

    # Restpuffer auf Blockgrösse auffüllen und schreiben
    if state["buf"]:
        pad = block_size - (len(state["buf"]) % block_size)
        if pad != block_size:
            state["buf"].extend(b"\xff" * pad)
        part.writeblocks(state["block"], bytes(state["buf"]))

    # Image-Integrität prüfen, BEVOR diese Partition als Boot-Ziel gesetzt wird.
    # Bei Mismatch bleibt die laufende Firmware aktiv (set_boot wird nie aufgerufen).
    if expected:
        digest = "".join("{:02x}".format(b) for b in h.digest())
        if digest != expected:
            raise OtaError("Firmware-SHA-256 stimmt nicht")

    part.set_boot()
    # Probezeit für die Firmware aktivieren (Bestätigung via confirm_boot_healthy)
    begin_probation("FIRMWARE", version)
    reset()
