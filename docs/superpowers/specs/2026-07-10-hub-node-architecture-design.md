# Design: Hub/Node-Architektur — lokale Autorität, eigenständige Schiessplätze

**Datum:** 2026-07-10
**Bezug:** Löst den offenen Punkt „Zugriff für den Schützen offline" aus [ADR-001](../../adr/ADR-001-smartnode-daten-sync.md) auf Ebene der Gesamtarchitektur; **widerspricht in Teilen ADR-001, ADR-002, ADR-003 und ADR-004** (siehe Abschnitt „Ersetzt/ändert bestehende Entscheide")
**Status:** Entwurf, zur Review
**Entscheider:** Jonas Studer

## Ziel

Ein GroundOwner muss seine Anlage sowohl vom Clubhaus als auch von einem beliebigen Schiessplatz aus verwalten können. Gleichzeitig soll ein Schiessplatz weiterarbeiten, wenn die Verbindung zur zentralen Instanz der Anlage abbricht — und die Anlage als Ganzes soll ohne Internet auskommen.

Dieses Dokument legt die Tier-Struktur, die Vertragsgrenzen, die Autoritätsverteilung und das Degradationsverhalten fest.

## Begriffe

| Begriff | Hardware | Anzahl | Rolle |
|---|---|---|---|
| **SmartGround Hub** | Raspberry Pi | 1 pro Anlage | Alleiniger Schreiber der anlagenweiten Konfigurationswahrheit; Verzeichnis und Dispatcher |
| **SmartGround Node** | Raspberry Pi | 1 pro Schiessplatz (+1 Clubhaus) | Cache, Fassade, Outbox; DHCP/DNS/Origin-Autorität (dnsmasq); ESP-NOW-Funkmodul; lokaler OTA-Server |
| **Client-AP** | dedizierte AP-Hardware | 1 pro Node (mit Client-Geräten) | Bridged Funkzelle für die Tablets; Dumb-AP (kein eigenes DHCP/DNS), am Node-LAN |
| **SmartBox** | ESP32 | n pro Node | GPIO-Aktorik/Sensorik |

Bei **einem** Schiessplatz laufen Hub und Node als zwei Prozesse auf derselben Hardware; der Node proxyt auf `localhost`. Das ist kein Sonderfall, sondern der allgemeine Fall bei N=1.

## Architektur

```
                 ┌──────────────────────────────────┐
                 │  SmartGround Hub  (Pi, 1/Anlage) │
                 │  Konfigurationswahrheit          │
                 │  Identität/RBAC · OTA-Artefakte  │
                 │  headless — kein Browser-UI      │
                 └──────────────┬───────────────────┘
                                │ hub-api (HTTPS, Node→Hub)
                                │ node-channel (Node-initiiert, Hub→Node)
          ┌─────────────────────┼─────────────────────┐
   ┌──────┴───────┐      ┌──────┴───────┐      ┌──────┴───────┐
   │ Node 1       │      │ Node 2       │ …    │ Node Clubhaus│
   │ SQLite Cache │      │              │      │ (0 Boxen)    │
   │ Fassade      │      │              │      │              │
   │ Outbox       │      │              │      │              │
   │ dnsmasq (DHCP/DNS) · UART→ESP32-Radio │      │              │
   └──┬────────┬──┘      └──────────────┘      └──────────────┘
      │        │ ESP-NOW (Betrieb)   ┌─ WLAN-STA (Wartung: OTA, Provisionierung)
      │     SmartBoxen ──────────────┘
      │ LAN-Bridge
   ┌──┴───────────┐
   │ Client-AP    │  Dumb-AP, bridged, externe Antennen
   └──┬───────────┘
      │ WLAN + node-api
   Tablet / Smartphone
```

### Kein Cloud-Server, kein MQTT

**Der Hub ist lokal.** Die Anlage benötigt kein Internet. Ein Cloud-Hub bleibt eine *unterstützte Deployment-Variante*, kein Zielbild — siehe unten.

**MQTT entfällt vollständig.** Ein Broker auf dem Node hätte genau einen Publisher und einen Subscriber, beide innerhalb derselben JVM. Der Broker existierte, um während der ESP-NOW-Migration das unveränderte Backend am Leben zu halten (Gerüst, kein Zielzustand). Was er leistete, übernehmen:

| MQTT-Leistung | Ersatz |
|---|---|
| Retained last-known-state | Zeile in der SQLite des Node (ohnehin nötig für Stale-Reads) |
| QoS-1-Deduplikation | ESP-NOW-Frame-IDs, `SeenCache`, `MAC_ACK` (ADR-002) |
| Config-Push, Commands | Direkter Radio-Send |
| Heartbeat | ESP-NOW-Frametyp |
| Discovery/Bootstrap (`K_Box`) | `box-api` über den WLAN-AP des Node (HTTPS, CA beim Flashen gepinnt) |

**Konsequenz:** Das Bootstrap-Credential-Problem aus dem Migrationsplan (geteilter dynsec-Account, der fremde Config-Pushes mitlesen kann) entfällt ersatzlos. Die Mosquitto-TLS-/dynsec-Arbeit (Tasks A–C) wird nicht weiterverwendet.

## Autorität

Die zentrale Unterscheidung dieses Designs:

**Der Hub besitzt die Konfigurationswahrheit. Der Node besitzt die operative Wahrheit seines eigenen Schiessplatzes.**

Der Hub ist für fremde Schiessplätze *Verzeichnis und Dispatcher*, nicht Eigentümer der Antwort. „Range 3 ist blockiert" gehört Node 3; der Hub spiegelt es nur.

### Vier Schreibklassen

| Klasse | Beispiele | Eigentümer | Offline (Hub weg) |
|---|---|---|---|
| **Anlagenkonfiguration** | User, Rollen, GPIO-Verdrahtung, Device↔Range-Zuordnung, Node-Registry, Serie veröffentlichen (`ownership='range'`, `published=true`) | Hub | **Abgelehnt** |
| **Benutzerdokumente** | Private Serie (`ownership='user'`), Passe | Besitzer | **Lokal geschrieben, Outbox** |
| **Operativer Zustand** | `blocked`, laufende `PlayInstance`, Box-Status | Node des jeweiligen Platzes | **Immer lokal** |
| **Resultate** | `PlayInstance`-Ergebnisse, `UserSerieScore` | entstehen am Node | **Append-only, Outbox** |

Ein Schütze kann an Schiessplatz 3 bei totem Hub eine eigene Serie erstellen und sofort schiessen. Sie platzweit sichtbar zu machen ist ein Admin-Akt und wartet auf den Hub. Niemand, der an einem Stand steht, wird von etwas blockiert, das er nicht tun kann.

## Verträge

Vier bindende Verträge, jeder zwischen genau zwei Parteien. Keine geteilte Spezifikation zwischen Tiers — ein Vertrag, der für beide Seiten „irgendwie" gilt, bindet nichts.

| Vertrag | Parteien | Inhalt |
|---|---|---|
| **`node-api`** | Client ↔ Node | Einzige Spec, gegen die die Vue-App kompiliert. Provenienz auf jedem Read, typisierte Ablehnungen auf jedem Command. |
| **`hub-api`** | Node → Hub | Cursor-Sync-Pull mit Tombstones, idempotenter Outbox-Push, Credential-Material, OTA-Artefakte, **Weiterleitung von Cross-Range-Commands**. Kein Mensch berührt ihn. |
| **`node-channel`** | Hub → Node | Versioniertes Nachrichtenschema über die Node-initiierte Dauerverbindung: Command-Dispatch, Cache-Invalidierung, Liveness. Kein REST. |
| **`box-api`** | Node → SmartBox | Provisionierungs-Handshake (`K_Box`), Firmware-Download. Nur auf dem AP erreichbar. |

Darunter unverändert: die ESP-NOW-Frame-Spec (Bausteine A–D), bereits in Java und MicroPython gegen gemeinsame Fixtures implementiert.

**Der Hub ist headless.** Keine Session, kein CORS, kein Zertifikat, dem ein fremdes Handy vertrauen muss. Deshalb bekommt auch das Clubhaus einen Node: die einzige clientseitige Ebene ist immer der Node.

**Modulgrenze:** Der Node hängt vom Hub ausschliesslich über `contracts` und einen `HubClient` ab. Keine geteilte Persistenz, kein Repository-Durchgriff. Zwei Spring-Boot-Artefakte (`smart-ground-hub`, `smart-ground-node`) über gemeinsamen Modulen `contracts` und `domain`. Die ESP-NOW-Pakete bleiben im Node; der Hub weiss nicht, was ein Frame ist.

## Netze und Kanäle

**Client-WLAN:** Die Funkzelle liefert **dedizierte, am Node-LAN gebridgete AP-Hardware** (Dumb-AP, eigenes DHCP/DNS abgeschaltet) — nicht das Node-Radio. Der Grund ist Reichweite und Abdeckung über den Schiessplatz samt externer/Outdoor-Antennen; damit ist die Abdeckung von der Compute-Hardware des Node entkoppelt. **Der Node bleibt aber die alleinige DHCP-/DNS-/Origin-Autorität:** er betreibt weiter `dnsmasq` (DHCP-Vergabe, autoritative Auflösung des festen Hostnamens auf seine eigene IP) und ist die einzige Origin, die seine Tablets je adressieren (`https://node-N.smartground.lan`). Selbstsigniertes Zertifikat, einmal beim Provisionieren des festen Tablets akzeptiert; danach voller Secure Context. Kein DNS-01, kein öffentliches PKI. Der AP macht ausschliesslich L2-Bridging; genau ein DHCP-/DNS-Server (der Node) darf im Segment leben.

**Backhaul:** IP zwischen Node und Hub. `hub-api` per HTTPS Node→Hub. Der Hub→Node-Kanal ist **Node-initiiert** (dauerhafte ausgehende Verbindung mit Reconnect/Backoff). Der Hub kennt die IP eines Node nie. Das ist die einzige Entscheidung, die die Cloud-Variante offenhält — hinter CGNAT gibt es keine eingehende Erreichbarkeit — und sie verhält sich im LAN identisch.

**Funksegment:** **ESP-NOW ist der Betriebskanal, WLAN der Wartungskanal.** Alles im laufenden Betrieb (Heartbeat, Status, Config, Commands) läuft über ESP-NOW, notfalls über Relais, und braucht keine WLAN-Reichweite. Jeder bewusste Wartungsakt (OTA, Provisionierung) läuft über WLAN und braucht deshalb temporäre Nähe zum AP des Node.

### Wartungsausflug der Box

Trigger über ESP-NOW → Box wechselt nach `MAINTENANCE`, verbindet sich als STA mit dem Node-AP, lädt über `box-api`, kehrt nach `OPERATIONAL` zurück.

1. **Unbedingter Rückweg.** Assoziation fehlgeschlagen → Timeout → zurück auf ESP-NOW, Fehler auf dem Betriebskanal melden. Sonst wird „ich trage die Box hinüber" zu „die Box ist aus Sicht des Node tot".
2. **Eine Box in Wartung ist taub, und der Node weiss das.** Der Node öffnet mit dem Trigger ein *erwartetes Abwesenheitsfenster* mit Deadline. Innerhalb des Fensters ist ausbleibender Heartbeat kein Offline-Alarm; nach der Deadline ist er ein Update-Fehler.
3. **Relais-Boxen können sich nie selbst updaten.** Der Node kennt die Relaistiefe und zeigt das an, bevor jemand vergeblich hinausläuft.

## Datenflüsse

### Cross-Range-Command (Range 3 blockieren, Tablet an Range 1)

```
Tablet ─node-api─▶ Node 1 ─hub-api─▶ Hub ─node-channel─▶ Node 3 ─ESP-NOW─▶ Box
```

Node 1 sieht ein fremdes Ziel → braucht den Hub. Kein Kanal → `HUB_UNREACHABLE`, **abgelehnt, nie gequeued**. Der Hub autorisiert (RBAC), prüft die Liveness von Node 3 (`NODE_UNREACHABLE`), dispatcht, wartet. Node 3 wendet an, erreicht seine Boxen (`BOX_UNREACHABLE`), acked.

**Ein Timeout sagt nicht, ob das Command lief.** Node 3 kann blockiert und das Ack verloren haben. Commands tragen deshalb eine UUID und sind idempotent; bei Timeout antwortet der Node `COMMAND_OUTCOME_UNKNOWN`, und die UI meldet nicht „fehlgeschlagen", sondern liest den Zustand von Range 3 neu.

Den eigenen Platz zu blockieren funktioniert immer, auch vollständig isoliert — operativer Zustand gehört dem Node.

### Resultat-Outbox

Serie/Passe/Play entstehen am Node mit UUID, werden in derselben Transaktion nach SQLite und in die Outbox geschrieben. Bei erreichbarem `hub-api` drainiert der Node; der Hub ist idempotent auf der UUID.

**Die Outbox ist geordnet, kein Beutel.** Ein offline erstellter Serie-Datensatz und die `PlayInstance`, deren `template_id` darauf zeigt, liegen beide darin. Wird das Resultat zuerst hochgeladen, nimmt der Hub eine Referenz auf eine Zeile, die er nie gesehen hat. Also: **FIFO pro Node, single-flight.** Kausalität ergibt sich damit gratis, weil das Dokument notwendigerweise vor seiner Referenz entstand.

Outbox-Tiefe ist eine überwachte Kennzahl: ein Node, der eine Woche offline war, und ein Node, dessen Upload still scheitert, sehen vom Clubhaus aus gleich aus.

### Offline-Login

Der Hub synct für die User *dieser Anlage* Credential-Material herunter: `username`, Passwort-Hash unter starker KDF, Rollen-/Permission-Zuweisungen, `updated_at`, `deleted`.

**Der Node authentifiziert immer lokal** — auch bei erreichbarem Hub. Ein Codepfad, ständig benutzt, kann nicht zum ungetesteten Zweig verrotten, den man während eines Ausfalls entdeckt. Der Node mintet ein auf sich selbst begrenztes Session-Token. Der Hub sieht nie ein Benutzerpasswort; er autorisiert Node→Hub-Aufrufe über das Service-Token des Node und vertraut dessen Behauptung, welcher User handelt.

Bewusst akzeptiert, laut auszusprechen:

- **Ein gestohlener Node kann jeden User seiner Anlage imitieren**, bis sein Service-Token widerrufen ist. Deshalb: Token pro Anlage, widerrufbar; keine Hub-Credentials auf einem Node; nur die für die Anlage relevanten User synchronisieren. Physische Sicherheit ist ein eigener, später zu führender Entscheid (gilt gleichermassen für die SmartBox).
- **Revocation hinkt nach.** Ein am Hub gelöschter User kann sich an einem noch nicht gesynchten Node anmelden, begrenzt durch das Sync-Intervall. Für einen Schiessverein sind Stunden akzeptabel.

Passwortänderungen sind Anlagenkonfiguration → offline abgelehnt.

### Benutzerdokumente offline

Vier Konsequenzen aus der Schreibklasse „Benutzerdokumente":

1. **`Serie` braucht `updated_at` und ein `deleted`-Flag.** Heute existiert nur `created_at`. (ADR-001 AI 1, erster konkreter Abnehmer.)
2. **Der Hub akzeptiert eine vom Node vergebene UUID.** Heute mintet Hibernate sie (`@GeneratedValue(strategy = GenerationType.UUID)`). Ein offline erstelltes Dokument hat seine Identität, bevor der Hub davon hört — und genau diese Identität macht den Upload idempotent.
3. **Der Read-Pfad des Node bedient seine eigenen ausstehenden Writes.** Sonst speichert der Schütze eine Serie und findet sie nicht in der Liste. Reads vereinigen gesyncte Zeilen mit der Outbox. Ohne Read-your-writes existiert das Feature nicht.
4. **Der Node-Uhr wird für Last-Write-Wins nicht getraut.** Ein wochenlang offline gewesener Pi hat kein NTP; ein verschobenes `updated_at` schlägt sonst dauerhaft eine tatsächlich neuere Hub-Änderung. Der Node schickt die `base_version`, aus der er editiert hat; der Hub stempelt die autoritative Zeit beim Empfang und erkennt den Konflikt selbst. **Bei echter Divergenz behält der Hub die überschriebene Version** — einem Schützen einen Abend Arbeit still wegzuräumen beendet das Vertrauen ins System.

## Fehlerbehandlung

**Erwartete Zustände sind typisiert, nicht exceptional.** Die Ablehnungen des Node folgen der bestehenden RFC-9457-Konvention: `/errors/hub-unreachable`, `/errors/node-unreachable`, `/errors/box-unreachable`, jeweils als `ProblemDetail` mit der Identität des Ziels im Body, damit die UI *„Range 3 ist von hier nicht erreichbar"* sagen kann statt „Fehler". Kein 500. Ein toter Backhaul ist kein Bug.

**Reads tragen ihre Provenienz im Envelope:** entweder `live` oder `as_of: <timestamp>` samt Alter. Es gibt keinen dritten Fall und keinen Default.

**Liveness hat pro Hop genau eine Quelle.** Der Hub weiss von einem Node durch dessen offenen, heartbeatenden Kanal; nach N ausgefallenen Beats ist der Node `STALE` und Commands dorthin werden abgelehnt statt in einen toten Socket dispatcht. Der Node weiss von einer Box durch ESP-NOW-Heartbeat — ausser im offenen Wartungsfenster.

**Die Outbox hat einen Terminalzustand.** Eine Hub-Ablehnung (Validierung, gelöschter Besitzer, geänderte Permission) überführt den Eintrag nach `FAILED` und macht ihn in der UI am betroffenen Dokument sichtbar. Nie endlos retryen, nie verschwinden.

**Vier Verbote:**

1. Ein Command queuen.
2. Einen Stale-Read ohne Kennzeichnung ausliefern.
3. Einen abgelehnten Upload unbegrenzt wiederholen.
4. Für einen vom Design vorgesehenen Zustand 500 zurückgeben.

## Testansatz

Die Verträge sind die Testfläche. `node-api`, `hub-api`, `node-channel` und `box-api` erhalten je Contract-Tests; die ESP-NOW-Frame-Spec besitzt bereits sprachübergreifende Fixtures (Java ↔ MicroPython), und dieses Muster setzt sich an den neuen Nahtstellen fort.

**Die Fassaden-Tabelle ist die Spezifikation des Degradationsverhaltens** und wird erschöpfend getestet, nicht stichprobenartig:

> jeder `node-api`-Endpoint × {Hub erreichbar, Hub weg} × {Ziel ist der eigene Platz, Ziel ist ein fremder Node} → genau eines aus {proxy, stale-read, refuse}

Fehlerinjektion:

- Backhaul mitten im Command kappen → `COMMAND_OUTCOME_UNKNOWN` + korrektes Nachlesen.
- Outbox mit Serie *und* darauf verweisender `PlayInstance` drainieren → der Hub sieht nie die hängende Referenz.
- Dasselbe Resultat zweimal hochladen → eine Zeile.
- Serie offline erstellen → erscheint sofort in der Liste des Node.
- Login bei ausgeschaltetem Hub.
- Node-Uhr eine Woche vorstellen → `base_version`-Prüfung löst den Konflikt korrekt, überschriebene Version bleibt erhalten.

Hardware: Eine Box, die ausserhalb der WLAN-Reichweite zum Update aufgefordert wird, muss die Assoziation aufgeben, das Timeout laufen lassen und über ESP-NOW zurückkommen.

**Abnahmetest** (ADR-001 AI 6, eine Ebene höher): 48 h mit ausgestecktem Hub durchgehend schiessen, danach reconnect und vollständiger Abgleich ohne Datenverlust.

## Deployment-Variante: Hub in der Cloud

Der Treiber ist **nicht die Grösse der Anlage, sondern die Spannweite des Backhauls.** Wo ein lokaler Link alle Schiessplätze mit dem Clubhaus verbindet, steht der Hub lokal. Wo keiner das schafft (Beispiel Dornsberg), brauchen die Nodes einen Rendezvouspunkt, der von allen erreichbar ist — und da ein eingehend erreichbarer Pi im Clubhaus das Schlechteste beider Welten wäre, liegt dieser Punkt in der Cloud. Die Nodes hängen dann per LTE daran.

Möglich wird das ausschliesslich durch den Node-initiierten `node-channel`. Alles Übrige ist Konfiguration.

Der Preis, bewusst akzeptiert:

- **Cross-Range-Control hängt dann am Internet.** Zwei Pis, die einander sehen, dürfen bei totem LTE nicht miteinander reden; die Ablehnungsregel greift korrekt, tut aber weh — ausgerechnet dort, wo der Weg zu Range 3 am weitesten ist. Falls es zu weh tut, ist Node↔Node-Direktrouting mit dem Hub als Verzeichnis die Antwort. Das ist ein eigenes Design, keine Deployment-Variante.
- **Die Angriffsfläche kehrt zurück**, die der lokale Hub gerade gelöscht hat.

## Ersetzt/ändert bestehende Entscheide

Dieses Dokument ist die Autorität. Die ADRs sind in einem separaten Arbeitspaket nachzuziehen.

| Dokument | Konflikt |
|---|---|
| **ADR-001** | „Der Hauptserver bleibt Source of Truth" — der Hauptserver war ein Cloud-Backend. Ersetzt durch: lokaler Hub; Autorität nach Schreibklasse getrennt (Konfiguration ↔ operativer Zustand). Die Sync-Mechanik (Cursor-Pull, Tombstones, idempotente Outbox) bleibt wörtlich gültig. Ergänzt: Outbox ist FIFO/kausal geordnet; „Resultate: keine Konflikte möglich" schweigt über Referenzen auf offline erstellte Dokumente. |
| **ADR-002** | Frametypen mappen 1:1 auf bestehende MQTT-Topics. Entfällt: kein MQTT. Frame-Format, `SeenCache`, `MAC_ACK`, Relais und Kanalplanung bleiben unverändert gültig. |
| **ADR-003** | Erst-Kontakt für `K_Box` läuft zwingend über WLAN/MQTT; Bootstrap-dynsec-Rolle mit akzeptiertem Restrisiko. Ersetzt durch: `box-api` über HTTPS auf dem Node-AP, CA beim Flashen gepinnt. Das Restrisiko und das Bootstrap-Arbeitspaket entfallen. |
| **ADR-004** | Echte Subdomain + Let's-Encrypt via DNS-01; Node als einzige Ebene ohne Hub-Tier. Ersetzt durch: selbstsigniertes Zertifikat, einmalig auf festen Tablets akzeptiert (Betriebsmodus „festes Tablet pro Schiessplatz"). Präzisiert: die Funkzelle liefert **externe, gebridgete AP-Hardware** (Dumb-AP), nicht das Node-Radio — `hostapd` verlässt den Node, `dnsmasq`/DHCP/DNS/Origin bleiben am Node (siehe „Client-WLAN"; ADR-004 Amendment 2026-07-10, dort bereits als Hardware-Option vorgesehen). Die Entscheide „dnsmasq statt mDNS", „Uplink getrennt vom Client-WLAN" bleiben gültig. |

## Offene Punkte (bewusst ausserhalb dieses Specs)

1. **Physische Sicherheit** von Node und SmartBox (Diebstahl, Schlüsselmaterial im Feld). Betrifft beide Tiers gleichermassen; eigener Entscheid.
2. **Backup-Strategie des Hub.** Der Pi im Clubhaus hält die einzige Kopie aller Resultate, Programme und Konten der Anlage. Ein nächtlicher Dump auf externen Datenträger genügt als v1; er muss existieren.
3. **Update-Strategie der Hub-/Node-Software.** Betriebliche Pflicht von SmartGround (Hotspot beim Besuch der Anlage), keine technische Automatik.
4. **Node↔Node-Direktrouting** — falls die Cloud-Variante die Cross-Range-Latenz/Verfügbarkeit unerträglich macht.
5. **Radio-Firmware (Phase 2a)** und `RadioInterface`/serielle I/O (Phase 2b) sind unberührt und weiterhin offen.
