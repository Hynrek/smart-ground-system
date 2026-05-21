# smart-ground-backend — Spring Boot Development Guide

## Project Overview

The Smart Ground backend is a Spring Boot 4 REST API that serves as the single authority for all IoT device commands and state. It integrates an MQTT broker to communicate with SmartBox firmware devices, manages a PostgreSQL database, and exposes OpenAPI-generated REST endpoints.

---

## Stack & Versions

- **Java**: 25 (latest LTS)
- **Spring Boot**: 4.x
- **Database**: PostgreSQL (production), H2 (testing)
- **MQTT**: Eclipse Paho MQTTv5, Spring Integration
- **Schema**: Liquibase (v1.0+), JPA/Hibernate (pre-v1.0)
- **Build**: Maven 3.8+
- **Container**: Docker Compose for local PostgreSQL + Mosquitto

---

## Project Structure

```
smart-ground-backend/
├── src/main/java/ch/jp/shooting/
│   ├── api/                   # Generated OpenAPI interfaces + implementations
│   ├── config/                # Spring config, MQTT router, handlers
│   ├── dto/                   # Request/response DTOs
│   ├── exception/             # Domain exceptions
│   ├── mapper/                # Entity ↔ DTO mapping
│   ├── model/                 # JPA entities
│   ├── repository/            # Spring Data repositories
│   └── service/               # Business logic
├── src/main/resources/
│   ├── static/openapi.yaml    # OpenAPI contract (contract-first)
│   ├── db/changelog/          # Liquibase migrations
│   └── application*.properties # Environment config
├── src/test/java/             # JUnit 5 tests (mirror main structure)
├── pom.xml                    # Maven dependencies
└── docker-compose.yml         # PostgreSQL + Mosquitto for local dev
```

---

## Running Locally

### Option 1: Docker (Recommended)
```bash
# Terminal 1: Start PostgreSQL & Mosquitto
docker compose up

# Terminal 2: Run backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
# Server runs on http://localhost:8080
# PostgreSQL on localhost:5432, Mosquitto on localhost:1883
```

### Option 2: Without Docker
```bash
# Requires local PostgreSQL installation
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

### Option 3: H2 In-Memory (Testing)
```bash
# Fast, no external DB needed
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2
```

---

## Authentication & Security

### JWT-Based Auth

Your backend uses **Spring Security** with **JWT tokens**:

1. **User Login** → `POST /api/auth/login`
   - Username + password → JWT token (contains username + role)
   - Token stored in `Authorization: Bearer <token>` header

2. **JwtAuthenticationFilter** → Validates token on every request
   - Extracts username and role from token
   - Sets authentication in SecurityContext
   - Public endpoints bypass filter

3. **User Roles**
   - `ADMIN` — Full system access, user management
   - `SHOOTER` — Standard user, can participate in sessions

### Current Implementation

**Classes involved:**
- `JwtService` — Token generation/validation
- `AuthController` — Login endpoint
- `JwtAuthenticationFilter` — Per-request validation
- `User` model — Username, password (hashed), role

**Endpoints:**
```
POST /api/auth/login           # Username + password → JWT
POST /api/users                # Create new user (ADMIN only)
PATCH /api/users/{id}/role     # Change user role (ADMIN only)
```

---

## Session/Competition Management

Your backend supports **reusable session templates** and **live competition sessions**:

### SessionTemplate (Reusable Configuration)

A template defines the "blueprint" for a competition:
- Name, type (COMPETITION, TRAINING, FREE)
- Optional program/segment snapshots
- Bracket type (ROUND_ROBIN, SINGLE_ELIMINATION, DOUBLE_ELIMINATION)
- Default tiebreaker strategy
- Default player list
- Created by, audit timestamps

**Use case:** Create once, reuse for multiple actual competitions.

### LiveSession (Active Competition)

An instance of a competition in progress:
- References SessionTemplate (optional)
- Status: SETUP → ACTIVE → PAUSED → COMPLETED/ABANDONED
- Immutable program/range snapshots (taken at start)
- List of SessionPlayers (competitors)
- Groups (e.g., "Group A", "Group B")
- Results tracking

**Lifecycle:**
1. Create from template or standalone
2. Add players/groups
3. Initialize bracket (if applicable)
4. Transition to ACTIVE
5. Record results as competition runs
6. Transition to COMPLETED

### Controllers

| Controller | Responsibility |
|---|---|
| `SessionController` | CRUD operations for sessions |
| `GroupController` | Manage groups within a session |
| `BracketController` | Initialize and manage tournament brackets |
| `CompetitionController` | Competition-specific operations |
| `ResultsController` | Record and retrieve results/scoring |

---

## Actual Database Schema

Your entities (from the JPA models):

### Core Entities

**smart_boxes** — Physical Pico 2W devices
- id (UUID)
- mac_address (unique)
- alias, status, firmware_version, config_synced

**devices** — GPIO devices registered on a SmartBox
- id (UUID)
- smartbox_id → smart_boxes
- device_type_id → device_types
- range_id → ranges (nullable)
- alias, pin_config (JSON), config_json (JSON), blocked, healthy

**ranges** — Shooting lanes/zones
- id (UUID)
- name (unique), description, locked, created_at

**device_types** — Categorized device capabilities
- id (UUID)
- name, signal_type_id, group_id, signal_duration_ms, delay_signal_duration_ms

### Authentication Entities

**users** — System users
- id (UUID)
- username (unique), password (bcrypt hashed), role (ADMIN/SHOOTER)
- created_at, updated_at

### Session/Competition Entities

**session_templates** — Reusable competition blueprints
- id (UUID)
- name (unique), type (COMPETITION/TRAINING/FREE)
- program_ids, range_segment_map, default_players (all JSON)
- bracket_type, default_tiebreaker, max_groups
- publish_results (boolean), created_by_id → users

**live_sessions** — Active or completed competitions
- id (UUID)
- name, type (COMPETITION/TRAINING/FREE)
- status (SETUP/ACTIVE/PAUSED/COMPLETED/ABANDONED)
- template_id → session_templates (nullable)
- program_snapshots, range_segment_map (immutable JSON)
- created_at, updated_at

**session_players** — Competitors in a session
- id (UUID)
- session_id → live_sessions
- user_id → users (nullable, for guests)
- type (USER/GUEST), display_name
- joined_at

**groups** — Player divisions within a session (e.g., "Group A")
- id (UUID)
- session_id → live_sessions
- name, created_at

### Notes on JSON Columns

Several columns store **immutable snapshots** as JSON:
- `program_snapshots` — Complete program structure at session start (prevents changes mid-competition)
- `range_segment_map` — Range/segment assignments at session start
- `default_players` — Pre-populated players for templates
- `pin_config` — GPIO mapping for devices
- `config_json` — Sensor threshold config for devices

These allow flexibility without breaking existing sessions.

---

## Testing

### Run All Tests
```bash
./mvnw test
# Uses H2 in-memory database (spring.jpa.hibernate.ddl-auto=create-drop)
```

### Run Specific Test
```bash
./mvnw test -Dtest=SmartBoxServiceTest
```

### Test Coverage
```bash
./mvnw clean test jacoco:report
# Report: target/site/jacoco/index.html
```

### Test Structure

**Unit Tests**: Business logic in isolation
- Path: `src/test/java/ch/jp/shooting/service/`
- Mock external dependencies (MQTT, DB)
- Use `@ExtendWith(MockitoExtension.class)`

**Integration Tests**: Database + MQTT with test containers
- Path: `src/test/java/ch/jp/shooting/config/mqtt/`
- Use `@SpringBootTest` + `@TestPropertySource`
- Embedded Mosquitto for MQTT integration
- H2 in-memory for database

Example:
```java
@SpringBootTest
@TestPropertySource(locations = "classpath:test-application.properties")
class SmartBoxMqttHandlerTest {
    @Autowired private SmartBoxRepository smartBoxRepository;
    @Autowired private MqttTemplate mqttTemplate;
    
    @Test
    void testDiscoveryMessage_CreatesSmartBox() {
        // MQTT discovery → database write
    }
}
```

---

## Code Review Checklist (Backend-Specific)

### Functional Correctness
- [ ] All tests pass: `./mvnw clean test`
- [ ] OpenAPI schema updated in `src/main/resources/static/openapi.yaml`
- [ ] New endpoints implement generated interface (no hardcoded `@RequestMapping`)
- [ ] Auth required endpoints use JWT validation (via `JwtAuthenticationFilter`)
- [ ] Session state transitions are validated (no invalid SETUP→COMPLETED jumps)
- [ ] MQTT topics follow `smartboxes/{mac}/...` convention
- [ ] Device commands routed correctly via MQTT (not direct GPIO)

### Code Quality
- [ ] All new classes annotated with `@NullMarked`
- [ ] New entities use `UUID` primary key with `GenerationType.UUID`
- [ ] Fetch strategies reviewed (EAGER only when necessary, default LAZY)
- [ ] JSON columns use TEXT type (not JSONB for H2 compatibility)
- [ ] New exceptions in `ch.jp.shooting.exception`, mapped in `@ControllerAdvice`
- [ ] Inline comments in German (domain logic), English (API docs)
- [ ] No hardcoded secrets (use `application.properties`)

### Database & Schema
- [ ] No breaking schema changes without migration plan
- [ ] Liquibase migrations (if v1.0+) or JPA entity changes (pre-v1.0)
- [ ] JSON snapshots documented (why immutable during session)
- [ ] Foreign key relationships verified
- [ ] Unique constraints documented (@Column(unique=true))

### Testing
- [ ] Auth flow tested (login, token validation, role checks)
- [ ] Session lifecycle tested (state transitions)
- [ ] Bracket initialization tested (seeding strategies)
- [ ] Result recording tested (player scoring)
- [ ] MQTT integration tested with embedded Mosquitto
- [ ] Mock H2 DB used for tests (not PostgreSQL)

---

## Database & Schema Management

### Pre-v1.0: JPA Controls Schema

Currently, Liquibase is disabled. JPA/Hibernate auto-generates the schema:

- **PostgreSQL profile**: `spring.jpa.hibernate.ddl-auto=update` (alter in place)
- **H2 profile**: `spring.jpa.hibernate.ddl-auto=create-drop` (rebuild on each test)

**To change the schema:**
1. Edit JPA entity classes in `src/main/java/ch/jp/shooting/model/`
2. Hibernate applies the diff on startup
3. Test with `./mvnw test` to verify

### Post-v1.0: Liquibase Takes Over

When the project reaches v1.0:

1. Set `spring.liquibase.enabled=true` and `spring.jpa.hibernate.ddl-auto=none`
2. All future schema changes go into `src/main/resources/db/changelog/01__init.xml`
3. New changesets follow: `v1.0-9`, `v1.0-10`, etc.
4. Never edit existing changesets (Liquibase checksums them)

Example adding a column post-v1.0:
```xml
<changeSet id="v1.0-9" author="smartground">
    <addColumn tableName="devices">
        <column name="config_json" type="TEXT"/>
    </addColumn>
    <modifySql dbms="h2">
        <replace replace="TEXT" with="CLOB"/>
    </modifySql>
</changeSet>
```

---

## OpenAPI & REST Contracts

### OpenAPI Workflow

1. **Write the contract** in `src/main/resources/static/openapi.yaml`
   ```yaml
   /api/smartboxes:
     get:
       operationId: listSmartBoxes
       responses:
         '200':
           content:
             application/json:
               schema:
                 type: array
                 items:
                   $ref: '#/components/schemas/SmartBoxDto'
   ```

2. **Generate interfaces** with Maven plugin (defined in `pom.xml`)
   ```bash
   ./mvnw generate-sources
   # Creates: target/generated-sources/openapi/ch/jp/shooting/api/SmartboxesApi.java
   ```

3. **Implement the interface**
   ```java
   @RestController
   @RequestMapping("/api")
   public class SmartBoxesController implements SmartboxesApi {
       @Override
       public ResponseEntity<List<SmartBoxDto>> listSmartBoxes() {
           // Implementation
       }
   }
   ```

### Never
- Add `@RequestMapping` to the controller class (OpenAPI generator creates the path)
- Modify generated interface files (regenerate instead)
- Create REST endpoints outside of OpenAPI contract

---

## MQTT Integration

### Architecture

```
MQTT Input (SmartBox publishes)
    ↓
SmartBoxMqttRouter (dispatches by topic suffix)
    ↓
Topic-specific handler (e.g., DiscoveryHandler)
    ↓
Service layer (SmartBoxService, DeviceService, etc.)
    ↓
Database & REST responses
```

### Adding a New MQTT Handler

1. **Define the topic** in `SmartBoxMqttRouter`:
   ```java
   if (topic.endsWith("/status")) {
       handlers.statusHandler().handle(payload, mac);
   }
   ```

2. **Create handler** in `ch.jp.shooting.config.mqtt`:
   ```java
   @Component
   public class StatusHandler {
       public void handle(String payload, String mac) {
           // Parse JSON, update SmartBox status
       }
   }
   ```

3. **Test** with embedded Mosquitto:
   ```java
   @SpringBootTest
   class StatusHandlerTest {
       @Autowired private MqttTemplate mqttTemplate;
       
       @Test
       void testStatusUpdate() {
           mqttTemplate.convertAndSend("smartboxes/aabbccddeeff/status", "{...}");
           // Verify SmartBox entity updated
       }
   }
   ```

### MQTT Topic Convention

Do not hardcode topic strings. Use the router and handlers pattern:

```
smartboxes/discovery           # SmartBox → Backend: registration
smartboxes/{mac}/status        # SmartBox → Backend: heartbeat
smartboxes/{mac}/config        # Backend → SmartBox: push config
smartboxes/{mac}/config/ack    # SmartBox → Backend: confirm receipt
smartboxes/{mac}/command       # Backend → SmartBox: fire device
```

---

## Conventions

### Naming
- **Entities**: `SmartBox`, `Device`, `Range` (singular, PascalCase)
- **Repositories**: `SmartBoxRepository`, `DeviceRepository`
- **Services**: `SmartBoxService`, `DeviceService` (business logic)
- **DTOs**: `SmartBoxDto`, `CreateDeviceRequest`, `DeviceResponse`
- **Handlers**: `DiscoveryHandler`, `StatusHandler`

### Classes & Annotations
- `@NullMarked` on all new classes (prevents null pointer errors)
- `@Nullable` on fields/parameters that can be null
- UUID primary key: `@Id @GeneratedValue(strategy = GenerationType.UUID)`
- Fetch: `@ManyToOne(fetch = FetchType.LAZY)` by default
- Comments: German inline comments for business logic

### Entity Example
```java
@Entity
@Table(name = "devices")
@NullMarked
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "smartbox_id")
    private SmartBox smartBox;
    
    @Nullable
    @Column(columnDefinition = "TEXT")
    private String configJson; // { "threshold_db": 85, ... }
    
    // Getters, setters
}
```

### Exception Handling
```java
// Define custom exception
@NullMarked
public class DeviceNotFound extends RuntimeException {
    public DeviceNotFound(UUID id) {
        super("Device not found: " + id);
    }
}

// Map in controller advice
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DeviceNotFound.class)
    public ResponseEntity<ErrorResponse> handleDeviceNotFound(DeviceNotFound ex) {
        return ResponseEntity.status(404).body(new ErrorResponse(ex.getMessage()));
    }
}
```

---

## Data Validation

### Validation Annotations
```java
@NullMarked
public class CreateDeviceRequest {
    @NotBlank(message = "Alias required")
    private String alias;
    
    @NotNull
    @Positive
    private UUID smartBoxId;
    
    @Min(0)
    @Max(28)
    private int pinNumber;
}
```

### Validation in Controller
```java
@PostMapping("/devices")
public ResponseEntity<DeviceResponse> createDevice(
    @RequestBody @Valid CreateDeviceRequest request
) {
    // request is guaranteed valid
}
```

---

## Caching

Use Caffeine for frequently-accessed data:

```java
@Service
@NullMarked
public class FirmwareConfigService {
    @Cacheable(value = "firmwareConfigs", key = "#version.concat('-').concat(#boxType)")
    public FirmwareConfig getConfig(String version, String boxType) {
        return repository.findByVersionAndBoxType(version, boxType)
            .orElseThrow(() -> new ConfigNotFound(version, boxType));
    }
}
```

---

## Building & Deployment

### Local Build
```bash
./mvnw clean package
# Creates: target/smart-ground-backend-*.jar
```

### Docker Build
```bash
docker build -t smart-ground-backend:latest .
docker run -e SPRING_PROFILES_ACTIVE=postgres -p 8080:8080 smart-ground-backend:latest
```

### Application Properties
```properties
# application-postgres.properties (production)
spring.datasource.url=jdbc:postgresql://localhost:5432/smartground
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# application-h2.properties (testing)
spring.datasource.url=jdbc:h2:mem:testdb
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
```

---

## Controllers Overview

| Controller | Endpoints | Purpose |
|---|---|---|
| `AuthController` | POST /api/auth/login, POST /api/users | JWT authentication, user registration |
| `DeviceController` | GET/POST/PATCH/DELETE /api/devices, /api/devices/{id}/command | Device CRUD and MQTT command routing |
| `SmartBoxController` | GET /api/smart-boxes, PATCH /api/smart-boxes/{id}/alias, POST /api/smart-boxes/{id}/push-config | SmartBox registration and management |
| `RangeController` | GET/POST/PATCH/DELETE /api/ranges, PATCH /api/ranges/{id}/locked | Shooting lane management |
| `DeviceTypeController` | GET /api/device-types, GET /api/device-types/firmware-configs, POST /api/device-types/firmware-configs | Capability registry |
| `SessionController` | GET/POST /api/sessions, GET /api/sessions/{id}, POST /api/sessions/{id}/groups | Session lifecycle |
| `BracketController` | POST /api/sessions/{id}/bracket | Tournament initialization |
| `CompetitionController` | GET /api/sessions/{id}/leaderboard, GET /api/sessions/{id}/results | Scoring and ranking |
| `ResultsController` | POST /api/results, GET /api/results/{sessionId} | Player result tracking |
| `UserController` | PATCH /api/users/{id}/role | User management |

---

## Key Files

| File | Purpose |
|---|---|
| `src/main/resources/static/openapi.yaml` | REST API contract (contract-first) — your source of truth |
| `src/main/java/ch/jp/shooting/config/JwtAuthenticationFilter.java` | Token validation on every request |
| `src/main/java/ch/jp/shooting/service/JwtService.java` | Token generation/validation logic |
| `src/main/java/ch/jp/shooting/config/MqttConfig.java` | MQTT broker integration |
| `src/main/java/ch/jp/shooting/config/mqtt/SmartBoxMqttRouter.java` | MQTT topic dispatcher |
| `src/main/java/ch/jp/shooting/model/LiveSession.java` | Active competition entity |
| `src/main/java/ch/jp/shooting/model/SessionTemplate.java` | Reusable competition blueprint |
| `src/main/resources/db/changelog/01__init.xml` | Database schema (Liquibase) |
| `pom.xml` | Maven dependencies & plugins |
| `docker-compose.yml` | Local PostgreSQL + Mosquitto |

---

## Implementation Status & Known Gaps

### ✅ Implemented
- JWT authentication (login, token validation)
- User management (ADMIN/SHOOTER roles)
- Device management (CRUD, commands, ranges)
- Session/competition lifecycle (create, update, complete)
- Bracket initialization with seeding strategies
- Player groups and results tracking
- MQTT integration for SmartBox communication
- OpenAPI v3.0 contract documentation

### 🔄 Partial/Needs Work
- **SSE Events** (`/api/events`) — Documented in OpenAPI but **implementation pending**
  - Planned: Real-time SmartBox status updates (ONLINE/OFFLINE/BLOCKED)
  - Planned: Device health changes (healthy/blocked flag)
  - Planned: Config sync acknowledgments
  - Current: Use polling as workaround (GET endpoints with timestamps)

- **Player Results/Scoring** — Schema exists but scoring logic needs finalization
  - Planned: Detailed shot-by-shot tracking
  - Current: Basic player result storage

- **Leaderboard Calculation** — Needs implementation
  - Planned: Real-time rank calculation with tiebreaker logic
  - Current: Schema ready, logic pending

### ❌ Not Yet Implemented
- OTA (over-the-air) firmware updates
- Multi-SmartBox device assignment (API not exposed)
- INPUT signal handling end-to-end (sensor → backend → trigger)
- Session templates management endpoints

---

## Troubleshooting

### Tests fail with "MqttException"
- Ensure Mosquitto is running: `docker compose up` in a separate terminal
- Or use H2 profile: `./mvnw test -Dspring-boot.run.profiles=h2`

### Hibernate DDL errors
- You're pre-v1.0, DDL is auto-managed. Edit JPA entities, restart server.
- Check `application.properties` for `spring.jpa.hibernate.ddl-auto` setting.

### "Connection refused" on PostgreSQL
- Start Docker: `docker compose up`
- Or switch to H2 profile: `./mvnw spring-boot:run -Dspring-boot.run.profiles=h2`

### MQTT messages not processed
- Check logs for topic in `SmartBoxMqttRouter`
- Verify handler is registered (should print on startup)
- Publish test message: `mosquitto_pub -t smartboxes/aabbccddeeff/status -m '...'`

### Authentication failed (401 Unauthorized)
- Check `Authorization: Bearer <token>` header is set
- Verify token not expired (JWT tokens have expiry)
- Check user role has permission for endpoint (ADMIN vs SHOOTER)
- Test login: `curl -X POST http://localhost:8080/api/auth/login -d '{"username":"admin","password":"password"}'`

### Session won't transition to ACTIVE
- Check all required fields filled (players, groups if needed)
- Verify bracket initialized (if bracket format selected)
- Check session status is SETUP (can't activate from other states)

