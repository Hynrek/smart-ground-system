# Design: SmartBox-Kopplung und -Konfiguration — Erst-Kontakt ohne Direktzugriff

**Datum:** 2026-07-11
**Bezug:** Konkretisiert [ADR-003](../../adr/ADR-003-pairing-schluesselverwaltung.md) Action Item 5 (Provisionierungs-Fallback / Erst-Kontakt) für die No-Cloud-Welt aus [Hub/Node-Architektur](./2026-07-10-hub-node-architecture-design.md); **erweitert ADR-002, ADR-003 und ADR-004** (siehe Abschnitt „Ersetzt/ändert bestehende Entscheide")
**Status:** Entwurf, zur Review
**Entscheider:** Jonas Studer

## Ziel

Eine fabrikneue SmartBox soll an einem beliebigen Schiessplatz in Betrieb genommen werden, **ohne dass jemand die Box direkt ansprechen muss** (kein SoftAP der Box, kein Kabel, kein vorab eingebranntes Standort-Wissen). Der Bediener stellt die Box hin, schaltet sie ein, sieht sie in der bestehenden Vue-App auftauchen und koppelt sie per Klick. Danach hat die Box ihren Langzeit-Schlüssel `K_Box` und operiert über ESP-NOW.

Dieses Dokument legt den Kopplungsablauf, den Vertrauensanker, das Zertifikatsmodell und die Zustellung der Betriebskonfiguration (inkl. Routing/Relais) fest.

## Die Lücke, die dieses Design schliesst

Das heutige Bild kennt den Erst-Kontakt nur unvollständig:

- ESP-NOW-Pairing (`DISCOVER/OFFER/CONFIRM`, ADR-003) setzt voraus, dass die Box `K_Box` bereits besitzt — jeder dieser Frames trägt MIC bzw. Ciphertext unter `K_Box`. Eine fabrikneue Box **kann** diese Frames gar nicht senden.
- Die Hub/Node-Architektur beschreibt `box-api`-Provisionierung über den Node-AP (HTTPS, Erst-Kontakt für `K_Box`), aber **nicht, wie eine fabrikneue Box den AP das erste Mal findet und betritt.**
- ADR-003 AI 5 sagt nur „Flash leer → WLAN-Provisionierungsmodus" — ohne Mechanismus.

Genau diesen fehlenden ersten Schritt liefert dieses Design. Der Gewinn: **generische Firmware ohne eingebrannte Standort-Konfiguration** — dasselbe Flash-Image funktioniert an jedem Node jeder Anlage.

## Vertrauensanker: Bediener-Nähe

Eine fabrikneue Box hat keinen Schlüssel, also ist ihre erste Meldung über die Luft **unauthentifiziert** — jeder ESP32 in Reichweite könnte „Ich bin neu" rufen. Der Vertrauensanker ist deshalb **nicht kryptografisch, sondern physisch**: der Bediener steht am Node, schaltet die Box selbst ein, und **der Klick auf „Koppeln" IST die Vertrauensentscheidung.** Ein unerwartetes Gerät in der Liste wird ignoriert.

**Geräte-Identität am Gerät:** Jede Box trägt ihre MAC (oder einen QR-Code) auf einem **aufgedruckten Etikett**. Die Meldung in der UI zeigt dieselbe MAC; der Bediener gleicht Etikett und Listeneintrag visuell ab. Kein Display, kein Knopf, keine LED-Rückmeldung nötig.

Dieser Anker ersetzt für die No-Cloud-Welt den Backend-verankerten Anker aus ADR-003 (dort erzeugte das Cloud-Backend `K_Box` für eine bestimmte Box). Hier erzeugt der Node `K_Box` lokal (siehe unten), und die physische Kontrolle über den Kopplungsmoment autorisiert das.

## Ablauf der Kopplung

```
  Box (UNPROVISIONED)        Node                         Bediener / Vue-App
        │                     │                                  │
        │  HELLO (Broadcast,  │                                  │
        │  Klartext: MAC,     │                                  │
        │  FW-Version, Nonce) │                                  │
        ├────────────────────▶│  pending-Eintrag (MAC, RSSI,     │
        │  (Backoff-Wiederh.) │  first-seen)                     │
        │                     ├─────────────────────────────────▶│ „Neue Geräte":
        │                     │                                  │  MAC-Etikett abgleichen
        │                     │◀─────────────────────────────────┤ Klick „Koppeln"
        │  ONBOARD_OFFER      │  Token erzeugen (einmalig, TTL)  │
        │  (Unicast, Klartext:│                                  │
        │  AP-SSID+PSK,       │                                  │
        │  box-api-Adresse,   │                                  │
        │  Zert-Fingerprint,  │                                  │
        │  Token, echo Nonce) │                                  │
        │◀────────────────────┤                                  │
        │                     │                                  │
   join AP (STA), TLS gegen box-api (Fingerprint gepinnt)        │
        │  POST /provision    │                                  │
        │  {mac, token, nonce}│                                  │
        ├────────────────────▶│  Token prüfen (unbenutzt,        │
        │                     │  nicht abgelaufen, MAC passt)    │
        │                     │  K_Box mint (32 B zufällig)      │
        │                     │  (uuid,mac,K_Box) → SQLite       │
        │                     │  Geräte-Registrierung → Outbox   │
        │  200 {uuid, K_Box,  │                                  │
        │  Initial-Config}    │                                  │
        │◀────────────────────┤                                  │
   K_Box+uuid in Flash (NVS), AP verlassen                       │
        │                     │                                  │
        │  DISCOVER/OFFER/    │  (normales Pairing ab hier,      │
        │  CONFIRM  → K_S     │   ADR-003) → OPERATIONAL          │
        ├────────────────────▶│                                  │
```

Der `box-api`-/AP-Pfad ist damit auf genau **zwei** schwergewichtige Vorgänge beschränkt — **Kopplung (`K_Box`)** und **Firmware-OTA** —, beide geheimnistragend oder gross. Kleine, häufige Konfiguration läuft über den Betriebskanal (ESP-NOW), nie über den AP.

## Neue ESP-NOW-Frames (Onboarding, unauthentifiziert)

Getrennt von den paired-box-Frames (`DISCOVER/OFFER/CONFIRM`, die MIC/Ciphertext unter `K_Box` tragen):

| Frame | Richtung | Adressierung | Inhalt (Klartext) |
|---|---|---|---|
| **`HELLO`** | Box → Node | Broadcast | MAC, FW-Version, Box-Nonce. Kein MIC. |
| **`ONBOARD_OFFER`** | Node → Box | Unicast | AP-SSID+PSK, `box-api`-Adresse, **Zert-Fingerprint**, **einmaliges Provisioning-Token** (TTL), echo der Box-Nonce. |

Die Box-Nonce-Echo ist ein billiger Replay-Schutz: die Box akzeptiert nur ein `ONBOARD_OFFER`, das ihre eigene, gerade gesendete Nonce spiegelt. Für den Unicast registriert der Node die Box-MAC als unverschlüsselten Peer.

## `box-api`: Provisioning-Endpoint

`POST /provision { mac, token, box_nonce }` über TLS auf dem Node-AP:

1. Token validieren: **unbenutzt, nicht abgelaufen, gebunden an die gekoppelte MAC**. Sonst typisierte Ablehnung (kein 500).
2. `K_Box` **lokal** erzeugen (32 Byte aus CSPRNG).
3. `(uuid, mac, K_Box)` in die Node-SQLite schreiben.
4. Geräte-Registrierung in die **Outbox** schreiben (steigt zum Hub, wenn erreichbar).
5. Antwort `{ uuid, K_Box, initial_config }`.

Die Box persistiert `K_Box`+`uuid` im Flash (NVS), verlässt den AP und startet reguläres ESP-NOW-Pairing.

### `K_Box` lokal erzeugen, später syncen

Kopplung funktioniert **auch bei totem Hub**. Der Node mintet `K_Box` selbst — dadurch entfällt am Kopplungsplatz die Reihenfolge-Bedingung aus ADR-003 („Node muss den Key vor dem Pairing gesynct haben") vollständig: der Node **hat** den Key, weil er ihn erzeugt hat. Die Geräte-Registrierung reist über die Outbox zum Hub, der — wie schon bei Node-vergebenen Serie-UUIDs — die vom Node vergebene Box-UUID idempotent akzeptiert.

Das macht Provisionierung zu einer **bewussten Ausnahme von der Schreibklassen-Regel** der Hub/Node-Architektur (Anlagenkonfiguration wird bei totem Hub sonst abgelehnt). Bewusst akzeptiert: eine frisch gekoppelte Box ist an ihrem Platz sofort nutzbar; ihre feinere Einordnung in die Anlagenkonfiguration (Range-Zuordnung, Capabilities, RBAC) folgt, sobald der Hub wieder da ist.

## Box-Zustandsautomat

```
UNPROVISIONED ──HELLO-Broadcast (Backoff)──▶ ONBOARD_OFFER empfangen
     ▲                                              │ AP betreten (STA)
     │ Provision fehlgeschlagen / Timeout           │ POST /provision
     │   ── bedingungsloser Rückweg zu ESP-NOW ◀────┤ K_Box speichern
     │                                              │
     │                                        OPERATIONAL ◀── Erfolg
     │  (Flash gelöscht → UNPROVISIONED)            │ DISCOVER/OFFER/CONFIRM → K_S
     │                                              │ empfängt CONFIG (Routing)
     │                        MAINTENANCE (nur OTA, bestehend, bedingungsloser Rückweg)
```

Dieselbe Disziplin des **bedingungslosen Rückwegs** zu ESP-NOW, die die Hub/Node-Architektur für den Wartungsausflug vorschreibt (Assoziation fehlgeschlagen → Timeout → zurück auf den Betriebskanal, Fehler dort melden), gilt hier für den Provisioning-Ausflug.

## Zertifikat / TLS: nginx + Fingerprint über ESP-NOW

Die Box hat kein Display und kann kein „Zertifikat akzeptieren?" beantworten. Statt dafür eine PKI zu betreiben, wird das Vertrauen **über den ohnehin schon vertrauten ESP-NOW-Kanal gebootstrappt**:

- **TLS-Terminierung an nginx** auf dem Node. nginx bedient `node-api`, `box-api`, das Vue-Bundle und die OTA-Artefakte unter einer Origin (`https://node-N.smartground.lan`). Zert-Rotation ist ein nginx-Reload, keine JVM-Keystore-Übung.
- **Jeder Node trägt ein schlichtes selbstsigniertes Zertifikat** — keine anlagen- oder herstellerweite CA.
- Der **Fingerprint dieses Zertifikats** reist im `ONBOARD_OFFER`. Die Box pinnt genau diesen Fingerprint für die Provisioning-TLS-Sitzung. Bei totem Hub oder Rotation gibt der Node einfach einen anderen Fingerprint aus.
- Für spätere OTA-Ausflüge einer **bereits gepairten** Box reist der Fingerprint in einem **verschlüsselten** Frame unter `K_S` — strikt stärker als beim Onboarding.

**Bewusst ausgesprochen:** Zert-Vertrauen ist über ESP-NOW verankert, nicht über eine PKI. Beim Onboarding ist das der Klartext-Kanal — dasselbe bediener-überwachte Nähe-Fenster, das wir für AP-Credentials und Token ohnehin akzeptieren (siehe „Sicherheit").

## Betriebskonfiguration: Routing/Relais

**Eigentümer ist der Node, nicht der Hub.** Die Hub/Node-Architektur ist explizit: „Die ESP-NOW-Pakete bleiben im Node; der Hub weiss nicht, was ein Frame ist." Relais-Topologie ist ESP-NOW-Routing → **Node-Konfiguration, keine Anlagenkonfiguration.** Sie lebt in der Node-SQLite (`Box→Radio`-Tabelle aus ADR-002 AI 4 plus Forwarding-Tabelle). Ein optionaler read-only-Telemetrie-Abzug zum Hub (Clubhaus-Übersicht) ist später möglich; die Autorenschaft bleibt lokal.

**Nichts davon steht im Box-Flash.** Der einzige persistierte Wert ist `K_Box`; alles andere wird bei jedem Boot neu zugestellt — dieselbe Disziplin, die ADR-003 auf den Session-Key anwendet (frischer `K_S` pro Pairing, keine Nonce-Persistenz). Aufgeteilt nach Box-Rolle:

- **Leaf-Box (Regelfall):** Ihre Route — „Node direkt auf Kanal 6" oder „über Relais R" — reist **im Pairing-`OFFER`**, der laut ADR-003 ohnehin „zugewiesenes Radio + Kanal" trägt. Bei jedem Boot neu abgeleitet; auf der Box gibt es nichts zu „aktualisieren".
- **Relais-Box:** Braucht eine Forwarding-Tabelle (für welche Box-IDs sie weiterleitet). Diese reist direkt nach dem Pairing als **verschlüsselter `CONFIG`-Frame unter `K_S`**, mit `CONFIG_ACK` bestätigt, im RAM gehalten, nach Reboot neu gepusht. `CONFIG`/`CONFIG_ACK` sind bereits in der Phase-0-Frame-Liste — kein neuer Frame-Typ.

**„Wie aktualisiere ich die Config?"** = im Node-UI die Topologie ändern → der Node pusht einen `CONFIG`-Frame an die betroffene(n) Box(en) über die **verschlüsselte ESP-NOW-Sitzung**, sofort wirksam; zusätzlich beim nächsten Pairing automatisch neu angewandt. Kein AP, kein Hub, kein Flash-Write.

### Doppelrolle: Box als Leaf und Relais gleichzeitig

**Erlaubt und von ADR-002 vorgesehen.** Die beiden Rollen liegen auf verschiedenen Frame-Ebenen und stören sich nicht:

- **Relais-Rolle** arbeitet auf dem **Klartext-Routing-Header** (Ziel, Quelle, Frame-ID, TTL): Frame mit `Ziel ≠ ich` und passendem Forwarding-Eintrag → mit `TTL-1` weitersenden. Nie entschlüsseln; der Payload bleibt opakes AES-GCM (deshalb braucht ein Relais keine Schlüssel).
- **Leaf-Rolle** arbeitet auf dem **verschlüsselten Payload** von Frames mit `Ziel = ich`: unter eigenem `K_S` entschlüsseln, GPIO auslösen, eigener Heartbeat/Status.

ADR-002s eigenes Beispiel — „Box X relayt für die Wurfmaschine im Graben" — ist genau eine funktionale Box, die zusätzlich weiterleitet. Im Konfigurationsmodell markiert der Node die Box mit **beiden** Rollen: Geräte-Config (GPIO/Capabilities) als Leaf **und** Forwarding-Tabelle via `CONFIG`.

Operative Randbedingungen (nicht Protokoll, sondern Betrieb):

1. **Netzversorgung zwingend.** Ein Relais ist SPOF für seinen Ast (ADR-002) → „netzversorgt betreiben". Eine Batterie-Box darf nicht relayen.
2. **Airtime-Konkurrenz.** Ein Radio, ein Kanal: während die Box einen Downstream-Burst weiterleitet, konkurriert dieser Verkehr mit ihren eigenen Sendungen. **Eine zeitkritische Box ist ein schlechtes Relais** — Weiterleitungs-Jitter frisst die eigene Trigger-Latenz. Zeitkritische Boxen gehören nahe an den Node, nicht auf Reichweiten-Dienst.
3. **Grösserer Blast Radius.** Fällt eine Doppelrollen-Box aus, verliert man ihre Funktion **und** alles dahinter. Die „Relais down vs. Box down"-Erkennung (alle Boxen hinter ihr verschwinden gleichzeitig aus dem Heartbeat) greift weiter, aber der Ausfall korreliert nun zwei Belange.
4. **Störenderer Reboot.** Peer-Tabellen liegen im RAM → nach Reboot pairt die Box neu **und** braucht ihre Forwarding-Tabelle neu gepusht; in diesem Fenster ist alles Downstream getrennt.

**Empfehlung:** erlaubt und normal für eine netzversorgte, nicht-zeitkritische Box an einem abdeckungsgünstigen Platz (Bunkereingang, Grabenkante); zu vermeiden für Boxen, deren eigenes Timing am meisten zählt.

**v1 nutzt einzelne statische Routen** (reines ADR-002 v1: „explizit konfigurierte Relais, kein selbstorganisierendes Mesh", deterministisch und debugbar). Blast-Radius-Minderung durch **alternative Routen** ist ausdrücklich als offener Punkt für eine spätere ESP-NOW-Resilienz-Spec vermerkt (siehe „Offene Punkte").

## UI: integriert in `smart-ground-ui`

Kein eigenes Controller-UI — eine **Admin-View in der bestehenden Vue-App** (`meta.layout: 'admin'`, unter `MainLayout`), neben dem bestehenden `/smartboxes`-Bereich (entweder ein „Neue Geräte"-Panel in `SmartBoxesView` oder eine Schwester-Route `/smartboxes/koppeln` mit Nav-Link in `Sidebar.vue`). Sie folgt der App-eigenen Kette:

- **Service:** `services/smartBoxApi.js` erweitern (oder neues `services/onboardingApi.js`) — die Endpoints leben in `node-api` (die Spec, gegen die die Vue-App kompiliert), heute die Backend-`openapi.yaml`.
- **Store:** `onboardingStore.js`, **pollt `GET /onboarding/pending`** nach dem `otaStore`-Muster (Intervall-Fetch, `stopAllPolling()` beim Unmount). Kein STOMP (nicht verdrahtet; der UI-Guide verbietet es ausdrücklich hinzuzufügen).
- **View:** listet pending Boxen (MAC, RSSI, first-seen); Bediener gleicht das aufgedruckte MAC-Etikett ab und klickt **Koppeln** → `POST /onboarding/{mac}/couple`; die Zeile spiegelt danach `offered → joined AP → provisioned → paired`, Terminalfehler als typisierte `ProblemDetail`-Zustände.
- **Konventionen:** deutsche Labels („Neue Geräte", „Koppeln"), englische Identifier/Kommentare, `--sg-*`-Tokens mit `.sg-card-surface--calm` für den dichten Admin-Screen, Loading/Error/Empty-States, Touch-Targets für Tablet-Bedienung.

**Berechtigungsgate:** Kopplung registriert ein Gerät (Anlagenkonfiguration) und braucht daher eine Admin-Permission. Empfehlung: dasselbe Gate wiederverwenden, das den heutigen SmartBox-Admin-Bereich schützt, statt eine neue Permission zu erfinden (in Review bestätigen).

## Deployment: docker compose

Bei N=1 (Hub + Node auf einem Pi) die natürliche Compose-Topologie:

| Service | Rolle | Netz |
|---|---|---|
| `hub` | Spring, Konfigurationswahrheit, SQLite-Volume | interne Bridge |
| `node` | Spring, `node-api`, ESP-NOW-Logik, SQLite-Volume | Bridge (zum Hub) + Radio-Device |
| `nginx` | TLS-Terminierung, Vue-Bundle, OTA-Artefakte, Reverse-Proxy | `network_mode: host` |
| `dnsmasq` | DHCP/DNS auf dem Client-/Box-Segment | `network_mode: host` |

**Constraint 1 — Serielles Passthrough fürs Radio.** Der ESP-NOW-Listener (fängt die `HELLO`-Broadcasts) läuft im `node`-Container und braucht das USB-Radio: `devices: ["/dev/…:/dev/…"]`. Weil Multi-Radio designed-in ist (ADR-002 — mehrere Funkmodule pro Node, jedes auf eigenem UART und Kanal 1/6/11), einen **udev-stabilen Symlink** (z. B. `/dev/smartground-radio0`) vorsehen, damit die Gerätebenennung deterministisch ist statt boot-reihenfolge-abhängig.

**Constraint 2 — Der box-/tablet-zugewandte Rand muss auf der Host-NIC landen.** Der Dumb-AP bridget L2 zur Node-LAN; `node-N.smartground.lan` löst (via dnsmasq) auf die **Host**-IP auf, und die Box verbindet sich dorthin über TLS. Deshalb `nginx` + `dnsmasq` mit **`network_mode: host`** (oder 443 auf die LAN-NIC publishen, dnsmasq auf dem Host). Hub↔Node-Verkehr bleibt auf der Bridge.

**Zert/Fingerprint-Koordination fällt natürlich heraus:** Das selbstsignierte Node-Zertifikat liegt auf einem **geteilten Volume** — `nginx` bedient es, der `node`-Service liest es, um den über ESP-NOW angekündigten Fingerprint zu berechnen. Rotation = Datei tauschen, `nginx -s reload`, node liest Fingerprint neu.

## Sicherheit / akzeptierte Restrisiken

- **`ONBOARD_OFFER` ist Klartext:** AP-Credentials, Token und Zert-Fingerprint sind in ESP-NOW-Reichweite mitlesbar — **nur während des bediener-überwachten Kopplungsfensters**. `K_Box` berührt die Luft nie. Das Token ist einmalig, kurz-TTL, an die gekoppelte MAC gebunden; `box-api` weist nicht passende/benutzte/abgelaufene Token ab. Ein Angreifer müsste physisch anwesend sein und innerhalb des Fensters zum AP rennen und die MAC spoofen. **Für v1 bewusst akzeptiert.**
- **Optionale Härtung (nicht v1):** eine dedizierte, bedarfsweise aktivierte Onboarding-SSID, die der Node nur während eines offenen Kopplungsfensters hochfährt.
- **Gestohlene Box:** `K_Box` liegt im Node-Store; der Node kann rotieren → alte Box wird nach Sync abgewiesen (ADR-003-Revocation greift unverändert).

## Fehlerbehandlung

Erwartete Zustände sind typisiert, kein 500 — konsistent mit der RFC-9457-Konvention der Hub/Node-Architektur. Konkret:

- `GET /onboarding/pending`: leere Liste ist kein Fehler.
- `POST /onboarding/{mac}/couple`: Box nicht mehr in Reichweite (kein `HELLO` mehr) → typisierte Ablehnung, UI zeigt „Gerät nicht mehr erreichbar".
- `POST /provision`: Token ungültig/abgelaufen/benutzt → typisierte Ablehnung; die UI-Zeile geht in einen sichtbaren Terminalfehler, nie in Endlos-Retry.
- Provisioning-Ausflug scheitert box-seitig (AP nicht assoziierbar) → bedingungsloser Rückweg zu ESP-NOW; die Box sendet weiter `HELLO`, der Eintrag bleibt koppelbar.

## Ersetzt/ändert bestehende Entscheide

Die Hub/Node-Architektur ist die übergeordnete Autorität; dieses Dokument konkretisiert ihren Provisionierungsteil. Die ADRs sind in einem separaten Arbeitspaket nachzuziehen.

| Dokument | Änderung |
|---|---|
| **ADR-002** | Ergänzt: `HELLO`/`ONBOARD_OFFER` als Onboarding-Frames zusätzlich zum Pairing-Broadcast. Doppelrolle Leaf+Relais explizit erlaubt mit Betriebs-Randbedingungen. Frame-Format, `SeenCache`, TTL, statische Relais unverändert. Alternative Routen bleiben (wie „dynamisches Mesh") aufgeschoben. |
| **ADR-003** | Vertrauensanker für die No-Cloud-Welt: **Node-erzeugter `K_Box` + Bediener-Nähe** statt Backend-erzeugter Key. AI 5 (Provisionierungs-Fallback) mit konkretem Mechanismus gefüllt. Reihenfolge-Bedingung entfällt am Kopplungsplatz (Node hat den Key, weil er ihn mintet). Handshake/HKDF/Revocation unverändert. |
| **ADR-004** | Für den `box-api`-Pfad: **kein DNS-01, keine öffentliche PKI, keine anlagenweite CA.** Schlichtes selbstsigniertes Node-Zertifikat, dessen Fingerprint über ESP-NOW gepinnt wird. Der Tablet-Pfad (einmal akzeptiertes Zert am festen Tablet) bleibt unberührt. |

## Offene Punkte (bewusst ausserhalb dieses Specs)

1. **Alternative/Backup-Routen** zur Blast-Radius-Minderung (Node-authored, geordnete Kandidatenliste + box-seitiges deterministisches Failover, wiederverwendet Re-Pairing + Seen-Cache/TTL). Erweitert ADR-002s v1-Linie bewusst → eigene ESP-NOW-Resilienz-Spec.
2. **Zentrale Topologie-Telemetrie** zum Hub (read-only Clubhaus-Übersicht), Autorenschaft bleibt am Node.
3. **Dedizierte Onboarding-SSID** als Härtung des Klartext-Fensters.
4. **Berechtigungsgate** der Kopplung — Wiederverwendung des SmartBox-Admin-Gates bestätigen.
5. **Physische Sicherheit** von Node und Box (Schlüsselmaterial im Feld) — gilt tier-übergreifend, eigener Entscheid (aus der Hub/Node-Architektur übernommen).
