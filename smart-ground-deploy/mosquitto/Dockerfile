# Thin wrapper around the official eclipse-mosquitto:2 image.
#
# The base image has no openssl binary (verified: `apk info` on eclipse-
# mosquitto:2 lists only libssl3, no `openssl` package), but the entrypoint
# below needs it at container *startup* to generate the local Dev-CA and
# server cert for the TLS listener (see docker-entrypoint.sh). Installed at
# build time — mirrors smart-ground-ui/Dockerfile's `apk add --no-cache
# openssl` — so no network access is required when the container starts.
FROM eclipse-mosquitto:2

RUN apk add --no-cache openssl

COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint-smartground.sh
RUN chmod +x /usr/local/bin/docker-entrypoint-smartground.sh

ENTRYPOINT ["/usr/local/bin/docker-entrypoint-smartground.sh"]
CMD ["/usr/sbin/mosquitto", "-c", "/mosquitto/config/mosquitto.conf"]
