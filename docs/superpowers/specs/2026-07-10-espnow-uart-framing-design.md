# Design: UART-Framing Node ↔ Funkmodul (Baustein D)

**Datum:** 2026-07-10
**Bezug:** [plan-espnow-migration.md](../../plan-espnow-migration.md) Phase 0 Punkt 3; [2026-07-09-espnow-protocol-contracts-design.md](2026-07-09-espnow-protocol-contracts-design.md) Abschnitt 6 (UART-Protokoll Node ↔ Funkmodul); baut auf Baustein A–C nicht direkt auf (eigene Schicht), liefert aber den Transportrahmen, in dem spätere `SEND`/`RECV`-Kommandos die Frames aus Baustein B/C roh transportieren
**Status:** Entwurf, zur Review

## Ziel

Bausteine A–C haben die ESP-NOW-Frame-Ebene (Box ↔ Node, verschlüsselt) in Java und MicroPython implementiert. Baustein D adressiert eine andere Ebene: das Kabel zwischen `smart-ground-node` (Java) und dem ESP32-Funkmodul (dumme Pipe, ADR-002) — Framing, Kommando-/Ereignis-Codierung und CRC-Absicherung des seriellen Bytestroms selbst. Wie bei A–C ist dies eine reine Codec-/Framing-Schicht, host-testbar ohne echte Hardware.

**Abweichung vom bisherigen Muster:** Anders als A–C wird hier **nur die Node-Seite (Java)** implementiert. Es existiert noch keine Funkmodul-Firmware im Repo (Phase 2a des Migrationsplans, eigenständiges späteres Arbeitspaket) — deren Implementierung ist bewusst nicht Teil dieses Bausteins. Ohne eine zweite Implementierung entfällt auch die kanonische Cross-Language-Fixture-Pipeline (Node.js-Generierung + Python-Gegenprüfung) aus A–C; stattdessen werden CRC-Korrektheit gegen einen publizierten Standard-Prüfwert und Frame-Layouts gegen von Hand nachgerechnete Beispiele verifiziert (siehe Testing-Ansatz).

Der Protokoll-Spec (Abschnitt 6) legt das Framing, die fünf Node→Radio-Kommandos und die zwei Radio→Node-Ereignisse bereits fest, definiert aber keine konkreten `cmd`-Byte-Werte und lässt das exakte Verhältnis zwischen der generischen `ACK`-Antwort und der `STATUS`-Antwort offen. Dieses Dokument schliesst diese Lücken.

## Architektur

Zwei Komponenten in `ch.jp.shooting.node.uart`:

- **`UartCodec`** (zustandslos) — baut Kommando-Frames (`encodeSetChannel`/`encodeAddPeer`/`encodeDelPeer`/`encodeSend`/`encodeStatus`) und parst Antwort-/Ereignis-Bodies (`parseAck`/`parseStatusAck`/`parseRecv`/`parseMacAck`) aus bereits decodierten Frames.
- **`UartFrameDecoder`** (zustandsbehaftet) — `feed(byte[] chunk) -> List<DecodedFrame>`. Ein echter serieller Port liefert beliebig fragmentierte Byte-Chunks ohne natürliche Frame-Grenzen; der Decoder puffert intern, sucht Start-Bytes, wartet bei unvollständigen Frames auf weitere Daten und synchronisiert sich nach CRC-Fehlern oder unplausiblen Längenangaben auf das nächste `0x7E` — exakt das im Protokoll-Spec (Abschnitt "Testing-Ansatz") geforderte Verhalten ("Bit-Fehlern/Fragmentierung mitten im Frame ... Resync auf das nächste 0x7E").
- **`Crc16`** — eigenständige Utility-Klasse, `ccittFalse(byte[] data) -> int`.

`SEND`s `esp_now_frame`-Parameter und `RECV`s `esp_now_frame`-Feld sind für `UartCodec` reine `byte[]` ohne jede Kenntnis von `FrameHeader`/`OperationalCodec`/`PairingCodec` (Baustein B/C) — klare Schichtentrennung, die UART-Schicht transportiert nur rohe Bytes.

**Explizit nicht Teil dieses Bausteins** (Business-/Infrastruktur-Logik, spätere Phasen):
- Echte serielle I/O (`jSerialComm`), `RadioInterface`-Abstraktion (Phase 2b)
- Timeout-/Retry-State-Machine für ausbleibende `ACK`s (Phase 2b)
- `STATUS`-Poll-Intervall und Schwellenwert für „Funkmodul hängt" (Phase 3)
- Funkmodul-Firmware (Phase 2a)

## Framing

Unverändert aus dem Protokoll-Spec (Abschnitt 6), hier nur referenziert:

```
Offset  Size  Feld
0       1     start-byte (0x7E, fix)
1       2     length      (uint16 LE, Länge von cmd_id..body)
3       1     cmd_id      (uint8, vom Node vergeben, Rundlauf-Zähler)
4       1     cmd         (Enum, siehe Kommando-Register unten)
5       N     body        (cmd-spezifisch)
5+N     2     crc16       (CRC-16/CCITT-FALSE über cmd_id..body, d. h. Offset 3..4+N)
```

Kein Byte-Stuffing: Da `body` längenpräfixiert (nicht durch ein Terminator-Byte begrenzt) ist, kann `0x7E` an beliebiger Stelle in `body`/`crc16` auftreten, ohne die Rahmung zu stören — das Start-Byte dient ausschliesslich als Resync-Anker nach Korruption, nicht als Body-Trenner.

## Kommando-Register (neu in diesem Dokument)

Der Protokoll-Spec benennt die Kommandos/Ereignisse, legt aber keine `cmd`-Byte-Werte fest. Direktion wird im Wertebereich codiert (Node→Radio niedrig, Radio→Node ab `0x80`), damit ein korrumpiertes `cmd`-Byte beim Debuggen sofort als plausibel/unplausibel für die erwartete Richtung erkennbar ist:

| cmd | Wert | Richtung | body |
|---|---|---|---|
| `SET_CHANNEL` | `0x01` | Node→Radio | `channel(1)` |
| `ADD_PEER` | `0x02` | Node→Radio | `mac(6)` |
| `DEL_PEER` | `0x03` | Node→Radio | `mac(6)` |
| `SEND` | `0x04` | Node→Radio | `dest_mac(6) ‖ esp_now_frame(N)` |
| `STATUS` | `0x05` | Node→Radio | — |
| `ACK` | `0x80` | Radio→Node | siehe unten |
| `RECV` | `0x81` | Radio→Node | `src_mac(6) ‖ rssi(1) ‖ esp_now_frame(N)` |
| `MAC_ACK` | `0x82` | Radio→Node | `dest_mac(6) ‖ frame_id(2) ‖ ok(1)` |

`0x06`–`0x7F` bleiben für künftige Node→Radio-Kommandos frei, `0x83`–`0xEF` für künftige Radio→Node-Ereignisse; `0xF0`–`0xFF` sind laut Protokoll-Spec für Diagnose-/Relay-Steuerframes (Phase 5) reserviert.

## `ACK`-Antwort — Vereinheitlichung mit `STATUS`

Der Protokoll-Spec beschreibt zwei scheinbar unterschiedliche Antwort-Bodies: die generische `ACK(cmd_id:1, ok:1)` für jedes Kommando, und für `STATUS` speziell `uptime_s(4) ‖ free_heap(4)`. Beides wird hier auf **eine** Antwort-Cmd (`ACK`, `0x80`) vereinheitlicht — die äussere `cmd_id` des Antwort-Frames spiegelt die `cmd_id` der Anfrage (Korrelation über das äussere Feld allein möglich); der Body ist immer `cmd_id(1) ‖ ok(1)`, für `STATUS` um `uptime_s(4) ‖ free_heap(4)` verlängert:

```
ACK-Body (SET_CHANNEL/ADD_PEER/DEL_PEER/SEND): cmd_id(1) ‖ ok(1)
ACK-Body (STATUS):                             cmd_id(1) ‖ ok(1) ‖ uptime_s(4) ‖ free_heap(4)
```

`UartCodec.parseAck` liefert immer `cmd_id`/`ok`; `parseStatusAck` erwartet zusätzlich die zwei 4-Byte-Felder und wirft, wenn der Body dafür zu kurz ist (kein `STATUS`-Antwort-Body). Aufrufer, die nur den generischen `ok`-Flag brauchen, ignorieren die zusätzlichen Bytes.

## CRC-16/CCITT-FALSE

Parameter: `poly=0x1021`, `init=0xFFFF`, keine Reflektion, kein XOR-Out. Diese konkrete Variante war im Protokoll-Spec ("CRC-16/CCITT") nicht eindeutig benannt — "CRC-16/CCITT" deckt mehrere inkompatible Parametrisierungen ab (u. a. XMODEM mit `init=0x0000`, KERMIT mit Bit-Reflektion). CCITT-FALSE wird hier festgelegt, weil sie ohne Bit-Reversal-Tabellen auskommt und der verbreitetste Default für serielle Framing-Protokolle dieser Art ist.

Verifiziert gegen den publizierten Standard-Prüfwert (`CRC-16/CCITT-FALSE` des ASCII-Strings `"123456789"` → `0x29B1`) statt gegen eine generierte Fixture-Datei — siehe Testing-Ansatz.

## `UartFrameDecoder` — Resync-Algorithmus

1. Eingehende Bytes werden an einen internen, wachsenden Puffer angehängt.
2. Der Puffer wird nach dem nächsten `0x7E` durchsucht; Bytes davor werden verworfen (kein gültiger Frame-Start).
3. Ist `0x7E` gefunden, aber der Puffer enthält noch nicht genug Bytes für das `length`-Feld: warten (Rückgabe leer, Puffer bleibt ab `0x7E` erhalten).
4. `length` gelesen (deckt `cmd_id ‖ cmd ‖ body` ab, siehe Framing-Tabelle). Übersteigt es eine Plausibilitätsgrenze (deutlich über der maximalen ESP-NOW-Framegrösse von 250 Byte plus Kommando-Overhead — Konstante `MAX_FRAME_LENGTH`, grosszügig z. B. 512), gilt das `0x7E` als Fehltreffer: ein Byte verwerfen, weiter bei Schritt 2.
5. Ist der volle Frame (`length` + Header + CRC) noch nicht vollständig im Puffer: warten (wie Schritt 3).
6. Vollständiger Frame vorhanden: CRC über `cmd_id..body` berechnen und mit dem übertragenen `crc16` vergleichen.
   - **Match:** `DecodedFrame(cmdId, cmd, body)` in die Ergebnisliste aufnehmen, Puffer um den kompletten Frame vorrücken, weiter bei Schritt 2 (mehrere Frames pro `feed()`-Aufruf möglich).
   - **Mismatch:** dieses `0x7E` war vermutlich kein echter Frame-Start (oder der Frame ist korrumpiert) — ein Byte verwerfen, weiter bei Schritt 2 (Resync).
7. `feed()` kehrt zurück, sobald der Puffer keine weiteren vollständigen Frames mehr hergibt; alle in diesem Aufruf decodierten Frames werden als Liste zurückgegeben.

## Testing-Ansatz

Kein gemeinsames Fixture-Format wie bei A–C (Begründung siehe Ziel-Abschnitt). Stattdessen:

- **`Crc16Test`**: Standard-Prüfwert (`"123456789"` → `0x29B1`) plus die CRC-Werte der Beispiel-Frames aus `UartCodecTest` (Cross-Check zwischen den beiden Testklassen).
- **`UartCodecTest`**: Encode/Decode-Rundlauf pro Kommando/Ereignis, byte-exakt gegen von Hand nachgerechnete Beispiel-Frames (analog zu den bisherigen Bausteinen, nur ohne externe Fixture-Datei — die Beispielwerte leben direkt im Testcode).
- **`UartFrameDecoderTest`**: einzelner vollständiger Frame; mehrere Frames in einem `feed()`-Aufruf; ein Frame auf zwei `feed()`-Aufrufe verteilt (Fragmentierung); Müll-Bytes vor einem gültigen Frame; ein CRC-korrupter Frame gefolgt von einem gültigen Frame (Resync-Nachweis); ein `length`-Feld weit über der Plausibilitätsgrenze (Fehltreffer-Behandlung).

## Scope-Abgrenzung

Dieses Dokument/die folgende Implementierung deckt **nur** Encode/Decode + CRC-Absicherung des UART-Framings ab. **Nicht enthalten:**
- Funkmodul-Firmware (Phase 2a, eigenes späteres Arbeitspaket)
- Echte serielle I/O (`jSerialComm`), `RadioInterface`-Abstraktion, Box→Radio-Tabelle (Phase 2b)
- Timeout-/Retry-Logik für ausbleibende `ACK`s, `STATUS`-Poll-Intervall/Health-Check-Schwelle (Phase 2b/3)
- `0xF0`–`0xFF`-Diagnose-/Relay-Steuerframes (Phase 5)
