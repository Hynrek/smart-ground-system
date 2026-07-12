# SmartRange – Entwicklungsrichtlinien

> Technologie-Constraints, Naming Conventions, Sicherheitsanforderungen, DE/EN-Mapping und Projektstruktur.
> Dieses Dokument ist Primärquelle für KI-Entwicklungsunterstützung.
> Eingebunden von [project.md](project.md).

---

## 1. Technologie-Constraints

| Technologie | Version / Detail |
|---|---|
| Spring Boot | **4** (nicht 3) |
| Java | **25** |
| Vue.js | **3 Composition API** – keine Options API |
| PostgreSQL | **18** mit Flyway-Migrationen |
| MQTT-Broker | **Mosquitto 2** / MQTT 3.1.1 |
| MQTT QoS | QoS 1 für sicherheitsrelevante Kommandos (fire, block, config), QoS 0 für Heartbeats |
| SmartBox-Firmware | **MicroPython** bevorzugt, `umqtt.simple` |

---

## 2. Naming Conventions

| Bereich | Konvention |
|---|---|
| Java Methoden/Felder | `camelCase` |
| Java Klassen | `PascalCase` |
| Java Packages | `com.smartrange.*` |
| Vue Komponenten | `PascalCase` |
| Vue Events | `kebab-case` |
| Vue Composables | `camelCase` (Prefix `use`) |
| MQTT Topics | `snake_case` mit `/` als Trenner, zweistufig `smartboxes/{id}/devices/{id}/...` |
| DB Tabellen | `snake_case` |
| DB Fremdschlüssel | `referenced_table_id` |
| REST Pfade | `kebab-case` (z.B. `/release-devices`) |

---

## 3. Sicherheitsanforderungen

- Alle REST-Endpunkte ausser `/auth/**` erfordern gültigen JWT.
- SmartBoxen authentifizieren sich am Broker mit Username/Passwort (pro SmartBox individuell).
- Passwörter: BCrypt.
- JWT-Signatur: RS256 mit Key-Pair.
- `fire`-Endpunkt prüft **immer**: Platz nicht gesperrt + User-Rolle passend + (falls Training-Kontext) passende aktive Passe/RangeRound.
- SmartBoxen halten zusätzlich einen lokalen `blocked`-Zustand pro Gerät (Defense-in-depth).
- Jede Auslösung wird im `audit_log` protokolliert.

---

## 4. Domänen-Begriffe (Deutsch/Englisch-Mapping)

| Fachlich (DE) | Code-Term (EN) |
|---|---|
| Schiessplatz | `Range` |
| Wurfmaschine | `Trap` (als `Device` mit `deviceType=TRAP`) |
| Gerät | `Device` |
| Gerät-Template | `DeviceTemplate` |
| SmartBox / Pico | `SmartBox` |
| Auslösegerät (Tablet/Browser) | `ReleaseDevice` |
| Schütze | `Shooter` (entspricht `User` mit Rolle `SCHUETZE`) |
| Rotte | `Squad` |
| Standwart | `RangeSteward` |
| Anlagebetreiber | `Admin` (Rolle `ANLAGEBETREIBER`) |
| Schiessprogramm | `ShootingProgram` |
| Passe | Entität `Passe` (Tabelle `passen`) |
| no-bird / Bruch | Feld `no_bird` auf `ClayResult` |
| Freie Nutzung | `FreeUse` / `FREE_FIRE` (Audit-Log-Action) |
| Trainingseinheit / Wettkampf | `Training` |
| Freie Passe | `FreePasse` (`is_free_passe = true`) |
| Platz-Runde | `RangeRound` |
| Schussblock | `ShotBlock` |
| Schussabgabe | `ShotSequence` |
| Tauben-Ergebnis | `ClayResult` |
| Platz-Segment (im Programm, JSONB) | `segments[]` in `ShootingProgram` |
| Wettkampfleiter | `WETTKAMPFLEITER` (Rolle) |
| Einzeltaube | `EINZELTAUBE` |
| Doppeltaube Auf Schuss | `DOPPEL_AUF_SCHUSS` |
| Doppeltaube Simultan | `DOPPEL_SIMULTAN` |
| Doppeltaube Raffale | `DOPPEL_RAFFALE` |
| Spotter | `Spotter` (Rolle am Tablet bei Auf-Schuss) |

---

## 5. Projektstruktur

```
smart-ground/
├── smart-ground-backend/              # Spring Boot 4
│   ├── src/main/java/com/smartrange/
│   │   ├── config/                    # SecurityConfig, MqttConfig
│   │   ├── controller/                # REST-Controller
│   │   ├── service/                   # Business-Logik (CommandService, TrainingService, SmartBoxService)
│   │   ├── repository/                # Spring Data JPA
│   │   ├── model/                     # JPA Entities (SmartBox, DeviceTemplate, Device, Range, ...)
│   │   ├── dto/                       # Request/Response DTOs
│   │   ├── mqtt/                      # MQTT Publisher/Subscriber (DiscoveryHandler, ConfigPushService)
│   │   └── websocket/                 # WS-Endpoint für Live-Updates
│   ├── src/main/resources/
│   │   ├── db/migration/              # Flyway V1__init.sql, …
│   │   └── application.yml
│   └── Dockerfile
│
├── smart-ground-ui/                   # Vue 3 (Admin-SPA + Auslöse-UI)
│   ├── src/
│   │   ├── admin/                     # Views für Anlagebetreiber/Standwart
│   │   ├── release/                   # Auslöse-UI für Tablet (Browser, kein Service Worker)
│   │   ├── shared/
│   │   │   ├── components/
│   │   │   ├── composables/
│   │   │   ├── stores/
│   │   │   └── api/
│   │   └── router/
│   └── Dockerfile
│
├── smart-box-firmware/                # MicroPython (Raspberry Pi Pico 2W)
│   ├── main.py
│   ├── mqtt_client.py
│   ├── discovery.py                   # Discovery-Message beim Start
│   ├── config_manager.py              # Config-Push empfangen, Flash, re-subscribe
│   ├── device_handlers.py             # Handler pro boxType: GPIO_PULSE, GPIO_INPUT, LED_DIGITAL, RELAY
│   └── config.py                      # WLAN-Credentials, MQTT-Broker-Adresse
│
├── mosquitto/
│   └── config/
│       ├── mosquitto.conf
│       └── passwd
│
├── ProjektManagement/
│   ├── project.md                     # Hauptdokument (Index)
│   ├── glossary.md                    # Glossar, Schusstypen, Rollen, Concurrency
│   ├── workflows.md                   # Fachliche Abläufe & Sicherheitsgrundsätze
│   ├── state-machines.md              # Zustandsmaschinen
│   ├── api.md                         # REST API & MQTT-Schema
│   ├── database.md                    # Datenbankschema
│   └── dev-guide.md                   # Dieses Dokument
│
└── docker-compose.yml
```
