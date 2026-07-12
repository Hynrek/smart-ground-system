# Hub/Node-Architektur — Roadmap der Teilprojekte

**Datum:** 2026-07-10
**Spec:** [2026-07-10-hub-node-architecture-design.md](../specs/2026-07-10-hub-node-architecture-design.md) — **die Autorität.** Vor jedem Teilprojekt lesen.
**Bezug:** [plan-espnow-migration.md](../../plan-espnow-migration.md) (Bausteine A–D erledigt, Phase 2a/2b offen)

## Zweck dieses Dokuments

Die Spec beschreibt das Zielbild. Sie umfasst mehrere unabhängige Teilsysteme und ist zu gross für einen einzigen Implementierungsplan. Dieses Dokument zerlegt sie in Teilprojekte, hält deren Abhängigkeiten und Status fest und ist der **Einstiegspunkt für jeden neuen Chat-Kontext**.

> **Statuspflege:** Der Status hier wird beim Abschluss eines Teilprojekts von Hand nachgezogen. Checkboxen in den Einzelplänen sind erfahrungsgemäss unzuverlässig (die Bausteine B/C wurden implementiert und committet, ohne dass ihre Boxen je abgehakt wurden). **Im Zweifel gilt die Git-Historie**, nicht die Checkbox.

## Vorgehen pro Teilprojekt (neuer Chat)

1. Spec lesen: `docs/superpowers/specs/2026-07-10-hub-node-architecture-design.md`
2. Diese Roadmap lesen, das nächste Teilprojekt mit erfüllten Abhängigkeiten wählen
3. `superpowers:writing-plans` für genau dieses Teilprojekt → Plan nach `docs/superpowers/plans/YYYY-MM-DD-<name>.md`
4. `superpowers:subagent-driven-development` (oder `executing-plans`) zur Ausführung
5. Status in dieser Tabelle nachziehen, Roadmap-Änderung mitcommitten

Brainstorming entfällt — das Design ist entschieden und liegt in der Spec.

## Teilprojekte

| # | Teilprojekt | Hängt ab von | Status | Plan |
|---|---|---|---|---|
| 1 | Modulgrenze und Artefakt-Split | — | erledigt | [2026-07-10-module-boundary-split.md](2026-07-10-module-boundary-split.md) |
| 2 | Sync-Fundament (`hub-api`, abwärts) | 1 | erledigt | [2026-07-12-sync-fundament.md](2026-07-12-sync-fundament.md) |
| 3 | Outbox (`hub-api`, aufwärts) | 2 | offen | — |
| 4 | `node-channel` | 1 | erledigt | [2026-07-12-node-channel.md](2026-07-12-node-channel.md) |
| 5 | `node-api`-Fassade | 4 | offen | — |
| 6 | Offline-Login | 2 | offen | — |
| 7 | MQTT-Ausbau und `box-api` | 1 | erledigt | [2026-07-10-mqtt-ausbau-box-api.md](2026-07-10-mqtt-ausbau-box-api.md) |
| 8 | Firmware: Transport + Wartungs-Zustandsmaschine | 7 | offen | — |
| 9 | Node-Image und Deployment | 5, 7 | offen | — |

**Orthogonal, unberührt** (aus dem Migrationsplan, keine Abhängigkeit in beide Richtungen): Radio-Firmware (Phase 2a) und `RadioInterface` + serielle I/O (Phase 2b). Ohne sie kann ein Node keine Box physisch ansteuern; alle Teilprojekte oben sind dennoch ohne Hardware testbar.

**Empfohlener Start: #1.** Mechanisch, blockiert alles Übrige, und heute am billigsten — `smart-ground-node` besteht aus vier Codec-Paketen und einer leeren `main`.

---

### 1. Modulgrenze und Artefakt-Split

`contracts` und `domain` als eigene Module extrahieren; `smart-ground-backend` → `smart-ground-hub`; `smart-ground-node` hängt vom Hub ausschliesslich über `contracts` und einen `HubClient`.

**Deliverable:** Beide Anwendungen booten. Ein Abhängigkeitstest (ArchUnit o. ä.) **bricht den Build**, wenn der Node in Hub-Interna greift. Damit wird die Regel aus dem Migrationsplan („Node kommuniziert ausschliesslich über Broker/HTTPS, nie DB-direkt oder in-process") vom Review-Kriterium zur Compilerregel.

### 2. Sync-Fundament (`hub-api`, abwärts)

`updated_at` (indexiert) + `deleted`-Flag auf syncbaren Entities, beginnend mit `Serie` (hat heute nur `created_at`). Cursor-Endpoint `GET /sync/{entity}?updated_after=` mit Tombstones, contract-first in `hub-api`. Node-seitiger Sync-Client nach SQLite.

**Deliverable:** Ein Node zieht Serien vom Hub, sieht Löschungen, überlebt einen Neustart mit korrektem Cursor.

### 3. Outbox (`hub-api`, aufwärts)

FIFO, single-flight. Vom Node vergebene UUIDs (der Hub akzeptiert sie; heute mintet Hibernate sie). Idempotenter Upload. Terminalzustand `FAILED` bei Hub-Ablehnung, sichtbar am betroffenen Dokument. **Read-your-writes:** der Read-Pfad des Node vereinigt gesyncte Zeilen mit der Outbox. `base_version`-Konflikterkennung; bei Divergenz behält der Hub die überschriebene Version.

**Deliverable:** Serie offline erstellen, sofort schiessen, Hub anstecken — Serie *und* die darauf verweisende `PlayInstance` landen in kausaler Reihenfolge, doppelter Upload erzeugt eine Zeile.

### 4. `node-channel`

Node-initiierte Dauerverbindung mit Reconnect/Backoff. Liveness mit `STALE`-Markierung nach N ausgefallenen Beats. Command-Dispatch abwärts. `COMMAND_OUTCOME_UNKNOWN` bei Timeout, Commands idempotent auf ihrer UUID.

**Deliverable:** Der Hub dispatcht ein Command an einen Node, ohne dessen IP zu kennen. Backhaul mitten im Command gekappt → `COMMAND_OUTCOME_UNKNOWN`, nicht „fehlgeschlagen".

> **Umfang:** Der Kanal (Registry, WS-Endpoint, HELLO-Auth, Liveness, `dispatchCommand` mit Ack-Korrelation) ist fertig und per End-to-End-Integrationstest bewiesen. Die *Verdrahtung* von `DeviceController.sendDeviceCommand` / `RangePositionService.sendPositionCommand` auf `dispatchCommand` bleibt offen — bewusst, sie braucht die Device→Node-Routing-Tabelle (Multi-SmartBox-Zuordnung, Sync-Fundament #2) und das Node→Box-ESP-NOW-Bein (Phase 2b). Diese Endpoints bleiben bis dahin `501`.

### 5. `node-api`-Fassade

Die erschöpfende Tabelle: jeder Endpoint × {Hub erreichbar, Hub weg} × {eigener Platz, fremder Node} → genau eines aus {proxy, stale-read, refuse}. Provenienz-Envelope (`live` | `as_of`). Ablehnungen als `ProblemDetail` (`/errors/hub-unreachable`, `/errors/node-unreachable`, `/errors/box-unreachable`).

**Deliverable:** Die vier Verbote der Spec sind als Tests formuliert und grün.

### 6. Offline-Login

Credential-Material für die User dieser Anlage synchronisieren (Hash unter starker KDF, Rollen, `updated_at`, `deleted`). Der Node authentifiziert **immer** lokal, auch bei erreichbarem Hub, und mintet ein auf sich selbst begrenztes Session-Token. Service-Token des Node pro Anlage, widerrufbar.

**Deliverable:** Login bei ausgeschaltetem Hub. Ein am Hub gelöschter User verliert nach dem nächsten Sync den Zugang.

### 7. MQTT-Ausbau und `box-api`

Mosquitto, `SmartBoxMqttRouter`, alle MQTT-Handler, `SmartBoxConfigPushService` und `mqttutils.py` entfernen. Provisionierung (`K_Box`) und OTA laufen über HTTPS auf dem Node-AP; CA beim Flashen gepinnt. Das Bootstrap-Credential-Arbeitspaket samt akzeptiertem Restrisiko entfällt ersatzlos.

**Deliverable:** Eine unprovisionierte Box holt sich `K_Box` über `box-api`, ohne dass irgendwo ein Broker läuft.

### 8. Firmware: Transport + Wartungs-Zustandsmaschine

Transport-Abstraktion über ESP-NOW (Betrieb) und WLAN (Wartung). Zustandsmaschine `OPERATIONAL ↔ MAINTENANCE` mit **unbedingtem Rückweg**: fehlgeschlagene Assoziation → Timeout → zurück auf ESP-NOW, Fehler melden. Node-seitig das erwartete Abwesenheitsfenster mit Deadline.

**Deliverable (Hardware):** Eine Box, die ausserhalb der WLAN-Reichweite zum Update aufgefordert wird, kommt über ESP-NOW zurück und meldet den Fehler.

### 9. Node-Image und Deployment

hostapd + dnsmasq, selbstsigniertes Zertifikat (einmalig auf dem festen Tablet akzeptiert), lokale Auslieferung der Vue-App durch den Node, Clubhaus-Node (null Boxen), nächtlicher Backup-Dump des Hub auf externen Datenträger.

**Deliverable:** Abnahmetest der Spec — 48 h mit ausgestecktem Hub durchgehend schiessen, danach vollständiger Abgleich ohne Datenverlust.

## Offene Punkte ausserhalb der Teilprojekte

Aus der Spec, bewusst nicht eingeplant: physische Sicherheit (Node und SmartBox gleichermassen), Update-Strategie der Hub-/Node-Software (betriebliche Pflicht, kein Code), Node↔Node-Direktrouting (nur falls die Cloud-Variante die Cross-Range-Verfügbarkeit unerträglich macht).

## Nachzuziehen

Die Spec widerspricht ADR-001 bis ADR-004 (Abschnitt „Ersetzt/ändert bestehende Entscheide"). ~~Die ADRs sind noch **nicht** angepasst.~~ **Erledigt (2026-07-12):** Alle vier ADRs tragen einen Überholt-Banner mit den konkreten Deltas; `docs/DESIGN.md`, `docs/DESIGN_RANGE_POSITIONS.md`, `docs/competition-management-view-plan.md` und `docs/plan-espnow-migration.md` sind als historisch/teilweise überholt markiert; `smart-ground-ui/CLAUDE.md` verweist auf die Hub/Node-Transition (node-api-Fassade, Provenienz, Offline). `CLAUDE.md` (Wurzel) und `smart-ground-hub/CLAUDE.md` (vormals `smart-ground-backend/CLAUDE.md`) wurden im Rahmen von Teilprojekt #7 bereits auf die neue Architektur (kein Backend-Alleinvertretungsanspruch mehr, kein MQTT als Transport) nachgezogen.
