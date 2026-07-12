# SmartRange – Fachliche Abläufe & Sicherheitsgrundsätze

> Referenz-Dokument für Verwaltungs- und Benutzerabläufe, technische Kernabläufe und Sicherheitsprinzipien.
> Eingebunden von [project.md](project.md).

---

## 1. Verwaltungsabläufe (Anlagebetreiber & Standwart)

### 1.1 Anlage einrichten (Erstinbetriebnahme)

**Phase 1 – SmartBox in Betrieb nehmen (automatisch)**

1. **Discovery**: Neue SmartBox verbindet sich mit WLAN und MQTT-Broker. Publiziert automatisch eine Discovery-Message (`smartrange/smartboxes/{mac}/discovery`) mit MAC, IP und Firmware-Version.
2. **Registrierung**: Backend legt SmartBox-Eintrag an (Status: `ONLINE – nicht konfiguriert`). Erscheint als unbenannte SmartBox im Admin-Dashboard.
3. **Benennen**: Anlagebetreiber vergibt Klartextnamen (z.B. „SmartBox-Stand-A-Links").

**Phase 2 – Geräte via Server registrieren**

4. **Gerät-Template wählen**: Anlagebetreiber wählt Template (z.B. „Werfer" mit `boxType: GPIO_PULSE`), weist SmartBox zu, gibt GPIO-Pin und optionalen Alias an.
5. **Config-Push**: Backend sendet nach Registrierung einen Config-Push an die SmartBox. SmartBox speichert Konfiguration lokal (Flash), bestätigt mit `config/ack`. Backend setzt `config_synced = true`.
6. **Re-Subscribe**: SmartBox abonniert/deabonniert basierend auf empfangener Konfiguration die korrekten MQTT-Topics.

**Phase 3 – Platz und Betrieb einrichten**

7. **Platz anlegen**: Schiessplätze erstellen (z.B. „Platz A") und registrierte Geräte zuweisen.
8. **Auslösegerät registrieren**: Tablet am Platz, Browser öffnen, URL aufrufen. Anlagebetreiber weist Platz zu – Tablet ist als ReleaseDevice gebunden (genau 1 pro Platz).
9. **Schiessprogramme anlegen**: Templates für Trap 25, Skeet 25 etc. mit Tauben-Verteilung pro Platz.

### 1.2 Laufendes Management

- **SmartBox-Überwachung**: Online/Offline-Status, IP, Firmware-Version, Uptime, Config-Sync-Status im Dashboard.
- **Platz sperren/entsperren**: Sicherheits-Lock (verhindert jedes `fire`-Kommando). Sperr-Grund optional erfassbar.
- **Standwarte zuweisen**: Benutzer mit Rolle `STANDWART` einem oder mehreren Plätzen zuordnen.
- **Schützen-Verwaltung**: Registrierte Schützen anlegen, bearbeiten, Rollen vergeben.
- **Schiessprogramme verwalten**: Templates und Custom-Programme erstellen, bearbeiten, löschen.

### 1.3 Konfigurationen

- **Signal-Dauer pro Gerät**: `signal_duration_s` bestimmt Puls-Dauer (INPUT-Geräte) bzw. Entprellzeit (OUTPUT-Geräte). Im Template vorgegeben, pro Instanz überschreibbar.
- **Solo-Delay**: Konfigurierbarer Standardwert für Verzögerungs-Modus (Einzelschütze allein).
- **MQTT-Credentials**: Pro SmartBox individuelle Broker-Zugangsdaten.
- **Schützen-QR-Codes**: Persönlicher QR-Code im Schützen-Profil (immer gültig, kein Ablaufdatum).

### 1.4 Statistiken & Gerätemonitoring

- **Würfe pro Gerät**: Anzahl `fire`-Kommandos (Betriebsstunden-Äquivalent für Wartungsplanung).
- **No-Bird-Rate pro Gerät**: Indikator für mechanischen Verschleiss oder Ladeproblem.
- **Auslöse-Statistik gesamt**: Würfe pro Tag/Woche/Platz, aufgeteilt nach Freier Nutzung, Training, Wettkampf.
- **Schützen-Statistik**: Trefferquote, No-Bird-Anteil, Verlauf über Zeit – im Schützen-Profil.
- **Gerätestatus-Historie**: Online/Offline-Wechsel, Fehlerrate, Firmware-Versionen.

---

## 2. Benutzerabläufe (Schütze / Standwart)

### 2.1 Freier Schütze (Standard-Betriebsmodus)

> **Grösster Use Case im Alltag.** Kein Account, kein Login, kein Programm nötig.

1. Schütze nimmt das am Platz montierte Tablet.
2. Browser zeigt Auslöse-UI im **Freien Modus**.
3. Schütze wählt Schusstyp (Einzeltaube / Doppeltaube + Variante) und Werfer.
4. Knopfdruck → Wurf wird ausgelöst.
5. Gesperrter Platz → Fire-Request wird abgewiesen.
6. Jeder Abschuss wird anonym im `audit_log` protokolliert (`FREE_FIRE`).

> Der Freie Modus ist der Default-Zustand jedes Tablet-Auslösegeräts. Training und Wettkampf sind explizit zu starten.

### 2.2 Training

> Für Einzelschützen, Gruppen und Vereine. Wettkampfähnlicher Ablauf, aber **flexibel** anpassbar.

**Grundstruktur:**
```
Training
 ├── Passe 1 (Freie Passe – offen, dynamisch)
 ├── Passe 2 (Schiessprogramm «Trap 25»)
 └── Passe n (beliebige Mischung)
```

**Einstieg:**
1. Schütze öffnet Browser, ruft SmartRange-URL auf.
2. Login mit eigenem Account. Persönlicher QR-Code im Profil abrufbar.
3. Schütze erstellt **Training** (Name, optional Rotten-Zusammensetzung).
4. Weitere Schützen treten bei: Scannen am Platz-Tablet ihren **Schützen-QR** → aktives Training wird automatisch erkannt.

**Szenario A – Freies Training (Freie Passe):**

1. Schütze scannt am Platz-Tablet seinen Schützen-QR → Training-Kontext aktiviert.
2. Aktiven Schützen aus Rotteliste wählen.
3. Schusstyp und Werfer wählen, auslösen.
4. Nach Abgabe: Hit / Miss / No-Bird erfassen.
5. Bei `no_bird`: Wiederholungswurf einplanen.
6. Alle Schützen absolvieren denselben Block → neuer Block oder Platz wechseln.
7. **Platz wechseln**: Abmelden → Platz B → Schützen-QR scannen → Training-Kontext bleibt aktiv.
8. **Beenden**: Profil → „Training beenden" → Ergebnistabelle Hit/Miss/No-Bird pro Schütze.

**Szenario B – Strukturiertes Training (mit Schiessprogramm):**

1. Schiessprogramm wählen (z.B. «Trap 25»), Rotte festlegen.
2. Ablauf wie eine Passe (→ 2.3), aber ohne Wettkampf-Einschränkungen: Ergebnisse editierbar, Blöcke anpassbar.

### 2.3 Wettkampf

> Strukturierter Betrieb mit mehreren Rotten, fixen Passen und offizieller Auswertung.

**Datenprinzip:** Alle Daten sind schützenzentriert. Der Wettkampf ist ein Rahmen.

**Struktur:** N Passen (Templates) × mehrere Rotten.

Beispiele:
- 4 × «Trap 25» = 100 Tauben
- 2 × «Trap 33» + 1 × «Trap 34» = 100 Tauben

**Gestaffelter Start (Staggered Start):** Rotten starten nicht am gleichen Platz für bessere Auslastung.

| Rotte | Start | Rotation |
|---|---|---|
| Rotte 1 | Platz A | → B → C → D |
| Rotte 2 | Platz B | → C → D → A |
| Rotte 3 | Platz C | → D → A → B |
| Rotte 4 | Platz D | → A → B → C |

**Ablauf einer Passe:**
1. Wettkampfleiter/Standwart weist Rotte einen **Startplatz** zu.
2. Rotte schiesst auf Startplatz (Platz-Runde): Jeder Schütze seine Tauben, Ergebnisse erfassen.
3. Nach Abschluss: UI signalisiert «Weiter zu nächstem Platz».
4. Rotte rotiert durch alle Plätze bis Passe vollständig.
5. **Wettkampfleiter bestätigt** abgeschlossene Passe final.
6. Bei Bedarf: Resultate korrigieren (Wettkampfleiter).

**Ergebnisauswertung:** Treffer/Fehler/No-Bird pro Schütze (absolut + Prozent), Rangierung, Auswertung nach Platz.

---

## 3. Technische Kernabläufe

### 3.1 Einzeltaube auslösen

1. Schütze drückt Auslöse-Button im UI.
2. UI sendet REST-Request `POST /api/devices/{id}/fire`.
3. Backend prüft: Platz gesperrt? Berechtigung? Aktive Passe/RangeRound (Training-Kontext)?
4. Bei OK: Backend publiziert MQTT `fire`-Kommando an Pico.
5. Pico prüft lokalen `blocked`-Status, löst Werfer aus.
6. Pico sendet `ack` zurück.
7. Backend empfängt Ack, UI erhält Live-Bestätigung via WebSocket.

### 3.2 Doppeltaube «Auf Schuss» auslösen

1. Schütze wählt Schusstyp `DOPPEL_AUF_SCHUSS`, Werfer 1 und Werfer 2.
2. **Fire Werfer 1**: Backend publiziert `fire` → Taube 1 fliegt.
3. **Spotter** erfasst Ergebnis Taube 1 (Hit / Miss / No-Bird).
4. Bei `no_bird` auf Taube 1: Spotter drückt **„Interrupt"** → kein Fire an Werfer 2, Wiederholung.
5. Andernfalls: Spotter drückt **„Taube 2 auslösen"** → Backend publiziert `fire` an Werfer 2.
6. Schütze schiesst → Ergebnis Taube 2 erfassen.

### 3.3 Platz sperren (Sicherheit)

1. Standwart/Anlagebetreiber klickt „Platz sperren".
2. Backend setzt `range.locked = true`.
3. Backend publiziert `block`-Kommando an **alle Geräte** des Platzes.
4. SmartBoxen quittieren `block` → Geräte-Status in UI rot.
5. Alle weiteren `fire`-Requests für diesen Platz werden serverseitig abgewiesen.
6. SmartBox verweigert zusätzlich lokal (**Defense-in-depth**).
7. Entsperren: analoger Ablauf mit `unblock`.

### 3.4 SmartBox Config-Push (Geräte-Registrierung)

1. Admin registriert Gerät via `POST /api/smart-boxes/{id}/devices` (Template-ID + Pin + optionaler Alias).
2. Backend legt Device-Eintrag an, setzt `config_synced = false`.
3. Backend serialisiert vollständige Geräteliste und publiziert Config-Push: `smartrange/smartboxes/{boxId}/config`.
4. SmartBox empfängt Config, speichert in Flash.
5. SmartBox bestätigt mit `config/ack` → Backend setzt `config_synced = true`.
6. SmartBox subscribes/unsubscribes entsprechend neuer Config.

> **Resync bei Neustart**: Discovery-Message triggert automatischen Config-Push falls `config_synced = false`.

---

## 4. Sicherheitsgrundsätze

- **Zwei-Ebenen-Sperre**: Backend weist `fire` ab **UND** SmartBox hat eigenen lokalen `blocked`-Zustand pro Gerät.
- **Kein Auto-Fire**: Geräte senden nur auf expliziten Request (Freie Nutzung, Training, Wettkampf, manuell).
- **ReleaseDevice-Binding**: Ein Tablet ist genau einem Platz zugeordnet; pro Platz genau 1 Auslösegerät.
- **Audit-Log**: Jede Auslösung (User oder anonym, Gerät, Schusstyp, Zeitstempel, Resultat) wird serverseitig protokolliert.
