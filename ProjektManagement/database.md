# SmartRange – Datenbankschema

> Vollständiges PostgreSQL-Schema mit Flyway-Migrationsreihenfolge und Entwurfshinweisen.
> Eingebunden von [project.md](project.md).

---

## Schema (SQL)

```sql
-- Tabellenreihenfolge: users → ranges → smart_boxes → device_templates → devices →
--                     release_devices → range_stewards → squads → squad_members →
--                     shooting_programs → trainings → passen → range_rounds →
--                     shot_blocks → shot_sequences → clay_results → audit_log → fire_log

-- Benutzer (Anlagebetreiber, Standwart, Schütze)
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(50) UNIQUE NOT NULL,
    email         VARCHAR(150) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20) NOT NULL,    -- ANLAGEBETREIBER | WETTKAMPFLEITER | STANDWART | SCHUETZE
    display_name  VARCHAR(100),
    qr_code       VARCHAR(64) UNIQUE,     -- Persönlicher Schützen-QR-Code (statisch, immer gültig)
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Schiessplätze
CREATE TABLE ranges (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    locked      BOOLEAN NOT NULL DEFAULT false,
    locked_by   UUID REFERENCES users(id),
    locked_at   TIMESTAMPTZ,
    lock_reason VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- SmartBoxen (Pico 2W)
CREATE TABLE smart_boxes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mac_address      VARCHAR(17) UNIQUE NOT NULL,  -- dient als boxId in MQTT-Topics
    alias            VARCHAR(100),
    ip_address       VARCHAR(45),
    status           VARCHAR(20) NOT NULL DEFAULT 'offline',  -- online | offline | error | unconfigured
    last_seen        TIMESTAMPTZ,
    firmware_version VARCHAR(20),
    mqtt_username    VARCHAR(100) UNIQUE,
    config_synced    BOOLEAN NOT NULL DEFAULT false,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Gerät-Templates (wiederverwendbare Vorlagen)
CREATE TABLE device_templates (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(100) NOT NULL,
    type              VARCHAR(50) NOT NULL,          -- TRAP | LED | BUTTON | SENSOR | ...
    box_type          VARCHAR(50) NOT NULL,          -- GPIO_PULSE | GPIO_INPUT | LED_DIGITAL | RELAY
    signal_type       VARCHAR(10) NOT NULL,          -- INPUT | OUTPUT
    signal_duration_s DECIMAL(5,2),
    description       TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Geräte (Instanzen auf einer SmartBox)
CREATE TABLE devices (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    smart_box_id      UUID NOT NULL REFERENCES smart_boxes(id) ON DELETE RESTRICT,
    template_id       UUID REFERENCES device_templates(id) ON DELETE SET NULL,
    range_id          UUID REFERENCES ranges(id) ON DELETE SET NULL,
    alias             VARCHAR(100) NOT NULL,
    pin               INTEGER,
    signal_duration_s DECIMAL(5,2),                  -- überschreibt Template-Wert
    blocked           BOOLEAN NOT NULL DEFAULT false,
    healthy           BOOLEAN NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Auslösegeräte (Tablets mit Browser-UI)
CREATE TABLE release_devices (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alias        VARCHAR(100) NOT NULL,
    range_id     UUID REFERENCES ranges(id),
    pairing_code VARCHAR(20) UNIQUE,
    last_seen    TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Zuordnung Standwart ↔ Plätze (N:M)
CREATE TABLE range_stewards (
    range_id UUID NOT NULL REFERENCES ranges(id) ON DELETE CASCADE,
    user_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (range_id, user_id)
);

-- Rotten (persistent oder ad-hoc)
CREATE TABLE squads (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    persistent  BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Mitglieder einer Rotte
CREATE TABLE squad_members (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    squad_id   UUID NOT NULL REFERENCES squads(id) ON DELETE CASCADE,
    user_id    UUID REFERENCES users(id),   -- NULL = Gast
    guest_name VARCHAR(100),
    position   INTEGER NOT NULL,
    CHECK (user_id IS NOT NULL OR guest_name IS NOT NULL)
);

-- Schiessprogramme (z.B. "Trap 25")
-- segments JSONB: [{range_id, target_count, order, device_ids[]}]
CREATE TABLE shooting_programs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(100) NOT NULL,
    is_template    BOOLEAN NOT NULL DEFAULT false,
    discipline     VARCHAR(30),          -- TRAP | SKEET | SPORTING | CUSTOM
    target_count   INTEGER NOT NULL,
    segments       JSONB NOT NULL,
    created_by     UUID REFERENCES users(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Trainingseinheiten / Wettkämpfe
CREATE TABLE trainings (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(100) NOT NULL,
    is_template      BOOLEAN NOT NULL DEFAULT false,
    is_competition   BOOLEAN NOT NULL DEFAULT false,
    passe_count      INTEGER NOT NULL DEFAULT 1,
    template_id      UUID REFERENCES trainings(id) ON DELETE SET NULL,
    default_squad_id UUID REFERENCES squads(id),
    created_by       UUID REFERENCES users(id),
    started_at       TIMESTAMPTZ,
    ended_at         TIMESTAMPTZ,
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT | ACTIVE | COMPLETED | ABORTED
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_trainings_status ON trainings(status) WHERE status = 'ACTIVE';

-- Passen innerhalb einer Trainingseinheit
-- Wird erst beim ERSTEN Wurf persistiert (startet direkt auf ACTIVE).
CREATE TABLE passen (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    training_id          UUID NOT NULL REFERENCES trainings(id) ON DELETE CASCADE,
    program_id           UUID REFERENCES shooting_programs(id),  -- NULL bei Freier Passe
    squad_id             UUID REFERENCES squads(id),
    passe_order          INTEGER NOT NULL,
    is_free_passe        BOOLEAN NOT NULL DEFAULT false,
    start_segment_offset INTEGER NOT NULL DEFAULT 0,    -- Staggered Start
    segment_overrides    JSONB,              -- [{range_id, target_count, order}]
    started_at           TIMESTAMPTZ NOT NULL,
    ended_at             TIMESTAMPTZ,
    confirmed_by         UUID REFERENCES users(id),
    confirmed_at         TIMESTAMPTZ,
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | COMPLETED | CONFIRMED | ABORTED
    UNIQUE NULLS NOT DISTINCT (training_id, squad_id, passe_order)
);
CREATE INDEX idx_passen_training_id ON passen(training_id);

-- Platz-Runden innerhalb einer Passe
-- Wird erst beim ERSTEN Wurf persistiert.
CREATE TABLE range_rounds (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    passe_id          UUID NOT NULL REFERENCES passen(id) ON DELETE CASCADE,
    range_id          UUID NOT NULL REFERENCES ranges(id),
    segment_order     INTEGER NOT NULL,
    target_count      INTEGER NOT NULL CHECK (target_count >= 1),
    release_device_id UUID REFERENCES release_devices(id),
    started_at        TIMESTAMPTZ NOT NULL,
    ended_at          TIMESTAMPTZ,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | COMPLETED
    UNIQUE (passe_id, segment_order)
);
CREATE INDEX idx_range_rounds_passe_id     ON range_rounds(passe_id);
CREATE INDEX idx_range_rounds_range_status ON range_rounds(range_id, status) WHERE status = 'ACTIVE';

-- Schussblöcke: Gruppe von Schussabgaben an einem Gerät
-- Wird erst beim ersten Wurf persistiert.
CREATE TABLE shot_blocks (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    range_round_id      UUID NOT NULL REFERENCES range_rounds(id) ON DELETE CASCADE,
    block_nr            INTEGER NOT NULL,
    primary_device_id   UUID NOT NULL REFERENCES devices(id),
    secondary_device_id UUID REFERENCES devices(id),
    shot_type           VARCHAR(30) NOT NULL,  -- EINZELTAUBE | DOPPEL_AUF_SCHUSS | DOPPEL_SIMULTAN | DOPPEL_RAFFALE
    planned_count       INTEGER CHECK (planned_count IS NULL OR planned_count >= 1),  -- NULL = Freier Block
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | COMPLETED
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (range_round_id, block_nr),
    CONSTRAINT chk_shot_type_devices CHECK (
        (shot_type = 'EINZELTAUBE'       AND secondary_device_id IS NULL) OR
        (shot_type = 'DOPPEL_RAFFALE'    AND secondary_device_id IS NULL) OR
        (shot_type = 'DOPPEL_AUF_SCHUSS' AND secondary_device_id IS NOT NULL) OR
        (shot_type = 'DOPPEL_SIMULTAN'   AND secondary_device_id IS NOT NULL)
    )
);
CREATE INDEX idx_shot_blocks_rr_id ON shot_blocks(range_round_id);

-- Schussabgaben (eine Abgabe = max. 2 Schüsse)
-- shot_type wird vom übergeordneten ShotBlock geerbt (kein redundantes Feld).
CREATE TABLE shot_sequences (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shot_block_id   UUID NOT NULL REFERENCES shot_blocks(id) ON DELETE CASCADE,
    squad_member_id UUID NOT NULL REFERENCES squad_members(id),
    sequence_nr     INTEGER NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',  -- IN_PROGRESS | COMPLETED | INTERRUPTED
    fired_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    UNIQUE (shot_block_id, sequence_nr)
);
CREATE INDEX idx_shot_sequences_block_id ON shot_sequences(shot_block_id);

-- Tauben-Ergebnisse (eine Zeile pro Taube pro Schussabgabe)
CREATE TABLE clay_results (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shot_sequence_id UUID NOT NULL REFERENCES shot_sequences(id) ON DELETE CASCADE,
    clay_nr          SMALLINT NOT NULL CHECK (clay_nr IN (1, 2)),
    device_id        UUID REFERENCES devices(id) ON DELETE SET NULL,
    hit              BOOLEAN,            -- NULL wenn no_bird ODER interrupted
    no_bird          BOOLEAN NOT NULL DEFAULT false,
    interrupted      BOOLEAN NOT NULL DEFAULT false,  -- nur möglich für clay_nr = 2
    fired_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (shot_sequence_id, clay_nr),
    CONSTRAINT chk_hit_semantics CHECK (
        (no_bird = true  AND hit IS NULL) OR
        (interrupted = true AND hit IS NULL) OR
        (no_bird = false AND interrupted = false AND hit IS NOT NULL)
    ),
    CONSTRAINT chk_interrupted_only_clay2 CHECK (
        interrupted = false OR clay_nr = 2
    )
);
CREATE INDEX idx_clay_results_seq_id ON clay_results(shot_sequence_id);

-- Audit-Log (Auslösungen, Sperren, Korrekturen etc.)
-- action-Werte: FIRE | FREE_FIRE | LOCK_RANGE | UNLOCK_RANGE | BLOCK_DEVICE |
--               TRAINING_START | TRAINING_END | PASSE_CONFIRM | SHOT_CORRECTION | ...
CREATE TABLE audit_log (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID REFERENCES users(id),  -- NULL bei Freier Nutzung
    action       VARCHAR(50) NOT NULL,
    target_type  VARCHAR(50),               -- 'PASSE' | 'DEVICE' | 'RANGE' | 'TRAINING'
    target_id    UUID,
    details_json JSONB,                     -- bei SHOT_CORRECTION: {before, after}
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);
CREATE INDEX idx_audit_log_user_id    ON audit_log(user_id);
CREATE INDEX idx_audit_log_target     ON audit_log(target_type, target_id);

-- Fire-Log: Jeder tatsächliche Wurf für Gerätestatistiken und Wartungsplanung.
-- context-Werte: FREE_FIRE | TRAINING | MANUAL
CREATE TABLE fire_log (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id        UUID REFERENCES devices(id) ON DELETE SET NULL,
    range_id         UUID REFERENCES ranges(id) ON DELETE SET NULL,
    context          VARCHAR(20) NOT NULL,    -- FREE_FIRE | TRAINING | MANUAL
    no_bird          BOOLEAN NOT NULL DEFAULT false,
    user_id          UUID REFERENCES users(id) ON DELETE SET NULL,
    shot_sequence_id UUID REFERENCES shot_sequences(id) ON DELETE SET NULL,  -- NULL bei FREE_FIRE
    fired_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_fire_log_device_fired  ON fire_log(device_id, fired_at);
CREATE INDEX idx_fire_log_range_context ON fire_log(range_id, context, fired_at);
```

---

## Entwurfshinweise

- **Datenhierarchie**: `Training` → `Passe` → `RangeRound` → `ShotBlock` → `ShotSequence` → `ClayResult`
- **Persistenz-Regel**: `passen`, `range_rounds` und `shot_blocks` werden **erst beim ersten Wurf** in die DB geschrieben. Leere Objekte existieren nicht in der DB. Jede persistierte Passe enthält garantiert min. eine Schussabgabe (`target_count >= 1` per CHECK).
- `shot_blocks.planned_count = NULL` kennzeichnet einen **Freien Block** (dynamisch wachsend, manuell beendet).
- `clay_results.interrupted = true` – Auf-Schuss durch Spotter abgebrochen (kein Ergebnis, keine Wertung). Nur `clay_nr = 2` darf diesen Status erhalten (`chk_interrupted_only_clay2`).
- `clay_results.hit = NULL` (nicht `false`) bedeutet `no_bird` – die Taube war ungültig.
- `shot_sequences` hat kein eigenes `shot_type`-Feld; der Typ wird über den übergeordneten `ShotBlock` abgeleitet.
- `passen.segment_overrides` erlaubt Abweichung vom Programm-Template pro Passe ohne Template-Änderung.
- `passen.start_segment_offset` ermöglicht gestaffelten Wettkampfstart (Offset 0 = Segment 1, Offset 1 = Segment 2 usw.).
- `passen` verwendet `UNIQUE NULLS NOT DISTINCT (training_id, squad_id, passe_order)` (PostgreSQL 15+) – verhindert Duplikate auch bei `squad_id = NULL`.
- `users.qr_code` – statischer Schützen-QR-Code. Beim Scan erkennt das System das aktive Training automatisch (max. 1 pro Schütze).
- `trainings.default_squad_id` – primäre Rotte. Bei Wettkämpfen mit mehreren Rotten wird pro Passe `passen.squad_id` verwendet.
- `trainings.template_id` – verweist auf das Quelltemplate. `NULL` wenn kein Template.
- `smart_boxes.mac_address` dient als `boxId` in MQTT-Topics (normalisiertes Format ohne Doppelpunkte, z.B. `AABBCCDDEEFF`).
- `smart_boxes.config_synced` – `false` nach Config-Push, `true` nach `config/ack`. Bei Discovery prüft Backend dieses Flag für Auto-Resync.
- `devices.signal_duration_s` überschreibt den Template-Wert falls gesetzt. Config-Push übermittelt effektiven Wert als `signal_duration_ms`.
- `devices.template_id = NULL` erlaubt manuell angelegte Geräte ohne Template-Basis.
- `fire_log` – strukturierte Daten für Gerätestatistiken (Würfe/Gerät, No-Bird-Rate). Der `audit_log` bleibt für sicherheits- und rollenbasierte Ereignisse.
- `audit_log.target_type` + `target_id` ersetzen ein früheres freies `target`-Feld. Filterung per Index `idx_audit_log_target`.
- Bei Freier Nutzung (`FREE_FIRE`) ist `user_id` im `audit_log` `NULL` – anonyme Nutzung ist explizit vorgesehen.
- Alle Zeitstempel sind `TIMESTAMPTZ` (UTC gespeichert, Zeitzonen-sicher).
