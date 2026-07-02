# tools/pack_ota.py

Packs an App Code OTA release for the SmartBox firmware: hashes every file,
writes `manifest.json`, and lays out a directory ready to be served over HTTP
and pushed via MQTT.

## What it produces

```
<out>/<version>/
├── manifest.json          # { "appVersion": "<version>", "files": [{ path, sha256, size }, ...] }
└── files/
    ├── main.py
    ├── mqttutils.py
    ├── ota.py
    ├── ...
    └── systemconfig/firmware_config.json
```

This matches exactly what `ota.py`'s `download_app()` fetches on the box:
`{base_url}/manifest.json` and `{base_url}/files/{path}` for each manifest entry.

## Prerequisites

- `systemconfig/firmware_config.json`'s `app_version` must already be bumped to
  the version you're about to pack. The script refuses to pack otherwise — a
  release whose shipped `firmware_config.json` disagrees with the version
  you're deploying would leave the box announcing the wrong version forever.
- Run from the `smart-box/` directory (or anywhere; paths are resolved relative
  to the repo root regardless of cwd).

## Basic usage

```bash
# 1. Bump app_version in systemconfig/firmware_config.json, e.g. "0.6" -> "0.7"

# 2. Pack the release (default file list = full App Code)
python tools/pack_ota.py 0.7

# -> writes dist/ota/0.7/manifest.json + dist/ota/0.7/files/...
# -> prints the manifest sha256/size and a ready-to-paste MQTT payload
```

Example output:

```
Packed version 0.7 -> dist/ota\0.7
  10 file(s), manifest sha256=40bd507c... size=1421

MQTT trigger payload (topic smartboxes/{mac}/ota):
{
  "type": "APP",
  "version": "0.7",
  "url": "http://<host>:8000/0.7",
  "sha256": "40bd507c...",
  "size": 1421
}
```

## Testing locally end-to-end

Serve the packed release and point a box at it in one step:

```bash
python tools/pack_ota.py 0.7 --serve --port 8000
```

This serves `dist/ota/0.7/` at `http://0.0.0.0:8000/`. Set `--url` so the
printed MQTT payload has your machine's LAN IP (the box can't resolve
`localhost`):

```bash
python tools/pack_ota.py 0.7 --serve --url http://192.168.1.50:8000/0.7
```

Then publish the printed JSON to `smartboxes/{mac}/ota` (e.g. via `mosquitto_pub`
or the backend) to trigger the update, and watch `smartboxes/{mac}/ota/status`
for progress (`DOWNLOADING` → `VERIFYING` → `APPLYING` → `APPLIED`, or `FAILED`).

## Options

| Flag | Default | Description |
|---|---|---|
| `version` (positional) | — | App Code version string, must match `firmware_config.json`'s `app_version` |
| `--out` | `dist/ota` | Output directory the versioned release folder is written into |
| `--files` | full App Code file list | Override which files go into the release (paths relative to `smart-box/`) |
| `--url` | `http://<host>:PORT/<version>` (placeholder) | Base URL to embed in the printed MQTT payload |
| `--serve` | off | Also start a local `http.server` serving the packed release |
| `--port` | `8000` | Port used by `--serve` and the default `--url` placeholder |

## Deploying to a real server

Copy `dist/ota/<version>/` to wherever your OTA HTTP host serves static files
(same layout, no transformation needed), then publish the MQTT payload with
`--url` pointing at that real base URL.

## Notes

- `userconfig/` files are never packed — they're device-owned runtime state
  (WiFi credentials, active device config, OTA probation state), not App Code.
- `systemconfig/firmware_config.json` **is** packed deliberately: it's how the
  box learns its own new `app_version` (and capabilities) after the update.
- Re-running the script for the same version overwrites `<out>/<version>/`
  from scratch.
