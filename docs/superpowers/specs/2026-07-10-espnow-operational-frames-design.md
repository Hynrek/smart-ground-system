# Design: ESP-NOW-Betriebs-Frames + kompakte Capability-Codierung (Baustein C)

**Datum:** 2026-07-10
**Bezug:** [plan-espnow-migration.md](../../plan-espnow-migration.md) Phase 0 Punkt 1; [2026-07-09-espnow-protocol-contracts-design.md](2026-07-09-espnow-protocol-contracts-design.md) Abschnitt 4 (Betriebs-Frames); baut auf Baustein A ([2026-07-09-espnow-crypto-foundation.md](../plans/2026-07-09-espnow-crypto-foundation.md)) und Baustein B ([2026-07-10-espnow-frame-pairing-codec.md](../plans/2026-07-10-espnow-frame-pairing-codec.md))
**Status:** Entwurf, zur Review

## Ziel

Baustein B hat den Klartext-Routing-Header und die Pairing-Frames (DISCOVER/OFFER/CONFIRM, unter `K_Box`) in Java und MicroPython implementiert. Baustein C erweitert das auf die sechs Betriebs-Frame-Typen (unter `K_S`, dem beim Pairing abgeleiteten Session-Key): `DISCOVERY`, `CONFIG`, `CONFIG_ACK`, `COMMAND`, `EXECUTED`, `HEARTBEAT`. Wie schon in Baustein A/B ist dies eine reine Codec-Schicht — Bauen/Parsen von Byte-Layouts, verifiziert gegen eine gemeinsame Fixture — keine Node-/Box-Geschäftslogik (Zähler-Sequenzierung, MQTT-Übersetzung, Timeout-State-Machines).

Der ursprüngliche Protokoll-Spec (Abschnitt 4) legt fünf der sechs Typen bereits vollständig auf Byte-Ebene fest. Offen war die Codierung der `capabilities`-Map in `DISCOVERY` — dieses Dokument schliesst diese Lücke.

## Architektur

Ein zustandsloser `OperationalCodec` (Java `ch.jp.shooting.node.operational`, MicroPython `operational_codec.py`) komponiert den bestehenden `FrameHeader`/`FrameType`-Codec (Baustein B) mit der `AesGcm`-Primitive (Baustein A), um die sechs Betriebs-Frame-Typen unter `K_S` zu bauen/parsen. Der Zähler-Nonce wird — wie schon bei `PairingCodec` — vom Aufrufer übergeben, nicht vom Codec verwaltet; echte Sequenzierung/Persistenz ist Phase-2b/2d-Sache.

Gemeinsamer Wrapper (Abschnitt 4 des Protokoll-Specs, hier nur referenziert, nicht neu definiert):

```
body = counter_nonce(4) ‖ ciphertext ‖ tag(16)
ciphertext, tag = AES-256-GCM-Encrypt(K_S, nonce = 0x0000000000000000 ‖ counter_nonce, aad = header, plaintext = <typ-spezifischer Klartext-Body>)
```

`counter_nonce` ist 4 Byte; der volle GCM-Nonce ist 12 Byte — 8 führende Null-Bytes gefolgt vom 4-Byte-Zähler ("padded auf 12 Byte mit führenden Nullen", Protokoll-Spec Abschnitt 4). Das unterscheidet sich von der Pairing-Nonce-Auffüllung (`PAIR_OFFER`, Baustein B), wo ein 8-Byte-Nonce mit 4 führenden Null-Bytes auf 12 Byte aufgefüllt wird — hier ist es umgekehrt: 4-Byte-Zähler mit 8 führenden Null-Bytes.

## Frame-Katalog

### Direkt aus dem Protokoll-Spec übernommen (keine offenen Fragen)

- **`CONFIG_ACK`, `HEARTBEAT`**: leerer Klartext-Body.
- **`COMMAND`**: `command_id(16) ‖ device_id(16) ‖ command(1, ON=0/OFF=1/BLOCK=2/UNBLOCK=3) ‖ signal_duration_ms(2, uint16 LE)`.
- **`EXECUTED`**: `command_id(16) ‖ device_id(16)`.
- **`CONFIG`**: `device_id(16) ‖ device_index(1) ‖ device_count(1) ‖ alias_len(1) ‖ alias ‖ device_type(1, GPIO=0/LED=1) ‖ direction(1, IN=0/OUT=1) ‖ command_len(1) ‖ command ‖ signal_duration_ms(2, uint16 LE) ‖ blocked(1)`.

### `DISCOVERY` — kompakte Capability-Codierung (neu in diesem Dokument)

Enum-basiert, konsistent mit den bereits im Spec festgelegten Enums für `device_type`/`direction` in `CONFIG`:

```
Offset  Size  Feld
0       2     app_version           major(1) ‖ minor(1), geparst aus dem "X.Y"-String in firmware_config.json
2       1     config_schema_version numerisches Byte, geparst aus dem String-Feld
3       1     box_type_len
4       N     box_type            UTF-8-codierter String, keine Terminierung (Länge kommt aus box_type_len)
4+N     1     device_type_count
                pro Geräte-Typ:
                  1     device_type_id      GPIO=0, LED=1 (gleiches Enum wie CONFIG.device_type)
                  1     directions_bitmask  bit0=INPUT, bit1=OUTPUT
                  1     commands_bitmask    bit0=ON, bit1=OFF (BLOCK/UNBLOCK sind keine Capability-Commands, siehe smart-box/CLAUDE.md)
                  1     config_field_count
                          pro Feld:
                            1       field_id      Enum, aktuell nur SIGNAL_DURATION_MS=0
                            1       type_id       Enum: INT=0, BOOL=1, STRING=2
                            1       default_len
                            N       default_bytes  INT: 4 Byte LE-Int32; BOOL: 1 Byte (0/1); STRING: N Byte UTF-8
```

Neue Geräte-Typen, Config-Felder oder Commands erfordern einen `config_schema_version`-Bump plus ein Codec-Update auf beiden Seiten — dieselbe Kompatibilitäts-Regel, die die Firmware für `device_config.json` bereits durchsetzt (`smart-box/CLAUDE.md`, Abschnitt Capability-Manifest).

**Enum-Register** (Ausgangswerte, erweiterbar per Schema-Version-Bump):
- `device_type_id`: `GPIO=0`, `LED=1`
- `directions_bitmask`: `INPUT=0x01`, `OUTPUT=0x02`
- `commands_bitmask`: `ON=0x01`, `OFF=0x02`
- `field_id`: `SIGNAL_DURATION_MS=0`
- `type_id`: `INT=0`, `BOOL=1`, `STRING=2`

## Testing-Ansatz

Gleiches Muster wie Baustein A/B: eine kanonische Fixture `docs/espnow/operational-test-vectors.json`, erzeugt via Node.js' natives `crypto`-Modul und unabhängig gegen `espnow_crypto.py` gegenzeprüft, mit je einem vollständigen Beispiel pro Frame-Typ plus mindestens einem `DISCOVERY`-Beispiel mit mehreren Geräte-Typen (GPIO + LED) zur Absicherung der verschachtelten Capability-Struktur. Java- und MicroPython-Codec werden beide Byte-für-Byte gegen dieselbe Fixture getestet (analog zu den Tasks 1–6 aus Baustein B).

## Scope-Abgrenzung

Dieses Dokument/die folgende Implementierung deckt **nur** Encode/Decode + AES-GCM-Wrapping der sechs Betriebs-Frame-Typen ab. **Nicht enthalten:**
- Zähler-Nonce-Persistenz/-Sequenzierung (Phase 2b/2d)
- Node-seitige MQTT-Übersetzungslogik
- `CONFIG`-Mehr-Geräte-Akkumulation/Timeout-State-Machine (Box-Seite, Phase 2d)
- Heartbeat-/Offline-Erkennungs-Timer (Phase 2b)
- UART-Protokoll Node↔Funkmodul (Baustein D, eigenes Folgedokument)
