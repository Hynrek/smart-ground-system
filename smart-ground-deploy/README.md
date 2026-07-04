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

A physical SmartBox connects its MQTT to **this machine's LAN IP** on port `1883`
(not `localhost`).
