# Admin "Testing" Panel + Test-Data Endpoints — Design

**Date:** 2026-07-02
**Status:** Approved
**Scope:** Backend (`smart-ground-backend`) + Frontend (`smart-ground-ui`)

## Motivation

After wiping the database (frequent during pre-v1.0 development), rebuilding a
usable test setup by hand is slow. This adds an admin-only **Testing** panel that
creates common test data on demand via three one-click actions: create a user,
seed the four standard ranges, and create a mock SmartBox with N devices.

`DataInitializer` is **not** changed — the actions are interactive/on-demand only.

## Approach

A dedicated contract-first `TestingApi` (new endpoints under `/api/testing`)
drives a new admin view. Rejected alternative: folding the actions into existing
controllers (`UserController`, `RangeController`, `SmartBoxController`) — none of
the three actions maps cleanly onto an existing endpoint (there is no
SmartBox-create or bulk-device endpoint today), and a separate `TestingController`
keeps this throwaway dev-tooling isolated and trivially removable before v1.0.

All endpoints are `ROLE_ADMIN`-gated.

## Backend (`ch.jp.shooting`)

New `TestingController implements TestingApi` (generated interface), delegating to
a new `TestDataService`. All three methods `@PreAuthorize("hasRole('ADMIN')")`.

### Endpoints

| Endpoint | Request | Behavior | Response |
|---|---|---|---|
| `POST /api/testing/users` | `CreateTestUserRequest { credential }` | username=`credential`, email=`{credential}@test.local`, password=`credential`, SHOOTER role, status ACTIVE, `emailBestaetigt=true`, `sprache="DE"` | `201 TestUserResponse { id, username, email }` |
| `POST /api/testing/ranges/seed` | *(none)* | Create `Vorderlader`, `Trapstand`, `Rollhase`, `Kippreh` if not already present (idempotent, matched by name) | `200 SeedRangesResponse { ranges: [{ id, name, created }] }` |
| `POST /api/testing/mock-smartbox` | `CreateMockSmartBoxRequest { deviceCount, alias? }` | Create a SmartBox with a generated locally-administered MAC, status OFFLINE, `firmwareConfig` = seeded `0.6`/`xiao-esp32s3`; then create `deviceCount` devices of the `Werfer` DeviceType (group `Wurfmaschine`), each unassigned to any range | `201 MockSmartBoxResponse { id, macAddress, alias, deviceCount }` |

### Rules & edge cases

- **User create:** if the username (case-insensitive) or email already exists →
  `ConflictException` (409, existing mapping). `credential` must be non-blank and
  must not contain `@` (username pattern forbids it) → `IllegalArgumentException`
  (400). Device role `SHOOTER` resolved via `RoleRepository.findByName("SHOOTER")`;
  missing → `IllegalStateException`.
- **Range seed:** idempotent. A name already present is returned with
  `created=false`; newly inserted with `created=true`.
- **Mock SmartBox:** `deviceCount` in `[1, 50]` — outside range →
  `IllegalArgumentException` (400). MAC generated as a random locally-administered
  address (first octet `0x02`), re-rolled until unique per `SmartBoxRepository`.
  Missing seeded firmware config or `Werfer` device type → `IllegalStateException`.
  Devices get `alias` like `Werfer 1..N`, `deviceTypeGroup` = Werfer's group,
  `deviceType` = Werfer.

### Contract-first workflow

1. Add the three paths + schemas to `src/main/resources/static/openapi.yaml`
   under a new `Testing` tag.
2. `./mvnw generate-sources` → `TestingApi` interface appears in
   `target/generated-sources/openapi/ch/jp/smartground/api/`.
3. Implement `TestingController implements TestingApi` (no Spring mapping
   annotations on class/methods).

### New classes

- `api/TestingController.java`
- `service/TestDataService.java`
- No new exceptions (reuse `ConflictException`, `IllegalArgumentException`,
  `IllegalStateException`, all already mapped in `GlobalExceptionHandler`).

### Testing (TDD)

`TestDataServiceTest` (`@ExtendWith(MockitoExtension.class)`, mocked repos +
`PasswordEncoder`):
- creates a user with expected username/email/password-encode + SHOOTER role
- duplicate username → `ConflictException`
- blank / `@`-containing credential → `IllegalArgumentException`
- range seed inserts missing, leaves existing, reports `created` flags correctly
- mock box creates exactly `deviceCount` devices with unique MAC
- `deviceCount` 0 or 51 → `IllegalArgumentException`

Coverage target ≥80% for new code.

## Frontend (`smart-ground-ui`)

- **`src/views/admin/TestingView.vue`** — three cards:
  - **Create User:** single text input (placeholder "username") + "Erstellen"
    button. Sends `{ credential }`. Shows created username/email or error inline.
  - **Schiessplatz Setup:** one button "4 Plätze erstellen". Shows per-range
    created/existing result.
  - **Create Mock SmartBox:** number input (device count, default 4, min 1 max 50)
    + optional alias input + "Erstellen" button. Shows MAC + device count on success.
  - Each card manages its own loading + inline success/error state.
- **`src/services/testingApi.js`** — `createTestUser(credential)`,
  `seedRanges()`, `createMockSmartBox({ deviceCount, alias })`, using `apiFetch`.
- **`router/index.js`** — add
  `{ path: '/testing', component: TestingView, meta: { layout: 'admin', permission: 'MANAGE_USERS' } }`.
- **`Sidebar.vue`** — add nav item
  `{ id: 'testing', label: 'Testing', icon: <existing Icons.vue icon>, requiredPermission: 'MANAGE_USERS' }`
  at the bottom of `allNavItems`.

### Nav gating

Nav item and route gate on the `MANAGE_USERS` permission (held only by ADMIN in the
seed); the backend enforces `ROLE_ADMIN`. No new `Permission` enum value is added.

## Defaults (confirmed)

- Mock SmartBox `status` = `OFFLINE` (not a real connected box).
- "Testing" nav sits at the bottom of the admin sidebar.

## Out of scope

- No `DataInitializer` changes.
- No range/position auto-assignment of mock devices (created unassigned).
- No delete/reset endpoints (DB wipe is done externally).
