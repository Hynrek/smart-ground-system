# Design: Node onboarding service + node-api (Plan 2)

**Datum:** 2026-07-12
**Bezug:** Konkretisiert den Node-/`node-api`-Teil von [SmartBox-Kopplung](./2026-07-11-smartbox-coupling-design.md); baut auf dem Frame-Kontrakt aus [Plan 1](../plans/2026-07-11-smartbox-onboarding-frame-contract.md) (`OnboardingCodec`, `HELLO`/`ONBOARD_OFFER`, bereits auf `main`) auf. Vorstufe zu `node-api` (Sub-Projekt #5) — bewusst nur die Onboarding-Scheibe, **nicht** die volle Node-Fassade.
**Status:** Entwurf, zur Review
**Entscheider:** Jonas Studer

## Ziel

Die node-seitige Hälfte des Kopplungsablaufs implementieren: eine fabrikneue Box, die per `HELLO` auftaucht, erscheint in einer pending-Liste; der Bediener koppelt sie per Klick; der Node emittiert ein `ONBOARD_OFFER` (via Sende-Seam), vergibt ein einmaliges Provisioning-Token, und die anschliessende (token-gesicherte) `box-api`-Discovery mintet `K_Box` und schreibt die Geräte-Registrierung in eine Outbox.

Der Client↔Node-Pfad (`GET /onboarding/pending`, `POST /onboarding/{mac}/couple`) wird als **eigenständige `node-api`-Fläche** im `smart-ground-node`-Prozess aufgesetzt — nicht als Interims-Erweiterung der Hub-`openapi.yaml`.

## Aufgelöste offene Entscheidung: `node-api` jetzt, nicht Hub-Interim

**Entscheidung:** Die Kopplungs-Endpoints leben physisch im `smart-ground-node`-Prozess als handgeschriebene `@RestController` (box-api-Muster), unter einer eigenen `node-api`-Basis. Die Hub-`openapi.yaml` wird **nicht** erweitert.

**Begründung (aus der Codebase verifiziert):**

1. **Kein Compile-Zeit-Zwang von der UI.** Jede `services/*Api.js` ist ein handgeschriebener `fetch` gegen `apiClient` mit `BASE_URL = '/api'`; es gibt keinen generierten TS-Client. „Die Vue-App kompiliert gegen die Hub-`openapi.yaml`" ist eine *Disziplin-Konvention* (openapi.yaml = Kontrakt-of-record), keine Build-Abhängigkeit. Die UI an node-gehostete Endpoints anzubinden kostet **einen Routing-Eintrag**, keine Codegen-Migration.
2. **Handgeschriebenes HTTP-Präzedens im Node.** `box-api` sind drei schlichte `@RestController` — **kein** OpenAPI-Generator im Node-`pom.xml`, Tests via `MockMvcBuilders.standaloneSetup(...)` (Offline-Zwang). `node-api` folgt exakt diesem Muster; kein Generator nötig.
3. **Onboarding ist eindeutig Node-Eigentum** und muss bei **totem Hub** funktionieren (Coupling-Spec: „Kopplung funktioniert auch bei totem Hub", „Eigentümer ist der Node"). Ein Hub-Interim wäre entweder ein lügender Kontrakt (Implementierung im Node, Deklaration im Hub) oder ein Hub→Node-Proxy, der genau die Tier-Kopplung wieder einführt, die der Split entfernt — und beim Eintreffen von `node-api` weggeworfen wird.

**Verworfene Alternative:** Hub-`openapi.yaml` erweitern + im Hub implementieren. Einziger echter Vorteil (Hub hat Auth + UI erreicht schon `/api`) ist schwach: Routing ist billig (Punkt 1) und das Bediener-Login geht ohnehin bereits zum Hub.

## Auth: signaturbasiert jetzt, feingranular später

**Entscheidung:** `node-api` validiert eingehende Bediener-Requests über einen **minimalen JWT-Filter, der nur Signatur + Ablauf** gegen ein geteiltes `jwt.secret` prüft. Authentifiziert ⇒ erlaubt. Das feingranulare Admin-Permission-Gate bleibt der **offene Punkt der Coupling-Spec** (Berechtigungsgate, „in Review bestätigen").

**Warum das reicht:** Das Hub-JWT trägt nur `subject` (E-Mail) + optionalen groben `role`-Claim (`JwtService.generateToken`); die effektive Permission-Menge wird hub-seitig aus der RBAC-DB via `/auth/me` aufgelöst — ein Node-Prozess kann sie offline nicht auflösen. Das Bediener-Login geht ohnehin zum Hub, also ist ein Auth-Check, der den Hub braucht, nicht schlechter als heute; der Kopplungsablauf selbst (nach dem Klick) läuft node-lokal weiter.

**Umsetzung:** **JDK-only HMAC-SHA256-Verifikation** (`javax.crypto.Mac` + `Base64`), konsistent mit der Node-Haltung „keine handgerollte Krypto — javax.crypto reicht". Vermeidet die `io.jsonwebtoken`-Abhängigkeit, die im Offline-`~/.m2` vermutlich fehlt.

## Bausteine

| Baustein | Ort | Aufgabe |
|---|---|---|
| `OnboardingController` | `node.onboarding` | `GET /node-api/v1/onboarding/pending`, `POST /node-api/v1/onboarding/{mac}/couple`; handgeschrieben, `standaloneSetup`-Tests, `ProblemDetail` für typisierte Ablehnungen |
| Node-JWT-Filter | `node.security` (neu) | Signatur+Ablauf gegen geteiltes `jwt.secret`, JDK-only HMAC-SHA256 |
| `PendingBoxRegistry` | `node.onboarding` | In-RAM-Map je MAC (`mac, rssi, firstSeen, lastSeen, boxNonce`); Ingest-Seam `onHello(mac, rssi, boxNonce)` |
| `ProvisioningToken` + Store | `node.onboarding` | Einmalig, TTL, an MAC gebunden, unbenutzt-Flag; node-seitig persistiert |
| `RadioSender` | `node.onboarding` | **Interface** `send(destMac, frame)`; Logging/In-Memory-Fake verdrahtet, echte Serial-Impl aufgeschoben |
| `NodeCertFingerprint` | `node.onboarding` | Liest Keystore, extrahiert Server-Zert, SHA-256 (JDK `KeyStore`+`MessageDigest`); speist `ONBOARD_OFFER` |
| box-api-Discovery-Erweiterung | `node.box` | `token`(+`boxNonce`) in `BoxDiscoveryRequest`; validieren unbenutzt/nicht-abgelaufen/MAC-gebunden → als benutzt markieren → `K_Box` via bestehenden `BoxProvisioningService` → Registrierung in Outbox |
| Outbox (minimal) | `node.onboarding` oder `node.hub` | Persistierte Registrierungs-Zeile (H2) + ein Best-Effort-`HubClient`-Push; bleibt bei Fehler in der Queue |

## couple()-Ablauf

1. MAC in `PendingBoxRegistry` nachschlagen — nicht (mehr) vorhanden ⇒ typisierte Ablehnung („Gerät nicht mehr erreichbar"), kein 500.
2. Einmaliges Token minten (TTL, an MAC gebunden), im Token-Store ablegen.
3. `NodeCertFingerprint` + konfigurierte AP-SSID/PSK + `box-api`-URL + echo-Nonce (aus dem pending-Eintrag) einsammeln.
4. `ONBOARD_OFFER` via `OnboardingCodec.buildOnboardOffer(...)` (Plan 1) bauen.
5. Über `RadioSender.send(mac, frame)` emittieren (Unicast an die Box-MAC).
6. `202`/Status zurück; die pending-Zeile spiegelt `offered`.

## box-api-Discovery = token-gesicherter Erst-Kontakt (bewusste Neudefinition)

Eine provisionierte Box holt `K_Box` **nie erneut** ab (sie persistiert ihn im Flash und wechselt auf den ESP-NOW-Pairing-Pfad). Daher **muss** Discovery **immer** ein gültiges Token verlangen — sonst könnte ein MAC-Spoofer den `K_Box` einer bestehenden Box token-los ziehen. Das alte Verhalten „idempotente Every-Boot-Discovery, die `K_Box` zurückgibt" wird herausdefiniert (pre-v1.0, erlaubt). Discovery ist ab jetzt der **Provisioning-Call des Erst-Kontakts**, token-gated.

## Scope-Grenzen (bewusst ausserhalb Plan 2)

- **Keine volle `node-api`-Fassade (#5):** kein Provenance-Envelope (`live`/`as_of`), keine Degradation/Refusal-Semantik, kein Offline-Login. Nur die Onboarding-Endpoints.
- **Outbox minimal (Bediener-Entscheid):** persistierte Seam + ein Best-Effort-Push; **kein** Retry/Drain-Worker, keine Reconciliation, keine Provenance — das gehört Sync-Fundament (#2).
- **HELLO-Ingest nur als Seam (Bediener-Entscheid):** `onHello(...)` wird von Tests (und später der Radio-Receive-Schleife) aufgerufen; **keine** Serial/UART-Receive-Anbindung in Plan 2 — konsistent damit, dass `RadioSender` nur Sende-Seam ist.
- **Echte Serial-Impl von `RadioSender`** aufgeschoben (UART-Codec aus Baustein D existiert; die Port-Anbindung ist ein späterer Plan).
- **Feingranulares Admin-Gate** bleibt offen (Coupling-Spec-Punkt 4).

## Fehlerbehandlung (RFC 9457, kein 500)

- `GET /onboarding/pending`: leere Liste ist kein Fehler.
- `POST /onboarding/{mac}/couple`: MAC nicht (mehr) pending → typisierte Ablehnung.
- `POST /box-api/v1/discovery`: Token ungültig/abgelaufen/benutzt/MAC-fremd → typisierte Ablehnung, nie 500, nie Endlos-Retry.

## Umgebungs-Constraints (aus dem Ledger)

- Maven **offline** (`mvn -o`) — kein Maven-Central-Weg.
- **Keine** `@AutoConfigureMockMvc`/Spring-Test-Slices — `MockMvcBuilders.standaloneSetup(...)` wie `OtaDownloadControllerTest`/`BoxDiscoveryControllerTest`.
- Root-Monorepo trackt `smart-ground-node/**`; alle Plan-2-Änderungen sind Root-Repo (`[node]`-Commits). `smart-box/` ist unberührt (Plan 2 ist rein node-seitig).
