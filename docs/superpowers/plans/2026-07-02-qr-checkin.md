# QR-Checkin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Echte Schützen können im Trainings-Gruppen-Setup per QR-Code (statischer, erneuerbarer Token) hinzugefügt werden; ihre Scores werden dem User-Account zugeordnet und im Profil als „Meine Ergebnisse" angezeigt.

**Architecture:** Backend bekommt `User.qrToken` (lazy-generiert, rotierbar) + 4 neue Endpoints (contract-first via `openapi.yaml`). `PlayerRefRecord`/`PlayerResultRecord` tragen eine optionale `userId`, die von der UI beim Gruppen-Setup (QR-Scan) mitgegeben und in `play_instances` persistiert wird. Die Profil-Ergebnisliste filtert abgeschlossene Instanzen per LIKE auf die User-UUID im JSON und extrahiert die Scores aus den Block-Resultaten.

**Tech Stack:** Java 25 / Spring Boot 4, OpenAPI codegen, JPA (H2 Tests / PostgreSQL), Vue 3 `<script setup>`, Pinia, Vitest; neue npm-Deps: `qr-scanner` (Kamera-Scan), `qrcode` (Rendern).

**Spec:** `docs/superpowers/specs/2026-07-02-qr-checkin-design.md`

**QR-Payload-Format:** `smartground://checkin/<token>` — Fremd-QR-Codes ohne dieses Präfix werden ignoriert.

**Konventionen (aus den CLAUDE.md):** Backend-Domänenkommentare auf Deutsch, Tests auf Englisch. Commits: `[backend] …` / `[ui] …`. Backend: Controller implementieren NUR generierte Interfaces, keine Spring-Mapping-Annotationen. UI: kein direkter API-Call in Komponenten (Store → Service). Nach jedem grünen Test committen.

---

## File Structure

**Backend (smart-ground-backend):**

| Datei | Änderung | Verantwortung |
|---|---|---|
| `src/main/resources/static/openapi.yaml` | Modify | 4 neue Endpoints, 3 neue Schemas, `userId` auf `PlayerRef` + `PlayerResult` |
| `src/main/java/ch/jp/shooting/model/auth/User.java` | Modify | Feld `qrToken` |
| `src/main/java/ch/jp/shooting/repository/auth/UserRepository.java` | Modify | `findByQrToken` |
| `src/main/java/ch/jp/shooting/service/auth/UserService.java` | Modify | `getOrCreateQrToken`, `rotateQrToken`, `getUserByQrToken`; `createUser` setzt Token |
| `src/main/java/ch/jp/shooting/api/UserController.java` | Modify | 3 QR-Endpoints implementieren |
| `src/main/java/ch/jp/shooting/dto/play/PlayerRefRecord.java` | Modify | `userId`-Komponente (ans Ende angehängt) |
| `src/main/java/ch/jp/shooting/dto/play/PlayerResultRecord.java` | Modify | `userId`-Komponente (ans Ende angehängt) |
| `src/main/java/ch/jp/shooting/mapper/PlayMapper.java` | Modify | `userId` in beide Richtungen mappen |
| `src/main/java/ch/jp/shooting/service/PlayInstanceService.java` | Modify | `userId` durchreichen; `listMyPlayResults` |
| `src/main/java/ch/jp/shooting/repository/PlayInstanceRepository.java` | Modify | LIKE-Query auf User-UUID |
| `src/main/java/ch/jp/shooting/api/PlayInstanceController.java` | Modify | `listMyPlayResults` implementieren |
| `src/test/java/ch/jp/shooting/service/UserServiceQrTest.java` | Create | QR-Token-Logik |
| `src/test/java/ch/jp/shooting/api/UserControllerQrTest.java` | Create | QR-Endpoints (MockMvc) |
| `src/test/java/ch/jp/shooting/service/PlayInstanceUserIdTest.java` | Create | userId-Durchreichung |
| `src/test/java/ch/jp/shooting/service/PlayInstanceMyResultsTest.java` | Create | Ergebnis-Extraktion |

**Frontend (smart-ground-ui):**

| Datei | Änderung | Verantwortung |
|---|---|---|
| `package.json` | Modify | Deps `qr-scanner`, `qrcode` |
| `src/constants/qr.js` | Create | Payload-Präfix, build/parse |
| `src/services/userApi.js` | Modify | 4 neue API-Funktionen |
| `src/stores/profileStore.js` | Create | qrToken, myResults, resolve |
| `src/components/shooter/QrScanModal.vue` | Create | Kamera-Scan-Modal |
| `src/views/shooter/ShooterPlayPage.vue` | Modify | „+ Schütze per QR"-Button, Badge, userId-Durchreichung |
| `src/stores/playSessionStore.js` | Modify | `buildPlayerResults` trägt `userId` |
| `src/views/shooter/ShooterProfilView.vue` | Modify | Tabs „QR-Code" + „Ergebnisse" |
| `src/stores/__tests__/profileStore.test.js` | Create | Store-Tests |
| `src/components/__tests__/QrScanModal.test.js` | Create | Scan-Modal-Tests |
| `src/views/__tests__/ShooterPlayPageQr.test.js` | Create | Gruppen-Setup-Integration |
| `src/stores/__tests__/playSessionStore.qr.test.js` | Create | buildPlayerResults + userId |
| `src/views/shooter/__tests__/ShooterProfilView.qr.test.js` | Create | Profil-Tabs |

Alle Pfade unten sind relativ zum jeweiligen Teilprojekt. Backend-Befehle laufen in `smart-ground-backend/`, UI-Befehle in `smart-ground-ui/`.

---

### Task 1: OpenAPI-Vertrag erweitern

Contract-first: Der Vertrag kommt zuerst, damit alle Folge-Tasks gegen generierte Typen kompilieren.

**Files:**
- Modify: `smart-ground-backend/src/main/resources/static/openapi.yaml`

- [ ] **Step 1: Neue Pfade einfügen**

In `openapi.yaml` direkt **nach** dem kompletten `/api/users/me/password`-Block (endet bei Zeile ~143 mit `description: Old password incorrect`) einfügen:

```yaml
  /api/users/me/qr:
    get:
      summary: Get the calling user's QR check-in token
      operationId: getMyQrCode
      tags: [User]
      responses:
        '200':
          description: Current QR token (created on first access)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/QrCodeResponse'

  /api/users/me/qr/rotate:
    post:
      summary: Replace the calling user's QR token with a new one
      operationId: rotateMyQrCode
      tags: [User]
      responses:
        '200':
          description: New QR token; the previous token is invalid from now on
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/QrCodeResponse'

  /api/users/by-qr/{token}:
    get:
      summary: Resolve a scanned QR check-in token to its user
      operationId: resolveUserByQr
      tags: [User]
      parameters:
        - name: token
          in: path
          required: true
          schema:
            type: string
      responses:
        '200':
          description: Display info for the group setup
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/QrUserResponse'
        '404':
          description: Unknown or rotated token

  /api/users/me/play-results:
    get:
      summary: List completed play runs the calling user participated in
      operationId: listMyPlayResults
      tags: [PlayInstance]
      responses:
        '200':
          description: Completed runs with the user's own score, newest first
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/MyPlayResultEntry'
```

Wichtig: `tags: [PlayInstance]` beim letzten Endpoint ist Absicht — die Implementierung landet dadurch im generierten `PlayInstanceApi` (Controller hat bereits `PlayInstanceService` als Dependency).

- [ ] **Step 2: Neue Schemas einfügen**

Im `components/schemas`-Abschnitt, direkt vor dem Kommentar `# ───── Play Instances ─────` (Zeile ~3035), einfügen:

```yaml
    # ───── QR Check-in ────────────────────────────────────────────────────────

    QrCodeResponse:
      type: object
      required: [qrToken]
      properties:
        qrToken:
          type: string
          description: Static check-in token; encoded as smartground://checkin/<token>

    QrUserResponse:
      type: object
      required: [userId, displayName]
      properties:
        userId:
          type: string
          format: uuid
        displayName:
          type: string
        profilbildUrl:
          type: string

    MyPlayResultEntry:
      type: object
      description: One completed run with the calling user's own aggregated score.
      properties:
        resultId:
          type: string
          format: uuid
        templateName:
          type: string
        rangeName:
          type: string
        completedAt:
          type: string
          format: date-time
        totalPoints:
          type: integer
        maxPoints:
          type: integer
```

Hinweis: `profilbildUrl` und `rangeName` bewusst OHNE `nullable: true` — optionale Properties genügen; `nullable: true` würde `JsonNullable<T>` generieren (siehe Backend-CLAUDE.md-Troubleshooting), was wir hier nicht wollen.

- [ ] **Step 3: `userId` auf `PlayerRef` und `PlayerResult` ergänzen**

Im Schema `PlayerRef` (Zeile ~3037) nach der `type`-Property und vor `displayName` einfügen (NICHT in die `required`-Liste aufnehmen):

```yaml
        userId:
          type: string
          format: uuid
          description: >
            Account of the participant when checked in via QR code.
            Absent for anonymous placeholder players and guests.
```

Im Schema `PlayerResult` (Zeile ~3082) nach `playerId` einfügen:

```yaml
        userId:
          type: string
          format: uuid
          description: Account of the player when checked in via QR code; absent otherwise.
```

- [ ] **Step 4: Code generieren und Kompilierung prüfen**

Run: `./mvnw generate-sources`
Expected: BUILD SUCCESS; danach existieren `target/generated-sources/openapi/ch/jp/smartground/model/QrCodeResponse.java`, `QrUserResponse.java`, `MyPlayResultEntry.java`; `UserApi.java` enthält `getMyQrCode`, `rotateMyQrCode`, `resolveUserByQr`; `PlayInstanceApi.java` enthält `listMyPlayResults`.

Run: `./mvnw clean compile`
Expected: BUILD SUCCESS (die neuen Interface-Methoden haben Default-Implementierungen; Controller kompilieren noch unverändert).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/openapi.yaml
git commit -m "[backend] Add QR check-in endpoints and userId on PlayerRef/PlayerResult to OpenAPI contract"
```

---

### Task 2: User.qrToken — Entity, Repository, UserService

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/model/auth/User.java`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/repository/auth/UserRepository.java`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/service/auth/UserService.java`
- Test: `smart-ground-backend/src/test/java/ch/jp/shooting/service/UserServiceQrTest.java`

- [ ] **Step 1: Failing Test schreiben**

Neue Datei `src/test/java/ch/jp/shooting/service/UserServiceQrTest.java`:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.exception.UserNotFoundException;
import ch.jp.shooting.mapper.UserMapper;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.auth.RoleRepository;
import ch.jp.shooting.repository.auth.ScopedAccessRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import ch.jp.shooting.service.auth.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceQrTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock ScopedAccessRepository scopedAccessRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserMapper userMapper;

    @InjectMocks UserService userService;

    private User userWithoutToken() {
        return new User("anna@test.local", "Anna", "Muster");
    }

    @Test
    void getOrCreateQrToken_generatesAndPersistsTokenOnFirstAccess() {
        UUID id = UUID.randomUUID();
        User user = userWithoutToken();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String token = userService.getOrCreateQrToken(id);

        assertNotNull(token);
        assertEquals(token, user.getQrToken());
        verify(userRepository).save(user);
    }

    @Test
    void getOrCreateQrToken_returnsExistingTokenWithoutSaving() {
        UUID id = UUID.randomUUID();
        User user = userWithoutToken();
        user.setQrToken("existing-token");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        String token = userService.getOrCreateQrToken(id);

        assertEquals("existing-token", token);
        verify(userRepository, never()).save(any());
    }

    @Test
    void rotateQrToken_replacesTheToken() {
        UUID id = UUID.randomUUID();
        User user = userWithoutToken();
        user.setQrToken("old-token");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String newToken = userService.rotateQrToken(id);

        assertNotNull(newToken);
        assertNotEquals("old-token", newToken);
        assertEquals(newToken, user.getQrToken());
    }

    @Test
    void getUserByQrToken_unknownTokenThrowsNotFound() {
        when(userRepository.findByQrToken("nope")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getUserByQrToken("nope"));
    }
}
```

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen**

Run: `./mvnw test -Dtest=UserServiceQrTest`
Expected: COMPILATION ERROR — `getQrToken`, `setQrToken`, `getOrCreateQrToken`, `rotateQrToken`, `getUserByQrToken`, `findByQrToken` existieren nicht.

- [ ] **Step 3: Entity-Feld ergänzen**

In `User.java`, nach dem Block `// ==================== MEMBERSHIP & CREDENTIALS ====================` (nach `schiessLizenzVerfallsdatum`, Zeile ~97) einfügen:

```java
    // ==================== QR CHECK-IN ====================
    // Statischer Check-in-Token für das Gruppen-Setup am Stand.
    // Null bei Alt-Usern bis zum ersten Abruf (Lazy-Backfill in UserService).
    @Nullable
    @Column(name = "qr_token", unique = true)
    private String qrToken;
```

Und bei den Gettern/Settern der Klasse (im Accessor-Bereich, gleicher Stil wie die Nachbarn):

```java
    @Nullable
    public String getQrToken() { return qrToken; }
    public void setQrToken(@Nullable String qrToken) { this.qrToken = qrToken; }
```

- [ ] **Step 4: Repository-Methode ergänzen**

In `UserRepository.java` neben den anderen Findern:

```java
    Optional<User> findByQrToken(String qrToken);
```

- [ ] **Step 5: UserService-Methoden ergänzen**

In `UserService.java` am Ende des Abschnitts `// ==================== USER CRUD ====================` (nach `getUserByEmail`) einfügen:

```java
    // ==================== QR CHECK-IN ====================

    /** Liefert den QR-Token des Users; erzeugt ihn beim ersten Zugriff (Backfill für Alt-User). */
    public String getOrCreateQrToken(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getQrToken() == null) {
            user.setQrToken(UUID.randomUUID().toString());
            userRepository.save(user);
        }
        return Objects.requireNonNull(user.getQrToken());
    }

    /** Erzeugt einen neuen QR-Token; der alte wird damit sofort ungültig. */
    public String rotateQrToken(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        user.setQrToken(UUID.randomUUID().toString());
        userRepository.save(user);
        return Objects.requireNonNull(user.getQrToken());
    }

    /** Löst einen gescannten QR-Token zum zugehörigen User auf. */
    public UserDTO getUserByQrToken(String qrToken) {
        User user = userRepository.findByQrToken(qrToken)
            .orElseThrow(() -> new UserNotFoundException("Unbekannter QR-Token"));
        return userMapper.toDto(user);
    }
```

(`java.util.Objects` ist über den bestehenden `import java.util.*;` verfügbar.)

Zusätzlich in `createUser` (nach `user.setStatus(User.UserStatus.ACTIVE);`, Zeile ~88) einfügen:

```java
        user.setQrToken(UUID.randomUUID().toString()); // Neue User bekommen ihren Check-in-Token sofort
```

- [ ] **Step 6: Test laufen lassen — muss grün sein**

Run: `./mvnw test -Dtest=UserServiceQrTest`
Expected: 4 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/ch/jp/shooting/model/auth/User.java src/main/java/ch/jp/shooting/repository/auth/UserRepository.java src/main/java/ch/jp/shooting/service/auth/UserService.java src/test/java/ch/jp/shooting/service/UserServiceQrTest.java
git commit -m "[backend] Add User.qrToken with lazy backfill, rotation and resolve lookup"
```

---

### Task 3: QR-Endpoints im UserController

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/api/UserController.java`
- Test: `smart-ground-backend/src/test/java/ch/jp/shooting/api/UserControllerQrTest.java`

- [ ] **Step 1: Failing Test schreiben**

Neue Datei `src/test/java/ch/jp/shooting/api/UserControllerQrTest.java` (spiegelt exakt den TestConfig-Aufbau des bestehenden `UserControllerTest.java`):

```java
package ch.jp.shooting.api;

import ch.jp.shooting.config.GlobalExceptionHandler;
import ch.jp.shooting.dto.UserDTO;
import ch.jp.shooting.exception.UserNotFoundException;
import ch.jp.shooting.service.auth.AuthorizationService;
import ch.jp.shooting.service.auth.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = UserControllerQrTest.TestConfig.class)
@WebAppConfiguration
class UserControllerQrTest {

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    @EnableWebMvc
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        UserService userService() { return mock(UserService.class); }

        @Bean
        AuthorizationService authorizationService() { return mock(AuthorizationService.class); }

        @Bean
        UserController userController(UserService userService, AuthorizationService authorizationService) {
            return new UserController(userService, authorizationService);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                .httpBasic(org.springframework.security.config.Customizer.withDefaults())
                .authorizeHttpRequests(authz -> authz.anyRequest().authenticated());
            return http.build();
        }
    }

    @Autowired WebApplicationContext wac;
    @Autowired UserService userService;

    MockMvc mockMvc;

    UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
        UserDTO me = new UserDTO();
        me.setId(userId);
        when(userService.getUserByEmail("anna@test.local")).thenReturn(me);
    }

    @Test
    @WithMockUser(username = "anna@test.local")
    void getMyQrCode_returnsToken() throws Exception {
        when(userService.getOrCreateQrToken(userId)).thenReturn("tok-1");

        mockMvc.perform(get("/api/users/me/qr"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.qrToken").value("tok-1"));
    }

    @Test
    @WithMockUser(username = "anna@test.local")
    void rotateMyQrCode_returnsNewToken() throws Exception {
        when(userService.rotateQrToken(userId)).thenReturn("tok-2");

        mockMvc.perform(post("/api/users/me/qr/rotate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.qrToken").value("tok-2"));
    }

    @Test
    @WithMockUser(username = "anna@test.local")
    void resolveUserByQr_returnsDisplayInfo() throws Exception {
        UUID resolvedId = UUID.randomUUID();
        UserDTO resolved = new UserDTO();
        resolved.setId(resolvedId);
        resolved.setVorname("Beat");
        resolved.setNachname("Beispiel");
        when(userService.getUserByQrToken("tok-3")).thenReturn(resolved);

        mockMvc.perform(get("/api/users/by-qr/tok-3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(resolvedId.toString()))
            .andExpect(jsonPath("$.displayName").value("Beat Beispiel"));
    }

    @Test
    @WithMockUser(username = "anna@test.local")
    void resolveUserByQr_unknownTokenIs404() throws Exception {
        when(userService.getUserByQrToken("gone")).thenThrow(new UserNotFoundException("Unbekannter QR-Token"));

        mockMvc.perform(get("/api/users/by-qr/gone"))
            .andExpect(status().isNotFound());
    }
}
```

Hinweis: Falls `UserDTO.getFullName()` nicht aus `vorname`/`nachname` abgeleitet ist, sondern ein eigenes Feld — im Test stattdessen `resolved.setFullName("Beat Beispiel")` setzen (kurz in `UserDTO.java` nachschauen).

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen**

Run: `./mvnw test -Dtest=UserControllerQrTest`
Expected: FAIL — die Requests laufen auf die Default-Implementierungen des generierten Interface (501 Not Implemented) statt auf echte Controller-Methoden.

- [ ] **Step 3: Controller-Methoden implementieren**

In `UserController.java` (nach `changePassword`, Zeile ~130) einfügen. Benötigte neue Imports: `ch.jp.smartground.model.QrCodeResponse`, `ch.jp.smartground.model.QrUserResponse`.

```java
    // ── QR-Checkin ─────────────────────────────────────────────────────────────

    @Override
    public ResponseEntity<QrCodeResponse> getMyQrCode() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDTO me = userService.getUserByEmail(auth.getName());
        return ResponseEntity.ok(new QrCodeResponse().qrToken(userService.getOrCreateQrToken(me.getId())));
    }

    @Override
    public ResponseEntity<QrCodeResponse> rotateMyQrCode() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDTO me = userService.getUserByEmail(auth.getName());
        return ResponseEntity.ok(new QrCodeResponse().qrToken(userService.rotateQrToken(me.getId())));
    }

    @Override
    public ResponseEntity<QrUserResponse> resolveUserByQr(String token) {
        UserDTO user = userService.getUserByQrToken(token);
        return ResponseEntity.ok(new QrUserResponse()
                .userId(user.getId())
                .displayName(user.getFullName())
                .profilbildUrl(user.getProfilbildUrl()));
    }
```

- [ ] **Step 4: Test laufen lassen — muss grün sein**

Run: `./mvnw test -Dtest=UserControllerQrTest`
Expected: 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ch/jp/shooting/api/UserController.java src/test/java/ch/jp/shooting/api/UserControllerQrTest.java
git commit -m "[backend] Implement QR token endpoints (get/rotate/resolve) in UserController"
```

---

### Task 4: userId in PlayerRefRecord / PlayerResultRecord durchreichen

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/dto/play/PlayerRefRecord.java`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/dto/play/PlayerResultRecord.java`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/mapper/PlayMapper.java`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/service/PlayInstanceService.java`
- Test: `smart-ground-backend/src/test/java/ch/jp/shooting/service/PlayInstanceUserIdTest.java`

- [ ] **Step 1: Failing Test schreiben**

Neue Datei `src/test/java/ch/jp/shooting/service/PlayInstanceUserIdTest.java`:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.mapper.PlayMapper;
import ch.jp.shooting.model.PlayInstance;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.PasseRepository;
import ch.jp.shooting.repository.PlayInstanceRepository;
import ch.jp.smartground.model.CompleteBlockRequest;
import ch.jp.smartground.model.PlayerRef;
import ch.jp.smartground.model.PlayerResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayInstanceUserIdTest {

    @Mock PlayInstanceRepository playInstanceRepository;
    @Mock PasseRepository passeRepository;
    @Mock PasseService passeService;
    @Mock SecurityHelper securityHelper;
    @Mock PositionLabelResolver positionLabelResolver;

    @InjectMocks PlayInstanceService service;

    @Test
    void startPasseInstance_persistsUserIdInPlayersJson() {
        var passeId = UUID.randomUUID();
        var passe = new ch.jp.shooting.model.Passe();
        passe.setId(passeId);
        passe.setName("Passe X");
        var liveSerien = List.of(new ch.jp.shooting.dto.play.EmbeddedSerieRecord(
            UUID.randomUUID(), "Serie 1", null, null, List.of(), false));

        when(passeRepository.findById(passeId)).thenReturn(Optional.of(passe));
        when(passeService.resolveLiveSerien(passe)).thenReturn(liveSerien);
        when(securityHelper.currentUser()).thenReturn(mock(User.class));
        when(playInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UUID accountId = UUID.randomUUID();
        var request = new ch.jp.smartground.model.StartPasseInstanceRequest()
            .passeId(passeId)
            .players(List.of(
                new PlayerRef().id("gp-1").type(PlayerRef.TypeEnum.USER)
                    .displayName("Anna").userId(accountId),
                new PlayerRef().id("gp-2").type(PlayerRef.TypeEnum.GUEST)
                    .displayName("Schütze 2")));

        service.startPasseInstance(request);

        ArgumentCaptor<PlayInstance> captor = ArgumentCaptor.forClass(PlayInstance.class);
        verify(playInstanceRepository).save(captor.capture());
        var savedPlayers = PlayMapper.parsePlayerRefs(captor.getValue().getPlayersJson());
        assertEquals(2, savedPlayers.size());
        assertEquals(accountId, savedPlayers.get(0).userId());
        assertNull(savedPlayers.get(1).userId());
    }

    @Test
    void completeBlock_persistsUserIdInBlockResult() {
        var instanceId = UUID.randomUUID();
        var blockId = UUID.randomUUID();
        var instance = new PlayInstance();
        instance.setInstanceId(instanceId);
        instance.setType("passe");
        instance.setTemplateId(UUID.randomUUID());
        instance.setTemplateName("T");
        instance.setStatus("active");
        instance.setPlayersJson("[]");
        instance.setStateJson("[{\"blockId\":\"" + blockId + "\",\"serieId\":\"" + UUID.randomUUID()
            + "\",\"serieAlias\":\"S\",\"steps\":[],\"status\":\"in_progress\"}]");

        when(playInstanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));
        when(playInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UUID accountId = UUID.randomUUID();
        var request = new CompleteBlockRequest().playerResults(List.of(
            new PlayerResult().playerId("gp-1").userId(accountId)
                .displayName("Anna").totalPoints(7).maxPoints(9)));

        service.completeBlock(instanceId, blockId, request);

        ArgumentCaptor<PlayInstance> captor = ArgumentCaptor.forClass(PlayInstance.class);
        verify(playInstanceRepository).save(captor.capture());
        var blocks = PlayMapper.parseBlocks(captor.getValue().getStateJson());
        var results = blocks.get(0).result().playerResults();
        assertEquals(1, results.size());
        assertEquals(accountId, results.get(0).userId());
        assertEquals(7, results.get(0).totalPoints());
    }
}
```

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen**

Run: `./mvnw test -Dtest=PlayInstanceUserIdTest`
Expected: COMPILATION ERROR — `PlayerRefRecord.userId()` / `PlayerResultRecord.userId()` existieren nicht (die generierten Modelle `PlayerRef.userId(...)` / `PlayerResult.userId(...)` existieren seit Task 1).

- [ ] **Step 3: Records erweitern**

`PlayerRefRecord.java` komplett ersetzen:

```java
package ch.jp.shooting.dto.play;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

// userId: Account-Verknüpfung bei QR-Checkin; null für anonyme Platzhalter und Gäste
public record PlayerRefRecord(String id, String type, String displayName, @Nullable UUID userId) {}
```

`PlayerResultRecord.java` komplett ersetzen:

```java
package ch.jp.shooting.dto.play;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

// userId: Account-Verknüpfung bei QR-Checkin; null für anonyme Platzhalter und Gäste
public record PlayerResultRecord(
    String playerId,
    String displayName,
    int totalPoints,
    int maxPoints,
    List<StepStateRecord> stepStates,
    @Nullable UUID userId
) {}
```

- [ ] **Step 4: Alle Konstruktor-Aufrufe finden und anpassen**

Run (im Backend-Root): `grep -rn "new PlayerRefRecord\|new PlayerResultRecord" src/`

Bekannte Stellen (weitere Treffer nach demselben Muster anpassen — bei Wettkampf-Pfaden `null` übergeben, Wettkampf bleibt laut Spec unberührt):

`PlayInstanceService.buildAndSaveInstance` (Zeile ~100):

```java
        var playerRecords = players.stream()
            .map(p -> new PlayerRefRecord(p.getId(), p.getType().getValue(), p.getDisplayName(), p.getUserId()))
            .toList();
```

`PlayInstanceService.completeBlock` (Zeile ~180) — dem `PlayerResultRecord`-Konstruktor am Ende `pr.getUserId()` mitgeben:

```java
        var playerResults = request.getPlayerResults().stream()
            .map(pr -> new PlayerResultRecord(
                pr.getPlayerId() != null ? pr.getPlayerId() : "",
                pr.getDisplayName() != null ? pr.getDisplayName() : "",
                pr.getTotalPoints() != null ? pr.getTotalPoints() : 0,
                pr.getMaxPoints() != null ? pr.getMaxPoints() : 0,
                pr.getStepStates().stream()
                    .map(ss -> new ch.jp.shooting.dto.play.StepStateRecord(
                        ss.getPlayerId() != null ? ss.getPlayerId() : "",
                        ss.getSerieIndex() != null ? ss.getSerieIndex() : 0,
                        ss.getStepIndex() != null ? ss.getStepIndex() : 0,
                        ss.getState() != null ? ss.getState().getValue() : "miss",
                        ss.getPointValue() != null ? ss.getPointValue() : 0,
                        ss.getNoBirds() != null ? ss.getNoBirds() : 0,
                        ss.getPointsEarned() != null ? ss.getPointsEarned() : 0
                    ))
                    .toList(),
                pr.getUserId()
            ))
            .toList();
```

- [ ] **Step 5: PlayMapper in beide Richtungen anpassen**

In `PlayMapper.java`:

`toPlayerRef` (Zeile ~180):

```java
    private static PlayerRef toPlayerRef(PlayerRefRecord r) {
        return new PlayerRef()
            .id(r.id())
            .type(PlayerRef.TypeEnum.fromValue(r.type()))
            .displayName(r.displayName())
            .userId(r.userId());
    }
```

`toPlayerResult` (Zeile ~212):

```java
    private static PlayerResult toPlayerResult(PlayerResultRecord r) {
        return new PlayerResult()
            .playerId(r.playerId())
            .userId(r.userId())
            .displayName(r.displayName())
            .totalPoints(r.totalPoints())
            .maxPoints(r.maxPoints())
            .stepStates(r.stepStates().stream()
                .map(PlayMapper::toGeneratedStepStateRecord)
                .toList());
    }
```

In `PlayInstanceService` gibt es zwei weitere Stellen, die `PlayerRef` aus Records bauen (`toPlayResultSummary` Zeile ~292 und `toPlayResultResponse` Zeile ~316) — dort jeweils `.userId(p.userId())` an die `new PlayerRef()`-Kette anhängen.

- [ ] **Step 6: Alle Tests laufen lassen**

Run: `./mvnw test -Dtest=PlayInstanceUserIdTest`
Expected: 2 tests PASS.

Run: `./mvnw test`
Expected: BUILD SUCCESS — bestehende Tests (v.a. `PlayInstanceServiceTest`, `CompetitionProgressServiceTest`, `TiebreakerServiceTest`) kompilieren und laufen weiter. Falls Kompilierfehler wegen der Record-Arity: fehlende Call-Sites aus Step 4 nachziehen (Wettkampf-Pfade: `null`).

- [ ] **Step 7: Commit**

```bash
git add -A src/
git commit -m "[backend] Carry optional userId through PlayerRefRecord and PlayerResultRecord"
```

---

### Task 5: „Meine Ergebnisse" — Repository-Query, Service, Endpoint

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/repository/PlayInstanceRepository.java`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/service/PlayInstanceService.java`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/api/PlayInstanceController.java`
- Test: `smart-ground-backend/src/test/java/ch/jp/shooting/service/PlayInstanceMyResultsTest.java`

- [ ] **Step 1: Failing Test schreiben**

Neue Datei `src/test/java/ch/jp/shooting/service/PlayInstanceMyResultsTest.java`:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.model.PlayInstance;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.PasseRepository;
import ch.jp.shooting.repository.PlayInstanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayInstanceMyResultsTest {

    @Mock PlayInstanceRepository playInstanceRepository;
    @Mock PasseRepository passeRepository;
    @Mock PasseService passeService;
    @Mock SecurityHelper securityHelper;
    @Mock PositionLabelResolver positionLabelResolver;

    @InjectMocks PlayInstanceService service;

    private final UUID uid = UUID.randomUUID();

    /** Completed instance: two done blocks, our user scored in both; a second player is noise. */
    private PlayInstance completedInstance() {
        var inst = new PlayInstance();
        inst.setInstanceId(UUID.randomUUID());
        inst.setType("passe");
        inst.setTemplateId(UUID.randomUUID());
        inst.setTemplateName("Jagd 1");
        inst.setStatus("completed");
        inst.setPlayersJson("[]");
        inst.setCompletedAt(Instant.parse("2026-07-01T10:00:00Z"));
        String block = "{\"blockId\":\"%s\",\"serieId\":\"%s\",\"serieAlias\":\"S\",\"rangeName\":\"Trapstand\","
            + "\"steps\":[],\"status\":\"done\",\"result\":{\"playerResults\":["
            + "{\"playerId\":\"gp-1\",\"displayName\":\"Anna\",\"totalPoints\":%d,\"maxPoints\":%d,\"stepStates\":[],\"userId\":\"" + uid + "\"},"
            + "{\"playerId\":\"gp-2\",\"displayName\":\"Gast\",\"totalPoints\":1,\"maxPoints\":9,\"stepStates\":[]}"
            + "]}}";
        inst.setStateJson("[" + block.formatted(UUID.randomUUID(), UUID.randomUUID(), 7, 9)
            + "," + block.formatted(UUID.randomUUID(), UUID.randomUUID(), 5, 9) + "]");
        return inst;
    }

    @Test
    void listMyPlayResults_aggregatesOwnScoreAcrossBlocks() {
        var me = mock(User.class);
        when(me.getId()).thenReturn(uid);
        when(securityHelper.currentUser()).thenReturn(me);
        when(playInstanceRepository.findCompletedByParticipantUserId(uid.toString()))
            .thenReturn(List.of(completedInstance()));

        var results = service.listMyPlayResults();

        assertEquals(1, results.size());
        var entry = results.get(0);
        assertEquals("Jagd 1", entry.getTemplateName());
        assertEquals("Trapstand", entry.getRangeName());
        assertEquals(12, entry.getTotalPoints()); // 7 + 5
        assertEquals(18, entry.getMaxPoints());   // 9 + 9
    }

    @Test
    void listMyPlayResults_skipsLikeMatchesWithoutRealParticipation() {
        // LIKE kann falsch-positiv matchen (z.B. UUID an anderer Stelle im JSON) —
        // Instanzen ohne echtes PlayerResult mit unserer userId werden verworfen.
        var me = mock(User.class);
        when(me.getId()).thenReturn(uid);
        when(securityHelper.currentUser()).thenReturn(me);

        var noise = completedInstance();
        noise.setStateJson(noise.getStateJson().replace(uid.toString(), UUID.randomUUID().toString()));
        when(playInstanceRepository.findCompletedByParticipantUserId(uid.toString()))
            .thenReturn(List.of(noise));

        assertTrue(service.listMyPlayResults().isEmpty());
    }
}
```

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen**

Run: `./mvnw test -Dtest=PlayInstanceMyResultsTest`
Expected: COMPILATION ERROR — `findCompletedByParticipantUserId` und `listMyPlayResults` existieren nicht.

- [ ] **Step 3: Repository-Query ergänzen**

In `PlayInstanceRepository.java` (bestehende Imports um `org.springframework.data.jpa.repository.Query` / `org.springframework.data.repository.query.Param` ergänzen, falls nicht vorhanden):

```java
    // Vorfilter per LIKE auf die User-UUID im JSON (H2- und PostgreSQL-kompatibel);
    // die echte Teilnahme-Prüfung macht der Service beim Parsen der Block-Resultate.
    @Query("select p from PlayInstance p where p.status = 'completed' "
        + "and (p.playersJson like concat('%', :userId, '%') or p.stateJson like concat('%', :userId, '%')) "
        + "order by p.completedAt desc")
    List<PlayInstance> findCompletedByParticipantUserId(@Param("userId") String userId);
```

- [ ] **Step 4: Service-Methode implementieren**

In `PlayInstanceService.java`, im Abschnitt `// ── Ergebnisse ──` (nach `getPlayResult`), einfügen. Neuer Import: `ch.jp.smartground.model.MyPlayResultEntry`, `java.util.Objects`.

```java
    /** Abgeschlossene Läufe, an denen der aktuelle User (per QR-Checkin) teilgenommen hat. */
    public List<MyPlayResultEntry> listMyPlayResults() {
        var user = securityHelper.currentUser();
        var uid = user.getId();
        return playInstanceRepository.findCompletedByParticipantUserId(uid.toString()).stream()
            .map(inst -> toMyPlayResultEntry(inst, uid))
            .filter(Objects::nonNull)
            .toList();
    }

    /** Summiert die eigenen Punkte über alle Blöcke; null wenn der User nirgends wirklich teilnahm. */
    @Nullable
    private MyPlayResultEntry toMyPlayResultEntry(PlayInstance inst, UUID uid) {
        int total = 0;
        int max = 0;
        boolean teilgenommen = false;
        String rangeName = null;
        for (var block : PlayMapper.parseBlocks(inst.getStateJson())) {
            if (rangeName == null && block.rangeName() != null) rangeName = block.rangeName();
            if (block.result() == null) continue;
            for (var pr : block.result().playerResults()) {
                if (uid.equals(pr.userId())) {
                    total += pr.totalPoints();
                    max += pr.maxPoints();
                    teilgenommen = true;
                }
            }
        }
        if (!teilgenommen) return null; // falsch-positiver LIKE-Treffer
        var entry = new MyPlayResultEntry()
            .resultId(inst.getInstanceId())
            .templateName(inst.getTemplateName())
            .rangeName(rangeName)
            .totalPoints(total)
            .maxPoints(max);
        if (inst.getCompletedAt() != null) {
            entry.completedAt(OffsetDateTime.ofInstant(inst.getCompletedAt(), ZoneOffset.UTC));
        }
        return entry;
    }
```

- [ ] **Step 5: Controller-Methode implementieren**

In `PlayInstanceController.java` (implementiert `PlayInstanceApi`; die Methode kam durch Task 1 ins Interface). Neuer Import: `ch.jp.smartground.model.MyPlayResultEntry`, `java.util.List`.

```java
    @Override
    public ResponseEntity<List<MyPlayResultEntry>> listMyPlayResults() {
        return ResponseEntity.ok(playInstanceService.listMyPlayResults());
    }
```

- [ ] **Step 6: Tests laufen lassen**

Run: `./mvnw test -Dtest=PlayInstanceMyResultsTest`
Expected: 2 tests PASS.

Run: `./mvnw test`
Expected: BUILD SUCCESS, alle Tests grün.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/ch/jp/shooting/repository/PlayInstanceRepository.java src/main/java/ch/jp/shooting/service/PlayInstanceService.java src/main/java/ch/jp/shooting/api/PlayInstanceController.java src/test/java/ch/jp/shooting/service/PlayInstanceMyResultsTest.java
git commit -m "[backend] Add GET /api/users/me/play-results (own scores from completed runs)"
```

---

### Task 6: Backend-Abschluss — Gesamtlauf + Doku

**Files:**
- Modify: `smart-ground-backend/CLAUDE.md`

- [ ] **Step 1: Gesamtverifikation**

Run: `./mvnw clean test`
Expected: BUILD SUCCESS, keine Warnings, alle Tests grün.

- [ ] **Step 2: CLAUDE.md aktualisieren**

In `smart-ground-backend/CLAUDE.md`:

1. Im Abschnitt **Auth / User tables** die Zeile `guests …` unverändert lassen und in der `users`-Zeile `qr_token` ergänzen (nach `schiess_lizenz_verfallsdatum`).
2. Unter **✅ Implemented** einen Eintrag ergänzen:

```markdown
- **QR-Checkin (Training)** (implemented 2026-07-02) — jeder `User` trägt einen statischen, rotierbaren `qrToken` (lazy-Backfill beim ersten Abruf; Payload-Format `smartground://checkin/<token>`). Endpoints: `GET /api/users/me/qr`, `POST /api/users/me/qr/rotate`, `GET /api/users/by-qr/{token}` (Resolve für das Stand-Tablet, jeder eingeloggte User), `GET /api/users/me/play-results` (eigene Scores aus abgeschlossenen `play_instances`; LIKE-Vorfilter auf die UUID im JSON + echte Teilnahme-Prüfung beim Parsen). `PlayerRefRecord`/`PlayerResultRecord` tragen eine optionale `userId`; Wettkampf-Pfade übergeben `null`. Entscheidung: eigener Token statt User-UUID im QR, damit ein abfotografierter Code per Rotation entwertet werden kann.
```

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "[backend] Document QR check-in feature in CLAUDE.md"
```

---

### Task 7: UI-Grundlagen — Dependencies, QR-Konstanten, userApi, profileStore

Ab hier alle Befehle in `smart-ground-ui/`.

**Files:**
- Modify: `smart-ground-ui/package.json` (via npm install)
- Create: `smart-ground-ui/src/constants/qr.js`
- Modify: `smart-ground-ui/src/services/userApi.js`
- Create: `smart-ground-ui/src/stores/profileStore.js`
- Test: `smart-ground-ui/src/stores/__tests__/profileStore.test.js`

- [ ] **Step 1: Dependencies installieren**

Run: `npm install qr-scanner qrcode`
Expected: beide Pakete in `package.json` unter `dependencies` (begründet: `qr-scanner` = Kamera-Scan im Gruppen-Setup, `qrcode` = Rendern des eigenen Codes im Profil; beide klein und ohne weitere Runtime-Abhängigkeiten).

- [ ] **Step 2: Failing Store-Test schreiben**

Neue Datei `src/stores/__tests__/profileStore.test.js`:

```js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useProfileStore } from '@/stores/profileStore.js'
import * as userApi from '@/services/userApi.js'

vi.mock('@/services/userApi.js', () => ({
  fetchMyQrToken: vi.fn(),
  rotateMyQrToken: vi.fn(),
  resolveUserByQr: vi.fn(),
  fetchMyPlayResults: vi.fn(),
}))

describe('profileStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loadQrToken stores the token', async () => {
    userApi.fetchMyQrToken.mockResolvedValue({ qrToken: 'tok-1' })
    const store = useProfileStore()

    await store.loadQrToken()

    expect(store.qrToken).toBe('tok-1')
    expect(store.error).toBeNull()
  })

  it('rotateQrToken replaces the token', async () => {
    userApi.rotateMyQrToken.mockResolvedValue({ qrToken: 'tok-2' })
    const store = useProfileStore()
    store.qrToken = 'tok-1'

    await store.rotateQrToken()

    expect(store.qrToken).toBe('tok-2')
  })

  it('loadQrToken captures errors', async () => {
    userApi.fetchMyQrToken.mockRejectedValue(new Error('boom'))
    const store = useProfileStore()

    await store.loadQrToken()

    expect(store.error).toBe('boom')
    expect(store.qrToken).toBeNull()
  })

  it('loadMyResults stores the result list', async () => {
    userApi.fetchMyPlayResults.mockResolvedValue([{ resultId: 'r1', totalPoints: 12 }])
    const store = useProfileStore()

    await store.loadMyResults()

    expect(store.myResults).toHaveLength(1)
  })

  it('resolveCheckinToken passes through the resolved user and rethrows errors', async () => {
    userApi.resolveUserByQr.mockResolvedValue({ userId: 'u1', displayName: 'Anna' })
    const store = useProfileStore()

    await expect(store.resolveCheckinToken('tok')).resolves.toEqual({ userId: 'u1', displayName: 'Anna' })

    const notFound = Object.assign(new Error('nope'), { status: 404 })
    userApi.resolveUserByQr.mockRejectedValue(notFound)
    await expect(store.resolveCheckinToken('bad')).rejects.toMatchObject({ status: 404 })
  })
})
```

- [ ] **Step 3: Test laufen lassen — muss fehlschlagen**

Run: `npm run test src/stores/__tests__/profileStore.test.js`
Expected: FAIL — `@/stores/profileStore.js` existiert nicht.

- [ ] **Step 4: QR-Konstanten anlegen**

Neue Datei `src/constants/qr.js`:

```js
// QR check-in payload format shared by profile display and scanner
export const QR_CHECKIN_PREFIX = 'smartground://checkin/'

export function buildCheckinPayload(token) {
  return `${QR_CHECKIN_PREFIX}${token}`
}

// Returns the token, or null when the payload is not a Smart Ground check-in code
export function parseCheckinPayload(text) {
  if (typeof text !== 'string' || !text.startsWith(QR_CHECKIN_PREFIX)) return null
  const token = text.slice(QR_CHECKIN_PREFIX.length).trim()
  return token.length > 0 ? token : null
}
```

- [ ] **Step 5: userApi erweitern**

Ans Ende von `src/services/userApi.js` anhängen:

```js
export async function fetchMyQrToken() {
  return apiFetch('/users/me/qr')
}

export async function rotateMyQrToken() {
  return apiFetch('/users/me/qr/rotate', { method: 'POST' })
}

export async function resolveUserByQr(token) {
  return apiFetch(`/users/by-qr/${encodeURIComponent(token)}`)
}

export async function fetchMyPlayResults() {
  return apiFetch('/users/me/play-results')
}
```

- [ ] **Step 6: profileStore anlegen**

Neue Datei `src/stores/profileStore.js`:

```js
import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as userApi from '@/services/userApi.js'

// Own QR check-in token and personal play results (profile page)
export const useProfileStore = defineStore('profile', () => {
  const qrToken = ref(null)
  const myResults = ref([])
  const isLoading = ref(false)
  const error = ref(null)

  const loadQrToken = async () => {
    isLoading.value = true
    error.value = null
    try {
      qrToken.value = (await userApi.fetchMyQrToken()).qrToken
    } catch (e) {
      error.value = e.message
    } finally {
      isLoading.value = false
    }
  }

  const rotateQrToken = async () => {
    isLoading.value = true
    error.value = null
    try {
      qrToken.value = (await userApi.rotateMyQrToken()).qrToken
    } catch (e) {
      error.value = e.message
    } finally {
      isLoading.value = false
    }
  }

  const loadMyResults = async () => {
    isLoading.value = true
    error.value = null
    try {
      myResults.value = await userApi.fetchMyPlayResults()
    } catch (e) {
      error.value = e.message
    } finally {
      isLoading.value = false
    }
  }

  // Resolve a scanned token. Errors (incl. e.status === 404) propagate to the
  // caller so the scan modal can show inline feedback and keep scanning.
  const resolveCheckinToken = async (token) => userApi.resolveUserByQr(token)

  return { qrToken, myResults, isLoading, error, loadQrToken, rotateQrToken, loadMyResults, resolveCheckinToken }
})
```

- [ ] **Step 7: Test laufen lassen — muss grün sein**

Run: `npm run test src/stores/__tests__/profileStore.test.js`
Expected: 5 tests PASS.

- [ ] **Step 8: Commit**

```bash
git add package.json package-lock.json src/constants/qr.js src/services/userApi.js src/stores/profileStore.js src/stores/__tests__/profileStore.test.js
git commit -m "[ui] Add QR constants, user QR/play-results API functions and profileStore"
```

---

### Task 8: QrScanModal-Komponente

**Files:**
- Create: `smart-ground-ui/src/components/shooter/QrScanModal.vue`
- Test: `smart-ground-ui/src/components/__tests__/QrScanModal.test.js`

- [ ] **Step 1: Failing Test schreiben**

Neue Datei `src/components/__tests__/QrScanModal.test.js`:

```js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import QrScanModal from '../shooter/QrScanModal.vue'
import * as userApi from '@/services/userApi.js'
import { buildCheckinPayload } from '@/constants/qr.js'

vi.mock('qr-scanner', () => {
  class FakeScanner {
    constructor(video, onDecode) {
      this.onDecode = onDecode
      FakeScanner.instances.push(this)
    }
    start = vi.fn().mockResolvedValue(undefined)
    destroy = vi.fn()
  }
  FakeScanner.instances = []
  return { default: FakeScanner }
})

vi.mock('@/services/userApi.js', () => ({
  fetchMyQrToken: vi.fn(),
  rotateMyQrToken: vi.fn(),
  resolveUserByQr: vi.fn(),
  fetchMyPlayResults: vi.fn(),
}))

import QrScanner from 'qr-scanner'

const mountModal = () => mount(QrScanModal)

const emitScan = async (wrapper, payload) => {
  const scanner = QrScanner.instances.at(-1)
  await scanner.onDecode({ data: payload })
  await flushPromises()
}

describe('QrScanModal', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    QrScanner.instances.length = 0
  })

  it('starts the camera scanner on mount and destroys it on unmount', async () => {
    const wrapper = mountModal()
    await flushPromises()
    const scanner = QrScanner.instances.at(-1)
    expect(scanner.start).toHaveBeenCalled()

    wrapper.unmount()
    expect(scanner.destroy).toHaveBeenCalled()
  })

  it('ignores QR codes without the check-in prefix', async () => {
    const wrapper = mountModal()
    await flushPromises()

    await emitScan(wrapper, 'https://example.com/some-random-qr')

    expect(userApi.resolveUserByQr).not.toHaveBeenCalled()
    expect(wrapper.emitted('resolved')).toBeUndefined()
  })

  it('resolves a valid payload and emits the user', async () => {
    userApi.resolveUserByQr.mockResolvedValue({ userId: 'u1', displayName: 'Anna Muster' })
    const wrapper = mountModal()
    await flushPromises()

    await emitScan(wrapper, buildCheckinPayload('tok-1'))

    expect(userApi.resolveUserByQr).toHaveBeenCalledWith('tok-1')
    expect(wrapper.emitted('resolved')[0][0]).toEqual({ userId: 'u1', displayName: 'Anna Muster' })
  })

  it('shows "Code ungültig" on 404 and keeps scanning', async () => {
    userApi.resolveUserByQr.mockRejectedValue(Object.assign(new Error('nope'), { status: 404 }))
    const wrapper = mountModal()
    await flushPromises()

    await emitScan(wrapper, buildCheckinPayload('rotated'))

    expect(wrapper.text()).toContain('Code ungültig')
    expect(wrapper.emitted('resolved')).toBeUndefined()
  })

  it('does not re-resolve the same token twice in a row', async () => {
    userApi.resolveUserByQr.mockResolvedValue({ userId: 'u1', displayName: 'Anna' })
    const wrapper = mountModal()
    await flushPromises()

    await emitScan(wrapper, buildCheckinPayload('tok-1'))
    await emitScan(wrapper, buildCheckinPayload('tok-1'))

    expect(userApi.resolveUserByQr).toHaveBeenCalledTimes(1)
  })

  it('emits close when cancel is clicked', async () => {
    const wrapper = mountModal()
    await flushPromises()

    await wrapper.get('.qr-scan-cancel').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
```

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen**

Run: `npm run test src/components/__tests__/QrScanModal.test.js`
Expected: FAIL — Komponente existiert nicht.

- [ ] **Step 3: Komponente implementieren**

Neue Datei `src/components/shooter/QrScanModal.vue`:

```vue
<template>
  <div class="qr-scan-overlay" @click.self="$emit('close')">
    <div class="qr-scan-modal">
      <h3 class="qr-scan-title">Schütze per QR-Code</h3>
      <video ref="videoEl" class="qr-scan-video" muted playsinline />
      <p v-if="error" class="qr-scan-error" role="alert">{{ error }}</p>
      <p v-else class="qr-scan-hint">QR-Code aus dem Profil vor die Kamera halten</p>
      <button class="qr-scan-cancel" @click="$emit('close')">Abbrechen</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import QrScanner from 'qr-scanner'
import { useProfileStore } from '@/stores/profileStore.js'
import { parseCheckinPayload } from '@/constants/qr.js'

const emit = defineEmits(['close', 'resolved'])
const profileStore = useProfileStore()

const videoEl = ref(null)
const error = ref('')
let scanner = null
let busy = false
let lastToken = ''

const handleScan = async (data) => {
  const token = parseCheckinPayload(data)
  if (!token) return // foreign QR code — ignore, keep scanning
  if (busy || token === lastToken) return
  busy = true
  lastToken = token
  error.value = ''
  try {
    const user = await profileStore.resolveCheckinToken(token)
    emit('resolved', user)
  } catch (e) {
    error.value = e?.status === 404 ? 'Code ungültig' : 'Verbindungsfehler — nochmals versuchen'
    lastToken = '' // allow retrying the same code after an error
  } finally {
    busy = false
  }
}

onMounted(async () => {
  try {
    scanner = new QrScanner(videoEl.value, (result) => handleScan(result.data), {
      returnDetailedScanResult: true,
    })
    await scanner.start()
  } catch {
    error.value = 'Kamera nicht verfügbar'
  }
})

onBeforeUnmount(() => {
  scanner?.destroy()
  scanner = null
})
</script>

<style scoped>
.qr-scan-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.qr-scan-modal {
  background: var(--sg-bg-card);
  border-radius: 12px;
  padding: 1.5rem;
  width: min(92vw, 420px);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  align-items: center;
}

.qr-scan-title {
  margin: 0;
  color: var(--sg-brand);
  font-size: 1.15rem;
}

.qr-scan-video {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 8px;
  background: #000;
}

.qr-scan-hint {
  margin: 0;
  color: var(--sg-text-muted);
  font-size: 0.9rem;
  text-align: center;
}

.qr-scan-error {
  margin: 0;
  color: var(--sg-color-danger-text);
  font-weight: 600;
  text-align: center;
}

.qr-scan-cancel {
  min-height: 48px;
  padding: 0.6rem 2rem;
  border: 1px solid var(--sg-border);
  border-radius: 8px;
  background: transparent;
  color: var(--sg-text-muted);
  font-size: 1rem;
  cursor: pointer;
}
</style>
```

- [ ] **Step 4: Test laufen lassen — muss grün sein**

Run: `npm run test src/components/__tests__/QrScanModal.test.js`
Expected: 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/components/shooter/QrScanModal.vue src/components/__tests__/QrScanModal.test.js
git commit -m "[ui] Add QrScanModal camera scanner for QR check-in"
```

---

### Task 9: Gruppen-Setup-Integration in ShooterPlayPage + userId in Ergebnissen

**Files:**
- Modify: `smart-ground-ui/src/views/shooter/ShooterPlayPage.vue`
- Modify: `smart-ground-ui/src/stores/playSessionStore.js`
- Test: `smart-ground-ui/src/views/__tests__/ShooterPlayPageQr.test.js`
- Test: `smart-ground-ui/src/stores/__tests__/playSessionStore.qr.test.js`

- [ ] **Step 1: Failing Store-Test schreiben (buildPlayerResults trägt userId)**

Neue Datei `src/stores/__tests__/playSessionStore.qr.test.js`:

```js
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePlaySessionStore } from '@/stores/playSessionStore.js'
import { StepState } from '@/constants/playEnums.js'

describe('playSessionStore QR check-in', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('buildPlayerResults carries userId for account players and null for placeholders', () => {
    const store = usePlaySessionStore()
    store.sessionPlayers = [
      { id: 'gp-1', displayName: 'Anna', userId: 'acc-1' },
      { id: 'gp-2', displayName: 'Schütze 2' },
    ]
    store.playScore = {
      totalPoints: 0,
      stepStates: [
        { playerId: 'gp-1', serieIndex: 0, stepIndex: 0, state: StepState.DONE, pointValue: 1, pointsEarned: 1, noBirds: 0 },
        { playerId: 'gp-2', serieIndex: 0, stepIndex: 0, state: StepState.FAILED, pointValue: 1, pointsEarned: 0, noBirds: 0 },
      ],
    }

    const results = store.buildPlayerResults()

    expect(results[0].userId).toBe('acc-1')
    expect(results[1].userId).toBeNull()
  })
})
```

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen**

Run: `npm run test src/stores/__tests__/playSessionStore.qr.test.js`
Expected: FAIL — `results[0].userId` ist `undefined`.

- [ ] **Step 3: buildPlayerResults erweitern**

In `src/stores/playSessionStore.js`, in `buildPlayerResults` (Zeile ~197) das zurückgegebene Objekt ergänzen:

```js
      return {
        playerId: player.id,
        userId: player.userId ?? null,
        displayName: player.displayName,
        totalPoints,
        maxPoints,
        stepStates: states.map((s) => ({ ...s })),
      };
```

- [ ] **Step 4: Store-Test laufen lassen — muss grün sein**

Run: `npm run test src/stores/__tests__/playSessionStore.qr.test.js`
Expected: PASS.

- [ ] **Step 5: Failing View-Test schreiben**

Neue Datei `src/views/__tests__/ShooterPlayPageQr.test.js`:

```js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ShooterPlayPage from '../shooter/ShooterPlayPage.vue'
import { usePlaySessionStore } from '@/stores/playSessionStore.js'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('@/stores/passeStore.js', () => ({
  usePasseStore: () => ({ savedPassen: [] }),
}))
vi.mock('@/services/rangePositionApi.js', () => ({
  sendPositionCommand: vi.fn().mockResolvedValue(undefined),
}))

const mountGroupSetup = () => {
  const store = usePlaySessionStore()
  store.showGroupSetup = true
  return mount(ShooterPlayPage, {
    props: { rangeId: 'r1' },
    global: { stubs: { Icons: true, QrScanModal: true } },
  })
}

describe('ShooterPlayPage QR check-in', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('shows a QR add button in the group setup', () => {
    const wrapper = mountGroupSetup()
    expect(wrapper.find('.add-player-qr-btn').exists()).toBe(true)
  })

  it('opens the scan modal on click', async () => {
    const wrapper = mountGroupSetup()
    expect(wrapper.findComponent({ name: 'QrScanModal' }).exists()).toBe(false)

    await wrapper.get('.add-player-qr-btn').trigger('click')

    expect(wrapper.findComponent({ name: 'QrScanModal' }).exists()).toBe(true)
  })

  it('adds a resolved account player with a badge and closes the modal', async () => {
    const wrapper = mountGroupSetup()
    await wrapper.get('.add-player-qr-btn').trigger('click')

    wrapper.findComponent({ name: 'QrScanModal' }).vm.$emit('resolved', {
      userId: 'acc-1',
      displayName: 'Anna Muster',
    })
    await wrapper.vm.$nextTick()

    const names = wrapper.findAll('.player-display-name').map((n) => n.text())
    expect(names).toContain('Anna Muster')
    expect(wrapper.find('.player-account-badge').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'QrScanModal' }).exists()).toBe(false)
  })

  it('rejects a duplicate account with a notice instead of adding twice', async () => {
    const wrapper = mountGroupSetup()
    const scanned = { userId: 'acc-1', displayName: 'Anna Muster' }

    await wrapper.get('.add-player-qr-btn').trigger('click')
    wrapper.findComponent({ name: 'QrScanModal' }).vm.$emit('resolved', scanned)
    await wrapper.vm.$nextTick()

    await wrapper.get('.add-player-qr-btn').trigger('click')
    wrapper.findComponent({ name: 'QrScanModal' }).vm.$emit('resolved', scanned)
    await wrapper.vm.$nextTick()

    const names = wrapper.findAll('.player-display-name').map((n) => n.text())
    expect(names.filter((n) => n === 'Anna Muster')).toHaveLength(1)
    expect(wrapper.get('.qr-scan-notice').text()).toContain('bereits in der Gruppe')
  })
})
```

- [ ] **Step 6: View-Test laufen lassen — muss fehlschlagen**

Run: `npm run test src/views/__tests__/ShooterPlayPageQr.test.js`
Expected: FAIL — `.add-player-qr-btn` existiert nicht.

- [ ] **Step 7: ShooterPlayPage erweitern**

In `src/views/shooter/ShooterPlayPage.vue`:

**Template** — in der `player-row` (Zeile ~10-20) das Badge nach dem Namen einfügen:

```html
            <span class="player-display-name">{{ player.displayName }}</span>
            <span v-if="player.userId" class="player-account-badge" title="Mit Account verknüpft — Ergebnis wird gespeichert">✓</span>
```

Nach dem bestehenden „+ Schütze hinzufügen"-Button (Zeile ~23-25) ergänzen:

```html
        <button v-if="!_isCompetitionMode" class="add-player-btn add-player-qr-btn" @click="openQrScan">
          + Schütze per QR
        </button>
        <p v-if="qrScanNotice" class="qr-scan-notice">{{ qrScanNotice }}</p>
        <QrScanModal v-if="qrScanOpen" @close="qrScanOpen = false" @resolved="addScannedPlayer" />
```

**Script** — Import ergänzen:

```js
import QrScanModal from '@/components/shooter/QrScanModal.vue';
```

Im Group-Setup-Abschnitt (bei `addPlayer`/`removePlayer`, Zeile ~467) ergänzen:

```js
const qrScanOpen = ref(false);
const qrScanNotice = ref('');

const openQrScan = () => {
  qrScanNotice.value = '';
  qrScanOpen.value = true;
};

const addScannedPlayer = (user) => {
  qrScanOpen.value = false;
  if (groupPlayers.value.some((p) => p.userId === user.userId)) {
    qrScanNotice.value = `${user.displayName} ist bereits in der Gruppe`;
    return;
  }
  groupPlayers.value.push({
    id: `gp-${_nextPlayerId++}`,
    displayName: user.displayName,
    userId: user.userId,
  });
};
```

Beim Prefill aus `info.players` (Zeile ~459) die `userId` mitnehmen:

```js
    groupPlayers.value = info.players.map((p, i) => ({
      id: p.id ?? `gp-${i + 1}`,
      displayName: p.displayName,
      userId: p.userId ?? null,
    }));
```

**Styles** — im `<style scoped>` bei den Group-Modal-Styles ergänzen:

```css
.player-account-badge {
  color: var(--sg-color-success);
  font-weight: 700;
  margin-left: 6px;
}

.qr-scan-notice {
  color: var(--sg-color-danger-text);
  font-size: 0.9rem;
  text-align: center;
  margin: 4px 0 0;
}
```

- [ ] **Step 8: Tests laufen lassen — müssen grün sein**

Run: `npm run test src/views/__tests__/ShooterPlayPageQr.test.js src/views/__tests__/ShooterPlayPage.test.js`
Expected: alle PASS (auch die bestehenden ShooterPlayPage-Tests).

- [ ] **Step 9: Commit**

```bash
git add src/views/shooter/ShooterPlayPage.vue src/stores/playSessionStore.js src/views/__tests__/ShooterPlayPageQr.test.js src/stores/__tests__/playSessionStore.qr.test.js
git commit -m "[ui] Add QR check-in to training group setup; carry userId into player results"
```

---

### Task 10: Profil — QR-Code-Tab und Ergebnisse-Tab

**Files:**
- Modify: `smart-ground-ui/src/views/shooter/ShooterProfilView.vue`
- Test: `smart-ground-ui/src/views/shooter/__tests__/ShooterProfilView.qr.test.js`

- [ ] **Step 1: Failing Test schreiben**

Neue Datei `src/views/shooter/__tests__/ShooterProfilView.qr.test.js`:

```js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ShooterProfilView from '../ShooterProfilView.vue'
import { useAuthStore } from '@/stores/authStore.js'
import * as userApi from '@/services/userApi.js'
import QRCode from 'qrcode'
import { buildCheckinPayload } from '@/constants/qr.js'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn() }),
}))
vi.mock('qrcode', () => ({
  default: { toCanvas: vi.fn().mockResolvedValue(undefined) },
}))
vi.mock('@/services/userApi.js', () => ({
  fetchMyQrToken: vi.fn(),
  rotateMyQrToken: vi.fn(),
  resolveUserByQr: vi.fn(),
  fetchMyPlayResults: vi.fn(),
}))

const mountView = () => {
  const auth = useAuthStore()
  auth.profile = { vorname: 'Anna', nachname: 'Muster', username: 'anna' }
  return mount(ShooterProfilView, {
    global: { stubs: { Icons: true } },
  })
}

const clickTab = async (wrapper, label) => {
  const tab = wrapper.findAll('.tab-btn').find((b) => b.text() === label)
  await tab.trigger('click')
  await flushPromises()
}

describe('ShooterProfilView QR tab', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads the token and renders the QR code when the tab is opened', async () => {
    userApi.fetchMyQrToken.mockResolvedValue({ qrToken: 'tok-1' })
    const wrapper = mountView()

    await clickTab(wrapper, 'QR-Code')

    expect(userApi.fetchMyQrToken).toHaveBeenCalled()
    expect(QRCode.toCanvas).toHaveBeenCalledWith(
      expect.anything(),
      buildCheckinPayload('tok-1'),
      expect.anything(),
    )
  })

  it('rotates the token and re-renders', async () => {
    userApi.fetchMyQrToken.mockResolvedValue({ qrToken: 'tok-1' })
    userApi.rotateMyQrToken.mockResolvedValue({ qrToken: 'tok-2' })
    const wrapper = mountView()
    await clickTab(wrapper, 'QR-Code')

    await wrapper.get('.qr-rotate-btn').trigger('click')
    await flushPromises()

    expect(userApi.rotateMyQrToken).toHaveBeenCalled()
    expect(QRCode.toCanvas).toHaveBeenLastCalledWith(
      expect.anything(),
      buildCheckinPayload('tok-2'),
      expect.anything(),
    )
  })

  it('shows my results in the Ergebnisse tab', async () => {
    userApi.fetchMyPlayResults.mockResolvedValue([
      { resultId: 'r1', templateName: 'Jagd 1', rangeName: 'Trapstand', completedAt: '2026-07-01T10:00:00Z', totalPoints: 12, maxPoints: 18 },
    ])
    const wrapper = mountView()

    await clickTab(wrapper, 'Ergebnisse')

    expect(wrapper.text()).toContain('Jagd 1')
    expect(wrapper.text()).toContain('12/18')
  })

  it('shows an empty state when there are no results', async () => {
    userApi.fetchMyPlayResults.mockResolvedValue([])
    const wrapper = mountView()

    await clickTab(wrapper, 'Ergebnisse')

    expect(wrapper.text()).toContain('Noch keine Ergebnisse')
  })
})
```

Hinweis: Der `authStore` wird hier NICHT gemockt, sondern real instanziert und `profile` direkt gesetzt — falls `authStore.init()` o.ä. beim Instanziieren Netzwerkcalls macht, den Store stattdessen mit `vi.mock('@/stores/authStore.js', …)` wie in bestehenden View-Tests mocken (in `src/views/__tests__/` nach `useAuthStore` suchen und das dortige Muster übernehmen).

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen**

Run: `npm run test src/views/shooter/__tests__/ShooterProfilView.qr.test.js`
Expected: FAIL — Tab „QR-Code" existiert nicht.

- [ ] **Step 3: View erweitern**

In `src/views/shooter/ShooterProfilView.vue`:

**Tabs-Liste** (Zeile ~170) erweitern:

```js
const tabs = [
  { id: 'profil', label: 'Profil' },
  { id: 'kontakt', label: 'Kontakt' },
  { id: 'mitgliedschaft', label: 'Mitgliedschaft' },
  { id: 'qr', label: 'QR-Code' },
  { id: 'ergebnisse', label: 'Ergebnisse' },
]
```

**Template** — nach dem Mitgliedschaft-Tab-Block (endet Zeile ~155) einfügen:

```html
    <!-- Tab: QR-Code -->
    <div v-if="activeTab === 'qr'" class="tab-content" role="tabpanel">
      <div class="qr-section">
        <p class="qr-explainer">
          Zeige diesen Code am Stand, um dich einer Gruppe anzuschliessen.
          Deine Ergebnisse werden deinem Konto zugeordnet.
        </p>
        <canvas ref="qrCanvas" class="qr-canvas" />
        <button class="save-btn qr-rotate-btn" :disabled="profileStore.isLoading" @click="rotateQr">
          Code erneuern
        </button>
        <p class="qr-rotate-hint">Beim Erneuern wird der alte Code sofort ungültig.</p>
        <div v-if="profileStore.error" class="save-error">{{ profileStore.error }}</div>
      </div>
    </div>

    <!-- Tab: Ergebnisse -->
    <div v-if="activeTab === 'ergebnisse'" class="tab-content" role="tabpanel">
      <p v-if="profileStore.myResults.length === 0" class="empty-results">
        Noch keine Ergebnisse — checke dich am Stand per QR-Code ein.
      </p>
      <div v-for="r in profileStore.myResults" :key="r.resultId" class="result-row">
        <div class="result-main">
          <span class="result-name">{{ r.templateName }}</span>
          <span class="result-meta">{{ r.rangeName ?? '—' }} · {{ formatDate(r.completedAt) }}</span>
        </div>
        <span class="result-score">{{ r.totalPoints }}/{{ r.maxPoints }}</span>
      </div>
    </div>
```

**Script** — Imports ergänzen:

```js
import { ref, reactive, computed, watch, watchEffect, nextTick } from 'vue'
import QRCode from 'qrcode'
import { useProfileStore } from '@/stores/profileStore.js'
import { buildCheckinPayload } from '@/constants/qr.js'
```

Nach `const activeTab = ref('profil')` ergänzen:

```js
const profileStore = useProfileStore()
const qrCanvas = ref(null)

// Lazy-load tab data on first visit
watch(activeTab, async (tab) => {
  if (tab === 'qr' && !profileStore.qrToken) await profileStore.loadQrToken()
  if (tab === 'ergebnisse') await profileStore.loadMyResults()
})

// Re-render the QR canvas whenever the token changes (initial load + rotation)
watch(() => profileStore.qrToken, async (token) => {
  if (!token) return
  await nextTick()
  if (!qrCanvas.value) return
  try {
    await QRCode.toCanvas(qrCanvas.value, buildCheckinPayload(token), { width: 240, margin: 1 })
  } catch {
    // canvas rendering unavailable (e.g. jsdom) — token is still shown via rotation flow
  }
})

async function rotateQr() {
  await profileStore.rotateQrToken()
}
```

**Styles** — im `<style scoped>` ergänzen:

```css
.qr-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 16px;
}

.qr-explainer,
.qr-rotate-hint {
  color: rgba(255, 255, 255, 0.6);
  font-size: 0.9rem;
  text-align: center;
  max-width: 320px;
  margin: 0;
}

.qr-canvas {
  background: #fff;
  border-radius: 12px;
  padding: 8px;
}

.empty-results {
  color: rgba(255, 255, 255, 0.6);
  text-align: center;
  padding: 24px 16px;
}

.result-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.result-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.result-name {
  font-weight: 600;
}

.result-meta {
  color: rgba(255, 255, 255, 0.5);
  font-size: 0.85rem;
}

.result-score {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
```

(Farbwerte folgen dem bestehenden Stil dieser View — sie nutzt `rgba(255,255,255,…)` auf dunklem Grund; beim Implementieren an die tatsächlich vorhandenen Nachbar-Styles angleichen.)

- [ ] **Step 4: Test laufen lassen — muss grün sein**

Run: `npm run test src/views/shooter/__tests__/ShooterProfilView.qr.test.js`
Expected: 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/views/shooter/ShooterProfilView.vue src/views/shooter/__tests__/ShooterProfilView.qr.test.js
git commit -m "[ui] Add QR code and results tabs to shooter profile"
```

---

### Task 11: Endkontrolle — Lint, Tests, Build, Doku

**Files:**
- Modify: `smart-ground-ui/CLAUDE.md`

- [ ] **Step 1: UI-Gesamtverifikation**

Run (in `smart-ground-ui/`):
```bash
npm run lint:check
npm run test
npm run build
```
Expected: keine Lint-Fehler, alle Tests grün, Build ohne Warnings. Lint-Findings mit `npm run lint` fixen.

- [ ] **Step 2: Backend-Gesamtverifikation (Regression nach UI-Arbeit)**

Run (in `smart-ground-backend/`): `./mvnw clean test`
Expected: BUILD SUCCESS.

- [ ] **Step 3: UI-CLAUDE.md aktualisieren**

In `smart-ground-ui/CLAUDE.md` im Stores-Abschnitt (Tabelle „Non-obvious stores") ergänzen:

```markdown
| `profileStore` | Own QR check-in token (`/users/me/qr`, rotate) and personal play results (`/users/me/play-results`); consumed by `ShooterProfilView` and `QrScanModal` (resolve on scan) |
```

Und unter Conventions/General Rules einen Hinweis ergänzen:

```markdown
- **QR check-in payload**: always build/parse via `constants/qr.js` (`smartground://checkin/<token>`) — never hard-code the prefix
```

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md
git commit -m "[ui] Document profileStore and QR payload convention"
```

---

## Self-Review (durchgeführt)

- **Spec-Abdeckung:** `User.qrToken` (Task 2), 4 Endpoints (Tasks 1/3/5), `PlayerRef.userId` + Play-Start (Tasks 1/4), Scan-Modal + Gruppen-Setup + Badge + Duplikat/Fremd-QR/Kamera-Fehlerfälle (Tasks 8/9), Profil-QR + Rotation + Ergebnisliste (Task 10), Mischbetrieb (userId optional überall). Fehlerfall „Backend nicht erreichbar beim Resolve" → QrScanModal zeigt „Verbindungsfehler", `lastToken` wird zurückgesetzt (Retry möglich).
- **Typ-Konsistenz:** `PlayerRefRecord(id, type, displayName, userId)` / `PlayerResultRecord(…, stepStates, userId)` — userId jeweils als letzte Komponente; alle gezeigten Call-Sites entsprechend. Generierte Modelle: `PlayerRef.userId`/`PlayerResult.userId` (UUID, optional, NICHT nullable-annotiert im Schema). Store-API: `loadQrToken`/`rotateQrToken`/`loadMyResults`/`resolveCheckinToken` konsistent zwischen Task 7 (Definition) und Tasks 8/10 (Verwendung).
- **Bekannte Unsicherheiten für den Implementierer:** (a) `UserDTO.getFullName()`-Herkunft in Task 3 prüfen; (b) weitere `new PlayerRefRecord/PlayerResultRecord`-Call-Sites per grep in Task 4 (Wettkampf-Pfade → `null`); (c) authStore-Instanziierung im Profil-Test (Task 10) ggf. auf das bestehende Mock-Muster umstellen.
