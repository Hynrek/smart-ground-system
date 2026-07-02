# QR-Checkin: Echte Schützen im Trainings-Gruppen-Setup

**Datum:** 2026-07-02
**Status:** Entwurf genehmigt (Brainstorming-Session)

## Problem

Beim Start einer Serie/Passe im Training erzeugt das Gruppen-Setup
(`ShooterPlayPage.vue`, `addPlayer`) nur lokale Platzhalter-Schützen
(„Schütze 1", „Schütze 2" mit lokalen IDs `gp-N`). Diese landen als anonyme
`PlayerRef`s im `PlayInstance.playersJson` — ohne Verbindung zu einem
`User`-Account. Trainingsscores sind damit nach dem Durchgang faktisch
verloren: keine Zuordnung zum Profil, keine Auswertbarkeit.

## Lösung (Kurzfassung)

Jeder User besitzt einen **statischen, neu generierbaren QR-Token**. Beim
Gruppen-Setup am Stand-Tablet (hat Kamera) scannt das Tablet den QR-Code des
Schützen (vom Handy oder gedruckt) und fügt ihn als **echten Schützen mit
`userId`** zur Gruppe hinzu. Abgeschlossene Durchgänge erscheinen auf der
Profilseite als „Meine Ergebnisse".

## Entscheidungen aus der Brainstorming-Session

| Frage | Entscheidung |
|---|---|
| Scan-Richtung | **Schütze zeigt QR, Tablet scannt** (Stand-Gerät hat Kamera) |
| Geltungsbereich | **Nur Training** (Gruppen-Setup vor Serie/Passe). Wettkampf/Rotten und Admin-Ansichten sind spätere Würfe |
| Fälschungssicherheit | **Statischer Code reicht** (Vereinskontext, nur Trainingsscores). Kein rotierender Token |
| Mischbetrieb | **Gemischt erlaubt**: echte Accounts (per QR) und anonyme Platzhalter in derselben Gruppe. Nur echte Accounts bekommen Score-Zuordnung |
| Score-Umfang | **Attribution + Profil-Anzeige**: `userId` am PlayerRef + „Meine Ergebnisse"-Liste im Profil. Keine CareerStats-Anbindung |
| QR-Inhalt | **Eigener QR-Token** (nicht die User-UUID): zufällige Kennung, bei Missbrauch im Profil erneuerbar — alter Code wird wertlos |

## 1. Datenmodell (Backend)

- **`User.qrToken`** — neue Spalte, zufälliger UUID-String, unique, non-null.
  - Generiert beim Anlegen eines Users.
  - Für bestehende User beim Startup nachgefüllt (pre-v1.0: Hibernate-Diff +
    Backfill, z.B. im `DataInitializer` oder als Startup-Routine).
- **`PlayerRef`** (Einträge in `play_instances.players_json`) bekommt ein
  optionales Feld **`userId`** (UUID, nullable).
  - Anonyme Platzhalter behalten `userId = null` — Mischbetrieb ohne
    Sonderfälle.
  - `SessionPlayer` (Wettkampf-Domäne) bleibt unberührt.

## 2. API (contract-first, `openapi.yaml` zuerst)

| Endpoint | Zweck | Auth |
|---|---|---|
| `GET /api/users/me/qr` | Eigenen `qrToken` liefern (Profil-Anzeige) | eingeloggt |
| `POST /api/users/me/qr/rotate` | Neuen Token erzeugen; alter Token sofort wertlos | eingeloggt |
| `GET /api/users/by-qr/{token}` | Resolve beim Scan → `{ userId, displayName, profilbildUrl }`; unbekannter Token → 404 | jeder eingeloggte User (Kiosk-Konto braucht es) |
| `GET /api/users/me/play-results` | Abgeschlossene Trainings-Durchgänge des Users: Datum, Passe-Name, Stand, Score/Max — aus `play_instances` gefiltert auf `userId` im `playersJson` | eingeloggt |

- Der Play-Start-Request (`POST /api/play-instances/passe`) akzeptiert pro
  Player die optionale `userId`.
- **QR-Payload-Format:** `smartground://checkin/<token>` — das Präfix
  verhindert, dass ein beliebiger fremder QR-Code als Schütze interpretiert
  wird.
- Resolve liefert nur Anzeigedaten (Name, Avatar) — keine sensiblen
  Profilfelder.

## 3. UI

### Gruppen-Setup-Modal (`ShooterPlayPage.vue`)

- Neben „+ Schütze hinzufügen" ein zweiter Button **„+ Schütze per QR"**.
- Öffnet ein Scan-Modal mit Kamerabild (Lib **`qr-scanner`**):
  - erkennt Payload mit Präfix `smartground://checkin/`,
  - ruft `GET /api/users/by-qr/{token}`,
  - fügt den Schützen mit echtem `displayName` + `userId` zur Gruppe hinzu.
- Echte Schützen bekommen ein Badge/Avatar zur Unterscheidung vom
  Platzhalter.
- Kein Backend-Roundtrip pro Frame — Resolve erst nach erkanntem, gültigem
  Payload.

### Profilseite

- QR-Code-Anzeige (client-seitig gerendert, Lib **`qrcode`**), gross genug
  zum Abscannen vom Handy-Display.
- Button **„Code erneuern"** → `POST /api/users/me/qr/rotate`, Anzeige
  aktualisiert sich.
- Liste **„Meine Ergebnisse"**: abgeschlossene Trainings-Durchgänge (Datum,
  Passe, Stand, Score/Max) via `GET /api/users/me/play-results`.

### Neue Dependencies

- `qr-scanner` (Kamera-Scan) und `qrcode` (Rendern) — beide klein, etabliert,
  ohne transitive Abhängigkeiten.

## 4. Fehlerfälle

| Fall | Verhalten |
|---|---|
| Unbekannter/rotierter Token | „Code ungültig" im Scan-Modal; Scan läuft weiter |
| Kamera verweigert / nicht vorhanden | Hinweis im Modal; anonymer Platzhalter bleibt als Fallback immer möglich |
| Derselbe User zweimal gescannt | Hinweis, kein Doppeleintrag in der Gruppe |
| Fremder QR-Code (falsches Präfix) | Wird ignoriert, Scan läuft weiter |
| Backend nicht erreichbar beim Resolve | Fehlermeldung mit Retry-Möglichkeit |

## 5. Testing

- **Backend:**
  - Resolve-Endpoint: Treffer / 404 bei unbekanntem Token.
  - Token-Rotation: alter Token liefert danach 404.
  - `play-results`-Query: nur abgeschlossene Instanzen, nur mit passender
    `userId`, korrekte Score-Extraktion.
  - Play-Start mit gemischter Gruppe (mit/ohne `userId`).
- **UI:**
  - Scan-Modal mit gemocktem Scanner-Callback: gültiger Code, ungültiger
    Code, Duplikat, falsches Präfix.
  - Store-Tests für neue Actions (resolve, rotate, play-results).
  - Profil: QR-Anzeige + Rotation aktualisiert den Code.

## Bewusst nicht im Scope (YAGNI)

- Wettkampf-/Rotten-Checkin (`RotteSetupModal`)
- Manuelle Namenssuche am Tablet
- CareerStats-Anbindung für Trainingsergebnisse
- Generierung gedruckter Mitgliederkarten
- Umgekehrte Scan-Richtung (Stand zeigt QR, Schütze scannt) — käme erst mit
  Live-Update der Gruppenliste (STOMP) in Frage

Alle diese Punkte können später auf demselben `qrToken` aufsetzen.
