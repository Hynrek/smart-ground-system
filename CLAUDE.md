# Smart Ground — Monorepo Overview

Smart Ground is an IoT system for managing shooting-range devices (clay pigeon throwers, LEDs, sensors) over MQTT.

```
smart-ground/
├── smart-ground-hub/       # Spring Boot 4 REST API + MQTT broker integration
├── smart-ground-contracts/ # Shared contracts (OpenAPI types) + domain (JPA entities) — own repo, consumed via Maven coordinates
├── smart-ground-ui/        # Vue 3 frontend
├── smart-box/              # MicroPython firmware (XIAO ESP32-S3; multi-board layer, Pico 2W parked)
└── smart-ground-deploy/    # Deployment: docker-compose + Mosquitto config
```

**No production release has been made.** Schema, API contracts, and migrations can be rewritten freely.

---

## Architecture

> ⚠️ **What follows describes the system as it exists today. It is superseded by [docs/superpowers/specs/2026-07-10-hub-node-architecture-design.md](./docs/superpowers/specs/2026-07-10-hub-node-architecture-design.md)** — a Hub/Node tier split that removes MQTT entirely and makes each Schiessplatz independently operable. Implementation is tracked in [docs/superpowers/plans/2026-07-10-hub-node-roadmap.md](./docs/superpowers/plans/2026-07-10-hub-node-roadmap.md). **Read both before starting architectural work.** This section is updated as the roadmap's sub-projects land.

```
Client App  ──REST──▶  Backend  ──MQTT──▶  SmartBox  ──GPIO/LED──▶  Physical Device
                           ▲                                              │
SmartBox   ──MQTT──────────┘   (INPUT: sensor triggers, status)          │
Client App  ──WS────▶  Backend  (STOMP/SockJS at /ws/shooting)           │
```

- **Client → Backend**: REST (OpenAPI contract-first, generated interfaces)
- **Backend → SmartBox**: MQTT publish (config push, commands)
- **SmartBox → Backend**: MQTT publish (discovery, status, config ACK)
- **Real-time push**: STOMP WebSocket at `/ws/shooting`
- SmartBox identified by **MAC address** in MQTT; UUID is the stable DB primary key

---

## Sub-Project Guides

Each sub-project has its own `CLAUDE.md` with full setup, schema, conventions, and task guides:

- **[smart-ground-hub/CLAUDE.md](./smart-ground-hub/CLAUDE.md)** — Java 25, Spring Boot 4, dynamic RBAC, Serie/Passe/Play system, competitions, MQTT, OTA, OpenAPI
- **[smart-ground-ui/CLAUDE.md](./smart-ground-ui/CLAUDE.md)** — Vue 3, Vite, Pinia, permission-based routing, Shooter Remote / Wettkampf features
- **[smart-box/CLAUDE.md](./smart-box/CLAUDE.md)** — MicroPython, XIAO ESP32-S3, OTA, memory discipline

---

## Running the Full Stack Locally

```bash
# Terminal 1: Backend (PostgreSQL + Mosquitto via Docker)
cd smart-ground-hub
docker compose up
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
# API: http://localhost:8080  |  Swagger: http://localhost:8080/swagger-ui.html

# Terminal 2: Frontend
cd smart-ground-ui
npm install
npm run dev   # http://localhost:5173

# Terminal 3: Firmware (requires a physical XIAO ESP32-S3)
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

- INPUT signal handling end-to-end (sensor → backend → trigger device)
- Multi-SmartBox device assignment (data model ready; API not exposed)
- Email / phone verification flows
- Play result scoring logic (`PlayResultController` stub exists)
- Real-time push to clients (STOMP WebSocket at `/ws/shooting` is configured but `SessionWebSocketService` is never called; no frontend STOMP client)

Note: OTA update delivery (App-Code + firmware) **is** implemented end-to-end — see the backend and smart-box guides.
