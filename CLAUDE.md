# Smart Ground — Monorepo Overview

Smart Ground is an IoT system for managing shooting-range devices (clay pigeon throwers, LEDs, sensors). MQTT has been fully removed from the stack (sub-project #7, `erledigt`) — see Architecture below.

```
smart-ground/
├── smart-ground-hub/       # Spring Boot 4 REST API — sync/competition authority (MQTT removed)
├── smart-ground-node/      # Spring Boot 4 process — box-api (HTTPS) for SmartBox discovery/status/OTA
├── smart-ground-contracts/ # Shared contracts (OpenAPI types) + domain (JPA entities) — consumed via Maven coordinates (`mvn install`)
├── smart-ground-ui/        # Vue 3 frontend
├── smart-box/              # MicroPython firmware (XIAO ESP32-S3; multi-board layer, Pico 2W parked)
└── smart-ground-deploy/    # Deployment: docker-compose (no Mosquitto — MQTT removed)
```

**No production release has been made.** Schema, API contracts, and migrations can be rewritten freely.

---

## Repository & Git Setup (settled 2026-07-12)

**This is a monorepo.** The entire `Smart Ground` folder is one git repository:
`git@github.com:Hynrek/smart-ground-system.git`. The subproject folders are **not**
separate git repos — do not `git init` or add remotes inside them.

- **One repo, one history**: the full histories of the former `smartground-backend`,
  `smartground-ui`, `smartground-firmware`, and `smart-ground-contracts` repos were merged
  in on 2026-07-12 (`git log -- <subfolder>/` reaches back to each subproject's first commit).
- **Old GitHub repos** (`smartground-backend`, `smartground-ui`, `smartground-firmware`,
  `smartground-deploy`): historical archives only — never push to them again.
- **`.git-archive/`** (gitignored, local only): the pre-migration `.git` dirs of the old
  subrepos, kept as backup (they also hold old side branches). Safe to delete once confident.
- **Line endings**: `.gitattributes` normalizes all text files to LF in the repo.
- **Branches**: work on `main` or short-lived feature branches; everything merges to `main`.
- Cross-cutting changes (e.g. contract → hub → ui) are **one atomic commit**.

---

## Architecture

> ⚠️ **What follows describes the system as it exists today. It is superseded by [docs/superpowers/specs/2026-07-10-hub-node-architecture-design.md](./docs/superpowers/specs/2026-07-10-hub-node-architecture-design.md)** — a Hub/Node tier split that removes MQTT entirely and makes each Schiessplatz independently operable. Implementation is tracked in [docs/superpowers/plans/2026-07-10-hub-node-roadmap.md](./docs/superpowers/plans/2026-07-10-hub-node-roadmap.md). **Read both before starting architectural work.** This section is updated as the roadmap's sub-projects land.
>
> **Sub-project #7 (MQTT-Ausbau und `box-api`) is `erledigt`.** The diagram below documents the *old*, now-removed MQTT transport purely as historical context — MQTT/Mosquitto no longer exist anywhere in this stack (no broker, no client, no dynsec). SmartBox discovery, heartbeat/status, and OTA-artifact serving now go over HTTPS to `smart-ground-node`'s `box-api`; see [smart-ground-node/CLAUDE.md](./smart-ground-node/CLAUDE.md). Command dispatch (device commands, config push) has no replacement transport yet — it 501s pending the `node-channel` sub-project (#4).

```
Client App  ──REST──▶  Backend  ──???──▶  SmartBox  ──GPIO/LED──▶  Physical Device
                           ▲                                              │
SmartBox   ──???──────────┘   (INPUT: sensor triggers, status)           │
Client App  ──WS────▶  Backend  (STOMP/SockJS at /ws/shooting)           │
```

- **Client → Backend**: REST (OpenAPI contract-first, generated interfaces)
- **Backend → SmartBox**: no transport yet (was MQTT publish; command dispatch now returns `501` — see node-channel, #4)
- **SmartBox → Backend**: discovery/heartbeat go to `smart-ground-node`'s `box-api` over HTTPS, not to the Backend directly (was MQTT publish)
- **Real-time push**: STOMP WebSocket at `/ws/shooting`
- SmartBox identified by **MAC address**; UUID is the stable DB primary key

---

## Sub-Project Guides

Each sub-project has its own `CLAUDE.md` with full setup, schema, conventions, and task guides:

- **[smart-ground-hub/CLAUDE.md](./smart-ground-hub/CLAUDE.md)** — Java 25, Spring Boot 4, dynamic RBAC, Serie/Passe/Play system, competitions, OTA, OpenAPI
- **[smart-ground-node/CLAUDE.md](./smart-ground-node/CLAUDE.md)** — Java 25, Spring Boot 4, `box-api` (HTTPS: discovery/provisioning, status, OTA proxy), embedded H2 box registry
- **[smart-ground-ui/CLAUDE.md](./smart-ground-ui/CLAUDE.md)** — Vue 3, Vite, Pinia, permission-based routing, Shooter Remote / Wettkampf features
- **[smart-box/CLAUDE.md](./smart-box/CLAUDE.md)** — MicroPython, XIAO ESP32-S3, OTA, memory discipline

---

## Running the Full Stack Locally

```bash
# Terminal 1: Backend (PostgreSQL via Docker)
cd smart-ground-hub
docker compose up
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
# API: http://localhost:8080  |  Swagger: http://localhost:8080/swagger-ui.html

# Terminal 2: Node (box-api — HTTPS, self-signed dev cert)
cd smart-ground-node
./mvnw spring-boot:run
# box-api: https://localhost:8443/box-api/v1/...

# Terminal 3: Frontend
cd smart-ground-ui
npm install
npm run dev   # http://localhost:5173

# Terminal 4: Firmware (requires a physical XIAO ESP32-S3)
cd smart-box
# See smart-box/CLAUDE.md for flashing instructions
```

---

## Cross-Cutting Conventions

| Area | Rule |
|---|---|
| Commit messages | `[backend\|ui\|firmware] short description` |
| Comments | German for backend/firmware domain logic; English for frontend and tests |
| Secrets | Never hardcode — use `application.properties` / `.env` |
| Schema changes | Edit JPA entities (pre-v1.0); Hibernate applies on restart |
| API changes | Edit `openapi.yaml` first, then `mvn generate-sources`, then implement |

---

## What is Not Yet Implemented

- Device command dispatch and config push (Backend → SmartBox) — both `501` since MQTT removal (#7); replacement transport is the `node-channel` sub-project (#4)
- INPUT signal handling end-to-end (sensor → backend → trigger device)
- Multi-SmartBox device assignment (data model ready; API not exposed)
- Email / phone verification flows
- Play result scoring logic (`PlayResultController` stub exists)
- Real-time push to clients (STOMP WebSocket at `/ws/shooting` is configured but `SessionWebSocketService` is never called; no frontend STOMP client)

Note: OTA update delivery (App-Code + firmware) **is** implemented end-to-end — see the backend and smart-box guides.
