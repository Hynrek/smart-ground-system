# ADR-003: Pairing und Schlüsselverwaltung im ESP-NOW-Segment

**Status:** Proposed
**Datum:** 2026-07-09
**Entscheider:** Jonas Studer
**Bezug:** ADR-001 (SmartNode-Sync), ADR-002 (ESP-NOW-Funksegment — konkretisiert dessen Action Item 2, ersetzt Action Item 6)

> ⚠️ **Teilweise überholt durch die [Hub/Node-Spec](../superpowers/specs/2026-07-10-hub-node-architecture-design.md)** (die Autorität). Der Erst-Kontakt für `K_Box` über WLAN/MQTT samt Bootstrap-dynsec-Rolle und akzeptiertem Restrisiko ist **ersetzt durch die `box-api` über HTTPS auf dem Node-AP** (CA beim Flashen gepinnt) — umgesetzt in Teilprojekt #7. Restrisiko und Bootstrap-Arbeitspaket entfallen ersatzlos. Der Hauptserver liegt zudem nicht mehr in der Cloud, sondern ist der lokale Hub. Die Pairing-Kryptografie selbst bleibt gültig.

## Kontext

ADR-002 legt App-Level-Verschlüsselung (AES-GCM, Key pro Box) fest. Offen ist, wie Box und SmartNode zu einem gemeinsamen Schlüssel kommen, ohne dass ein Angreifer in Funkreichweite mitlesen, sich einschleusen (MITM) oder alte Handshakes wiederholen kann. Wer den Schlüsselaustausch kompromittiert, kann Wurfmaschinen auslösen — dies ist die sicherheitskritischste Stelle des Funksegments.

Randbedingungen:

- Der Hauptserver läuft in der **Cloud, ausserhalb der Anlage**; die Anlage muss auch bei totem Uplink voll funktionieren.
- Jede Box spricht bei der Inbetriebnahme ohnehin einmal per WLAN mit dem Backend (Registrierung, Config, OTA) — ein bestehender, kontrollierbarer Kanal.
- MicroPython auf ESP32-S3: AES (hardwarebeschleunigt, `ucryptolib`) und SHA-256 verfügbar; asymmetrische Kryptografie (ECDH/X25519) nur als langsame Pure-Python-Fremdbibliothek.
- Boxen haben keinen Bildschirm; Fingerprint-Bestätigung an der Box ist nicht möglich.

## Entscheidung

**Kein Schlüsselaustausch über die Luft.** Der Vertrauensanker ist das Backend:

1. **Provisionierung (einmalig, über Internet — Standort egal):** Das Backend erzeugt pro Box einen Langzeit-Key `K_Box` (32 Byte) und pusht ihn beim Config-Push über MQTT/TLS zur Box (Clublokal, Werkstatt, zu Hause — die Box muss dafür nicht auf der Anlage sein). Die Box persistiert ihn im Flash.
2. **Key-Verteilung:** Nodes der zugewiesenen Anlage erhalten `(Box-UUID, MAC, K_Box)` über den HTTPS-Sync aus ADR-001. Asynchron; Latenz irrelevant.
3. **ESP-NOW-Pairing (bei jedem Boot/Radiowechsel, rein lokal):** Gegenseitiger Besitznachweis statt Key-Austausch:
   - `DISCOVER` (Broadcast, Box → Node): Box-UUID, `Nonce_B`, MIC mit `K_Box` — der Node erkennt legitime Boxen, ignoriert fremde.
   - `OFFER` (Unicast, Node → Box): zugewiesenes Radio + Kanal, `Nonce_N`, verschlüsselt mit `K_Box` inkl. `Nonce_B` — nur ein Besitzer von `K_Box` kann antworten; Rogue-Nodes scheitern hier. Nonces machen Replays wertlos.
   - `CONFIRM` (Box → Node, auf zugewiesenem Kanal): enthält `Nonce_N`.
4. **Session-Key:** Beide leiten `K_S = HKDF(K_Box, Nonce_B ‖ Nonce_N)` ab. Betriebsverkehr läuft mit AES-GCM unter `K_S`, **Zähler-Nonce ab 0** — da `K_S` pro Pairing frisch ist, entfällt jede Nonce-Persistenz im Flash (ersetzt ADR-002, Action Item 6). Reboot → neu pairen → neuer Key → Zähler zurück.

**Cloud-Konsequenzen (verbindlich):**

- Mosquitto ist öffentlich erreichbar → **TLS + Client-Authentifizierung werden zwingend** (Voraussetzung für den `K_Box`-Push).
- Pairing und Betrieb brauchen null Internet — der Node hat Keys und Programme lokal (ADR-001); die Anlage bleibt bei Uplink-Ausfall voll funktionsfähig.
- **Reihenfolge-Bedingung:** Der Node muss den Key einer Box gesynct haben, *bevor* sie pairen kann. Box registrieren → Anlage zuweisen → Sync-Intervall abwarten → Pairing lokal.
- WLAN-Fallback der Boxen läuft übers Internet zur Cloud: für Config/Resultate okay, für zeitkritische Trigger-Pfade nicht — zeitkritische Boxen gehören in die ESP-NOW-Zelle.

**Key-Lifecycle:**

- Box mit gelöschtem Flash fällt automatisch in den WLAN-Provisionierungsmodus zurück (neuer `K_Box`).
- Backend kann `K_Box` jederzeit rotieren; nach Sync lehnen Nodes den alten Key ab → gestohlene/kompromittierte Box ist per Klick aussperrbar.

## Betrachtete Optionen

### Option A: Backend-verankerte Pre-Shared Keys (gewählt)

| Dimension | Bewertung |
|---|---|
| Komplexität | Niedrig — nur AES-GCM + HKDF auf der Box |
| Kosten | Keine; nutzt bestehende Kanäle (MQTT/TLS, HTTPS-Sync) |
| Skalierbarkeit | Gut — Key-Verteilung skaliert mit dem Sync |
| Team-Vertrautheit | Symmetrische Crypto, gut beherrschbar |

**Pro:** Kein MITM-Problem über die Luft; keine PKI; Revocation zentral; Box-Crypto bleibt minimal (hardwarebeschleunigtes AES).
**Contra:** Einmaliger Internet-Kontakt der Box zwingend; Reihenfolge-Bedingung beim ersten Einsatz auf einer Anlage; Provisionierungskanal muss TLS sein.

### Option B: ECDH (X25519) über die Luft + Out-of-Band-Bestätigung

**Pro:** Kein Backend-Kontakt nötig; Keys entstehen lokal.
**Contra:** MITM nur durch Out-of-Band-Bestätigung abwehrbar (Fingerprint in Node-UI + physischer Knopf) — Boxen haben keinen Bildschirm; X25519 in MicroPython nur als langsame Fremdbibliothek; Trust-on-First-Use bleibt angreifbar im Pairing-Fenster. Verworfen als Hauptpfad; bleibt Phase-2-Option für Offline-Inbetriebnahme.

### Option C: Ein gemeinsamer Anlagen-Key für alle Boxen

**Pro:** Trivial zu verteilen.
**Contra:** Eine kompromittierte Box kompromittiert die ganze Anlage; keine Einzel-Revocation; Replay zwischen Boxen möglich. Verworfen.

## Trade-off-Analyse

Der Kern-Trade-off ist **Backend-Anker (A) vs. lokale Autonomie (B)**. A gewinnt, weil die Box den Internet-Kontakt für Registrierung und OTA ohnehin braucht — die „zusätzliche" Abhängigkeit existiert bereits. B müsste MITM-Abwehr ohne Display lösen und schwere Crypto auf MicroPython bringen, nur um einen Randfall (fabrikneue Box auf Anlage ohne Uplink) abzudecken. Dieser Randfall wird für v1 bewusst ausgeschlossen und als Voraussetzung dokumentiert: **Inbetriebnahme neuer Boxen erfordert einmalig Internet.**

## Konsequenzen

- **Einfacher:** Keine Nonce-Persistenz (ADR-002 AI 6 entfällt); Revocation und Rotation zentral; Pairing-Code auf der Box klein und testbar; Anlage voll offline-fähig.
- **Schwerer:** TLS + Client-Auth auf Mosquitto wird Blocker für das gesamte Konzept; Sync-Verfügbarkeit wird Teil der Inbetriebnahme-UX (Wartezeit nach Zuweisung).
- **Später zu prüfen:** Lokales Fallback-Pairing (Option B) für Offline-Inbetriebnahme; Key-Rotations-Intervall; Sync-Push statt Poll, um die Reihenfolge-Wartezeit zu verkürzen.

## Action Items

1. [ ] Mosquitto auf TLS + Client-Authentifizierung umstellen (Blocker)
2. [ ] `K_Box`-Erzeugung + Config-Push im Backend; Persistenz im Box-Flash (NVS)
3. [ ] Key-Sync in den ADR-001-Entity-Sync aufnehmen (`(uuid, mac, key, version)`, nur an Nodes der zugewiesenen Anlage)
4. [ ] Handshake implementieren: DISCOVER/OFFER/CONFIRM + HKDF-Ableitung, beidseitig
5. [ ] Provisionierungs-Fallback bei Key-Verlust (Flash leer → WLAN-Modus)
6. [ ] Revocation-Test: Key rotieren → alte Box wird nach Node-Sync abgewiesen
7. [ ] Inbetriebnahme-Doku: „Neue Box braucht einmalig Internet; danach Anlage zuweisen und Sync abwarten"
