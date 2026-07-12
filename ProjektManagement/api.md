# SmartRange – REST API & MQTT-Topic-Schema

> Vollständige API-Referenz: REST-Endpunkte und MQTT-Topics mit Payload-Beispielen.
> Eingebunden von [project.md](project.md).

---

## 1. REST API

### 1.1 Authentifizierung

| Endpoint | Methode | Funktion |
|---|---|---|
| `/auth/login` | POST | Login, liefert JWT |
| `/auth/refresh` | POST | JWT-Refresh |
| `/auth/release-device/pair` | POST | Tablet-Pairing an Platz binden |

### 1.2 SmartBox-Verwaltung (Anlagebetreiber)

| Endpoint | Methode | Funktion |
|---|---|---|
| `/api/smart-boxes` | GET | Alle SmartBoxen auflisten (Online-Status, IP, Config-Sync) |
| `/api/smart-boxes/{id}` | GET | Details einer SmartBox |
| `/api/smart-boxes/{id}/alias` | PUT | Klartextnamen setzen |
| `/api/smart-boxes/{id}/devices` | GET | Registrierte Geräte einer SmartBox |
| `/api/smart-boxes/{id}/devices` | POST | Neues Gerät registrieren (Template-ID + Pin + optionaler Alias) |
| `/api/smart-boxes/{id}/config/push` | POST | Config-Push manuell auslösen (z.B. nach Desync) |

### 1.3 Geräte-Verwaltung

| Endpoint | Methode | Funktion |
|---|---|---|
| `/api/devices` | GET | Alle Geräte auflisten (filterbar nach SmartBox, Platz) |
| `/api/devices/{id}` | GET / PUT / DELETE | Gerät abrufen / bearbeiten (Alias, signal_duration_s) / löschen |
| `/api/devices/{id}/fire` | POST | Gerät manuell auslösen (nur Standwart/Admin) |

### 1.3a Gerät-Templates (Anlagebetreiber)

| Endpoint | Methode | Funktion |
|---|---|---|
| `/api/device-templates` | GET | Alle Templates auflisten |
| `/api/device-templates` | POST | Neues Template anlegen |
| `/api/device-templates/{id}` | GET / PUT / DELETE | Template abrufen / bearbeiten / löschen |

### 1.4 Schiessplätze

| Endpoint | Methode | Funktion |
|---|---|---|
| `/api/ranges` | GET / POST | Plätze auflisten / anlegen |
| `/api/ranges/{id}/devices` | POST | Gerät an Platz zuweisen |
| `/api/ranges/{id}/lock` | POST | Platz sperren (Sicherheit) |
| `/api/ranges/{id}/unlock` | POST | Platz entsperren |

### 1.5 Auslösegeräte (Tablets)

| Endpoint | Methode | Funktion |
|---|---|---|
| `/api/release-devices` | GET / POST | Tablets verwalten |
| `/api/release-devices/{id}/assign` | PUT | Tablet einem Platz zuordnen |

### 1.6 Schützen & Rotten

| Endpoint | Methode | Funktion |
|---|---|---|
| `/api/shooters` | GET / POST | Schützen verwalten |
| `/api/shooters/{id}/history` | GET | Trainingshistorie |
| `/api/squads` | GET / POST | Rotten verwalten (persistent) |
| `/api/squads/{id}/members` | PUT | Mitglieder einer Rotte |

### 1.7 Schiessprogramme

| Endpoint | Methode | Funktion |
|---|---|---|
| `/api/programs` | GET | Templates + Custom-Programme auflisten |
| `/api/programs` | POST | Schiessprogramm anlegen (inkl. Platz-Segmente) |
| `/api/programs/{id}` | GET / PUT / DELETE | Programm abrufen / bearbeiten / löschen |
| `/api/programs/{id}/segments` | GET / PUT | Platz-Segmente abrufen / komplett ersetzen |

### 1.8 Freie Nutzung (Gast / Anonymous Fire)

| Endpoint | Methode | Funktion |
|---|---|---|
| `/api/ranges/{id}/free-fire` | POST | Wurf anonym auslösen (kein Auth-Token). Platz muss entsperrt sein. Protokolliert als `FREE_FIRE` im `audit_log`. |

### 1.9 Schussblöcke & Schussabgaben

| Endpoint | Methode | Funktion |
|---|---|---|
| `/api/range-rounds/{id}/blocks` | POST | Neuen Schussblock erstellen (Gerät, Schusstyp, Anzahl) |
| `/api/shot-blocks/{id}/fire` | POST | Taube auslösen (Einzeltaube / Simultan / Raffale) |
| `/api/shot-blocks/{id}/fire-clay2` | POST | **Auf Schuss**: Taube 2 manuell auslösen (Spotter) |
| `/api/shot-blocks/{id}/interrupt` | POST | **Auf Schuss**: Taube 2 unterbrechen (No-Bird auf Taube 1) |
| `/api/shot-blocks/{id}/result` | PUT | Tauben-Ergebnis erfassen (`{clay_nr, hit, no_bird}`) |
| `/api/shot-blocks/{id}/complete` | POST | Block abschliessen |

### 1.10 Platz-Runden (RangeRound)

| Endpoint | Methode | Funktion |
|---|---|---|
| `/api/range-rounds/{id}` | GET | Platz-Runde mit Blöcken und Ergebnissen abrufen |
| `/api/range-rounds/{id}/complete` | POST | Platz-Runde abschliessen → nächster Platz freigegeben |

### 1.11 Passen

| Endpoint | Methode | Funktion |
|---|---|---|
| `/api/passen` | POST | Passe starten (Schiessprogramm, Rotte, optionale Segment-Overrides; `is_free_passe` flag) |
| `/api/passen/{id}` | GET | Passe-Status (aktuelle Platz-Runde, Blöcke, Ergebnisse) |
| `/api/passen/{id}/end` | POST | Passe beenden |
| `/api/passen/{id}/confirm` | POST | Passe final bestätigen (nur Wettkampfleiter) |
| `/api/passen/{id}/shots/{shot_sequence_id}` | PUT | Ergebnis nachträglich korrigieren (nur Wettkampfleiter) |

### 1.12 Trainingseinheiten / Wettkämpfe

| Endpoint | Methode | Funktion |
|---|---|---|
| `/api/trainings` | GET / POST | Trainingseinheiten auflisten / erstellen |
| `/api/trainings/{id}` | GET / PUT / DELETE | Trainingseinheit abrufen / bearbeiten / löschen |
| `/api/trainings/{id}/start` | POST | Trainingseinheit starten (Passe 1 automatisch) |
| `/api/trainings/{id}/next-passe` | POST | Nächste Passe starten |
| `/api/trainings/{id}/end` | POST | Trainingseinheit vorzeitig beenden |
| `/api/trainings/{id}/results` | GET | Gesamtauswertung: Hit/Miss/No-Bird pro Schütze, Rangierung |
| `/api/trainings/join` | POST | Laufendes Training beitreten via Schützen-QR-Code (`{shooter_qr_code}`) |
| `/api/trainings/generate` | POST | Trainingseinheit aus verfügbaren Plätzen + Programm auto-generieren |

---

## 2. MQTT-Topic-Schema

Zweistufige Adressierung (SmartBox + Gerät):

| Topic | Richtung & Zweck |
|---|---|
| `smartrange/smartboxes/{boxId}/discovery` | SmartBox → Backend: Einmaliger Publish beim Start (MAC, IP, Firmware) |
| `smartrange/smartboxes/{boxId}/status` | SmartBox → Backend: Heartbeat alle 10 s |
| `smartrange/smartboxes/{boxId}/config` | Backend → SmartBox: Vollständige Gerätekonfiguration |
| `smartrange/smartboxes/{boxId}/config/ack` | SmartBox → Backend: Bestätigung des Config-Push |
| `smartrange/smartboxes/{boxId}/devices/{deviceId}/command` | Backend → SmartBox: `fire`, `block`, `unblock`, `ping` |
| `smartrange/smartboxes/{boxId}/devices/{deviceId}/ack` | SmartBox → Backend: Bestätigung eines Kommandos |
| `smartrange/smartboxes/{boxId}/devices/{deviceId}/event` | SmartBox → Backend: OUTPUT-Geräte-Events (Knopf, Sensor) |
| `smartrange/ranges/{rangeId}/events` | Backend → UI (via WS-Bridge): Platz-weite Events |

### Discovery-Payload (SmartBox → Backend)

```json
{
  "boxId": "AA-BB-CC-DD-EE-FF",
  "mac": "AA:BB:CC:DD:EE:FF",
  "ip": "192.168.1.50",
  "firmware": "1.0.0",
  "timestamp": "2026-04-19T10:00:00Z"
}
```

### Config-Push (Backend → SmartBox)

```json
{
  "action": "configure",
  "requestId": "uuid-cfg-001",
  "devices": [
    {
      "deviceId": "uuid-werfer-1",
      "alias": "Werfer 1",
      "boxType": "GPIO_PULSE",
      "pin": 15,
      "signal_type": "INPUT",
      "signal_duration_ms": 300
    },
    {
      "deviceId": "uuid-knopf-1",
      "alias": "Knopf 1",
      "boxType": "GPIO_INPUT",
      "pin": 8,
      "signal_type": "OUTPUT",
      "signal_duration_ms": 50
    }
  ]
}
```

### Config-Ack (SmartBox → Backend)

```json
{
  "requestId": "uuid-cfg-001",
  "status": "applied",
  "timestamp": "2026-04-19T10:00:01Z"
}
```

### Kommando-Beispiele (Backend → SmartBox)

```json
// fire (INPUT-Gerät)
{
  "action": "fire",
  "requestId": "uuid-1234",
  "shotSequenceId": "uuid-sequence"
}

// block (Platz-Sperre)
{
  "action": "block",
  "reason": "range_locked",
  "requestId": "uuid-5678"
}

// unblock
{
  "action": "unblock",
  "requestId": "uuid-9012"
}
```

### Device-Ack (SmartBox → Backend)

```json
{
  "requestId": "uuid-1234",
  "status": "executed",
  "deviceState": { "blocked": false },
  "timestamp": "2026-04-19T10:00:00.123Z"
}
```

### Device-Event (SmartBox → Backend, OUTPUT-Geräte)

```json
{
  "deviceId": "uuid-knopf-1",
  "event": "triggered",
  "timestamp": "2026-04-19T10:00:02Z"
}
```

### Status-Payload (SmartBox → Backend)

```json
{
  "boxId": "AA-BB-CC-DD-EE-FF",
  "ip": "192.168.1.50",
  "status": "online",
  "firmware": "1.0.2",
  "uptime_s": 3600,
  "devices": [
    { "deviceId": "uuid-werfer-1", "blocked": false, "healthy": true },
    { "deviceId": "uuid-knopf-1",  "blocked": false, "healthy": true }
  ],
  "timestamp": "2026-04-19T10:00:00Z"
}
```

### Unterstützte boxType-Werte (Firmware-Handler)

| boxType | Verhalten | signal_type |
|---|---|---|
| `GPIO_PULSE` | Setzt GPIO-Pin für `signal_duration_ms` auf HIGH (Relais, Werfer) | INPUT |
| `GPIO_INPUT` | Liest GPIO-Pin, publiziert Event bei Zustandsänderung (Knopf, Schalter) | OUTPUT |
| `LED_DIGITAL` | Schaltet LED für `signal_duration_ms` ein | INPUT |
| `RELAY` | Wie GPIO_PULSE, aber mit eigener Absicherungslogik | INPUT |
