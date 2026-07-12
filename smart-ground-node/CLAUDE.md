# smart-ground-node — SmartNode Development Guide

## Project Overview

`smart-ground-node` is the future ESP-NOW↔MQTT bridge (SmartNode) described in ADR-001.
It runs today as a separate Spring Boot process on the same machine as the backend
(`plan-espnow-migration.md`, "Vereinte Zwischenstufe") and will later move to its own
Pi-class device without a rewrite — it speaks only the final interfaces (MQTT, HTTPS,
UART) from day one, never the backend's database or in-process APIs directly.

## Stack & Versions

- **Java 25**, Spring Boot 4.0.5, Maven (system `mvn`, no wrapper committed)
- Standalone Maven module (like `smart-ground-hub`), not part of a reactor build

## Project Structure

```
smart-ground-node/
├── pom.xml
├── src/main/java/ch/jp/shooting/node/
│   ├── SmartGroundNodeApplication.java
│   ├── crypto/                         # AES-256-GCM + HKDF-SHA256 (ADR-002/ADR-003)
│   └── hub/                            # HubClient + Hub communication
└── src/test/java/ch/jp/shooting/node/
    ├── crypto/                         # Cross-verified against docs/espnow/crypto-test-vectors.json
    └── architecture/                   # ModuleBoundaryTest (dependency guard)
```

## Talking to the Hub

Node depends on the Hub **only** through the `contracts` Maven artifact (`ch.jp.smartground:contracts`,
built from the sibling `smart-ground-contracts` repo — run `mvn install` there after any contract
change) and `ch.jp.shooting.node.hub.HubClient`. Never add a dependency on `smart-ground-hub` itself —
`ModuleBoundaryTest` (`src/test/java/ch/jp/shooting/node/architecture/`, a plain JUnit test that
parses `pom.xml` directly — no ArchUnit, see that task's plan note for why) fails the build if
`pom.xml` ever declares a dependency on `ch.jp.shooting:smart-ground-hub`
(no shared persistence, no repository reach-through).

## Conventions

Same as `smart-ground-hub`: German comments for domain logic, English identifiers.
Contract-first for anything crossing a process boundary (MQTT payloads, HTTPS sync
endpoints, UART framing) — see `docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md`
for the wire formats this module implements.

## Crypto (`crypto/` package)

`Hkdf` and `AesGcm` are thin wrappers around `javax.crypto` (HMAC-SHA256 / AES/GCM/NoPadding,
both natively supported by the JDK — no hand-rolled crypto needed on this side, unlike
MicroPython's `ucryptolib` which lacks a native GCM mode). Both are tested against the
canonical fixture at `../docs/espnow/crypto-test-vectors.json`, which `smart-box`'s
MicroPython implementation is tested against too — this is what keeps the two sides from
silently diverging.

## Host Tests

```bash
mvn test
```

No hardware dependency yet (Baustein A is crypto primitives + module scaffold only).
Later tasks (frame codec, UART, pairing) will add hardware-adjacent seams (serial port
abstraction) analogous to `smart-box`'s `http_stream`/`esp32.Partition` test seams.

## box-api (sub-project #7, `erledigt`)

`smart-ground-node` now also serves a box-facing HTTPS API — `box-api` — that replaces the
Hub's deleted MQTT integration for discovery, provisioning, and OTA-artifact serving. This
is a *different* concern from the ESP-NOW↔MQTT bridge role described above: box-api is a
plain synchronous HTTPS API a MicroPython client calls, not a frame-level bridge. Both live
in this module because both run as the process closest to the physical boxes.

### Package layout

```
src/main/java/ch/jp/shooting/node/box/
├── BoxDiscoveryController.java   # POST /box-api/v1/discovery
├── BoxDiscoveryRequest.java / BoxDiscoveryResponse.java
├── BoxProvisioningService.java   # provision(): create-or-update BoxRecord, generate K_Box once per MAC
├── BoxStatusController.java      # POST /box-api/v1/boxes/{macAddress}/status
├── BoxStatusRequest.java
├── BoxOtaController.java         # GET /box-api/v1/ota/app/{version}/manifest.json, /files/{*path}, /box-api/v1/ota/firmware/{version}
├── BoxRecord.java                # JPA entity — embedded H2 box registry
├── BoxRecordRepository.java
└── KBoxGenerator.java            # 32 random bytes (SecureRandom) per box
```

All three controllers are hand-written (`@RestController` with plain `@PostMapping`/`@GetMapping`), not generated from `openapi.yaml` — same deliberate exception as the Hub's `OtaDownloadController`: the SmartBox is a MicroPython client, not a Java consumer of the `contracts` artifact.

### Discovery / provisioning (`BoxDiscoveryController`, `BoxProvisioningService`)

`POST /box-api/v1/discovery` — request: `{ macAddress, appVersion, firmwareVersion, boxType, capabilitiesJson }`; response: `{ kBoxBase64, wasNewlyProvisioned }`.

`BoxProvisioningService.provision(...)` is **idempotent per MAC address**: it looks up `BoxRecord` by `macAddress`; if none exists, it creates one and generates a fresh `K_Box` via `KBoxGenerator` (32 `SecureRandom` bytes); if one already exists, it reuses the existing `K_Box` and just refreshes `appVersion`/`firmwareVersion`/`boxType`/`capabilitiesJson`. A box can call discovery on every boot without ever getting a new key — the key is generated exactly once per MAC, for the lifetime of the H2 registry row.

### Status / heartbeat (`BoxStatusController`)

`POST /box-api/v1/boxes/{macAddress}/status` — request: `{ status }`. Looks up the `BoxRecord` by MAC; `404` with a `/errors/box-unknown` `ProblemDetail` if the box was never provisioned (discovery must run first). On success, updates `lastStatus` and `lastSeenAt`.

### OTA proxy (`BoxOtaController`)

Three GETs (manifest, per-file, firmware image) that proxy byte-for-byte from the Hub's existing `OtaDownloadController` via `HubClient` (plain HTTP — Node and Hub trust each other on the same machine). This means the box never needs to know the Hub's address; it only ever talks to the Node. **Trigger/notification is not wired up** — a box currently has no way to be told an update is available (that requires `node-channel`, #4); this proxy only serves the *read* path for whenever a download is initiated.

### Embedded H2 box registry (`BoxRecord`/`BoxRecordRepository`)

A **separate, lightweight registry from the Hub's `SmartBox`/`FirmwareConfig` domain model** — this is Node-local state, not a replacement for Hub state. Columns: `macAddress` (unique), `kBox` (32 raw bytes), `boxType`, `appVersion`, `firmwareVersion`, `capabilitiesJson`, `provisionedAt`, `lastSeenAt`, `lastStatus`. Backed by a file-based H2 database at `${user.home}/.smartground-node/box-registry` (`spring.datasource.url` in `application.properties`), `ddl-auto=update`, so it survives process restarts (unlike an in-memory H2 instance) without needing a full PostgreSQL setup on the Node.

### `K_Box` generation semantics (Task 5)

`KBoxGenerator.generate()` returns 32 cryptographically random bytes (`java.security.SecureRandom`), matching the AES-256 key size the ESP-NOW crypto layer (`crypto/AesGcm.java`, `smart-box`'s `espnow_crypto.py`) expects. Generation is **idempotent per MAC** at the `BoxProvisioningService` layer (see above) — `KBoxGenerator` itself is stateless and simply produces fresh bytes each call; it is `BoxProvisioningService.provision()`'s create-vs-reuse branch that guarantees a given box only ever gets one `K_Box` for the life of its registry row.

### HTTPS setup (Task 8)

`box-api` is served over HTTPS on port **8443** (the Hub already claims 8080; Hub and Node run as two processes on the same machine at N=1 — see `application.properties`). TLS is terminated with a **self-signed dev keystore** checked into `src/main/resources/node-dev-keystore.p12` (`server.ssl.key-store=classpath:node-dev-keystore.p12`, alias `smartground-node`, password via `NODE_KEYSTORE_PASSWORD` env var, default `changeit` for dev). The firmware pins this dev CA/cert via `systemconfig/node_ca.crt` on the box side (see `smart-box/CLAUDE.md`).

**Not yet done (deferred to sub-project #9, Node-Image und Deployment):** there is no `hostapd`/access-point setup on the Node yet. Today the Node is reached over a regular WiFi/LAN the box is already joined to (via `box_api_base_url` configured through the captive portal or manually) — there is no Node-hosted WiFi AP for boxes to join directly. That access-point story, along with a production (non-self-signed) certificate story, is explicitly out of scope here and tracked under #9.

## node-api (onboarding slice)

`smart-ground-node` also serves a **client-facing** `node-api` — distinct from the box-facing `box-api`. Plan 2 stands up only the **onboarding slice**; the full node-api facade (provenance envelope, Hub-degradation, offline login) is sub-project #5 and is **not** built here.

### Endpoints (hand-written `@RestController`, not generated — same exception as box-api)

- `GET /node-api/v1/onboarding/pending` → `List<PendingBoxResponse>` — boxes that announced themselves via `HELLO` (mac, rssi, firstSeen, lastSeen; `boxNonce` is not exposed).
- `POST /node-api/v1/onboarding/{mac}/couple` → `CoupleResult` — mints a one-time provisioning token and emits an `ONBOARD_OFFER` to the box via the `RadioSender` seam; returns `status="offered"`. `404 /errors/box-not-pending` if the MAC is no longer announcing.

### Auth

`/node-api/*` is guarded by `NodeApiAuthFilter` (`ch.jp.shooting.node.security`): a plain servlet filter doing **JDK-only HMAC-SHA256** verification of the bearer JWT's **signature + expiry** against the shared `jwt.secret` (must equal the Hub's). No `spring-security`, no `io.jsonwebtoken`. **Fine-grained admin permission is deliberately deferred** (the Hub JWT carries no permission claims; the operator gate is the coupling spec's open point). `/box-api/*` stays unauthenticated.

### Onboarding flow pieces (`ch.jp.shooting.node.onboarding`)

- `PendingBoxRegistry` — in-RAM, fed by the `onHello(mac, rssi, boxNonce)` **ingestion seam** (no serial receive wiring in Plan 2).
- `ProvisioningTokenService` / `ProvisioningTokenRecord` — one-time token (16 random bytes, TTL, MAC-bound, single-use), persisted in the node's H2.
- `RadioSender` — **send-only seam**; `LoggingRadioSender` is the current impl (real serial impl deferred).
- `NodeCertFingerprint` — SHA-256 of the node's TLS cert, pinned by the box for the provisioning TLS session; travels in the `ONBOARD_OFFER`.
- `OnboardingService.couple(mac)` — orchestrates lookup → mint token → build `ONBOARD_OFFER` (Plan 1 `OnboardingCodec`) → `RadioSender.send`.
- `outbox/RegistrationOutboxService` — on provisioning, persists a device-registration row and makes **one best-effort** push through the `HubRegistrationClient` seam (`LoggingHubRegistrationClient` currently no-ops; the real Hub endpoint + retry/drain worker are Sync-Fundament #2). The outbox row carries identity only — **never `K_Box`**.

### box-api discovery is now token-gated first-contact provisioning

`POST /box-api/v1/discovery` now **requires** a valid `token` (`BoxDiscoveryRequest.token`): `ProvisioningTokenService.validateAndConsume(token, mac)` runs first (typed `400 /errors/invalid-provisioning-token` on unknown/used/expired/MAC-mismatch), then `BoxProvisioningService.provision(...)` mints `K_Box` and `RegistrationOutboxService.enqueueAndAttempt(...)` queues the registration. A provisioned box never re-fetches `K_Box` (it persists it and moves to the ESP-NOW paired path), so the old idempotent every-boot discovery is redefined out (pre-v1.0).

## node-channel (#4) — client to the Hub

`ch.jp.shooting.node.nodechannel` dials the Hub's `/node-channel` WebSocket outbound (`NodeChannelClient`, `StandardWebSocketClient`) with exponential reconnect/backoff, sends `HELLO` (configured `node-channel.node-id`/`token`) then periodic `HEARTBEAT`. Incoming `COMMAND`s run through `CommandDeduplicator` (idempotent on the command UUID) into the `NodeCommandHandler` **seam** — `LoggingNodeCommandHandler` is the default (logs, returns `OK`); real ESP-NOW box dispatch replaces it later (Phase 2b), exactly like `RadioSender`. Envelope types (`NodeChannelMessage`) come from `contracts`; the Node uses its own Jackson-3 codec. Reaches the Hub only via the shared contract types — `ModuleBoundaryTest` stays green (no dependency on `smart-ground-hub`).
