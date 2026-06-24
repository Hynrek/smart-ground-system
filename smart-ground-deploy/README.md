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
   - Frontend → http://localhost:5173
   - Backend / Swagger → http://localhost:8080/swagger-ui.html

Stop with `docker compose down` (keeps the database) or `docker compose down -v`
(wipes the database volume).

---

## Why these ports

- Backend is published on **8080** because the frontend (a browser app) has its API URL
  baked in at build time to `http://localhost:8080`.
- Frontend is published on **5173** because the backend's CORS only allows
  `http://localhost:5173` / `:5174`.

Open the browser **on the same machine** running Docker. To reach the app from another
device on the network, the frontend image must be rebuilt with a `VITE_API_BASE_URL`
pointing at the host's LAN IP, and that origin added to the backend's CORS list.

A physical SmartBox connects its MQTT to **this machine's LAN IP** on port `1883`
(not `localhost`).
