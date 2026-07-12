# SmartRange – Glossar, Schusstypen & Rollen

> Referenz-Dokument für Domänenbegriffe, Schussmechanik, Rollen und Nebenläufigkeits-Regeln.
> Eingebunden von [project.md](project.md).

---

## 1. Domänen-Glossar

| Begriff | Definition |
|---|---|
| **Schiessanlage** | Die gesamte physische Anlage. In V1 genau **eine** Anlage pro Installation – keine eigene Entität, sondern globale Konfiguration. |
| **Schiessplatz (Range)** | Abgegrenzter Bereich innerhalb der Anlage mit 1–n zugeordneten Wurfmaschinen. Beispiele: „Platz A", „Platz B", „Vorderlader". |
| **Wurfmaschine (Trap)** | Physisches Gerät, das eine Tontaube wirft. Wird über eine SmartBox angesteuert. Muss aus Sicherheitsgründen **blockierbar** sein. |
| **Gerät (Device)** | Physisches Gerät auf der Anlage, das von einer SmartBox angesteuert oder ausgelesen wird. Basiert auf einem **Gerät-Template**. Signal-Perspektive: `INPUT` = Gerät reagiert auf Signal vom Server (Werfer, LED); `OUTPUT` = Gerät sendet Signal an Server (Knopf, Sensor). |
| **Gerät-Template (DeviceTemplate)** | Wiederverwendbare Vorlage für einen Gerätetyp. Definiert `name`, `type` (UI-Gruppierung: TRAP/LED/BUTTON), `boxType` (Firmware-Handler: GPIO_PULSE/GPIO_INPUT), `signal_type` (INPUT/OUTPUT) und Standard-`signal_duration_s`. Pin wird pro Instanz angegeben. |
| **SmartBox** | Raspberry Pi Pico 2W im Netzwerk. Meldet sich beim Start per Discovery-Message an. Verwaltet 1–n registrierte Geräte via Config-Push. Führt IP-Adresse, Status, Firmware-Version und Config-Sync-Zustand. |
| **Auslösegerät (ReleaseDevice)** | Tablet mit Browser, fest am Stand eines Schiessplatzes. Genau **1 pro Platz**. Dient dem Schützen/Standwart zur Auslösung der Wurfmaschinen. |
| **Schütze (Shooter)** | Person, die auf einem Platz schiesst. Kann registriert (Profil & Historie) oder Gast sein. |
| **Rotte (Squad)** | Gruppe von Schützen, die gemeinsam schiessen. Entweder persistent (Vereinsmannschaft) oder ad-hoc. |
| **Standwart** | Benutzer, der einen Platz verwaltet: sperren/entsperren, manuell auslösen, Session begleiten. |
| **Anlagebetreiber** | Administrativer Benutzer. Verwaltet SmartBoxen, Templates, Geräte, Plätze, Schützen, Programme. |
| **Schiessprogramm (ShootingProgram)** | Template mit Platz-Segmenten und Taubenanzahl. Beispiel «Trap 25»: Platz A=6, B=8, C=5, D=6 → total 25. Verteilung als JSONB. |
| **Passe** | Eine vollständige Ausführung eines Schiessprogramms durch eine Rotte. Die Rotte wandert **gemeinsam** sequenziell durch alle Plätze. |
| **no-bird / Bruch** | Fehlerhafte Taube (zerbrochen, Fehlwurf). Nicht als Fehlschuss gewertet. **Gesamte Schussabgabe wird wiederholt** – bei Einzel- und Doppeltaube. |
| **Sperre (Lock)** | Sicherheitszustand eines Schiessplatzes. Kein `fire`-Kommando kann an zugeordnete Geräte gesendet werden. |
| **Freie Nutzung (FreeUse)** | Standard-Betriebsmodus. Kein Account, kein Login, keine Session. Tablet nehmen, Knopf drücken, schiessen. Anonym, aber vollständig im Audit-Log protokolliert. |
| **Trainingseinheit (Training)** | Sammlung von N Passen. Flexibler Rahmen für Einzel-/Gruppentraining. |
| **Wettkampf (Competition)** | Spezialfall von Training mit fixen Regeln: mehrere Rotten, definierte Passenanzahl, genaue Auswertung. |
| **Platz-Runde (RangeRound)** | Ausführung eines einzelnen Platz-Segments innerhalb einer Passe. |
| **Freie Passe (FreePasse)** | Offene Passe ohne fixe Taubenanzahl. Wächst dynamisch, wird manuell beendet. |
| **Schussblock (ShotBlock)** | Gruppe von Schussabgaben an einem Gerät. Alle Schützen der Rotte schiessen denselben Block, bevor gewechselt wird. |
| **Schussabgabe (ShotSequence)** | Eine Abgabe = max. 2 Schüsse. Entweder **Einzeltaube** (1 Taube, 2 Versuche) oder **Doppeltaube** (2 Tauben, 1 Schuss je). |
| **Einzeltaube** | 1 Taube, 2 Schussversuche. Ergebnis: `hit` / `miss` / `no_bird`. |
| **Doppeltaube** | 2 Tauben, je 1 Schuss. Untertypen: **Auf Schuss**, **Simultan**, **Raffale**. Ergebnis pro Taube. |
| **Auf Schuss** | Sequenziell, Spotter-gesteuert: Taube 1 fliegt, danach löst Spotter Taube 2 aus. |
| **Simultan** | Beide Werfer gleichzeitig ausgelöst. |
| **Raffale** | Derselbe Werfer feuert zweimal hintereinander. |
| **Spotter** | Person am Tablet, die bei «Auf Schuss» Taube 2 manuell auslöst. |
| **QR-Code (Schützen-QR)** | Persönlicher, statischer QR-Code aus dem Schützen-Profil. Beim Scan erkennt das System das aktive Training automatisch (max. 1 pro Schütze). |

---

## 2. Schusstypen & Schussmechanik

### Grundprinzip

Doppelläufige Schrotflinte: max. **2 Schüsse pro Abgabe**. Jede `ShotSequence` umfasst genau diese 2 Schüsse, verteilt auf 1 oder 2 Tauben.

### Einzeltaube

| Eigenschaft | Wert |
|---|---|
| Anzahl Tauben | 1 |
| Schussversuche | 2 (beide Läufe auf dieselbe Taube) |
| Werfer-Auslösungen | 1 |
| Ergebnis | `hit` / `miss` / `no_bird` |

### Doppeltaube

| Eigenschaft | Wert |
|---|---|
| Anzahl Tauben | 2 |
| Schussversuche | 1 pro Taube |
| Werfer-Auslösungen | 1–2 (je nach Ausprägung) |
| Ergebnis | `hit` / `miss` / `no_bird` **pro Taube** |

**Auf Schuss** – sequenziell, Spotter-gesteuert:
1. Werfer 1 wirft Taube 1 → Schütze schiesst
2. Am Tablet nur **«No Bird»** wählbar (kein Hit/Miss – Ergebnis erst nach Taube 2)
3. Bei «No Bird»: gesamte Schussabgabe wird wiederholt (beide Tauben)
4. Kein No-Bird: **Spotter** drückt «Taube 2 auslösen» → Werfer 2 → Schütze schiesst
5. Hit/Miss für **beide Tauben** gemeinsam eingeben

**Simultan:** Werfer 1 + Werfer 2 gleichzeitig ausgelöst (parallel MQTT-Kommandos).

**Raffale:** Derselbe Werfer zweimal hintereinander; Schütze schiesst Taube 1, dann Taube 2.

### Schusstypen-Übersicht

| Typ (Code) | Tauben | Werfer |
|---|---|---|
| `EINZELTAUBE` | 1 | 1 |
| `DOPPEL_AUF_SCHUSS` | 2 | 2 (sequenziell, Spotter-gesteuert) |
| `DOPPEL_SIMULTAN` | 2 | 2 (gleichzeitig) |
| `DOPPEL_RAFFALE` | 2 | 1 (zweimal hintereinander) |

Hit/Miss/No-Bird wird in allen Fällen **manuell** am Tablet erfasst.

---

## 3. Rollen & Berechtigungen

| Rolle | Berechtigungen |
|---|---|
| **ANLAGEBETREIBER** (Admin) | Alles: SmartBoxen, Gerät-Templates, Geräte, Plätze, Schützen, Programme, Systemkonfiguration. Einziger mit Verwaltungszugriff auf das Netzwerk-Dashboard. |
| **WETTKAMPFLEITER** | Passen einer Rotte **final bestätigen**, Ergebnisse nachträglich **anpassen**, Wettkampffortschritt überwachen. Kein Zugriff auf Systemkonfiguration. |
| **STANDWART** | Zugeordnete Plätze verwalten: sperren/entsperren, manuell auslösen, Sessions starten/begleiten. |
| **SCHUETZE** | Eigenes Profil, Trainingshistorie, Session starten, Ergebnisse erfassen. Persönlicher QR-Code für Tablet-Login. |
| **GUEST** | Kein Profil. Kann als Teil einer Rotte teilnehmen. Ergebnisse der Session zugeordnet, nicht persönlich historisiert. |
| **BOX** (intern) | Technische Rolle für SmartBoxen (MQTT-Authentifizierung). Kein UI-Zugriff. |

---

## 4. Nebenläufigkeits-Regeln (Concurrency)

Mehrere Trainings/Wettkämpfe können **gleichzeitig** laufen. Trennlinien:

- **Pro Schütze**: Max. **ein aktives Training** gleichzeitig. QR-Scan während aktivem Training → Backend verweigert mit Fehlermeldung.
- **Pro Rotte**: Max. **eine aktive Passe** gleichzeitig. Nächster Platz-Schritt erst nach Abschluss des vorherigen.
- **Pro Platz**: Max. **eine aktive Rotte** zur selben Zeit. Zweite Rotte muss warten.
- **Staggered Start** ist explizit erlaubt: Mehrere Rotten starten parallel auf **unterschiedlichen** Plätzen und rotieren durch die Segmente. Platz-Reservierung wird implizit durch die aktive RangeRound gehalten.
- **Freie Nutzung + Training** auf demselben Platz schliessen sich aus: Bei aktiver RangeRound sind `FREE_FIRE`-Requests für diesen Platz gesperrt.
