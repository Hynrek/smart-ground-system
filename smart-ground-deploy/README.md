# smart-ground-deploy

Run the full Smart Ground stack (PostgreSQL + Mosquitto + backend + frontend) from
**prebuilt images**, on any machine with Docker Desktop. No application source needed
here — only this folder.

The three code repos stay separate:

| Repo                    | Becomes a Docker image?            |
|-------------------------|------------------------------------|
| `smart-ground-backend`  | ✅ `smart-ground-backend`          |
| `smart-ground-ui`       | ✅ `smart-ground-ui`               |
| `smart-box` (firmware)  | ❌ MicroPython, flashed to the Pico |

`db` (postgres) and `mosquitto` use official images, so nothing to build for them.

---

## One-time: build & push the two images

Pick a registry and log in. Examples use **GitHub Container Registry (ghcr.io)**;
Docker Hub works the same way (use `docker.io/<user>` and `docker login`).

```bash
# Authenticate (ghcr needs a Personal Access Token with write:packages scope)
echo $CR_PAT | docker login ghcr.io -u hynrek --password-stdin
```

**Backend** — from inside the `smart-ground-backend` repo:

```bash
docker build -f Dockerfile.jvm -t ghcr.io/hynrek/smart-ground-backend:latest .
docker push ghcr.io/hynrek/smart-ground-backend:latest
```

**Frontend** — from inside the `smart-ground-ui` repo:

```bash
docker build -t ghcr.io/hynrek/smart-ground-ui:latest .
docker push ghcr.io/hynrek/smart-ground-ui:latest
```

Re-run these two steps whenever you change backend or UI code. Consider tagging with a
version or git SHA instead of `latest` (e.g. `:2026-06-24`) so you can pin/rollback.

> **Automated (recommended):** each code repo has a
> `.github/workflows/docker-publish.yml` that builds and pushes its image to GHCR on
> every push to `main` (and on `v*` tags). Once those run, you can skip the manual
> `docker build`/`push` above and just `docker compose pull` on the laptop.

---

## Running on the laptop (or any machine)

1. Install **Docker Desktop** and start it.
2. Copy this `smart-ground-deploy` folder onto the machine (or `git clone` it).
3. Create your env file and set the registry/tag:

   ```bash
   cp .env.example .env
   # edit .env: REGISTRY=ghcr.io/hynrek   TAG=latest
   ```

4. Pull and start:

   ```bash
   docker login ghcr.io      # only if the images are private
   docker compose pull
   docker compose up -d
   ```

5. Open:
   - Frontend → http://localhost (or `https://localhost` — needed for camera access
     like the QR scanner; see **Trusting the https certificate** below to get rid of
     the browser warning)
   - Backend / Swagger → http://localhost:8080/swagger-ui.html

Stop with `docker compose down` (keeps the database) or `docker compose down -v`
(wipes the database volume).

---

## Trusting the https certificate (no more "untrusted" warning)

There is no public domain here, so a certificate from a public CA (Let's Encrypt) is
not possible. Instead the `web` container creates a **local CA** ("Smart Ground Local
CA") on first start and issues its server certificate from it. Install that CA once
per device and every browser on it shows a normal green lock.

**Setup (once per deployment):**

1. Put this machine's LAN IP into `TLS_SANS` in `.env`
   (e.g. `TLS_SANS=DNS:localhost,IP:127.0.0.1,IP:192.168.0.15`), then
   `docker compose up -d` — the server cert is reissued automatically whenever
   `TLS_SANS` changes.
2. Download the CA certificate from `http://<host>/ca.crt` on each device.

**Install the CA per device:**

- **Windows**: double-click `ca.crt` → *Install Certificate* → *Local Machine* →
  *Place all certificates in the following store* → **Trusted Root Certification
  Authorities**. (Firefox: Settings → Privacy & Security → Certificates → Import,
  or enable `security.enterprise_roots.enabled`.)
- **Android**: download `ca.crt` → Settings → *Security & privacy* → *More security
  settings* → *Encryption & credentials* → *Install a certificate* → **CA
  certificate**. Chrome then trusts it.
- **iOS/iPadOS**: download `ca.crt` in Safari → Settings → *Profile Downloaded* →
  Install → then **Settings → General → About → Certificate Trust Settings** and
  enable full trust for "Smart Ground Local CA" (without this last step Safari still
  warns).

**Notes:**

- The CA lives in the `tls_certs` Docker volume, so it survives image updates and
  restarts — devices stay trusted. `docker compose down -v` deletes it; after that,
  a new CA is generated and devices must install the new `ca.crt`.
- The CA is valid 10 years, the server cert 820 days (iOS caps leaf certs at 825
  days); restart the container after expiry gets close — delete
  `selfsigned.crt` in the volume (or change `TLS_SANS`) to force a reissue.

---

## Why these ports

- Backend is published on **8080** because the frontend (a browser app) has its API URL
  baked in at build time to `http://localhost:8080`.
- Frontend is published on **80** (standard HTTP) so the app is reachable at
  `http://localhost` with no port suffix. The deploy image serves the SPA via nginx,
  which also proxies `/api` to the backend container on the internal Docker network.

> **Note:** the dev server (`npm run dev` in `smart-ground-ui`) still runs on **5173** —
> that's Vite's default and what the backend's CORS allows. Only this prebuilt-image
> deploy publishes on 80.

Open the browser **on the same machine** running Docker. To reach the app from another
device on the network, the frontend image must be rebuilt with a `VITE_API_BASE_URL`
pointing at the host's LAN IP, and that origin added to the backend's CORS list. Use
`https://<host-lan-ip>` (port 443, see **Trusting the https certificate**) rather than `http://` for that —
mobile browsers only allow camera access (`getUserMedia`, used by the QR scanner) over
a secure context, and a LAN IP over plain http doesn't qualify.

A physical SmartBox connects its MQTT to **this machine's LAN IP** on port `8883`
(TLS, not `localhost`) — see **MQTT broker: TLS + Dynamic Security** below. Plain
`1883` is no longer published to the host at all; it's only reachable by other
containers on the `smartground` Docker network.

---

## MQTT broker: TLS + Dynamic Security

Mosquitto requires TLS and authenticated clients — `allow_anonymous` is off and there
is no plaintext listener reachable from outside Docker. This is Phase 1 of the
`mqtt-tls-client-auth` plan, scoped to a **local Dev-CA** (no public domain / Let's
Encrypt — see **Production TLS** below) and Mosquitto's **Dynamic Security plugin**
(dynsec) for auth/ACLs instead of a static `password_file`.

### How it's built

`mosquitto` is no longer the bare `eclipse-mosquitto:2` image — `docker-compose.yml`
now `build:`s it from `./mosquitto/Dockerfile`, which adds `openssl` (not present in
the base Alpine image; verified via `apk info` — only `libssl3` ships by default) and
a custom entrypoint (`mosquitto/docker-entrypoint.sh`). `docker compose up` builds it
automatically the first time; `docker compose build mosquitto` to force a rebuild
after editing the Dockerfile/entrypoint.

### Certs: generation, volume, and path convention

`mosquitto/docker-entrypoint.sh` generates a local CA + server cert at **container
startup** (not build time), following the same pattern as the `web` service's
`docker-entrypoint.sh`:

- **CA**: created once if missing, kept in the `mosquitto_certs` volume so it survives
  restarts/rebuilds.
- **Server cert**: (re)issued if missing or if `MQTT_TLS_SANS` (set via `.env`, same
  syntax as `TLS_SANS`) changed since the last issue (tracked via a `sans.txt` marker).
- Both are `chown`ed to the `mosquitto` user (the image always runs mosquitto as that
  user, dropping root after binding the listener sockets) and mode `600` (keys) /
  `644` (certs).

**Path convention (Task B/C depend on this):** everything lives in the
`mosquitto_certs` Docker volume, mounted at **`/mosquitto/config/certs`** inside the
`mosquitto` container:

| File | Purpose |
|---|---|
| `/mosquitto/config/certs/ca.crt` | Dev-CA cert — mount this volume **read-only** in the backend/other containers to trust the broker's TLS cert |
| `/mosquitto/config/certs/ca.key` | Dev-CA private key — stays in the volume, never needed outside mosquitto |
| `/mosquitto/config/certs/server.crt` / `server.key` | The broker's own TLS leaf cert/key |
| `/mosquitto/config/certs/sans.txt` | Marker file tracking the SANs baked into `server.crt`, to decide reissue |

To mount it read-only from another compose service: `mosquitto_certs:/some/path:ro`
(the volume is already declared at the bottom of `docker-compose.yml`).

To test with `mosquitto_pub`/`mosquitto_sub` from the host, copy the CA out once:
```bash
docker cp smartground-mosquitto_deploy:/mosquitto/config/certs/ca.crt ./ca.crt
mosquitto_pub -h localhost -p 8883 --cafile ca.crt -u <username> -P <password> -t devices/discovery -m hello
```

### Listeners

- **`8883` (TLS)** — the only port published to the host (`docker-compose.yml`). TLS
  1.2 floor (`tls_version tlsv1.2`); confirmed TLS 1.3 negotiates fine on top.
- **`1883` (plaintext)** — kept in `mosquitto.conf` for containers on the internal
  `smartground` Docker network (e.g. the backend today, until Task B moves it to TLS)
  but **not** port-mapped in `docker-compose.yml`. This is the isolation boundary:
  Mosquitto binds `1883` on all interfaces inside its own container regardless of the
  `listener 1883` directive — nothing in `mosquitto.conf` restricts who can reach it.
  It's unreachable from the host/internet purely because `docker-compose.yml` doesn't
  publish it. Keep this in mind if that compose file is ever changed.
- `9001` (websockets) was dropped — it was only ever port-mapped, never configured in
  `mosquitto.conf`, so there was nothing to remove there.

### Dynsec bootstrap

**Verified against a live `eclipse-mosquitto:2` container (mosquitto 2.1.2) as part of
this task** — not just docs:

- If `plugin_opt_config_file` (`/mosquitto/config/dynsec/dynamic-security.json`)
  doesn't exist at startup, the dynsec plugin auto-generates it with a default
  `admin` user (plus a `democlient`, which this setup doesn't use — feel free to
  delete it after bootstrapping, `mosquitto_ctrl dynsec deleteClient democlient`).
- The initial `admin` password is controlled via the `MOSQUITTO_DYNSEC_PASSWORD`
  environment variable (read directly by the plugin, not by our entrypoint script) —
  wired from `.env`'s `MQTT_DYNSEC_ADMIN_PASSWORD` (**change this** before deploying
  anywhere but a laptop). If that variable isn't set, mosquitto instead writes a
  **random** password in plaintext to `dynamic-security.json.pw` next to the config
  file (in the `mosquitto_dynsec` volume) — confirmed by log line
  `Generated passwords are at /mosquitto/config/dynsec/dynamic-security.json.pw`.
  Either way, this only happens the *very first* time (empty `mosquitto_dynsec`
  volume); once `dynamic-security.json` exists, the env var/random-password path is
  never taken again.
- Dynsec state (`dynamic-security.json`) lives in the **`mosquitto_dynsec`** volume,
  mounted at `/mosquitto/config/dynsec` — separate from the certs volume; no other
  service needs to mount it.

**Role + backend client setup (scripted, run once against a live broker):**
`dynsec-init.sh` is bind-mounted into the container at
`/mosquitto/config/dynsec-init.sh` and creates the two roles the plan calls for, plus
the backend's own dynsec client login, via `mosquitto_ctrl` over the internal
plaintext listener:

```bash
docker compose exec mosquitto sh /mosquitto/config/dynsec-init.sh "$MQTT_DYNSEC_ADMIN_PASSWORD" "$MQTT_BACKEND_PASSWORD"
```

**`MQTT_BACKEND_PASSWORD` <-> `MQTT_PASSWORD` pairing (Task B):** the backend
container authenticates as dynsec username `backend`, password taken from its own
`MQTT_PASSWORD` env var (`docker-compose.yml`'s `app` service sets
`MQTT_PASSWORD: ${MQTT_BACKEND_PASSWORD}`, so both read from the **same** `.env` var).
`dynsec-init.sh`'s second argument sets that same password on the dynsec side
(`setClientPassword backend <password>`). If you ever change
`MQTT_BACKEND_PASSWORD` in `.env`, you must **both** restart `app` (to pick up the new
env var) **and** re-run `dynsec-init.sh`'s client-password step (or run
`mosquitto_ctrl ... dynsec setClientPassword backend <new-password>` directly) — they
are two independent stores of the same secret, not linked automatically after the
first bootstrap.

- **`backend`** — read/write `devices/#` and `$CONTROL/#` (needed for dynsec admin
  and future auto-provisioning of SmartBox clients).
- **`smartbox`** — write-only `devices/discovery`; read/write scoped to its own
  `devices/<mac>/...` subtree via the dynsec `%u` (username) pattern — the plan sets
  each SmartBox's MQTT **username = its MAC address**, so `%u` resolves correctly
  without needing per-client ACL entries.

  **Security note:** the `%u`-scoped ACL's per-device isolation depends entirely on
  the dynsec username being safe. Usernames assigned to `smartbox`-role clients
  **must** be validated as MAC-address format (or otherwise free of `+`/`#`/wildcard
  characters) at provisioning time — a username containing an MQTT wildcard would
  turn `devices/%u/#` into `devices/+/#` or `devices/#`, defeating the "Box A
  darf Topics von Box B weder lesen noch beschreiben" isolation goal. This is
  currently safe because usernames are admin-assigned via `createClient`, not
  client-supplied; whoever implements the backend's device-provisioning /
  user-lifecycle service next must preserve that validation.

  **Recovery from a partial failure:** `dynsec-init.sh` is not idempotent (it uses
  `set -eu` and aborts on the first error). If it fails partway through — e.g. a
  transient `mosquitto_ctrl` failure mid-role — a rerun will immediately fail at
  `createRole` with "role already exists". To recover, manually delete the
  half-configured role(s) (`mosquitto_ctrl ... dynsec deleteRole backend|smartbox`,
  plus `deleteClient <username>` for any client created against them) before
  rerunning the script. See the comment block in `dynsec-init.sh` for the exact
  commands.

This part **was actually run against a live broker** during this task (not just
documented): roles were created, verified with `dynsec getRole backend|smartbox`, and
exercised end-to-end with a throwaway `smartbox`-role client over TLS — publishing to
its own `devices/<mac>/status` and `devices/discovery` succeeded, publishing to
another device's topic and to `$CONTROL/#` was silently dropped (ACL deny, QoS 0), and
subscribing to the broad `devices/#` wildcard was rejected at `SUBSCRIBE` time
(`SUBACK` reason code 128 — "not authorized"), while subscribing to its own
`devices/<mac>/cmd` succeeded. See `.superpowers/sdd/task-A-report.md` for the full
transcript.

**Not scripted (left for Task D / an operator):** creating **per-SmartBox** dynsec
clients (`createClient <mac> -p <password>`, `addClientRole <mac> smartbox 10`) and
deleting them when a box is decommissioned — that's the dynamic user-lifecycle
service, a separate later task. The backend's own `backend` client (above) *is*
scripted here since it's a single, well-known login the backend bootstraps for
itself. `dynsec-init.sh` prints the exact follow-up commands for per-box clients at
the end of its output.

### Production TLS (out of scope here)

This task only covers a local, self-signed Dev-CA suitable for LAN/dev deployments —
matching what the `web` service already does for HTTPS. A production deployment with
a public domain would instead use a certificate from a public CA (e.g. Let's Encrypt
via an ACME client/sidecar) issued for that domain, mounted the same way at
`/mosquitto/config/certs`; that's a follow-up, not implemented here.
