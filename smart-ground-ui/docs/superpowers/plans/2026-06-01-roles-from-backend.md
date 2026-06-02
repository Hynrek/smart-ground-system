# Available Roles from Backend — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hardcoded `ROLES` array in the frontend with a live list fetched from `GET /api/roles`, so the role chips in the user detail panel always reflect the roles that actually exist in the database.

**Architecture:** Add a `RoleController` to the backend (following the `DeviceTypeGroupController` pattern — repository directly, inner record DTO, no service layer needed). On the frontend, add `fetchAvailableRoles` to `userApi`, an `availableRoles` state + `loadAvailableRoles` action to `userStore`, and wire `UsersView` to fetch on mount and iterate over `userStore.availableRoles` instead of a hardcoded array.

**Tech Stack:** Java 25 / Spring Boot 4 (backend), Vue 3 `<script setup>` / Pinia (frontend), Vitest (frontend tests), JUnit 5 / `@WebMvcTest` (backend tests)

---

## Files

| File | Change |
|---|---|
| `smart-ground-backend/.../api/RoleController.java` | **Create** — `GET /api/roles` returning `List<RoleResponse>` |
| `smart-ground-backend/.../api/RoleControllerTest.java` | **Create** — `@WebMvcTest` for `RoleController` |
| `src/services/userApi.js` | Add `fetchAvailableRoles()` |
| `src/stores/userStore.js` | Add `availableRoles` ref + `loadAvailableRoles()` action |
| `src/stores/__tests__/userStore.test.js` | Add tests for `loadAvailableRoles` |
| `src/views/UsersView.vue` | Replace hardcoded `ROLES` with `userStore.availableRoles`; fetch on mount |

---

### Task 1: Backend — `GET /api/roles` endpoint

**Files:**
- Create: `smart-ground-backend/src/main/java/ch/jp/shooting/api/RoleController.java`
- Create: `smart-ground-backend/src/test/java/ch/jp/shooting/api/RoleControllerTest.java`

#### Background

The `Role` entity lives in `ch.jp.shooting.model.auth.Role` and is backed by `RoleRepository extends JpaRepository<Role, UUID>`. The database already has 7 seeded roles (ADMIN, OWNER, SHOOTER, COMPETITION_MASTER, COMPETITION_RANGE_MASTER, RANGE_OPERATOR, COMPETITION_GUEST), all with descriptions.

Follow the same pattern as `DeviceTypeGroupController` — inject the repository directly, map to an inner record DTO, return a list.

- [ ] **Step 1: Write the failing test**

Create `smart-ground-backend/src/test/java/ch/jp/shooting/api/RoleControllerTest.java`:

```java
package ch.jp.shooting.api;

import ch.jp.shooting.model.auth.Role;
import ch.jp.shooting.repository.auth.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RoleController.class)
class RoleControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    RoleRepository roleRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void listRoles_returnsAllRoles() throws Exception {
        Role admin = new Role("ADMIN", "System administrator. Full system access.");
        Role shooter = new Role("SHOOTER", "User with full operational access.");
        when(roleRepository.findAll()).thenReturn(List.of(admin, shooter));

        mockMvc.perform(get("/api/roles"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(2))
               .andExpect(jsonPath("$[0].name").value("ADMIN"))
               .andExpect(jsonPath("$[0].description").value("System administrator. Full system access."))
               .andExpect(jsonPath("$[1].name").value("SHOOTER"));
    }

    @Test
    void listRoles_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/roles"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "SHOOTER")
    void listRoles_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/roles"))
               .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

```bash
cd smart-ground-backend
./mvnw test -pl . -Dtest=RoleControllerTest -q 2>&1 | tail -20
```

Expected: FAIL — `RoleController` class not found.

- [ ] **Step 3: Create `RoleController`**

Create `smart-ground-backend/src/main/java/ch/jp/shooting/api/RoleController.java`:

```java
package ch.jp.shooting.api;

import ch.jp.shooting.model.auth.Role;
import ch.jp.shooting.repository.auth.RoleRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/roles")
@NullMarked
public class RoleController {

    private final RoleRepository roleRepository;

    public RoleController(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RoleResponse>> listRoles() {
        List<RoleResponse> roles = roleRepository.findAll()
                .stream()
                .map(r -> new RoleResponse(r.getId(), r.getName(), r.getDescription()))
                .toList();
        return ResponseEntity.ok(roles);
    }

    public record RoleResponse(UUID id, String name, @Nullable String description) {}
}
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
cd smart-ground-backend
./mvnw test -pl . -Dtest=RoleControllerTest -q 2>&1 | tail -20
```

Expected: all 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/api/RoleController.java
git add smart-ground-backend/src/test/java/ch/jp/shooting/api/RoleControllerTest.java
git commit -m "[backend] Add GET /api/roles endpoint"
```

---

### Task 2: Frontend — `fetchAvailableRoles` in userApi + `loadAvailableRoles` in userStore

**Files:**
- Modify: `src/services/userApi.js`
- Modify: `src/stores/userStore.js`
- Modify: `src/stores/__tests__/userStore.test.js`

#### Background

`userApi.js` uses `apiFetch` from `./apiClient.js` — pass the path and optional options. The store uses `ref()` for state, follows the pattern: `isLoading.value = true` → try/catch/finally with `isLoading.value = false`. The existing `loadUsers()` action is the model to follow.

`availableRoles` shape: `Array<{ id: string, name: string, description: string | null }>`

- [ ] **Step 1: Write failing tests**

Add to `src/stores/__tests__/userStore.test.js` (append a new `describe` block — do not replace existing tests):

```js
describe('useUserStore — loadAvailableRoles', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetches roles and stores them', async () => {
    const mockRoles = [
      { id: 'r1', name: 'ADMIN', description: 'System administrator.' },
      { id: 'r2', name: 'SHOOTER', description: 'Full operational access.' },
    ]
    userApi.fetchAvailableRoles.mockResolvedValue(mockRoles)

    const store = useUserStore()
    await store.loadAvailableRoles()

    expect(userApi.fetchAvailableRoles).toHaveBeenCalledOnce()
    expect(store.availableRoles).toEqual(mockRoles)
    expect(store.isLoading).toBe(false)
  })

  it('sets error on failure', async () => {
    userApi.fetchAvailableRoles.mockRejectedValue(new Error('Network error'))

    const store = useUserStore()
    await store.loadAvailableRoles()

    expect(store.error).toBe('Network error')
    expect(store.availableRoles).toEqual([])
    expect(store.isLoading).toBe(false)
  })
})
```

- [ ] **Step 2: Run test — verify it fails**

```bash
cd smart-ground-ui
npm run test src/stores/__tests__/userStore.test.js 2>&1 | tail -20
```

Expected: FAIL — `userApi.fetchAvailableRoles is not a function`

- [ ] **Step 3: Add `fetchAvailableRoles` to userApi**

Append to `src/services/userApi.js`:

```js
export async function fetchAvailableRoles() {
  return apiFetch('/roles')
}
```

- [ ] **Step 4: Add `availableRoles` state and `loadAvailableRoles` action to userStore**

In `src/stores/userStore.js`:

Add to the state refs at the top of the store body (after `const isLoading = ref(false)`):

```js
const availableRoles = ref([])
```

Add the action after `changePassword`:

```js
async function loadAvailableRoles() {
  isLoading.value = true
  error.value = null
  try {
    availableRoles.value = await userApi.fetchAvailableRoles()
  } catch (err) {
    error.value = err.message || 'Failed to load roles'
  } finally {
    isLoading.value = false
  }
}
```

Add `availableRoles` and `loadAvailableRoles` to the `return` object.

- [ ] **Step 5: Run tests — verify they pass**

```bash
cd smart-ground-ui
npm run test src/stores/__tests__/userStore.test.js 2>&1 | tail -20
```

Expected: all tests PASS (existing + 2 new).

- [ ] **Step 6: Commit**

```bash
git add src/services/userApi.js src/stores/userStore.js src/stores/__tests__/userStore.test.js
git commit -m "[ui] Add fetchAvailableRoles to userApi and loadAvailableRoles to userStore"
```

---

### Task 3: Frontend — Wire `UsersView` to use `availableRoles` from store

**Files:**
- Modify: `src/views/UsersView.vue`

#### Background

Currently `UsersView.vue` has a hardcoded constant in `<script setup>`:

```js
const ROLES = [
  { name: 'ADMIN', label: 'Admin' },
  { name: 'SHOOTER', label: 'Schütze' },
  { name: 'OWNER', label: 'Bereichsleiter' },
]
```

The template iterates `v-for="role in ROLES"` and uses `role.label` for display text and in the `aria-label`.

After this task, `ROLES` is removed and replaced with `userStore.availableRoles` (shape: `{ id, name, description }`). The template uses `role.description || role.name` as display text. `loadAvailableRoles()` is called in `onMounted` alongside the existing `loadUsers()`.

- [ ] **Step 1: Update `onMounted` to also load available roles**

In `src/views/UsersView.vue`, find:

```js
onMounted(() => userStore.loadUsers())
```

Replace with:

```js
onMounted(() => {
  userStore.loadUsers()
  userStore.loadAvailableRoles()
})
```

- [ ] **Step 2: Remove the hardcoded `ROLES` constant**

In `<script setup>`, find and delete these lines:

```js
const ROLES = [
  { name: 'ADMIN', label: 'Admin' },
  { name: 'SHOOTER', label: 'Schütze' },
  { name: 'OWNER', label: 'Bereichsleiter' },
]
```

- [ ] **Step 3: Update the chip template to use `userStore.availableRoles`**

In the `<template>`, find the chips `v-for`:

```html
    <button
      v-for="role in ROLES"
      :key="role.name"
      :class="['role-chip', { 'role-chip--active': hasRole(selectedUser.id, role.name) }]"
      :disabled="userStore.isLoading"
      :aria-pressed="hasRole(selectedUser.id, role.name)"
      :aria-label="`Rolle ${role.label} ${hasRole(selectedUser.id, role.name) ? 'entfernen' : 'zuweisen'}`"
      @click="handleToggleRole(selectedUser.id, role.name)"
    >
      {{ role.label }}
    </button>
```

Replace with:

```html
    <button
      v-for="role in userStore.availableRoles"
      :key="role.name"
      :class="['role-chip', { 'role-chip--active': hasRole(selectedUser.id, role.name) }]"
      :disabled="userStore.isLoading"
      :aria-pressed="hasRole(selectedUser.id, role.name)"
      :aria-label="`Rolle ${role.description || role.name} ${hasRole(selectedUser.id, role.name) ? 'entfernen' : 'zuweisen'}`"
      @click="handleToggleRole(selectedUser.id, role.name)"
    >
      {{ role.description || role.name }}
    </button>
```

- [ ] **Step 4: Verify lint**

```bash
cd smart-ground-ui
npm run lint
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add src/views/UsersView.vue
git commit -m "[ui] Wire role chips to availableRoles from backend"
```

---

## Self-Review

**Spec coverage:**
- ✅ `GET /api/roles` returns all roles with id, name, description — Task 1
- ✅ Endpoint protected (ADMIN only) — Task 1, Step 3
- ✅ 3 backend tests: success, 401, 403 — Task 1
- ✅ `fetchAvailableRoles` in userApi — Task 2
- ✅ `availableRoles` state + `loadAvailableRoles` in userStore — Task 2
- ✅ 2 frontend store tests: success, error — Task 2
- ✅ `loadAvailableRoles()` called on mount — Task 3
- ✅ Hardcoded `ROLES` array removed — Task 3
- ✅ Chips iterate over `userStore.availableRoles` — Task 3
- ✅ Display text uses `role.description || role.name` — Task 3

**Placeholder scan:** None found.

**Type consistency:**
- `availableRoles`: `Array<{ id: string, name: string, description: string | null }>` — consistent between `RoleController.RoleResponse`, `userStore.availableRoles`, and template usage
- `fetchAvailableRoles()` — consistent between `userApi.js`, store action, and test mock
- `loadAvailableRoles()` — consistent between store, tests, and `onMounted` call in view
- `role.description || role.name` — used in both `{{ }}` and `:aria-label` bindings — consistent
