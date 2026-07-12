# Firmware Lean Pass — Design

**Date:** 2026-06-26
**Component:** smart-box firmware (MicroPython, Pico 2W) + minor smart-ground-backend MQTT-contract cleanup
**Status:** Approved

## Goal

Resolve the findings from the firmware architecture review. The unifying theme is
**lean firmware**: delete checks that don't earn their keep, push timing logic to the
backend, remove the only blocking calls from the main loop, and add a test harness so
the changed logic is verifiable off-hardware.

Net effect: a smaller security surface, a single-phase non-blocking scheduler, a
heartbeat-based liveness model (backend already supports it), watchdog-backed hang
recovery, and zero blocking `time.sleep_ms()` in the hot path.

## Scope summary

| # | Change | Repo |
|---|--------|------|
| 0 | Host test harness (`tests/`, stdlib `unittest` + fake modules) | firmware |
| 1 | Delete rate limiter → replace with scheduler **busy-reject** | firmware |
| 2 | Delete **auto-block-after-5-failures** | firmware |
| 3 | One-phase non-blocking scheduler; remove `delay_ms` from firmware | firmware |
| 4 | Discovery → **heartbeat split** (boot-only discovery + 20 s status heartbeat) | firmware |
| 5 | `machine.WDT` in the main loop | firmware |
| 6 | Small cleanups (`broker_port`, `_blocked_devices` prune, publish signatures, QoS doc) | firmware |
| 7 | MQTT-contract cleanup: drop delay fields from config-push + command payloads | backend |

Backend liveness (status handler + stale-offline sweep) and config propagation
(REST-triggered pushes) are **already implemented** — verified during brainstorming —
so the heartbeat split needs no backend code.

---

## 0. Test harness — `smart-box/tests/`

No pytest dependency. Use the stdlib `unittest` runner (Python 3.14 present on the dev
machine).

A bootstrap (`tests/_stubs.py`, imported first in each test or via a shared base)
installs fake modules into `sys.modules` **before** importing firmware modules:

- `machine` — `Pin` records `value()`/`toggle()` calls; `WDT` is a no-op recording
  `feed()`; `reset()` sets a flag.
- `micropython` — `const = lambda x: x`.
- `network` — minimal `WLAN` stub returning a fixed MAC/IP.
- `umqtt.simple` — `FakeMQTTClient` capturing `publish(topic, payload)` and
  `subscribe(topic)` calls; invokes the registered callback on demand.
- `time` — a controllable monotonic clock exposing `ticks_ms`, `ticks_add`,
  `ticks_diff`, `sleep_ms`, `sleep`, `time`, plus a test helper `advance(ms)`. No real
  sleeping; timing is deterministic.

Tests cover the logic we change:

- **Security** — allowlist rejects unknown IDs; admin BLOCK/UNBLOCK; busy-reject of ON
  while active; OFF cancels while active.
- **Scheduler** — ON turns pin/LED on and schedules OFF; `tick()` expires the pulse at
  the right tick; second ON while busy is ignored; LED rejects `value=0`.
- **Config parse** — `_handle_config` builds the device map; ACK only when ≥1 device
  initialised; `_update_known_devices` prunes stale entries (incl. `_blocked_devices`).
- **Heartbeat/discovery** — payload shapes (`{"mac", "firmwareVersion", "boxType",
  "ip"}` for discovery; `{"mac"}` for heartbeat).

Hardware-only wiring (real GPIO toggling, real WDT, real WiFi) is **not** unit-tested;
it gets a manual Pico 2W smoke test at the end (see Verification).

---

## 1. Delete the rate limiter → busy-reject

The rate limiter (`RATE_LIMIT_S = 0.1`) compared against `time.time()`, which is
**1-second resolution** on bare-metal MicroPython — so the 100 ms window never worked,
and a rate-limit rejection fed the auto-block counter (5 rapid legit commands → 5-min
lockout). The real constraint for an actuator is "don't re-trigger while it's working,"
which the scheduler state already expresses.

**Remove:** `_last_command_time`, `_check_rate_limit()`, `RATE_LIMIT_S`, and the
rate-limit branch in `message_callback`.

**Add:** busy-reject in the scheduler (see §3). A device is **busy** when it has an
active scheduled action (`device_id in _scheduled`). An ON command to a busy device is
ignored (logged, no `/executed` ACK — the backend's ACK timeout handles it). OFF is
never busy-blocked; it cancels.

`message_callback` ON/OFF path after the change:

```
if device_id not in _known_devices: return            # allowlist
if _is_device_blocked(device_id):    return            # admin block only
success = gpio_manager.set(device_id, 1|0, signal_duration_ms)
if success: _send_device_command_ack(device_id)        # accept-time ACK
# busy/blocked/unknown-to-gpio → success False → no ACK, just logged
```

---

## 2. Delete auto-block-after-5-failures

The control was ineffective and harmful: unknown IDs are already rejected by the
allowlist *before* the block check, so auto-blocking them does nothing but grow
`_failed_commands`/`_blocked_devices` unbounded (memory-DoS vector, since unknown IDs
are never pruned). For known devices it only fired on `set()` returning False — i.e.
misconfiguration/protocol misuse (bad pin, config-blocked, or sending OFF to an LED
five times locked the device for 5 minutes).

**Remove:** `_failed_commands`, `MAX_FAILED_ATTEMPTS`, `BLOCK_DURATION_S`,
`_record_failed_attempt()`, `_clear_failed_attempts()`, and the auto-expiry branch of
`_is_device_blocked()`.

`_is_device_blocked(device_id)` collapses to:

```python
def _is_device_blocked(device_id):
    # Nur noch administrative Blockierung (ADMIN-Token) – keine Auto-Blockierung mehr
    return _blocked_devices.get(device_id) == ADMIN_BLOCK_TOKEN
```

`_blocked_devices` stays (admin blocks only). `_update_known_devices` prunes stale
entries from it (§6). Resulting security model: **allowlist + admin BLOCK/UNBLOCK**,
with busy-reject as an operational guard.

> Note: this removes a control documented in `smart-box/CLAUDE.md`. The removal is a
> deliberate, approved decision (the control provided near-zero real protection); the
> CLAUDE.md security section is rewritten to match (§ Documentation).

---

## 3. One-phase non-blocking scheduler; remove `delay_ms`

### Delay removed from firmware

`delay_ms` was applied via a **blocking** `time.sleep_ms()` in `GpioManager.set()`,
freezing the whole loop (no `check_msg`, no keep-alive, no pulse expiry). Per the
"logic lives in the backend, firmware stays lean" decision, delay is removed from the
firmware entirely rather than made non-blocking.

Firmware stops reading/storing delay: drop `delay_ms` from the device map in
`setup()`, from `save_device_config`/`load_device_config` round-tripping (the field is
simply ignored if present), and drop `delaySignalDurationMs` from command parsing in
`message_callback`. The `delay_ms` / `delay` parameter is removed from
`GpioManager.set()`.

### Scheduler becomes single-phase

`_pulse_active` (`device_id → end_ticks`) is kept as the single source of "busy" and
renamed conceptually to a scheduled-OFF map (name can stay `_pulse_active`). There is
no longer a delayed-ON phase, so one entry per device with an end-tick suffices.

`GpioManager.set(device_id, value, signal_duration_ms=None)`:

- `entry = self._devices.get(device_id)`; `None` or `entry["blocked"]` → return False.
- LED + `value == 0` → return False (unchanged rule).
- **Busy-reject:** `value == 1` and `device_id in self._pulse_active` → return False
  (ignore re-trigger of an in-flight pulse).
- `effective_duration = signal_duration_ms if not None else entry["signal_duration_ms"]`.
- `value == 1` and `effective_duration > 0`: drive pin/LED HIGH, set
  `_pulse_active[device_id] = ticks_add(ticks_ms(), effective_duration)`, return True.
- `value == 1`, no duration: drive HIGH, ensure no scheduled OFF, return True.
- `value == 0`: drive LOW, `_pulse_active.pop(device_id, None)` (cancel), return True.

`update_pulses()` → renamed **`tick()`**: iterate `_pulse_active`, expire entries whose
`ticks_diff(now, end) >= 0`, drive the pin/LED LOW, drop the entry. (Logic identical to
today's `update_pulses`, just renamed.) `mqttutils.update_device_pulses()` and the
`main.py` call site are updated to call `tick()`; no blocking sleep anywhere.

---

## 4. Discovery → heartbeat split (firmware-only)

Today `main.py` re-publishes `smartboxes/discovery` every 20 s, and the backend pushes
config on **every** discovery (`SmartBoxDiscoveryHandler` → `configPushService.push`).
That means a full `gpio_manager.reset()` (all pins → 0, any in-flight pulse killed) +
flash rewrite every 20 s.

The backend already has the correct liveness path: `SmartBoxStatusHandler` consumes
`smartboxes/{mac}/status` (`{"mac": …}`), refreshes `lastSeen` + ONLINE, and a
`@Scheduled markStaleBoxesOffline()` marks boxes OFFLINE after 30 s. Config changes
already push immediately from the REST controllers. The firmware just never sent
heartbeats.

**Change (firmware only):**

- Send `publish_discovery()` **once on boot**, after the initial MQTT connect. Not on
  reconnect — a pure MQTT/WiFi reconnect keeps the in-RAM config, so re-triggering a
  config push (which resets pins) is undesirable; the resumed heartbeat restores
  liveness on its own.
- The main loop publishes a **heartbeat** every `PUBLISH_INTERVAL_S` (20 s) via a new
  `publish_heartbeat(client_id)` → topic `smartboxes/{mac}/status`, payload
  `{"mac": client_id}`. Keep the `gc.collect()` after each publish.

20 s heartbeat vs the backend's 30 s offline threshold tolerates one lost heartbeat.
`PUBLISH_INTERVAL_S` stays a `# --- KONFIGURATION ---` constant.

---

## 5. Watchdog — `machine.WDT`, main loop only

The captive portal and initial connect can legitimately wait minutes for a human, so
the WDT is scoped to **normal operation**.

- Create `wdt = machine.WDT(timeout=8000)` immediately before entering the main loop
  (after the initial connect + discovery). The exact maximum timeout for the target
  RP2350 MicroPython build is verified during implementation; 8000 ms is near the rp2
  ceiling.
- `wdt.feed()` at the top of every loop tick.
- The in-loop reconnect can block far longer than 8 s (12 attempts × 10 s). To avoid a
  reset during a *successful* reconnect, the WDT is threaded into the reconnect helpers:
  `reconnect_wifi(ssid, pw, wdt=None)` and `reconnect_mqtt(wdt=None)` feed it, and
  `connect_wifi(..., wdt=None)` feeds it inside its toggle-wait loop. The 10 s
  inter-attempt delay is chunked into ≤ 4 s sleeps with a feed between them. `wdt`
  defaults to `None` so the boot-time calls (before the WDT exists) are unaffected.
- A genuinely wedged loop misses the feed and the WDT resets the box — the same
  recovery the code already performs explicitly on unrecoverable errors.

---

## 6. Small cleanups

- **`broker_port`** — honor `config.get('broker_port')` (string → int, default 1883) in
  the `connect_mqtt` call from `main.py`. Currently saved by the portal but ignored.
- **`publish_discovery` signature** — drop the unused `broker`/`port` parameters; it
  only ever publishes via the persistent `_mqtt_client`. `publish_heartbeat` takes only
  `client_id` for the same reason.
- **`_blocked_devices` prune** — in `_update_known_devices`, also
  `_blocked_devices.pop(device_id, None)` for stale devices (currently only
  `_last_command_time`/`_failed_commands` were cleaned, both now deleted).
- **QoS** — documented decision, no code change: commands are delivered QoS 0; the
  backend tracks `/executed` ACKs and is the place to retry on missing ACK. Recorded in
  `smart-box/CLAUDE.md`.
- **`RECONNECT_*` duplication** — `RECONNECT_ATTEMPTS`/`RECONNECT_DELAY_S` exist in both
  `mqttutils.py` and `networkutils.py`. Kept intentionally separate (WiFi and MQTT
  reconnect cadence are independent tunables); documented as deliberate, not a defect.

---

## 7. Backend MQTT-contract cleanup (smart-ground-backend)

So firmware and backend agree after delay is removed from the firmware:

- `SmartBoxConfigPushService.DeviceConfigEntry` — remove the `@JsonProperty("delay_ms")
  Integer delayMs` field and the `effectiveDelay` resolution in `buildPayload`. The
  config-push payload no longer carries `delay_ms`.
- The device **command** payload — remove `delaySignalDurationMs`.
- **Kept:** `DeviceType.delaySignalDurationMs` (and any `device`-level override) stays
  in the backend domain/DB as modeled intent.
- **Deferred (documented OPEN item in backend CLAUDE.md):** backend-side delayed
  dispatch — applying the delay by scheduling the command publish at send-time, using
  the retained `DeviceType.delaySignalDurationMs`. Not built in this effort; there is no
  confirmed use case and it is a real feature (scheduling, restart-persistence). Until
  built, no delay is applied. Safe pre-v1 (no production release).

Existing backend tests that assert on `delay_ms` / `delaySignalDurationMs` in payloads
are updated to match the trimmed contract.

---

## Implementation order

1. Test harness (stubs + first failing tests for the security/scheduler logic).
2. Security trim — delete rate limiter (§1) and auto-block (§2).
3. One-phase scheduler + busy-reject + `delay_ms` removal (§3).
4. Heartbeat split (§4).
5. WDT (§5).
6. Small cleanups (§6).
7. Backend contract cleanup (§7).
8. Documentation — rewrite affected sections of `smart-box/CLAUDE.md` and add the
   backend OPEN item.

Each firmware step is TDD where the logic is host-testable (steps 2–4, 6); steps 5 and
the GPIO wiring are validated on hardware.

---

## Consequences / notes

- **Security model is smaller and documented.** Allowlist + admin block only. The
  CLAUDE.md security section and the "auto-block / rate-limit" references are rewritten.
- **`reset()` mid-pulse no longer happens every 20 s** — pulses run to completion under
  normal operation.
- **Scheduler is single-phase** — easier to reason about; one `_pulse_active` entry per
  device is both the timer and the busy flag.
- **Delay becomes a backend concern** — firmware carries none of it. If a delayed-release
  or staggered-double requirement materialises, it is implemented backend-side.
- **Conventions** per `smart-box/CLAUDE.md`: German inline comments, English
  identifiers, `const()` for integers, `# --- KONFIGURATION ---` blocks,
  `except Exception as e: print(...)`, safe-exit pattern preserved in `main.py`.

## Verification

- **Host tests** (`python -m unittest` from `smart-box/`): security, scheduler, config
  parse, payload shapes — all green.
- **Backend tests** (`./mvnw test`): updated payload assertions green.
- **Manual Pico 2W smoke test:**
  1. Boot with valid config → 3 startup blinks → connect → one discovery → heartbeats
     every 20 s on `smartboxes/{mac}/status`; backend shows the box ONLINE without
     repeated config pushes.
  2. Config push → GPIO initialised; ON command fires the pin for its duration and
     auto-offs; a second ON during the pulse is ignored; OFF cancels.
  3. Admin BLOCK → ON ignored; UNBLOCK → ON works again.
  4. Pull the WiFi → connecting blink + reconnect; box does **not** re-send discovery;
     heartbeats resume; backend returns it to ONLINE.
  5. Free heap > 30 KB under normal operation (`gc.mem_free()` in REPL).
