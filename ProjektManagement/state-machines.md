# SmartRange – State Machines

> Zustandsmaschinen für alle Entitäten der Datenhierarchie.
> Eingebunden von [project.md](project.md).

---

## Grundprinzipien

- **Persistenz-Regel**: `passe`, `range_round`, `shot_block` werden erst beim Übergang in `ACTIVE` in die DB geschrieben (= beim ersten Wurf). Davor leben sie in-memory im `TrainingService`.
- **Autorität**: Backend entscheidet alle Transitionen. UI-Aktionen sind Vorschläge, nie direkte DB-Writes.
- **Audit**: Jede manuelle Transition (LOCK, CONFIRM, ABORT, CORRECTION) erzeugt einen `audit_log`-Eintrag.
- **Propagation**: Transitionen propagieren von unten nach oben: `ShotSequence COMPLETED` → kann `ShotBlock COMPLETED` auslösen → `RangeRound COMPLETED` → `Passe COMPLETED`. Bottom-up geschieht automatisch; top-down nur durch explizite Aktionen.
- Der `TrainingService` ist die einzige Komponente, die Status-Felder schreibt.

---

## 1. Training

```
    ┌─────────┐   start()           ┌─────────┐   end()         ┌───────────┐
    │  DRAFT  │ ──────────────────► │ ACTIVE  │ ───────────────►│ COMPLETED │
    └─────────┘                     └────┬────┘                 └───────────┘
                                         │ abort()
                                         ▼
                                    ┌─────────┐
                                    │ ABORTED │
                                    └─────────┘
```

| Transition | Auslöser | Wer darf | Bedingung |
|---|---|---|---|
| `DRAFT → ACTIVE` | `POST /api/trainings/{id}/start` | Ersteller, Admin, Wettkampfleiter | Min. 1 Schütze; kein anderes aktives Training für die beteiligten Schützen |
| `ACTIVE → COMPLETED` | Letzte Passe CONFIRMED (Wettkampf) oder `POST /api/trainings/{id}/end` | Ersteller, Admin, Wettkampfleiter | Keine Passe mehr im Zustand `ACTIVE` |
| `ACTIVE → ABORTED` | `POST /api/trainings/{id}/abort` | Admin, Wettkampfleiter | — (Rest-Passen werden ebenfalls `ABORTED`) |

---

## 2. Passe

```
    (in-memory: DRAFT) ──first_fire()──► ┌────────┐  complete()  ┌───────────┐  confirm() ┌───────────┐
                                          │ ACTIVE │─────────────►│ COMPLETED │────────────►│ CONFIRMED │
                                          └───┬────┘              └─────┬─────┘            └───────────┘
                                              │                         │ (nur Wettkampf;
                                              │ abort()                 │  Training überspringt direkt nach COMPLETED)
                                              ▼                         ▼
                                          ┌─────────┐              (terminal)
                                          │ ABORTED │
                                          └─────────┘
```

| Transition | Auslöser | Wer darf | Bedingung |
|---|---|---|---|
| `DRAFT → ACTIVE` | Erste Schussabgabe im ersten Block | automatisch | Training `ACTIVE`; Rotte hat keine andere `ACTIVE`-Passe |
| `ACTIVE → COMPLETED` | Letzter RangeRound `COMPLETED` (Programm-Passe) oder `POST /api/passen/{id}/end` (Freie Passe) | automatisch / Schütze / Standwart | Alle RangeRounds abgeschlossen |
| `COMPLETED → CONFIRMED` | `POST /api/passen/{id}/confirm` | Wettkampfleiter | Nur wenn `is_competition = true` |
| `ACTIVE → ABORTED` | `POST /api/passen/{id}/abort` | Admin, Wettkampfleiter, Standwart | — |

Ergebnis-Korrekturen nach `CONFIRMED` sind gesperrt. Korrekturen zwischen `COMPLETED` und `CONFIRMED` nur durch Wettkampfleiter; jede Korrektur erzeugt `SHOT_CORRECTION`-Audit-Eintrag mit `{before, after}`.

---

## 3. RangeRound

```
    (in-memory) ──first_fire()──► ┌────────┐  complete()  ┌───────────┐
                                   │ ACTIVE │─────────────►│ COMPLETED │
                                   └────────┘              └───────────┘
```

| Transition | Auslöser | Wer darf | Bedingung |
|---|---|---|---|
| → `ACTIVE` | Erste Schussabgabe auf diesem Platz | automatisch | Platz nicht gesperrt; keine andere `ACTIVE`-RangeRound auf demselben Platz |
| `ACTIVE → COMPLETED` | `target_count` erreicht (Programm-Passe) oder `POST /api/range-rounds/{id}/complete` (Freie Passe) | automatisch / Schütze / Standwart | Keine `ACTIVE`-ShotBlocks mehr |

Freigabe des Platzes für die nächste Rotte erst bei `COMPLETED`.

---

## 4. ShotBlock

```
    (in-memory) ──first_fire()──► ┌────────┐  complete()  ┌───────────┐
                                   │ ACTIVE │─────────────►│ COMPLETED │
                                   └────────┘              └───────────┘
```

| Transition | Auslöser | Wer darf | Bedingung |
|---|---|---|---|
| → `ACTIVE` | Erste ShotSequence | automatisch | RangeRound `ACTIVE` |
| `ACTIVE → COMPLETED` | `planned_count` erreicht (Fixer Block) oder `POST /api/shot-blocks/{id}/complete` (Freier Block) | automatisch / Schütze | Alle ShotSequences `COMPLETED` oder `INTERRUPTED` |

---

## 5. ShotSequence

```
    ┌─────────────┐   fire_clay1+[clay2] / result()   ┌───────────┐
    │ IN_PROGRESS │ ─────────────────────────────────►│ COMPLETED │
    └──────┬──────┘                                    └───────────┘
           │ interrupt() (nur DOPPEL_AUF_SCHUSS, No-Bird auf Taube 1)
           ▼
    ┌─────────────┐
    │ INTERRUPTED │
    └─────────────┘
```

| Transition | Auslöser | Wer darf | Bedingung |
|---|---|---|---|
| `(neu) → IN_PROGRESS` | `POST /api/shot-blocks/{id}/fire` | Schütze, Standwart, Spotter | Block `ACTIVE`, Platz nicht gesperrt, Gerät gesund |
| `IN_PROGRESS → COMPLETED` | `PUT /api/shot-blocks/{id}/result` für alle Tauben | Schütze, Standwart, Spotter | `clay_results` vollständig für alle erwarteten `clay_nr` |
| `IN_PROGRESS → INTERRUPTED` | `POST /api/shot-blocks/{id}/interrupt` | Spotter | Nur bei `shot_type = DOPPEL_AUF_SCHUSS`, noch kein `fire_clay2` |

Eine `INTERRUPTED`-Sequenz wird **nicht gewertet**, aber im Audit-Log gezählt. Wiederholung folgt als neue Sequenz.
