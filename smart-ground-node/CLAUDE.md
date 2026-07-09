# smart-ground-node — SmartNode Development Guide

## Project Overview

`smart-ground-node` is the future ESP-NOW↔MQTT bridge (SmartNode) described in ADR-001.
It runs today as a separate Spring Boot process on the same machine as the backend
(`plan-espnow-migration.md`, "Vereinte Zwischenstufe") and will later move to its own
Pi-class device without a rewrite — it speaks only the final interfaces (MQTT, HTTPS,
UART) from day one, never the backend's database or in-process APIs directly.

## Stack & Versions

- **Java 25**, Spring Boot 4.0.5, Maven (system `mvn`, no wrapper committed)
- Standalone Maven module (like `smart-ground-backend`), not part of a reactor build

## Project Structure

```
smart-ground-node/
├── pom.xml
├── src/main/java/ch/jp/shooting/node/
│   ├── SmartGroundNodeApplication.java
│   └── crypto/                         # AES-256-GCM + HKDF-SHA256 (ADR-002/ADR-003)
└── src/test/java/ch/jp/shooting/node/
    └── crypto/                         # Cross-verified against docs/espnow/crypto-test-vectors.json
```

## Conventions

Same as `smart-ground-backend`: German comments for domain logic, English identifiers.
Contract-first for anything crossing a process boundary (MQTT payloads, HTTPS sync
endpoints, UART framing) — see `docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md`
for the wire formats this module implements.

## Crypto (`crypto/` package)

`Hkdf` and `AesGcm` are thin wrappers around `javax.crypto` (HMAC-SHA256 / AES/GCM/NoPadding,
both natively supported by the JDK — no hand-rolled crypto needed on this side, unlike
MicroPython's `ucryptolib` which lacks a native GCM mode). Both are tested against the
canonical fixture at `../docs/espnow/crypto-test-vectors.json`, which `smart-box`'s
MicroPython implementation is tested against too — this is what keeps the two sides from
silently diverging.

## Host Tests

```bash
mvn test
```

No hardware dependency yet (Baustein A is crypto primitives + module scaffold only).
Later tasks (frame codec, UART, pairing) will add hardware-adjacent seams (serial port
abstraction) analogous to `smart-box`'s `http_stream`/`esp32.Partition` test seams.
