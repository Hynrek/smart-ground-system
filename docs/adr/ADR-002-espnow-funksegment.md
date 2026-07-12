# ADR-002: Ausgestaltung des ESP-NOW-Funksegments (Peers, Verschlüsselung, Multi-Radio)

**Status:** Proposed
**Datum:** 2026-07-09
**Entscheider:** Jonas Studer
**Bezug:** ADR-001 (SmartNode als Edge-Cache mit Sync); ADR-003 (Pairing und Schlüsselverwaltung — konkretisiert Action Item 2, ersetzt Action Item 6)

> ⚠️ **Teilweise überholt durch die [Hub/Node-Spec](../superpowers/specs/2026-07-10-hub-node-architecture-design.md)** (die Autorität). Das 1:1-Mapping der Frametypen auf MQTT-Topics **entfällt — es gibt kein MQTT mehr** (Teilprojekt #7, erledigt). Frame-Format, `SeenCache`, `MAC_ACK`, Relais und Kanalplanung bleiben unverändert gültig.

## Kontext

SmartBoxen kommunizieren künftig per ESP-NOW mit dem SmartNode (Bridge zu MQTT, siehe Architektur-Entscheid Hybrid-Modell). ESP-NOW ist verbindungslos; drei Fragen sind offen:

1. **Adressierung:** Broadcast oder Unicast an registrierte Peers?
2. **Verschlüsselung:** Der ESP32 unterstützt hardwareseitig max. 20 Peers, davon standardmässig nur **6–7 verschlüsselt** (LMK-Slots; per ESP-IDF-Config bis 17, bei MicroPython nur via eigenem Firmware-Build). Eine Zelle soll aber deutlich mehr Boxen tragen können.
3. **Skalierung des Node:** Wie wächst eine Zelle über das Peer-Limit eines einzelnen Funkmoduls hinaus?

Randbedingungen: Traffic ist **burst-artig** (Schiessbetrieb), nicht konstant. Kritische Kommandos (Wurfmaschine auslösen) brauchen ohnehin Empfangsbestätigung auf Anwendungsebene. ESP-NOW-Payload: 250 Byte. Der SmartNode ist ein Pi-Klasse-Gerät mit ESP32-Funkmodul(en) per UART/USB (ADR-001).

## Entscheidung

1. **Unicast an registrierte Peers; Broadcast nur fürs Pairing.** Discovery-Broadcast der Box → Antwort des Node → gegenseitige Peer-Registrierung. Danach ausschliesslich Unicast (MAC-Layer-ACK + automatische Retransmits).
2. **App-Level-Verschlüsselung statt ESP-NOW-LMK.** Frames werden ESP-NOW-seitig unverschlüsselt gesendet; der Payload wird mit **AES-GCM** und einem **Key pro Box** (beim Pairing ausgehandelt) verschlüsselt. Damit entfällt das 7er-Limit — es gilt nur noch das 20-Peer-Limit pro Funkmodul.
3. **SmartNode-Bridge von Anfang an auf N Funkmodule ausgelegt.** Ein `RadioInterface` pro UART-Device; Box→Radio-Zuordnung als Tabelle in SQLite; Module auf getrennten Kanälen (1/6/11).
4. **Relay-Fähigkeit (Hopping) im Frame-Format vorgesehen.** ESP-NOW hat kein eingebautes Mesh — Hopping heisst: eine Box leitet Frames weiter. Dank App-Level-Crypto (Ende-zu-Ende zwischen Box und Node) braucht ein Relay **keine Schlüssel**: Es forwarded opake Ciphertext-Frames und kann weder mitlesen noch manipulieren (AES-GCM). Dafür trägt jeder Frame einen **Routing-Header ausserhalb des verschlüsselten Payloads**: Ziel-Box-ID, Quelle, Frame-ID, TTL (max. 1–2 Hops). Relays und Node führen einen Seen-Cache auf Frame-IDs (Duplikat-/Storm-Unterdrückung). Pairing durch ein Relay: DISCOVER wird mit Hop+1 re-broadcastet, OFFER läuft denselben Weg zurück; der Node speichert „Box Y erreichbar via Relay X, Kanal n".
   **Für v1: explizit konfigurierte Relays, kein selbstorganisierendes Mesh.** Statische Zuordnung („Box X relayt für die Wurfmaschine im Graben"), beim Pairing gesetzt, auf dem Node gespeichert — deterministisch und debugbar. Vor dem Einsatz eines Relays zuerst die billigen Fixes prüfen: externe Antenne, Position der Relay-Box (z. B. Grabenkante).
   **Topologie-Regeln:**
   - *Ketten* (Node → Relay A → Relay B → Box): erlaubt, gleicher Mechanismus (Forwarding-Tabelle pro Relay), **TTL max. 3**. Durchsatz sinkt auf ~1/3, Fehlerwahrscheinlichkeit multipliziert sich pro Hop — eine Zwei-Relay-Kette ist als Ausnahme zu behandeln und ein Signal, dass die Anlage eine zweite Zelle oder ein umplatziertes Radio braucht.
   - *Fan-out* (ein Relay vor mehreren Boxen, z. B. „ein Relay am Bunkereingang, vier Maschinen dahinter"): die bevorzugte Richtung. Grenzen: Peer-Table des Relays (19 Boxen + Node) und Airtime (jeder Frame kreuzt den Kanal doppelt) — bei Burst-Traffic irrelevant.
   - Ein Relay ist **Single Point of Failure** für seinen Ast → netzversorgt betreiben. Der Node muss „Relay down" von „Box down" unterscheiden: Verschwinden alle Boxen hinter einem Relay gleichzeitig aus dem Heartbeat, wird das Relay als Ursache gemeldet.

Ergänzend auf Anwendungsebene (da keine Verbindung existiert):

- **Heartbeat** Box → Node (Online/Offline-Status, analog MQTT-Status-Logik)
- **App-Level-ACK** für kritische Kommandos (MAC-ACK bestätigt nur Frame-Empfang, nicht Verarbeitung)
- **Re-Pairing-Logik** nach Reboot (Peer-Tables liegen im RAM); bei ausbleibendem Heartbeat-ACK scannt die Box bekannte Kanäle nach Discovery-Antworten

## Betrachtete Optionen

### Option A: App-Level-Crypto (AES-GCM pro Box) — gewählt

| Dimension | Bewertung |
|---|---|
| Komplexität | Mittel — Key-Handling beim Pairing, Nonce-Verwaltung |
| Kosten | ~28 Byte Overhead (Nonce + Auth-Tag) von 250; CPU vernachlässigbar bei Bursts |
| Skalierbarkeit | ~20 Boxen pro Funkmodul, verschlüsselt unbegrenzt |
| Team-Vertrautheit | AES-GCM in MicroPython/Python verfügbar |

**Pro:** Kein LMK-Slot-Limit; liefert Authentizität + Replay-Schutz mit (deckt den App-ACK-Bedarf gleich ab); Box-Keys liegen auf dem Pi → Radio-Wechsel einer Box ohne neuen Key-Austausch.
**Contra:** Nonce-Disziplin nötig (nie wiederverwenden); Pairing-Handshake muss selbst abgesichert werden (z. B. Pairing-Modus nur per physischem Trigger/Zeitfenster).

### Option B: ESP-NOW-LMK mit dynamischem Peer-Swapping

**Pro:** Hardware-Crypto, kein eigener Code. `add_peer`/`del_peer` sind RAM-Operationen ohne Handshake — Slot-Pooling wäre wegen Burst-Traffic machbar.
**Cons:** Zustandsverwaltung mit Ecken (Slot-Verdrängung während aktiver Bursts); LMK bietet keinen sauberen Replay-Schutz; App-ACK bräuchte es trotzdem. Verworfen.

### Option C: LMK-Limit per Firmware-Build auf 17 erhöhen

**Contra:** Eigenes MicroPython-Build pflegen für ein Limit, das bei 17 wieder deckelt; löst das Skalierungsproblem nur halb. Verworfen.

### Option D: Nur Broadcast (unverschlüsselt)

**Contra:** Kein ACK, kein Retry, keine Vertraulichkeit — für ein System, das Wurfmaschinen auslöst, inakzeptabel. Verworfen; Broadcast bleibt aufs Pairing beschränkt.

## Trade-off-Analyse

Der Kern-Trade-off ist **Hardware-Crypto mit Slot-Jonglage (B/C) vs. eigene Crypto ohne Limit (A)**. Das Burst-Muster würde beides tragen, aber A gewinnt, weil die „Extras" von AES-GCM (Authentizität, Replay-Schutz, radio-unabhängige Keys) genau die Lücken füllen, die ESP-NOW ohnehin offen lässt und die sonst separat gebaut werden müssten. Die Nonce-Disziplin ist der Preis — beherrschbar mit Zähler-Nonce pro Box + Persistenz des Zählers.

Beim Multi-Radio-Design ist der Trade-off **jetzt abstrahieren vs. später refactoren**: Die Abstraktion (`RadioInterface`, Zuordnungstabelle) kostet beim Prototyp fast nichts, macht das zweite Modul später zum reinen Einstecken und gibt Redundanz/Failover gratis dazu.

## Konsequenzen

- **Einfacher:** Zellen skalieren auf ~20 Boxen pro Modul und linear mit Modulen; Failover zwischen Radios; Sicherheitsmodell einheitlich (ein Mechanismus für Vertraulichkeit, Authentizität, Replay).
- **Schwerer:** Pairing wird sicherheitskritisch (Key-Austausch absichern); Nonce-/Key-Verwaltung auf Box und Pi; Kanalplanung bei mehreren Modulen (Antennenabstand 30–50 cm gegen Desense).
- **Neu zu beachten (Relay):** Jeder Hop halbiert grob den Durchsatz auf dem Kanal (Relay empfängt und sendet auf demselben Radio); MAC-ACK bestätigt nur hop-by-hop — Ende-zu-Ende-Bestätigung liefert der App-Level-ACK; Relay-Boxen sollten netzversorgt sein.
- **Später zu prüfen:** Key-Rotation; Pairing-UX (physischer Knopf vs. Zeitfenster vs. QR); Lastverteilung der Boxen über Radios (RSSI vs. Auslastung); dynamisches Mesh-Routing nur bei nachgewiesenem Bedarf.

## Action Items

1. [ ] Frame-Format definieren: Routing-Header im Klartext (Ziel, Quelle, Frame-ID, TTL, Typ) + AES-GCM-Payload (Zähler-Nonce) + Tag, ≤250 Byte
2. [x] Pairing-Flow spezifiziert → **ADR-003** (backend-verankerte Pre-Shared Keys, DISCOVER/OFFER/CONFIRM, Session-Key per HKDF)
3. [ ] Heartbeat- und App-ACK-Protokoll festlegen (Intervalle, Timeouts, Offline-Erkennung inkl. „Relay down"-Unterscheidung)
4. [ ] Bridge-Software: `RadioInterface`-Abstraktion + Box→Radio-Tabelle (SQLite)
5. [ ] Prototyp: 1 Node (Pi + 1 Funkmodul) + 2 Boxen, Burst-Test inkl. Failover durch Modul-Reset
6. [x] ~~Nonce-Persistenz auf der Box testen~~ — entfällt: ADR-003 leitet pro Pairing einen frischen Session-Key ab, die Zähler-Nonce startet gefahrlos bei 0
