# Design: ESP-NOW-Protokollverträge (Phase 0 von plan-espnow-migration.md)

**Datum:** 2026-07-09
**Bezug:** [plan-espnow-migration.md](../../plan-espnow-migration.md) Phase 0, Punkte 1–3; ADR-001 (SmartNode-Sync), ADR-002 (ESP-NOW-Funksegment), ADR-003 (Pairing/Schlüsselverwaltung)
**Status:** Entwurf, zur Review

## Ziel

Phase 0 des ESP-NOW-Migrationsplans verlangt, dass alles, was Box, Radio-Firmware, Node und Backend gemeinsam sprechen, zuerst spezifiziert wird — diese Verträge sollen den späteren Split (Node vom Backend-Prozess zum eigenständigen Pi-Gerät) unverändert überleben. Dieses Dokument legt drei der fünf Phase-0-Punkte fest:

1. **Frame-Format** (Routing-Header + Payload, ≤250 Byte)
2. **Heartbeat- und App-ACK-Protokoll**
3. **UART-Protokoll Node ↔ Funkmodul**

Krypto-Test-Vektoren (Punkt 4) und das Anlegen von `smart-ground-node/` (Punkt 5) sind Implementierungsschritte, keine Design-Fragen, und folgen als eigene Arbeitspakete nach diesem Spec.

Die kryptografischen Grundsatzentscheidungen (App-Level-AES-GCM pro Box, DISCOVER/OFFER/CONFIRM-Pairing, `K_S = HKDF(K_Box, Nonce_B ‖ Nonce_N)`) stammen aus ADR-002/ADR-003 und werden hier nicht neu verhandelt, nur auf Byte-Ebene konkretisiert.

## Scope-Entscheidungen

- **Nachrichtentypen:** Pairing (DISCOVER/OFFER/CONFIRM) + Betriebsverkehr (DISCOVERY, CONFIG, CONFIG_ACK, COMMAND, EXECUTED, HEARTBEAT). **Kein OTA über ESP-NOW** — OTA-Payloads (Manifest + Dateien) sprengen das 250-Byte-Frame-Budget bei Weitem; OTA bleibt auf dem bestehenden WLAN/HTTP-Pfad, auch für Boxen, die im Regelbetrieb über ESP-NOW laufen (eine Box braucht für ein OTA-Update also vorübergehend WLAN — das ist bereits heute der einzige Weg für einen Erstkontakt/Provisionierung, siehe ADR-003).
- **Adressierung:** 6-Byte-MAC-Adressen (nicht Box-UUID), weil ESP-NOW selbst MAC-adressiert und Backend/MQTT die Box heute schon per MAC identifizieren — keine zusätzliche Mapping-Tabelle nötig.
- **CONFIG bei mehreren Geräten:** Ein Frame pro Gerät (kein generisches Fragmentierungsprotokoll) — passt zur heutigen Geräteanzahl pro Box (1–4) und hält die Box-Firmware einfach.

## 1. Frame-Envelope (Klartext-Routing-Header)

```
Offset  Size  Feld
0       6     dest_mac
6       6     src_mac
12      2     frame_id   (uint16 LE, Sender-lokaler Zähler, wrapt)
14      1     ttl        (Start = Hop-Budget, max. 3 gemäß ADR-002; pro Hop -1, 0 beim Empfang = verwerfen statt weiterleiten)
15      1     type        (1-Byte-Enum, siehe Abschnitt 2)
16      …     body        (typ-abhängig, siehe Abschnitte 3–4)
```

Header = 16 Byte. Bei 250 Byte ESP-NOW-Payload-Limit bleiben 234 Byte für den Body.

**Seen-Cache (Relay/Node):** Schlüssel `(src_mac, frame_id)`. Einträge verfallen nach einem konfigurierbaren Fenster (Vorschlag: 5 s — deutlich länger als jede realistische Retransmit-Kette, aber kurz genug, um den Cache klein zu halten). Ein Duplikat (bekannter Schlüssel innerhalb des Fensters) wird verworfen, nicht weitergeleitet, kein Fehler gemeldet.

## 2. Typ-Katalog

```
0x01 PAIR_DISCOVER   Box  → Node   (Broadcast, ADR-003)
0x02 PAIR_OFFER      Node → Box    (Unicast)
0x03 PAIR_CONFIRM    Box  → Node   (Unicast)
0x10 DISCOVERY       Box  → Node   → smartboxes/discovery
0x11 CONFIG          Node → Box    → smartboxes/{mac}/config
0x12 CONFIG_ACK      Box  → Node   → smartboxes/{mac}/config/ack
0x13 COMMAND         Node → Box    → smartboxes/{mac}/command
0x14 EXECUTED        Box  → Node   → smartboxes/{mac}/device/{id}/executed
0x15 HEARTBEAT       Box  → Node   → smartboxes/{mac}/status
```

`0x00` reserviert (ungültig, wird verworfen). `0xF0–0xFF` für spätere Relay-/Diagnose-Frames freigehalten (Phase 5, hier nicht implementiert).

## 3. Pairing-Frames (unter `K_Box`, kein Session-Key vorhanden)

Referenz: ADR-003, Abschnitt „Entscheidung", Punkt 3.

- **`PAIR_DISCOVER`** — `box_uuid(16) ‖ nonce_b(8) ‖ mic(16)`
  `mic = HMAC-SHA256(K_Box, header ‖ box_uuid ‖ nonce_b)[0:16]` — Header hier ist der 16-Byte-Klartext-Envelope aus Abschnitt 1 (mit `type=0x01`).
- **`PAIR_OFFER`** — `radio_id(1) ‖ channel(1) ‖ nonce_n(8) ‖ ciphertext(8) ‖ tag(16)`
  `ciphertext, tag = AES-GCM-Encrypt(K_Box, nonce=nonce_n padded auf 12 Byte mit führenden Nullen, plaintext=nonce_b, aad=header)`. Nur ein Besitzer von `K_Box` kann `nonce_b` korrekt verschlüsseln zurückschicken.
- **`PAIR_CONFIRM`** — `nonce_n(8) ‖ mic(16)`
  `mic = HMAC-SHA256(K_Box, header ‖ nonce_n)[0:16]`.

Danach leiten beide Seiten `K_S = HKDF-SHA256(K_Box, salt=nonce_b ‖ nonce_n, info="smart-ground-espnow-session")` ab (32 Byte). Der Zähler-Nonce für den Betriebsverkehr (Abschnitt 4) startet bei 0.

**Re-Pairing:** Bei ausbleibendem Heartbeat-ACK (siehe Abschnitt 5) oder nach Reboot startet die Box erneut mit `PAIR_DISCOVER` auf bekannten Kanälen. Peer-Tabellen liegen im RAM des Funkmoduls (ADR-002) und werden bei jedem Boot neu aufgebaut.

## 4. Betriebs-Frames (unter `K_S`)

Einheitliche Struktur:

```
body = counter_nonce(4) ‖ ciphertext ‖ tag(16)
```

`ciphertext, tag = AES-GCM-Encrypt(K_S, nonce=counter_nonce padded auf 12 Byte mit führenden Nullen, plaintext=<typ-spezifischer Klartext>, aad=header)`. `counter_nonce` ist ein reiner Zähler ab 0 pro Session (persistiert nirgends — ein Reboot erzwingt Re-Pairing und damit einen frischen `K_S`, siehe ADR-003).

Klartext-Inhalt pro Typ (kompakt binär, kein JSON — Feldnamen aus den bestehenden MQTT-Payloads in `smart-box/CLAUDE.md`, `device_id` als 16-Byte-UUID statt String):

| Typ | Klartext-Body |
|---|---|
| `DISCOVERY` | `app_version(2) ‖ config_schema_version(1) ‖ box_type_len(1) ‖ box_type ‖ capabilities_blob` (kompakte Encodierung der Capability-Map — Details bei Implementierung, sobald das Capability-Schema aus `firmware_config.json` feststeht) |
| `CONFIG` | `device_id(16) ‖ device_index(1) ‖ device_count(1) ‖ alias_len(1) ‖ alias ‖ device_type(1, GPIO=0/LED=1) ‖ direction(1, IN=0/OUT=1) ‖ command_len(1) ‖ command ‖ signal_duration_ms(2) ‖ blocked(1)` |
| `CONFIG_ACK` | leer (nur der Frame-Typ selbst ist die Bestätigung) |
| `COMMAND` | `command_id(16) ‖ device_id(16) ‖ command(1, ON=0/OFF=1/BLOCK=2/UNBLOCK=3) ‖ signal_duration_ms(2)` |
| `EXECUTED` | `command_id(16) ‖ device_id(16)` |
| `HEARTBEAT` | leer |

**CONFIG bei mehreren Geräten:** Der Node sendet für eine Box mit N Geräten N einzelne `CONFIG`-Frames mit `device_index = 0..N-1` und konstantem `device_count = N`. Die Box akkumuliert die Geräte in einer temporären Liste und sendet erst `CONFIG_ACK`, wenn `device_index == device_count - 1` empfangen wurde. Bleibt der Satz nach 5 s unvollständig (Timeout), verwirft die Box die temporäre Liste und wartet auf einen neuen Durchlauf ab `device_index = 0` — die alte, zuletzt vollständig angewendete Config bleibt in der Zwischenzeit aktiv (kein Blackout).

## 5. Heartbeat- und App-ACK-Protokoll

- **Heartbeat:** Box sendet `HEARTBEAT` alle 20 s (identisch zum bestehenden MQTT-Intervall `PUBLISH_INTERVAL_S`). Node übersetzt 1:1 auf `smartboxes/{mac}/status`.
- **Offline-Erkennung (Box):** Node markiert eine Box offline, wenn 3 Heartbeats in Folge ausbleiben (60 s Fenster) — bewusst großzügig gegenüber einzelnen Funkstörungen/Kollisionen.
- **„Relay down" vs. „Box down":** Fällt eine einzelne Box aus, meldet der Node nur diese Box offline. Fallen alle Boxen hinter einem beim Pairing gespeicherten Relay gleichzeitig aus, meldet der Node stattdessen einen eigenen Relay-Down-Status (nicht N einzelne Box-Offline-Events) — Unterscheidung anhand der statisch konfigurierten Relay-Zuordnung aus ADR-002.
- **App-Level-ACK für COMMAND:** Der Node erwartet `EXECUTED` innerhalb von 2 s nach `COMMAND`-Versand (das MAC-Layer-ACK von ESP-NOW bestätigt nur Frame-Empfang, nicht Ausführung). Bleibt `EXECUTED` aus: **ein Retry** mit **neuer `frame_id`** (nicht dieselbe — ein Duplikat-Filter auf `(src_mac, frame_id)` würde einen Retry mit alter `frame_id` sonst als Duplikat verwerfen). Bleibt auch der Retry unbeantwortet, meldet der Node den Command als fehlgeschlagen; weitere Fehlerbehandlung (Retry-Strategie, Nutzer-Feedback) ist Backend-/UI-Sache, nicht Teil dieses Frame-Protokolls.

## 6. UART-Protokoll Node ↔ Funkmodul

Das Funkmodul ist eine dumme Pipe (ADR-002) — sämtliche Frame-Interpretation (Typ, Verschlüsselung, Routing) liegt im Node; das UART-Protokoll transportiert nur rohe ESP-NOW-Frames plus Peer-/Kanalverwaltung.

**Framing:**

```
Offset  Size  Feld
0       1     start-byte (0x7E, fix)
1       2     length      (uint16 LE, Länge von cmd_id..body)
3       1     cmd_id      (uint8, vom Node vergeben, Rundlauf-Zähler, für Response-Korrelation)
4       1     cmd         (Enum, siehe unten)
5       N     body        (cmd-spezifisch)
5+N     2     crc16       (CRC-16/CCITT über cmd_id..body)
```

**Kommandos (Node → Radio), jedes mit `ACK(cmd_id:1, ok:1)`-Antwort:**

| cmd | body | Zweck |
|---|---|---|
| `SET_CHANNEL` | `channel(1)` | ESP-NOW-Kanal setzen |
| `ADD_PEER` | `mac(6)` | Peer-Tabelle ergänzen (RAM-Operation) |
| `DEL_PEER` | `mac(6)` | Peer-Tabelle bereinigen |
| `SEND` | `dest_mac(6) ‖ esp_now_frame(N)` | Frame-Envelope aus Abschnitt 1 roh senden |
| `STATUS` | — | Health-Check (Antwort-Body: `uptime_s(4) ‖ free_heap(4)`) |

**Ereignisse (Radio → Node, unaufgefordert, kein `cmd_id`-Bezug, eigene Startbyte-Erkennung reicht):**

| cmd | body | Zweck |
|---|---|---|
| `RECV` | `src_mac(6) ‖ rssi(1) ‖ esp_now_frame(N)` | Empfangener Frame |
| `MAC_ACK` | `dest_mac(6) ‖ frame_id(2) ‖ ok(1)` | ESP-NOW-Sendebestätigung zu einem vorherigen `SEND` |

Der Node nutzt `STATUS`-Timeouts (keine Antwort auf mehrere `STATUS`-Anfragen in Folge), um ein hängendes/abgestürztes Funkmodul von einem funktionierenden Modul mit schlechter Funkstrecke zu unterscheiden — relevant für die Multi-Radio-Failover-Logik aus ADR-002 (Task 2b/Phase 3).

## Testing-Ansatz

- **Frame-Envelope + Typ-Encoding:** Host-Tests (Java, `smart-ground-node`) und MicroPython-Host-Tests (Box) kodieren/dekodieren dieselben Beispiel-Frames und vergleichen Byte-für-Byte gegen fest verdrahtete Erwartungswerte — verhindert Auseinanderlaufen der beiden Implementierungen, analog zu den in Phase 0 Punkt 4 geplanten Krypto-Test-Vektoren.
- **Pairing-Handshake:** Unit-Test pro Nachricht (MIC-Berechnung, AEAD-Encrypt/Decrypt) gegen die Krypto-Test-Vektoren aus Punkt 4 (eigenes Arbeitspaket, folgt).
- **UART-Framing:** Host-Test mit einem simulierten seriellen Kanal (Byte-Stream inkl. Bit-Fehlern/Fragmentierung mitten im Frame), prüft CRC-Erkennung und Resync auf das nächste `0x7E`.
- **Heartbeat/ACK-Timing:** Wie bei der Firmware-Testsuite (`tests/_stubs.py`-Muster) mit kontrollierbarer Uhr, kein reales Warten.

## Offene Punkte für spätere Phasen (bewusst nicht hier entschieden)

- Genaues Encoding der `capabilities`-Map in `DISCOVERY` (hängt vom finalen Capability-Schema ab, das sich noch weiterentwickelt — wird beim Implementieren von Punkt 5/Phase 2b konkretisiert, nicht hier vorweggenommen).
- Radio-`STATUS`-Poll-Intervall und genaue Schwelle für „Funkmodul hängt" (Phase 3, Prototyp-Härtung).
- `0xF0–0xFF`-Diagnose-/Relay-Steuerframes (Phase 5).
