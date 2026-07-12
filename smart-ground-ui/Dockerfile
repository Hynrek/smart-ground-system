FROM node:20-alpine AS builder

WORKDIR /app

COPY package.json ./
RUN npm install --legacy-peer-deps

COPY . .
RUN npm run build

FROM nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# TLS material is generated at container start (see docker-entrypoint.sh): a
# local CA persisted in the /etc/nginx/ssl volume signs a server cert whose
# SANs come from the TLS_SANS env var — the LAN IP is only known at deploy
# time, not build time. Devices trust https:// by installing the CA once
# (served at /ca.crt), which removes the browser warning entirely.
RUN apk add --no-cache openssl

COPY docker-entrypoint.sh /docker-entrypoint-smartground.sh
RUN chmod +x /docker-entrypoint-smartground.sh

EXPOSE 80 443

ENTRYPOINT ["/docker-entrypoint-smartground.sh"]