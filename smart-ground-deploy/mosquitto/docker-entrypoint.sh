#!/bin/sh
# Generates the local Dev-CA + server cert for the Mosquitto TLS listener
# (8883) at container start, then hands off to the base image's own
# entrypoint (which fixes up /mosquitto/data ownership) to launch mosquitto.
#
# Mirrors smart-ground-ui/docker-entrypoint.sh: one CA created once and kept
# in a volume (mosquitto_certs) so already-trusted clients survive restarts;
# the server leaf cert is reissued only when missing or the SAN list
# (MQTT_TLS_SANS) changed. Unlike the ui script, there is no HTTP server here
# to publish ca.crt from — Task B/C mount the mosquitto_certs volume
# read-only to read it directly off disk.
set -eu

CERT_DIR=/mosquitto/config/certs
DYNSEC_DIR=/mosquitto/config/dynsec
SANS="${MQTT_TLS_SANS:-DNS:localhost,DNS:mosquitto,IP:127.0.0.1}"

mkdir -p "$CERT_DIR" "$DYNSEC_DIR"

# One CA per deployment, created once and kept in the volume.
if [ ! -f "$CERT_DIR/ca.crt" ]; then
    openssl req -x509 -nodes -newkey rsa:2048 -days 3650 \
        -keyout "$CERT_DIR/ca.key" -out "$CERT_DIR/ca.crt" \
        -subj "/CN=Smart Ground Local CA/O=Smart Ground" \
        -addext "basicConstraints=critical,CA:TRUE" \
        -addext "keyUsage=critical,keyCertSign,cRLSign"
fi

# (Re)issue the server cert when it is missing or MQTT_TLS_SANS changed.
if [ ! -f "$CERT_DIR/server.crt" ] || [ "$(cat "$CERT_DIR/sans.txt" 2>/dev/null || true)" != "$SANS" ]; then
    openssl req -nodes -newkey rsa:2048 \
        -keyout "$CERT_DIR/server.key" \
        -subj "/CN=smart-ground-mosquitto" \
        -out /tmp/server.csr
    {
        printf 'subjectAltName=%s\n' "$SANS"
        printf 'extendedKeyUsage=serverAuth\n'
        printf 'keyUsage=digitalSignature,keyEncipherment\n'
        printf 'basicConstraints=CA:FALSE\n'
    } > /tmp/server.ext
    # 820 days: iOS/macOS reject leaf certs valid longer than 825 days.
    openssl x509 -req -in /tmp/server.csr \
        -CA "$CERT_DIR/ca.crt" -CAkey "$CERT_DIR/ca.key" -CAcreateserial \
        -days 820 -out "$CERT_DIR/server.crt" -extfile /tmp/server.ext
    printf '%s' "$SANS" > "$CERT_DIR/sans.txt"
    rm -f /tmp/server.csr /tmp/server.ext
fi

chmod 600 "$CERT_DIR"/*.key
chmod 644 "$CERT_DIR"/ca.crt "$CERT_DIR"/server.crt

# mosquitto drops root privileges to the `mosquitto` user after opening its
# listener sockets (the base image runs it as that user unconditionally),
# so the key/certs and the dynsec state dir must be readable/writable by it
# — this script itself still runs as root at this point. The base
# entrypoint (below) only chowns /mosquitto/data, not these paths.
chown -R mosquitto:mosquitto "$CERT_DIR" "$DYNSEC_DIR"

# Hand off to the base image's own entrypoint (permission fixups for
# /mosquitto/data), which then execs the real command (mosquitto).
exec /docker-entrypoint.sh "$@"
