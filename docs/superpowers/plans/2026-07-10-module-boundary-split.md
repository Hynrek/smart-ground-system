# Modulgrenze und Artefakt-Split — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract `contracts` and `domain` into a new, independent `smart-ground-contracts` repo consumed via versioned Maven coordinates; rename `smart-ground-backend` to `smart-ground-hub`; give `smart-ground-node` a `HubClient` that depends only on `contracts`; add an ArchUnit test that fails the build if Node ever gains a dependency edge to Hub internals.

**Architecture:** Three independent git repos build three independent Maven artifacts. `smart-ground-contracts` (new repo) is a two-module reactor (`contracts`, `domain`) with no Spring Boot dependency of its own — pure library jars. `smart-ground-hub` (renamed from `smart-ground-backend`, keeps its own repo/remote) depends on both via versioned coordinates resolved from the local `~/.m2` cache (`mvn install` in `smart-ground-contracts` first — there is no reactor spanning repos). `smart-ground-node` (stays plain-tracked in the root repo) depends only on `contracts`. Because `ch.jp.shooting.model.*` and `ch.jp.smartground.*` package names are preserved exactly as they are today, moving them into `domain`/`contracts` requires zero source-file edits in Hub's controllers/services/repositories — only `pom.xml` changes and a directory move.

**Tech Stack:** Java 25, Maven 3.9, Spring Boot 4.0.5, ArchUnit (bytecode dependency rules), openapi-generator-maven-plugin 7.9.0.

## Global Constraints

- No production release has been made — schema/contracts/module layout can be rewritten freely (root `CLAUDE.md`).
- Commit messages: `[backend|ui|firmware] short description` (root `CLAUDE.md`). Use `[backend]` for Hub/contracts/domain work, no established prefix for Node — use `[node]`.
- German comments for backend/firmware domain logic; English for frontend/tests (root `CLAUDE.md`).
- `openapi.yaml` is the single source of truth for REST endpoints; never hand-edit generated `ch.jp.smartground.*` sources (`smart-ground-backend/CLAUDE.md`).
- Existing behavior must be unchanged — this sub-project moves code and erects a boundary, it adds no features (task brief).
- Do **not** add `updated_at`/`deleted` to `Serie` or any other entity — that is sub-project #2's scope (task brief).
- Out of scope: MQTT removal (#7), sync endpoints (#2), outbox (#3), `node-channel` (#4), `node-api` façade (#5).
- Repo topology decision (locked in with the user before this plan was written): keep `smart-ground-hub` and `smart-ground-node` in their current, separate repo homes. `contracts` and `domain` live in a **new**, independent repo (`smart-ground-contracts`), consumed by both via versioned Maven coordinates (local `mvn install`, not a multi-repo reactor). No remote/CI artifact publishing in this sub-project — local `~/.m2` install is sufficient pre-v1.0.

---

## File Structure

```
Smart Ground/                                    (root repo)
├── CLAUDE.md                                     [MODIFY — rename references]
├── smart-ground-contracts/                       [NEW — own git repo, untracked by root, like smart-box/]
│   ├── pom.xml                                   [NEW — reactor parent, packaging=pom]
│   ├── contracts/
│   │   ├── pom.xml                                [NEW]
│   │   ├── openapi-generator-ignore               [MOVED from smart-ground-backend/]
│   │   └── src/main/resources/openapi.yaml        [MOVED from smart-ground-backend/src/main/resources/static/]
│   └── domain/
│       ├── pom.xml                                [NEW]
│       └── src/main/java/ch/jp/shooting/model/**  [MOVED from smart-ground-backend/, package unchanged]
├── smart-ground-hub/                             [RENAMED from smart-ground-backend/ — own git repo, own remote]
│   ├── pom.xml                                    [MODIFY — drop generator plugin + model/, add contracts+domain deps, rename artifact]
│   └── CLAUDE.md                                  [RENAMED from smart-ground-backend/CLAUDE.md — path/name refs updated]
└── smart-ground-node/                            [MODIFY — plain-tracked in root repo]
    ├── pom.xml                                    [MODIFY — add contracts + archunit deps]
    ├── src/main/resources/application.properties  [NEW — hub.base-url]
    └── src/main/java/ch/jp/shooting/node/
        └── hub/
            ├── HubClient.java                     [NEW]
            └── HubClientConfig.java               [NEW]
    └── src/test/java/ch/jp/shooting/node/
        ├── hub/HubClientTest.java                 [NEW]
        └── architecture/ModuleBoundaryTest.java   [NEW]
```

**Resolved versions (from `smart-ground-backend`'s current dependency tree — do not guess, use these):**
`jakarta.persistence-api:3.2.0`, `jakarta.validation-api:3.1.1`, `org.jspecify:jspecify:1.0.0`, `openapi-generator-maven-plugin:7.9.0`, Spring Boot parent `4.0.5`.

---

### Task 1: Scaffold the `smart-ground-contracts` repo (reactor + two empty modules)

**Files:**
- Create: `smart-ground-contracts/pom.xml`
- Create: `smart-ground-contracts/contracts/pom.xml`
- Create: `smart-ground-contracts/domain/pom.xml`

**Interfaces:**
- Produces: Maven coordinates `ch.jp.smartground:contracts:0.0.1-SNAPSHOT` (jar) and `ch.jp.shooting:domain:0.0.1-SNAPSHOT` (jar), both resolvable from `~/.m2` after `mvn install`. Later tasks depend on these exact coordinates.

- [ ] **Step 1: Create the directory and init a fresh git repo**

```bash
mkdir -p "smart-ground-contracts/contracts/src/main/resources"
mkdir -p "smart-ground-contracts/domain/src/main/java"
cd smart-ground-contracts
git init
cd ..
```

Expected: `smart-ground-contracts/.git` exists; this repo has no remote yet (add one manually later — out of scope for this plan, an external/hard-to-reverse action).

- [ ] **Step 2: Write the reactor parent pom**

`smart-ground-contracts/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>ch.jp.smartground</groupId>
    <artifactId>smart-ground-contracts-parent</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>smart-ground-contracts-parent</name>
    <description>Shared contracts (OpenAPI-generated wire types) and domain (JPA entities) modules, consumed by smart-ground-hub and smart-ground-node via versioned Maven coordinates.</description>

    <modules>
        <module>contracts</module>
        <module>domain</module>
    </modules>

    <properties>
        <maven.compiler.source>25</maven.compiler.source>
        <maven.compiler.target>25</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <jakarta.persistence.version>3.2.0</jakarta.persistence.version>
        <jakarta.validation.version>3.1.1</jakarta.validation.version>
        <jspecify.version>1.0.0</jspecify.version>
        <openapi.generator.version>7.9.0</openapi.generator.version>
    </properties>
</project>
```

- [ ] **Step 3: Write the `contracts` module pom (empty spec for now)**

`smart-ground-contracts/contracts/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>ch.jp.smartground</groupId>
        <artifactId>smart-ground-contracts-parent</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>contracts</artifactId>
    <packaging>jar</packaging>
    <name>contracts</name>
    <description>OpenAPI-generated wire types (ch.jp.smartground.api / ch.jp.smartground.model) shared between Hub and Node.</description>

    <dependencies>
        <!-- Runtime deps the openapi-generator "spring" templates emit into every generated
             model/interface (@Schema, JsonNullable, @Generated, @JsonProperty). Deliberately NOT
             springdoc-openapi-starter-webmvc-ui — that pulls in spring-webmvc for serving Swagger
             UI, a Hub-only web concern this library jar has no business depending on. Versions
             match what smart-ground-backend's springdoc dependency resolves to today (see plan
             investigation) so generated code compiles identically. -->
        <dependency>
            <groupId>jakarta.annotation</groupId>
            <artifactId>jakarta.annotation-api</artifactId>
            <version>3.0.0</version>
        </dependency>
        <dependency>
            <groupId>org.openapitools</groupId>
            <artifactId>jackson-databind-nullable</artifactId>
            <version>0.2.6</version>
        </dependency>
        <dependency>
            <groupId>io.swagger.core.v3</groupId>
            <artifactId>swagger-annotations-jakarta</artifactId>
            <version>2.2.21</version>
        </dependency>
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
            <version>${jakarta.validation.version}</version>
        </dependency>
    </dependencies>
</project>
```

Create a placeholder `openapi.yaml` so the module compiles before Task 2 moves the real one:
```bash
echo "openapi: 3.0.3
info:
  title: placeholder
  version: '0.0.1'
paths: {}
" > smart-ground-contracts/contracts/src/main/resources/openapi.yaml
```

- [ ] **Step 4: Write the `domain` module pom (empty for now)**

`smart-ground-contracts/domain/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>ch.jp.smartground</groupId>
        <artifactId>smart-ground-contracts-parent</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <groupId>ch.jp.shooting</groupId>
    <artifactId>domain</artifactId>
    <packaging>jar</packaging>
    <name>domain</name>
    <description>Hub's JPA entities (ch.jp.shooting.model), extracted into their own artifact so a future consumer can depend on them without pulling in the Hub's service/repository/web layers.</description>

    <dependencies>
        <dependency>
            <groupId>jakarta.persistence</groupId>
            <artifactId>jakarta.persistence-api</artifactId>
            <version>${jakarta.persistence.version}</version>
        </dependency>
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
            <version>${jakarta.validation.version}</version>
        </dependency>
        <dependency>
            <groupId>org.jspecify</groupId>
            <artifactId>jspecify</artifactId>
            <version>${jspecify.version}</version>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 5: Verify the reactor builds**

```bash
cd smart-ground-contracts
mvn -q compile
cd ..
```
Expected: `BUILD SUCCESS` (no output with `-q` on success). `domain` has no `.java` files yet, `contracts` has no generator execution wired yet — both still compile as empty jars.

- [ ] **Step 6: Commit in the new repo**

```bash
cd smart-ground-contracts
git add pom.xml contracts/pom.xml contracts/src domain/pom.xml
git commit -m "feat: scaffold contracts/domain reactor"
cd ..
```

---

### Task 2: Move `openapi.yaml` into `contracts` and wire the generator plugin

**Files:**
- Modify: `smart-ground-contracts/contracts/pom.xml`
- Create: `smart-ground-contracts/contracts/src/main/resources/openapi.yaml` (real spec, moved)
- Create: `smart-ground-contracts/contracts/openapi-generator-ignore` (moved)
- Delete (in the Hub repo, done in Task 4): `smart-ground-backend/src/main/resources/static/openapi.yaml`, `smart-ground-backend/openapi-generator-ignore`

**Interfaces:**
- Produces: `contracts.jar` containing generated sources under `ch.jp.smartground.api` and `ch.jp.smartground.model`, `interfaceOnly=true` (no controller implementations generated) — same generator config Hub used, verbatim.

- [ ] **Step 1: Copy the real spec across repos (do not delete the Hub copy yet — Task 4 does that once Hub depends on `contracts`)**

```bash
cp "smart-ground-backend/src/main/resources/static/openapi.yaml" "smart-ground-contracts/contracts/src/main/resources/openapi.yaml"
cp "smart-ground-backend/openapi-generator-ignore" "smart-ground-contracts/contracts/openapi-generator-ignore"
```

- [ ] **Step 2: Add the generator plugin execution to `contracts/pom.xml`**

Replace the file's closing `</project>` with (i.e. add a `<build>` block before it), matching the config `smart-ground-backend/pom.xml` used (see plan's investigation — same generator, same options, `interfaceOnly=true`, `useDtoProject=true`):

```xml
    <build>
        <plugins>
            <plugin>
                <groupId>org.openapitools</groupId>
                <artifactId>openapi-generator-maven-plugin</artifactId>
                <version>${openapi.generator.version}</version>
                <executions>
                    <execution>
                        <id>generate-api-interfaces</id>
                        <phase>generate-sources</phase>
                        <goals>
                            <goal>generate</goal>
                        </goals>
                        <configuration>
                            <inputSpec>${project.basedir}/src/main/resources/openapi.yaml</inputSpec>
                            <generatorName>spring</generatorName>
                            <apiPackage>ch.jp.smartground.api</apiPackage>
                            <modelPackage>ch.jp.smartground.model</modelPackage>
                            <generateApiDocumentation>false</generateApiDocumentation>
                            <generateModelDocumentation>false</generateModelDocumentation>
                            <generateApiTests>false</generateApiTests>
                            <generateModelTests>false</generateModelTests>
                            <ignoreFileOverride>${project.basedir}/openapi-generator-ignore</ignoreFileOverride>
                            <skipValidateSpec>true</skipValidateSpec>
                            <configOptions>
                                <useTags>true</useTags>
                                <useDtoProject>true</useDtoProject>
                                <useSpringBoot4>true</useSpringBoot4>
                                <useJakartaEe>true</useJakartaEe>
                                <interfaceOnly>true</interfaceOnly>
                            </configOptions>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
```

Also add the `<repositories>` block for Eclipse Paho if any generated model needs it — check first: the generator itself needs no Paho dependency, only Hub's MQTT integration does, so **skip** that block here (YAGNI — it belongs to Hub's pom, unchanged).

- [ ] **Step 3: Generate and inspect**

```bash
cd smart-ground-contracts
mvn -q generate-sources -pl contracts
find contracts/target/generated-sources/openapi/src/main/java/ch/jp/smartground -maxdepth 2
cd ..
```
Expected: directories `ch/jp/smartground/api` and `ch/jp/smartground/model` exist with generated `.java` files (dozens of files — this mirrors exactly what `smart-ground-backend/target/generated-sources/openapi/` produced before).

- [ ] **Step 4: Full compile + package**

```bash
cd smart-ground-contracts
mvn -q install -pl contracts -am
cd ..
```
Expected: `BUILD SUCCESS`, and `contracts-0.0.1-SNAPSHOT.jar` now present in `~/.m2/repository/ch/jp/smartground/contracts/0.0.1-SNAPSHOT/`.

- [ ] **Step 5: Commit**

```bash
cd smart-ground-contracts
git add contracts/pom.xml contracts/src/main/resources/openapi.yaml contracts/openapi-generator-ignore
git commit -m "feat: wire openapi generator into contracts module"
cd ..
```

---

### Task 3: Move Hub's JPA entities into the `domain` module

**Files:**
- Move: `smart-ground-backend/src/main/java/ch/jp/shooting/model/**` → `smart-ground-contracts/domain/src/main/java/ch/jp/shooting/model/**` (35 files, package unchanged: `ch.jp.shooting.model` and `ch.jp.shooting.model.auth`)
- Delete stray files not worth carrying over: `User.java.bak`, `UserRole.java.bak` (dead backup files, not `.java` sources, already confirmed to have no compiler role — leave behind, do not move)

**Interfaces:**
- Produces: `domain-0.0.1-SNAPSHOT.jar` containing `ch.jp.shooting.model.*` (all entities: `SmartBox`, `Device`, `Range`, `RangePosition`, `Serie`, `Passe`, `PlayInstance`, `UserSerieScore`, `LiveSession`, `SessionTemplate`, `ShooterGroup`, `SessionPlayer`, `PlayerResult`, `BracketMatch`, `CareerStats`, `CompetitionSerieResult`, `CompetitionTiebreaker`, `Reservation`, `Guest`, `FirmwareConfig`, `OtaRelease`, plus enums, plus `model.auth.*`: `User`, `Role`, `Permission`, `PermissionEntity`, `ScopedAccess`, `UserRoleEntity`). This confirmed-clean package has **zero** imports of `ch.jp.shooting.dto/service/repository/api/config` — verified during investigation — so no source edits are needed, only the physical move.

- [ ] **Step 1: Copy the model package across repos**

```bash
mkdir -p "smart-ground-contracts/domain/src/main/java/ch/jp/shooting"
cp -r "smart-ground-backend/src/main/java/ch/jp/shooting/model" "smart-ground-contracts/domain/src/main/java/ch/jp/shooting/model"
rm "smart-ground-contracts/domain/src/main/java/ch/jp/shooting/model/User.java.bak"
rm "smart-ground-contracts/domain/src/main/java/ch/jp/shooting/model/UserRole.java.bak"
```

- [ ] **Step 2: Compile the domain module standalone**

```bash
cd smart-ground-contracts
mvn -q install -pl domain
cd ..
```
Expected: `BUILD SUCCESS`, `domain-0.0.1-SNAPSHOT.jar` present in `~/.m2/repository/ch/jp/shooting/domain/0.0.1-SNAPSHOT/`.

If this fails with "package does not exist" for anything outside `jakarta.*`/`java.*`/`org.jspecify.*`, that means the investigation missed a leaked import — stop and re-grep `smart-ground-backend/src/main/java/ch/jp/shooting/model` for the offending import before proceeding; do not add unrelated dependencies to paper over it.

- [ ] **Step 3: Commit**

```bash
cd smart-ground-contracts
git add domain/src
git commit -m "feat: move Hub JPA entities into domain module"
cd ..
```

(Do **not** delete `smart-ground-backend/src/main/java/ch/jp/shooting/model` yet — Task 4 does that once Hub's pom depends on `domain`.)

---

### Task 4: Rewire `smart-ground-hub` to depend on `contracts` + `domain`, delete the moved sources

**Files:**
- Modify: `smart-ground-backend/pom.xml`
- Delete: `smart-ground-backend/src/main/java/ch/jp/shooting/model/` (whole directory, now lives in `domain`)
- Delete: `smart-ground-backend/src/main/resources/static/openapi.yaml`
- Delete: `smart-ground-backend/openapi-generator-ignore`

**Interfaces:**
- Consumes: `ch.jp.smartground:contracts:0.0.1-SNAPSHOT`, `ch.jp.shooting:domain:0.0.1-SNAPSHOT` (produced by Tasks 2/3, already `mvn install`-ed into `~/.m2`).

- [ ] **Step 1: Remove the generator plugin execution from `smart-ground-backend/pom.xml`**

Delete the entire `<plugin>` block for `openapi-generator-maven-plugin` (currently lines ~209–240, the `<executions>` block shown in the investigation) from the `<build><plugins>` section. Also remove the now-unused `<openapi.generator.version>7.9.0</openapi.generator.version>` property if nothing else in the pom references it (it won't, after this step).

- [ ] **Step 2: Add `contracts` and `domain` as dependencies**

In `smart-ground-backend/pom.xml`, inside `<dependencies>`:
```xml
        <dependency>
            <groupId>ch.jp.smartground</groupId>
            <artifactId>contracts</artifactId>
            <version>0.0.1-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>ch.jp.shooting</groupId>
            <artifactId>domain</artifactId>
            <version>0.0.1-SNAPSHOT</version>
        </dependency>
```

- [ ] **Step 3: Delete the moved/superseded files**

```bash
cd smart-ground-backend
rm -rf src/main/java/ch/jp/shooting/model
rm src/main/resources/static/openapi.yaml
rm openapi-generator-ignore
cd ..
```

- [ ] **Step 4: Compile and run the H2 test suite**

```bash
cd smart-ground-backend
mvn -q clean generate-sources
mvn test -Dspring.profiles.active=h2
cd ..
```
Expected: `BUILD SUCCESS`, same test count/pass rate as before this sub-project started (no behavior change — verify by running `mvn test -Dspring.profiles.active=h2` once more against the pre-change `git stash` if in doubt, but since only pom + module location changed and package names are identical, the diff should be zero test failures). If any test fails with a missing-symbol error, it means an import was relying on Hub's own copy of `openapi-generator-ignore`'s exclusions or a leaked reference Task 3's grep missed — fix by locating the actual missing type, not by re-adding the deleted files.

- [ ] **Step 5: Commit**

```bash
cd smart-ground-backend
git add -A
git commit -m "[backend] depend on contracts/domain artifacts, drop local model/ and openapi.yaml"
cd ..
```

---

### Task 5: Rename `smart-ground-backend` → `smart-ground-hub`

**Files:**
- Rename directory: `smart-ground-backend/` → `smart-ground-hub/` (filesystem rename of the nested repo; the root repo's gitlink pointer moves with it)
- Modify: `smart-ground-hub/pom.xml` (`artifactId`, `name`)
- Rename: `smart-ground-backend/CLAUDE.md` → `smart-ground-hub/CLAUDE.md` (content updated in Task 9)
- Modify (root repo): `CLAUDE.md`

**Interfaces:** none — this is a pure rename, no code changes.

- [ ] **Step 1: Rename the directory**

```bash
mv smart-ground-backend smart-ground-hub
```

- [ ] **Step 2: Update the Maven identity**

In `smart-ground-hub/pom.xml`, change:
```xml
    <artifactId>smartground</artifactId>
```
to:
```xml
    <artifactId>smart-ground-hub</artifactId>
```
and:
```xml
    <name>smartground</name>
```
to:
```xml
    <name>smart-ground-hub</name>
```
(`<groupId>ch.jp.shooting</groupId>` stays — package names inside the app are unaffected, this only changes the Maven artifact coordinate.)

- [ ] **Step 3: Verify it still builds under the new path**

```bash
cd smart-ground-hub
mvn -q clean generate-sources
mvn test -Dspring.profiles.active=h2
cd ..
```
Expected: `BUILD SUCCESS`, same result as Task 4 Step 4.

- [ ] **Step 4: Commit inside the Hub's own repo**

```bash
cd smart-ground-hub
git add -A
git commit -m "[backend] rename artifact to smart-ground-hub"
cd ..
```

- [ ] **Step 5: Update the root repo's gitlink to point at the new path**

The root repo currently tracks the old path as a gitlink (mode `160000`). Re-point it:
```bash
git add smart-ground-hub
git rm --cached smart-ground-backend
git status
```
Expected: `git status` shows `smart-ground-hub` staged as a new gitlink entry and `smart-ground-backend` staged for removal — no working-tree files are deleted (the directory was already renamed on disk in Step 1, `smart-ground-backend` no longer exists there).

- [ ] **Step 6: Update root `CLAUDE.md` references**

In `CLAUDE.md` (root), replace every occurrence of `smart-ground-backend` with `smart-ground-hub`:
- The directory tree under "Smart Ground — Monorepo Overview" (`├── smart-ground-backend/` → `├── smart-ground-hub/`)
- The "Sub-Project Guides" link: `[smart-ground-backend/CLAUDE.md](./smart-ground-backend/CLAUDE.md)` → `[smart-ground-hub/CLAUDE.md](./smart-ground-hub/CLAUDE.md)`
- The "Running the Full Stack Locally" section: `cd smart-ground-backend` → `cd smart-ground-hub`

Also add a one-line mention of `smart-ground-contracts` under the monorepo tree, since it's now a real sibling repo:
```
├── smart-ground-contracts/ # Shared contracts (OpenAPI types) + domain (JPA entities) — own repo, consumed via Maven coordinates
```

- [ ] **Step 7: Commit in the root repo**

```bash
git add CLAUDE.md smart-ground-hub
git commit -m "docs: rename smart-ground-backend to smart-ground-hub"
```

Note: this commits the gitlink re-point (`smart-ground-hub` staged in Step 5) together with the `CLAUDE.md` text changes — both belong to the same rename.

---

### Task 6: Node — add the `contracts` dependency

**Files:**
- Modify: `smart-ground-node/pom.xml`

**Interfaces:**
- Consumes: `ch.jp.smartground:contracts:0.0.1-SNAPSHOT` (built in Task 2).

- [ ] **Step 1: Add the dependency**

In `smart-ground-node/pom.xml`, inside `<dependencies>` (after the existing `spring-boot-starter-test` block or anywhere within `<dependencies>`):
```xml
        <dependency>
            <groupId>ch.jp.smartground</groupId>
            <artifactId>contracts</artifactId>
            <version>0.0.1-SNAPSHOT</version>
        </dependency>
```

- [ ] **Step 2: Verify it resolves and compiles**

```bash
cd smart-ground-node
mvn -q compile
cd ..
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add smart-ground-node/pom.xml
git commit -m "[node] depend on contracts artifact"
```

---

### Task 7: Node — implement `HubClient` (proves the `contracts`-only dependency edge works end-to-end)

**Files:**
- Modify: `smart-ground-node/pom.xml` (add `spring-web`)
- Create: `smart-ground-node/src/main/resources/application.properties`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/hub/HubClient.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/hub/HubClientConfig.java`
- Test: `smart-ground-node/src/test/java/ch/jp/shooting/node/hub/HubClientTest.java`

**Interfaces:**
- Produces: `HubClient.login(String username, String password)` → `ch.jp.smartground.model.LoginResponse` (existing generated DTO, `{token: String}`, from `contracts`). This is the first concrete proof that Node can talk to Hub through nothing but `contracts` — no Hub classes involved.
- Consumes: `ch.jp.smartground.model.LoginRequest` / `ch.jp.smartground.model.LoginResponse` (generated in Task 2 from the existing `/api/auth/login` endpoint, which is already implemented on Hub and requires no changes).

- [ ] **Step 1: Add `spring-web` (for `RestClient`) — Node has no web starter yet**

In `smart-ground-node/pom.xml`, inside `<dependencies>`:
```xml
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-web</artifactId>
        </dependency>
```
(Version comes from the inherited `spring-boot-starter-parent` BOM — do not pin one explicitly.)

- [ ] **Step 2: Write the failing test**

`smart-ground-node/src/test/java/ch/jp/shooting/node/hub/HubClientTest.java`:
```java
package ch.jp.shooting.node.hub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.jp.smartground.model.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HubClientTest {

    @Test
    void loginPostsCredentialsAndReturnsToken() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hub.local");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HubClient client = new HubClient(builder.build());

        server.expect(requestTo("http://hub.local/api/auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.username").value("node-service"))
                .andExpect(jsonPath("$.password").value("secret"))
                .andRespond(withSuccess("{\"token\":\"jwt-abc\"}", MediaType.APPLICATION_JSON));

        LoginResponse response = client.login("node-service", "secret");

        assertThat(response.getToken()).isEqualTo("jwt-abc");
        server.verify();
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

```bash
cd smart-ground-node
mvn test -Dtest=HubClientTest
cd ..
```
Expected: `FAIL` — compile error, `HubClient` does not exist yet.

- [ ] **Step 4: Implement `HubClient`**

`smart-ground-node/src/main/java/ch/jp/shooting/node/hub/HubClient.java`:
```java
package ch.jp.shooting.node.hub;

import ch.jp.smartground.model.LoginRequest;
import ch.jp.smartground.model.LoginResponse;
import org.springframework.web.client.RestClient;

/**
 * Node's only channel to the Hub. Depends solely on {@code contracts} wire types —
 * never on Hub-internal packages (enforced by {@code ModuleBoundaryTest}).
 */
public class HubClient {

    private final RestClient restClient;

    public HubClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public LoginResponse login(String username, String password) {
        LoginRequest request = new LoginRequest(username, password);
        return restClient.post()
                .uri("/api/auth/login")
                .body(request)
                .retrieve()
                .body(LoginResponse.class);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
cd smart-ground-node
mvn test -Dtest=HubClientTest
cd ..
```
Expected: `PASS`.

- [ ] **Step 6: Wire `HubClient` as a Spring bean, reading the Hub base URL from config**

`smart-ground-node/src/main/resources/application.properties`:
```properties
hub.base-url=http://localhost:8080
```

`smart-ground-node/src/main/java/ch/jp/shooting/node/hub/HubClientConfig.java`:
```java
package ch.jp.shooting.node.hub;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class HubClientConfig {

    @Bean
    HubClient hubClient(@Value("${hub.base-url}") String hubBaseUrl) {
        return new HubClient(RestClient.builder().baseUrl(hubBaseUrl).build());
    }
}
```

- [ ] **Step 7: Run the full Node test suite (context-load + new test)**

```bash
cd smart-ground-node
mvn test
cd ..
```
Expected: `BUILD SUCCESS`, all tests pass including `SmartGroundNodeApplicationTests#contextLoads` (proves the new `@Configuration` bean doesn't break Spring context startup) and `HubClientTest`.

- [ ] **Step 8: Commit**

```bash
git add smart-ground-node/pom.xml smart-ground-node/src
git commit -m "[node] add HubClient backed by contracts DTOs"
```

---

### Task 8: Node — ArchUnit test that fails the build if Node depends on Hub internals

**Files:**
- Modify: `smart-ground-node/pom.xml` (add `archunit-junit5`)
- Create: `smart-ground-node/src/test/java/ch/jp/shooting/node/architecture/ModuleBoundaryTest.java`

**Interfaces:** none new — this task only adds a test.

- [ ] **Step 1: Add the ArchUnit test dependency**

In `smart-ground-node/pom.xml`, inside `<dependencies>`:
```xml
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <version>1.3.0</version>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Write the test**

`smart-ground-node/src/test/java/ch/jp/shooting/node/architecture/ModuleBoundaryTest.java`:
```java
package ch.jp.shooting.node.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Node kommuniziert mit dem Hub ausschliesslich über contracts + HubClient — nie über
 * Hub-Interna (Persistenz, Services, Controller). Dieser Test scannt den gesamten
 * Runtime-Classpath: sobald jemand smart-ground-hub als Dependency hinzufügt, tauchen
 * diese Pakete hier auf und der Build bricht.
 */
class ModuleBoundaryTest {

    private static final String[] FORBIDDEN_HUB_PACKAGES = {
            "ch.jp.shooting.service..",
            "ch.jp.shooting.repository..",
            "ch.jp.shooting.mapper..",
            "ch.jp.shooting.exception..",
            "ch.jp.shooting.api..",
            "ch.jp.shooting.config..",
            "ch.jp.shooting.model.."
    };

    @Test
    void nodeClasspathMustNotContainHubInternals() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importClasspath();

        ArchRule rule = noClasses()
                .should().resideInAnyPackage(FORBIDDEN_HUB_PACKAGES)
                .because("Node darf ausschliesslich über contracts + HubClient mit dem Hub sprechen — "
                        + "kein Repository-Durchgriff, keine geteilte Persistenz (Hub/Node-Architektur-Spec).");

        rule.check(classes);
    }
}
```

- [ ] **Step 3: Run it — should already pass (Node has no Hub dependency)**

```bash
cd smart-ground-node
mvn test -Dtest=ModuleBoundaryTest
cd ..
```
Expected: `PASS`. This confirms today's state is clean, but doesn't yet prove the rule *catches* a violation — that's the next step.

- [ ] **Step 4: Prove the rule is not a tautology — temporarily add a forbidden dependency**

First, make Hub's jar available locally (it was already built with `mvn install` in Task 5 Step 3's `mvn test` — but that ran `mvn test`, not `install`; install it now):
```bash
cd smart-ground-hub
mvn -q install -DskipTests
cd ..
```

Temporarily add this block to `smart-ground-node/pom.xml`'s `<dependencies>` (do not commit this):
```xml
        <dependency>
            <groupId>ch.jp.shooting</groupId>
            <artifactId>smart-ground-hub</artifactId>
            <version>0.0.1-SNAPSHOT</version>
        </dependency>
```

Run:
```bash
cd smart-ground-node
mvn test -Dtest=ModuleBoundaryTest
cd ..
```
Expected: `FAIL` — ArchUnit reports violations, e.g. `noClasses should resideInAnyPackage ['ch.jp.shooting.service..', ...] because ... but classes ... were violated`, listing Hub's `SmartBoxService`, `UserRepository`, etc. This is the proof: the rule is load-bearing.

- [ ] **Step 5: Remove the temporary dependency, confirm green again**

Remove the block added in Step 4 from `smart-ground-node/pom.xml`.

```bash
cd smart-ground-node
mvn test -Dtest=ModuleBoundaryTest
cd ..
```
Expected: `PASS`.

- [ ] **Step 6: Run the full Node suite one more time**

```bash
cd smart-ground-node
mvn test
cd ..
```
Expected: `BUILD SUCCESS`, all tests pass (crypto, frame, operational, pairing, uart, hub, architecture — everything from before this sub-project plus the two new test classes).

- [ ] **Step 7: Commit**

```bash
git add smart-ground-node/pom.xml smart-ground-node/src/test/java/ch/jp/shooting/node/architecture
git commit -m "[node] add ArchUnit test forbidding Hub-internal dependencies"
```

---

### Task 9: Update `smart-ground-hub/CLAUDE.md` and `smart-ground-node/CLAUDE.md` for the new module boundary

**Files:**
- Modify: `smart-ground-hub/CLAUDE.md`
- Modify: `smart-ground-node/CLAUDE.md`

**Interfaces:** none — documentation only.

- [ ] **Step 1: Update `smart-ground-hub/CLAUDE.md`**

Change the title line `# Smart Ground — Backend Development Guide` → `# Smart Ground Hub — Backend Development Guide`.

In the "Project Structure" section, add a note directly above the `src/main/java/ch/jp/shooting/` tree:
```markdown
> **`model/` and the OpenAPI contract now live in separate repos.** `ch.jp.shooting.model.*`
> (JPA entities) is the `domain` module and `ch.jp.smartground.*` (generated interfaces/DTOs)
> is the `contracts` module — both in the sibling `smart-ground-contracts` repo, consumed here
> via versioned Maven coordinates (`ch.jp.shooting:domain`, `ch.jp.smartground:contracts`).
> After editing `domain` or `contracts`, run `mvn install` in `smart-ground-contracts` before
> building the Hub — there is no multi-repo reactor.
```

Update the "Key Environment Variables" / "OpenAPI & REST Contracts" section's workflow step 1 (`Edit src/main/resources/static/openapi.yaml`) to:
```markdown
1. **Edit `openapi.yaml`** in the sibling `smart-ground-contracts` repo, at `contracts/src/main/resources/openapi.yaml`
2. **Regenerate + install**: in `smart-ground-contracts`, run `mvn install -pl contracts`
3. **Regenerate Hub's dependency**: back in `smart-ground-hub`, run `./mvnw generate-sources`
   - Output (via the `contracts` jar): `ch.jp.smartground.api` / `ch.jp.smartground.model`
4. **Implement**: `@RestController class FooController implements FooApi`
```

- [ ] **Step 2: Update `smart-ground-node/CLAUDE.md`**

Add a new section after "Project Structure":
```markdown
## Talking to the Hub

Node depends on the Hub **only** through the `contracts` Maven artifact (`ch.jp.smartground:contracts`,
built from the sibling `smart-ground-contracts` repo — run `mvn install` there after any contract
change) and `ch.jp.shooting.node.hub.HubClient`. Never add a dependency on `smart-ground-hub` itself —
`ModuleBoundaryTest` (ArchUnit, `src/test/java/ch/jp/shooting/node/architecture/`) fails the build if
Node's classpath ever contains `ch.jp.shooting.service/repository/mapper/exception/api/config/model`
(Hub-internal packages, including its JPA entities — no shared persistence, no repository reach-through).
```

Update the Project Structure tree to include the new `hub/` package.

- [ ] **Step 3: Commit**

```bash
git add smart-ground-hub/CLAUDE.md
cd smart-ground-hub && git commit -m "[backend] document contracts/domain module boundary" && cd ..
git add smart-ground-node/CLAUDE.md
git commit -m "[node] document HubClient / ModuleBoundaryTest"
```

(Two separate commits in two separate repos — `smart-ground-hub/CLAUDE.md` belongs to the Hub's own repo.)

---

### Task 10: Update the roadmap status and do a full cross-repo verification pass

**Files:**
- Modify: `docs/superpowers/plans/2026-07-10-hub-node-roadmap.md`

**Interfaces:** none.

- [ ] **Step 1: Flip the status table from "geplant" to "erledigt"**

The row was already updated to `geplant` (with a link to this plan) when the plan was written. Once every task above is done and Step 2 below is green, change the row in `docs/superpowers/plans/2026-07-10-hub-node-roadmap.md` from:
```markdown
| 1 | Modulgrenze und Artefakt-Split | — | geplant | [2026-07-10-module-boundary-split.md](2026-07-10-module-boundary-split.md) |
```
to:
```markdown
| 1 | Modulgrenze und Artefakt-Split | — | erledigt | [2026-07-10-module-boundary-split.md](2026-07-10-module-boundary-split.md) |
```

- [ ] **Step 2: Full clean verification, all three repos, in dependency order**

```bash
cd smart-ground-contracts
mvn -q clean install
cd ..

cd smart-ground-hub
mvn -q clean generate-sources
mvn test -Dspring.profiles.active=h2
cd ..

cd smart-ground-node
mvn -q clean compile
mvn test
cd ..
```
Expected: three `BUILD SUCCESS` results, zero test failures anywhere. This is the "both applications boot" deliverable: Hub's `@SpringBootTest`-backed repository tests (`UserSerieScoreRepositoryTest`, `OtaReleaseRepositoryTest`, `UserRepositoryTest`, `CompetitionTiebreakerRepositoryTest`) exercise a full Spring context including every entity now sourced from `domain`; Node's `SmartGroundNodeApplicationTests#contextLoads` boots its full context including the new `HubClientConfig` bean.

- [ ] **Step 3: Commit the roadmap update in the root repo**

```bash
git add docs/superpowers/plans/2026-07-10-hub-node-roadmap.md
git commit -m "docs: mark Modulgrenze und Artefakt-Split done"
```

---

## Manual follow-ups (explicitly out of scope for this plan, do not attempt)

- Creating a GitHub remote for `smart-ground-contracts` and pushing it — external, hard-to-reverse action requiring the user's own GitHub access.
- Renaming the `Hynrek/smartground-backend` GitHub repo to match `smart-ground-hub` — same reason; the local directory rename and remote name are intentionally decoupled by this plan.
- Publishing `contracts`/`domain` to a shared artifact repository (GitHub Packages, Nexus) for CI use — local `~/.m2` install is sufficient pre-v1.0; revisit when CI needs it.
