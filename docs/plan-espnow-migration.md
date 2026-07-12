# Entwicklungsplan: ESP-NOW-Migration — vereinter Node/Server vor dem Split

**Datum:** 2026-07-09
**Bezug:** ADR-001 (SmartNode-Sync), ADR-002 (ESP-NOW-Funksegment), ADR-003 (Pairing/Schlüssel)
**Status:** Teilweise überholt — Bausteine A–D erledigt, **alle MQTT-Anteile (inkl. Zielbild-Diagramm) sind durch die [Hub/Node-Spec](superpowers/specs/2026-07-10-hub-node-architecture-design.md) ersetzt** (MQTT vollständig ausgebaut, Teilprojekt #7). Weiterhin gültig und offen: Radio-Firmware (Phase 2a) und `RadioInterface`/serielle I/O (Phase 2b) — siehe [Hub/Node-Roadmap](superpowers/plans/2026-07-10-hub-node-roadmap.md).

## Leitidee

Der SmartNode kommt als neues Element hinzu (ESP-NOW↔MQTT-Bridge + Edge-Cache + lokaler Webserver). Statt ihn sofort als eigenständiges Pi-Gerät zu bauen, entsteht er als **eigener Spring-Boot-Prozess (`smart-ground-node`) auf derselben Maschine wie das Backend**. Entscheidend: Er spricht **von Anfang an die finalen Schnittstellen** — MQTT zum Broker, HTTPS zum Backend, UART zum ESP32-Funkmodul. Kein Direktzugriff auf die Backend-DB, keine In-Process-Aufrufe. Der spätere Split ist dann Deployment (Prozess auf den Pi verschieben), kein Refactoring.

**Stack-Entscheid:** Spring Boot für den Node, weil laut ADR-001 substanzielle Serverlogik dorthin wandert (lokaler Webserver, Entity-Sync, Auth) und OpenAPI-Contracts/DTOs wiederverwendet werden können. UART via jSerialComm, SQLite via JDBC. Preis: AES-GCM/HKDF existiert in Java **und** MicroPython → gemeinsame Test-Vektoren als Pflicht (siehe Phase 0).

## Zielbild (nach Split)

```
App ──REST──▶ Backend (Cloud) ──MQTT/TLS──▶ Mosquitto ◀──MQTT── SmartNode (Pi)
                   ▲                                              │  ├── SQLite (Cache, Keys, Outbox)
                   └────────── HTTPS Entity-Sync ─────────────────┘  ├── lokaler Webserver (offline)
                                                                     └── ESP32-Radio(s) via UART
                                                                            │ ESP-NOW
                                                                       SmartBoxen
```

## Vereinter Zwischenstand (Ende Phase 3)

```
Backend + Mosquitto + smart-ground-node   — alle auf dem Dev-Rechner
                          │ UART/USB
                     ESP32-Funkmodul ──ESP-NOW──▶ 2 SmartBoxen
```

Kein Cache, kein Sync, kein lokaler Webserver — der Node ist zunächst reine Bridge. Das Backend bleibt unverändert: Die Bridge übersetzt ESP-NOW-Frames auf die **bestehenden MQTT-Topics** (`smartboxes/{mac}/...`), sodass Discovery, Config-Push, Commands und Heartbeat funktionieren wie heute über WLAN.

---

## Phase 0 — Verträge festnageln (die Seams des späteren Splits)

Alles, was Box, Radio-Firmware, Node und Backend gemeinsam sprechen, wird zuerst spezifiziert — diese Verträge überleben den Split unverändert.

1. **Frame-Format** (ADR-002 AI 1): Routing-Header im Klartext (Ziel, Quelle, Frame-ID, TTL, Typ) + AES-GCM-Payload (Zähler-Nonce) + Tag, ≤250 Byte. Nachrichtentypen so definieren, dass sie 1:1 auf die bestehenden MQTT-Topics mappen (DISCOVERY, CONFIG, CONFIG_ACK, COMMAND, EXECUTED, HEARTBEAT, OTA-*).
2. **Heartbeat- und App-ACK-Protokoll** (ADR-002 AI 3): Intervalle, Timeouts, Offline-Erkennung inkl. „Relay down"-Unterscheidung.
3. **UART-Protokoll Node↔Funkmodul**: Framing (z. B. länge-präfixiert + CRC), Kommandos (send, recv, set_channel, add_peer). Das Funkmodul ist eine dumme Pipe — sämtliche Crypto- und Routing-Logik liegt im Node (Java), damit `RadioInterface` radios austauschbar hält.
4. **Krypto-Test-Vektoren**: AES-GCM + HKDF Referenz-Vektoren (Key, Nonce, Plaintext → Ciphertext, Tag) als Fixture-Dateien, gegen die sowohl Java- (Node) als auch MicroPython-Implementierung (Box) getestet werden. Verhindert stilles Auseinanderlaufen der zwei Implementierungen.
5. **Modul anlegen**: `smart-ground-node/` als neues Subprojekt im Monorepo (eigenes CLAUDE.md, gleiche Konventionen).

**Meilenstein M0:** Frame-Spec + UART-Spec als Doku im Repo; Test-Vektoren bestehen auf beiden Seiten.

## Phase 1 — ESP-NOW physisch erleben (Hands-on-Spike)

Bewusst klein: kein formales Messprotokoll, sondern ESP-NOW einmal selbst in der Hand haben.

- Zwei ESP32-S3, zwei minimale MicroPython-Skripte (`sender.py` / `receiver.py`): Peers registrieren sich gegenseitig (Unicast), der Sender schickt alle 5 s einen Befehl, der Empfänger lässt die Onboard-LED 1 s aufleuchten.
- Nebenbei beobachten (ohne Protokollpflicht): Verbindet es zuverlässig? Was passiert bei Reboot eines Peers? Grobe Reichweite im Raum/Gebäude.
- Formale Messungen (Reichweite auf der Anlage, Bursts, Paketverlust) verschieben sich in Phase 3, wo ohnehin der Prototyp-Aufbau steht.

**Meilenstein M1:** LED blinkt auf Befehl über ESP-NOW; grundlegendes Gefühl für Pairing/Reboot-Verhalten vorhanden.

## Phase 2 — Vereinte Stufe bauen (Node als Bridge neben dem Backend)

### 2a Radio-Firmware (ESP32-Funkmodul)

- Minimale Firmware: ESP-NOW ↔ UART nach Spec aus Phase 0. Bewusst klein halten (kein Key-Material auf dem Radio, ADR-002).

### 2b `smart-ground-node` (Spring Boot)

- `RadioInterface`-Abstraktion + serielle Implementierung (jSerialComm); Box→Radio-Tabelle zunächst in-memory/Datei, SQLite erst in Phase 4 (ADR-002 AI 4 wird hier vorbereitet, dort abgeschlossen).
- Pairing-Handshake Node-Seite: DISCOVER validieren (MIC mit `K_Box`), OFFER senden, CONFIRM verarbeiten, `K_S = HKDF(...)` ableiten (ADR-003 AI 4, Node-Hälfte).
- Betriebsverkehr: AES-GCM unter `K_S`, Zähler-Nonce, Seen-Cache auf Frame-IDs.
- MQTT-Übersetzung: Frames ↔ bestehende `smartboxes/{mac}/...`-Topics, Verbindung zum Mosquitto mit eigenem dynsec-Account (neue Rolle `node`).
- Key-Bezug **von Anfang an über die finale Schnittstelle**: `GET /sync/box-keys?updated_after=` gegen das Backend (ADR-003 AI 3) — auch wenn Backend auf localhost läuft. Kein DB-Durchgriff.

### 2c Backend

**Vorarbeit (Blocker): Bootstrap-Credential.** Eine unprovisionierte Box kann sich heute nicht zum gehärteten Broker verbinden und daher nie den ersten Config-Push (und damit `K_Box`) empfangen. Eigenes kleines Arbeitspaket, vor 2c abschliessen:

1. `smart-ground-deploy/dynsec-init.sh`: dynsec-Rolle `bootstrap` mit minimalen ACLs — publish nur auf `smartboxes/discovery`, subscribe nur auf `smartboxes/+/config` (nötig, weil die MAC beim geteilten Bootstrap-Client vorab unbekannt ist).
2. Flash-Zeit-Schritt dokumentieren/skripten, der initiale `mqtt_username`/`mqtt_password` (Bootstrap) in `userconfig/client_config.json` schreibt — Firmware-Code ist bereits vorbereitet (`connect_mqtt` nimmt beliebige Credentials).
3. Backend: nach Registrierung per erstem Config-Push die echten Box-Credentials liefern (existiert bereits, `SmartBoxConfigPushService`).
4. Bewusst akzeptiertes Restrisiko dokumentieren: Das geteilte Bootstrap-Credential kann Config-Pushes fremder Boxen mitlesen; da darin einmalig Credentials (und später `K_Box`) reisen, gilt: Bootstrap-Credential rotierbar halten und v2-Option (per-Box-Bootstrap ab Werk) offenlassen.

Danach regulär:

- `K_Box`-Erzeugung + Config-Push über MQTT/TLS (ADR-003 AI 2, Backend-Hälfte). Voraussetzung TLS + Client-Auth auf Mosquitto ist erfüllt (Task A–C).
- Sync-Endpoint für Box-Keys in `openapi.yaml` (contract-first) + Service-Token-Auth für den Node (Vorgriff auf ADR-001 AI 5; ein Token-Konzept, das später pro Anlage skaliert).
- Zuordnung Box↔Anlage↔Node im Datenmodell aktivieren (Reihenfolge-Bedingung aus ADR-003 beachten: Key muss vor Pairing gesynct sein).

### 2d Firmware SmartBox

- **Transport-Abstraktion**: gemeinsames Interface über heutigem WLAN/MQTT-Pfad und neuem ESP-NOW-Pfad; Modus aus `client_config.json`. WLAN bleibt als Provisionierungs- und Fallback-Pfad (ADR-003: Erst-Kontakt für `K_Box` läuft zwingend über WLAN/MQTT).
- `K_Box`-Persistenz im Flash (ADR-003 AI 2, Box-Hälfte) + Fallback bei Key-Verlust: Flash leer → WLAN-Provisionierungsmodus (AI 5).
- Pairing Box-Seite (DISCOVER/OFFER/CONFIRM + HKDF), Re-Pairing nach Reboot/Heartbeat-Timeout (ADR-002).
- Heartbeat + App-ACK nach Spec aus Phase 0. Speicherbudget beachten (AES-GCM via `ucryptolib`, HKDF minimal implementieren).

**Meilenstein M2:** Eine Box paart lokal mit dem Node und ein Command aus der UI löst über ESP-NOW einen GPIO aus — Backend-Code für den Command-Pfad unverändert.

## Phase 3 — Vereint härten (Prototyp-Validierung)

- Aufbau nach ADR-002 AI 5: 1 Node + 1 Funkmodul + 2 Boxen; Burst-Tests, Failover durch Modul-Reset.
- Revocation-Test: `K_Box` rotieren → Box wird nach Key-Sync abgewiesen (ADR-003 AI 6).
- Offline-Erkennung: Heartbeat-Ausfall Box vs. Node-Neustart, Re-Pairing verifizieren.
- Inbetriebnahme-Doku: „Neue Box braucht einmalig Internet; danach Anlage zuweisen und Sync abwarten" (ADR-003 AI 7).

**Meilenstein M3:** Vereintes System läuft stabil über mehrere Tage Dev-Betrieb. **Erst jetzt lohnt sich der Split.**

## Phase 4 — Auftrennen (Node wird eigenständig, ADR-001)

Jetzt kommt dazu, was den Node vom Bridge-Prozess zum Edge-Gerät macht:

- SQLite: Box→Radio-Tabelle (ADR-002 AI 4 abschliessen), Key-Store, Programm-Cache, Outbox.
- Entity-Sync komplett: `updated_at` + Soft-Delete auf syncbaren Entities (ADR-001 AI 1), `GET /sync/{entity}?updated_after=` (AI 2), Outbox + idempotenter Upload für Resultate (AI 3).
- Lokaler Webserver für den Offline-Zugriff der Schützen (AI 4).
- Deployment auf Pi: Provisionierung, Update-Strategie für die Node-Software, Monitoring (Konsequenz aus ADR-001).
- Mosquitto-Anbindung des Node übers Internet (Cloud-Broker) statt localhost — Konfigurationswechsel, kein Codewechsel.

**Meilenstein M4:** ADR-001 AI 6 — 48 h Offline-Betrieb, danach vollständiger Abgleich ohne Datenverlust.

## Phase 5 — Ausbau (nach Bedarf, nicht auf dem kritischen Pfad)

- Multi-Radio pro Node (Kanäle 1/6/11, Antennenabstand), Lastverteilung.
- Statisch konfigurierte Relays inkl. „Relay down"-Erkennung (ADR-002).
- Key-Rotation-Intervall, Sync-Push statt Poll, Pairing-UX.
- WLAN-Fallback-Politik pro Box-Typ (zeitkritische Boxen nur in der Zelle).

---

## Zuordnung der offenen ADR-Action-Items

| Action Item | Phase |
|---|---|
| ADR-002 AI 1 (Frame-Format) | 0 |
| ADR-002 AI 3 (Heartbeat/App-ACK) | 0 |
| ADR-002 AI 4 (RadioInterface + Box→Radio-Tabelle) | 2b (in-memory) → 4 (SQLite) |
| ADR-002 AI 5 (Prototyp Burst/Failover) | 3 |
| ADR-003 AI 1 (Mosquitto TLS + Client-Auth) | erledigt (Task A–C) |
| ADR-003 AI 2 (`K_Box` Erzeugung/Push/Persistenz) | 2c + 2d |
| ADR-003 AI 3 (Key-Sync an Nodes) | 2c |
| ADR-003 AI 4 (Handshake beidseitig) | 2b + 2d |
| ADR-003 AI 5 (Provisionierungs-Fallback) | 2d |
| ADR-003 AI 6 (Revocation-Test) | 3 |
| ADR-003 AI 7 (Inbetriebnahme-Doku) | 3 |
| ADR-001 AI 1–4 (Sync, Outbox, Webserver) | 4 |
| ADR-001 AI 5 (Service-Token) | 2c (Konzept) → 4 (pro Anlage) |
| ADR-001 AI 6 (48h-Offline-Test) | 4 |

## Risiken & Gegenmassnahmen

- **Split wird trotzdem teuer**, wenn der Node heimlich Abkürzungen nimmt → Regel: Node kommuniziert ausschliesslich über MQTT-Broker und HTTPS-Endpoints, nie DB-direkt oder in-process. Review-Kriterium in `smart-ground-node/CLAUDE.md` verankern.
- **Krypto-Drift Java↔MicroPython** → Test-Vektoren aus Phase 0 laufen in beiden CI-/Host-Test-Suiten.
- **Funkstrecke enttäuscht auf der realen Anlage** → Phase 1 vor jeder Integrationsarbeit; billige Fixes (Antenne, Position) vor Relay-Komplexität.
- **Firmware-RAM** (AES-GCM, HKDF, zweiter Transportpfad) → früh auf der XIAO messen (Phase 2d), Transportpfade lazy importieren analog AppCode-Variants-Entscheid.
- **Offener Punkt Bootstrap-Credential** (unprovisionierte Box kann nicht zum gehärteten Broker verbinden, siehe smart-box/CLAUDE.md) blockiert den WLAN-Erst-Kontakt für `K_Box` → als Vorarbeit-Arbeitspaket in Phase 2c konkretisiert.
