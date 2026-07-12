#!/bin/sh
# Generates the TLS material at container start, then runs nginx.
#
# Why at runtime and not at image build: the LAN IP the phones use is only
# known at deploy time. A plain self-signed cert can never be trusted by a
# browser; instead we create a local CA (persisted in the ssl volume so device
# trust survives image updates) and issue the server cert from it. Installing
# the CA once per device (download from /ca.crt) removes the trust warning —
# required for a clean secure context (getUserMedia / QR scanner) on phones.
set -eu

SSL_DIR=/etc/nginx/ssl
HTML_DIR=/usr/share/nginx/html
SANS="${TLS_SANS:-DNS:localhost,IP:127.0.0.1}"

mkdir -p "$SSL_DIR"

# One CA per deployment, created once and kept in the volume.
if [ ! -f "$SSL_DIR/ca.crt" ]; then
    openssl req -x509 -nodes -newkey rsa:2048 -days 3650 \
        -keyout "$SSL_DIR/ca.key" -out "$SSL_DIR/ca.crt" \
        -subj "/CN=Smart Ground Local CA/O=Smart Ground" \
        -addext "basicConstraints=critical,CA:TRUE" \
        -addext "keyUsage=critical,keyCertSign,cRLSign"
fi

# (Re)issue the server cert when it is missing or TLS_SANS changed.
if [ ! -f "$SSL_DIR/selfsigned.crt" ] || [ "$(cat "$SSL_DIR/sans.txt" 2>/dev/null || true)" != "$SANS" ]; then
    openssl req -nodes -newkey rsa:2048 \
        -keyout "$SSL_DIR/selfsigned.key" \
        -subj "/CN=smart-ground" \
        -out /tmp/server.csr
    {
        printf 'subjectAltName=%s\n' "$SANS"
        printf 'extendedKeyUsage=serverAuth\n'
        printf 'keyUsage=digitalSignature,keyEncipherment\n'
        printf 'basicConstraints=CA:FALSE\n'
    } > /tmp/server.ext
    # 820 days: iOS/macOS reject leaf certs valid longer than 825 days.
    openssl x509 -req -in /tmp/server.csr \
        -CA "$SSL_DIR/ca.crt" -CAkey "$SSL_DIR/ca.key" -CAcreateserial \
        -days 820 -out "$SSL_DIR/selfsigned.crt" -extfile /tmp/server.ext
    # Serve the full chain (leaf + CA): some TLS stacks (e.g. Windows Schannel)
    # refuse to build the chain from the trust store alone.
    cat "$SSL_DIR/ca.crt" >> "$SSL_DIR/selfsigned.crt"
    printf '%s' "$SANS" > "$SSL_DIR/sans.txt"
    rm -f /tmp/server.csr /tmp/server.ext
fi

# Expose the CA cert in the webroot so devices can download and trust it.
cp "$SSL_DIR/ca.crt" "$HTML_DIR/ca.crt"

exec nginx -g 'daemon off;'
