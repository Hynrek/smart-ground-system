#!/usr/bin/env python3
"""
Packs an App Code OTA release: computes per-file SHA-256, writes manifest.json,
and lays out a directory tree matching what ota.py expects to fetch over HTTP:

    <out>/<version>/manifest.json
    <out>/<version>/files/<path>...

Serve <out>/<version>/ at some base_url, then trigger the update via MQTT:

    { "type": "APP", "version": "<version>", "url": "<base_url>",
      "sha256": "<manifest sha256 printed below>", "size": <manifest size> }

Usage:
    python tools/pack_ota.py 0.7
    python tools/pack_ota.py 0.7 --out dist/ota --files main.py mqttutils.py ...
    python tools/pack_ota.py 0.7 --serve   # also start a local HTTP server for testing
"""
import argparse
import hashlib
import http.server
import functools
import json
import os
import shutil
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Files that make up the App Code (device-agnostic). systemconfig/firmware_config.json
# ships with every release: it carries the release metadata (app_version,
# config_schema_version, capabilities) the box reports in its discovery payload.
# userconfig/ is device-owned state (WiFi credentials, device config, ota_state) and is
# NEVER shipped via OTA — ota.py rejects manifests containing userconfig/ paths.
DEFAULT_FILES = [
    "main.py",
    "mqttutils.py",
    "ota.py",
    "hardware.py",
    "networkutils.py",
    "accesspoint.py",
    "accesspoint.html",
    "boards/pico2w.py",
    "boards/xiao_esp32s3.py",
    "systemconfig/firmware_config.json",
]

FIRMWARE_CONFIG = "systemconfig/firmware_config.json"


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(functools.partial(f.read, 65536), b""):
            h.update(chunk)
    return h.hexdigest()


def check_version_consistency(version):
    """The box reports app_version from firmware_config.json after the update —
    packing a release whose version disagrees with that file would leave the box
    announcing the wrong version forever. Fail fast instead."""
    cfg_path = os.path.join(REPO_ROOT, FIRMWARE_CONFIG)
    try:
        with open(cfg_path) as f:
            app_version = json.load(f).get("app_version")
    except (OSError, ValueError) as e:
        raise SystemExit(f"error: cannot read {FIRMWARE_CONFIG}: {e}")
    if app_version != version:
        raise SystemExit(
            f"error: app_version in {FIRMWARE_CONFIG} is {app_version!r}, but packing "
            f"version {version!r} — bump the file first (it ships with the release)")


def build_manifest(version, files):
    entries = []
    for rel in files:
        src = os.path.join(REPO_ROOT, rel)
        if not os.path.isfile(src):
            raise SystemExit(f"error: file not found: {rel}")
        entries.append({
            "path": rel,
            "sha256": sha256_of(src),
            "size": os.path.getsize(src),
        })
    return {"appVersion": version, "files": entries}


def pack(version, files, out_dir):
    release_dir = os.path.join(out_dir, version)
    files_dir = os.path.join(release_dir, "files")
    if os.path.exists(release_dir):
        shutil.rmtree(release_dir)
    os.makedirs(files_dir)

    manifest = build_manifest(version, files)

    for entry in manifest["files"]:
        rel = entry["path"]
        dst = os.path.join(files_dir, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(os.path.join(REPO_ROOT, rel), dst)

    manifest_path = os.path.join(release_dir, "manifest.json")
    with open(manifest_path, "w") as f:
        json.dump(manifest, f, indent=2)

    manifest_sha256 = sha256_of(manifest_path)
    manifest_size = os.path.getsize(manifest_path)
    return release_dir, manifest_sha256, manifest_size, manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("version", help="App Code version string, e.g. 0.7")
    parser.add_argument("--out", default="dist/ota", help="output directory (default: dist/ota)")
    parser.add_argument("--files", nargs="+", default=DEFAULT_FILES, help="files to include (relative to smart-box/)")
    parser.add_argument("--url", default=None, help="base_url this release will be served from, used only to print the ready-to-send MQTT command")
    parser.add_argument("--serve", action="store_true", help="serve the packed release directory over HTTP for local testing")
    parser.add_argument("--port", type=int, default=8000, help="port for --serve (default: 8000)")
    args = parser.parse_args()

    check_version_consistency(args.version)
    release_dir, manifest_sha256, manifest_size, manifest = pack(args.version, args.files, args.out)

    print(f"Packed version {args.version} -> {release_dir}")
    print(f"  {len(manifest['files'])} file(s), manifest sha256={manifest_sha256} size={manifest_size}")

    base_url = args.url or f"http://<host>:{args.port}/{args.version}"
    command = {
        "type": "APP",
        "version": args.version,
        "url": base_url,
        "sha256": manifest_sha256,
        "size": manifest_size,
    }
    print("\nMQTT trigger payload (topic smartboxes/{mac}/ota):")
    print(json.dumps(command, indent=2))

    if args.serve:
        os.chdir(release_dir)
        handler = http.server.SimpleHTTPRequestHandler
        print(f"\nServing {release_dir} at http://0.0.0.0:{args.port}/ (Ctrl+C to stop)")
        with http.server.HTTPServer(("0.0.0.0", args.port), handler) as httpd:
            try:
                httpd.serve_forever()
            except KeyboardInterrupt:
                pass


if __name__ == "__main__":
    main()
