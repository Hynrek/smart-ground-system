# ADR-001: Datenabgleich zwischen Hauptserver und SmartNode

**Status:** Proposed — teilweise überholt (siehe Banner)
**Datum:** 2026-07-09
**Entscheider:** Jonas Studer

> ⚠️ **Teilweise überholt durch die [Hub/Node-Spec](../superpowers/specs/2026-07-10-hub-node-architecture-design.md)** (Abschnitt „Ersetzt/ändert bestehende Entscheide" — die Spec ist die Autorität). Konkret: „Der Hauptserver bleibt Source of Truth" meinte ein **Cloud-Backend**; ersetzt durch den **lokalen Hub**, mit Autorität nach Schreibklasse getrennt (Konfiguration ↔ operativer Zustand). Die **Sync-Mechanik bleibt wörtlich gültig** (Cursor-Pull, Tombstones, idempotente Outbox), ergänzt um: Outbox ist FIFO/kausal geordnet; „Resultate: keine Konflikte möglich" schweigt über Referenzen auf offline erstellte Dokumente.

## Kontext

Mit der geplanten ESP-NOW-Erweiterung kommt der **SmartNode** als neues Element hinzu: Er dient als ESP-NOW↔MQTT-Bridge und als lokaler Webserver, damit eine Anlage auch ohne (oder mit instabilem) Internet-Uplink als vollwertiges SmartGround-System funktioniert.

User erstellen und speichern eigene **Schiessprogramme** auf dem Hauptserver. Ein Schütze muss auf der Anlage auf seine Programme zugreifen können und darf keine Resultate (Serien/Passen) verlieren — auch wenn der Uplink tage­lang ausfällt. Dafür braucht es einen Datenabgleich zwischen Hauptserver und SmartNode.

Kräfte:

- Der Uplink (Ethernet/WLAN/LTE) ist vorhanden, aber nicht garantiert verfügbar.
- Resultate entstehen ausschliesslich auf der Anlage; Programme werden primär online (App → Hauptserver) editiert.
- Es gibt noch kein Production-Release — Schema und API dürfen frei angepasst werden.
- UUIDs sind bereits durchgängig Primary Keys (offline erzeugte Datensätze kollidieren nicht).

## Entscheidung

Der **Hauptserver bleibt Source of Truth**. Der SmartNode ist ein **Edge-Cache mit Offline-Queue**, kein DB-Replikat. Der Abgleich läuft als **Entity-Sync über HTTPS auf dem bestehenden Uplink** (cursor-basierter Pull nach unten, Outbox-Push nach oben). LoRaWAN wird als Sync-Kanal verworfen. Der SmartNode wird auf stärkerer Hardware (Raspberry-Pi-Klasse, SQLite, lokales Backend) realisiert; der ESP32 hängt als ESP-NOW-Funkmodul per UART daran.

### Sync-Mechanik

**Runter (Server → Node):** Der Node pollt periodisch `GET /sync/programs?updated_after={cursor}` für die relevante Teilmenge (Programme der Mitglieder dieser Anlage, nicht die ganze DB). Löschungen als **Soft-Delete-Flag** (Tombstone), sonst erfährt der Node sie nie.

**Rauf (Node → Server):** **Outbox-Pattern.** Lokale Änderungen landen in einer persistenten Queue (SQLite) und werden bei verfügbarem Uplink idempotent abgearbeitet (UUID als Idempotenz-Schlüssel).

**Konfliktbehandlung über das Datenmodell:**

| Entity | Charakteristik | Konfliktstrategie |
|---|---|---|
| Resultate (Serie/Passe/Play) | Append-only Events, entstehen nur auf der Anlage, nie editiert | Keine Konflikte möglich — Queue rauf, fertig |
| Schiessprogramme | Dokumente, selten gleichzeitig offline + online editiert | `version`/`updated_at` pro Programm, Last-Write-Wins; bei Bedarf später immutable Versionen |
| Stammdaten (User, Rollen, Anlage) | Nur online editierbar | Read-only auf dem Node, reiner Pull |

**Zugriff für den Schützen:** Online spricht die App wie bisher direkt mit dem Hauptserver. Offline serviert der lokale Webserver des Node die gecachten Programme und nimmt Resultate entgegen.

## Betrachtete Optionen

### Option A: Entity-Sync über HTTPS (gewählt)

| Dimension | Bewertung |
|---|---|
| Komplexität | Niedrig — zwei Endpoints + Outbox |
| Kosten | Keine zusätzliche Infrastruktur |
| Skalierbarkeit | Gut — pro Anlage eine kleine Teilmenge |
| Team-Vertrautheit | Hoch — Spring Boot REST wie gehabt |

**Pro:** Volle Kontrolle, minimale Abhängigkeiten, passt zum contract-first OpenAPI-Workflow, Konfliktregeln explizit im Datenmodell.
**Contra:** Cursor-Logik, Tombstones und Idempotenz selbst bauen und testen.

### Option B: Sync-Framework (PowerSync, ElectricSQL, CouchDB-Replikation)

| Dimension | Bewertung |
|---|---|
| Komplexität | Mittel–Hoch — neue Infrastruktur + Lernkurve |
| Kosten | Zusätzliche Komponenten, teils Lizenz/Hosting |
| Skalierbarkeit | Sehr gut |
| Team-Vertrautheit | Niedrig |

**Pro:** Konfliktauflösung, Cursor und Retry geschenkt; skaliert auf viele Entities.
**Contra:** Für zwei Entity-Typen mit trivialen Konfliktregeln überdimensioniert; CouchDB würde ein zweites Datenbankparadigma neben PostgreSQL einführen.

### Option C: Volle DB-Replikation (PostgreSQL logical replication auf den Node)

**Pro:** Kein eigener Sync-Code.
**Contra:** Node erhält die *gesamte* DB (Datenschutz, Speicher), Postgres auf Edge-Hardware betreiben und upgraden, bidirektionale Replikation mit Postgres ist konfliktträchtig und operativ schwer. Verworfen.

### Option D: LoRaWAN als Sync-Kanal

**Contra:** 0,3–50 kbit/s, 51–242 Byte Payload, 1 % Duty-Cycle (EU868) — ein einzelnes Schiessprogramm bräuchte Minuten, Downlink noch stärker limitiert. Löst ein anderes Problem (km-Reichweite bei Batteriebetrieb). Verworfen; höchstens später als Notfall-Telemetrie denkbar.

## Trade-off-Analyse

Der zentrale Trade-off ist **Eigenbau-Sync (A) vs. Framework (B)**. A gewinnt, weil das Datenmodell die Konflikte bereits entschärft: Resultate sind append-only (konfliktfrei), Programme haben einen klaren Owner und werden praktisch nie gleichzeitig offline und online editiert. Die verbleibende Eigenbau-Komplexität (Cursor, Tombstones, idempotente Outbox) ist klein und gut testbar — kleiner als der Betrieb eines Sync-Frameworks. Sollte die Zahl der zu syncenden Entity-Typen deutlich wachsen (>5–6) oder echte Mehrbenutzer-Konflikte auftreten, ist B der Migrationspfad; die Cursor-/Tombstone-Vorarbeit bleibt dabei nutzbar.

## Konsequenzen

- **Einfacher:** Offline-Betrieb der Anlage; Resultate gehen nie verloren; Backend-Architektur bleibt unverändert (Server bleibt Source of Truth).
- **Schwerer:** Jede neue synchronisierte Entity braucht Cursor-Support, Soft-Delete und Konfliktregel; der SmartNode wird eine eigene kleine Applikation (Deployment, Updates, Monitoring).
- **Neu erforderlich:** `updated_at`-Index + Soft-Delete auf syncbaren Entities; Auth-Konzept für den Node (Service-Account/Token pro Anlage); OTA-/Update-Strategie für die Node-Software.
- **Später zu prüfen:** Immutable Programm-Versionen statt Last-Write-Wins; Sync-Framework ab >5–6 Entity-Typen; lokale Accounts/Login am Node ohne Uplink.

## Action Items

1. [ ] Syncbare Entities um `updated_at` (indexiert) und `deleted`-Flag erweitern
2. [ ] `GET /sync/{entity}?updated_after=` in `openapi.yaml` definieren, dann generieren
3. [ ] Outbox-Tabelle + idempotenter Upload-Endpoint für Resultate
4. [ ] SmartNode-Prototyp: Pi + SQLite + Sync-Client + lokaler Webserver
5. [ ] Auth: Service-Token pro Anlage/Node
6. [ ] Testfall: 48 h Offline-Betrieb, danach vollständiger Abgleich ohne Datenverlust
