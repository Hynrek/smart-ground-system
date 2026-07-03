FROM node:20-alpine AS builder

WORKDIR /app

COPY package.json ./
RUN npm install --legacy-peer-deps

COPY . .
RUN npm run build

FROM nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Self-signed cert so the deployed stack can also serve https:// — required for
# getUserMedia (QR scanner camera) on phones reached via LAN IP, not localhost.
# The IP isn't known at build time, so browsers will still show a trust warning;
# same trade-off as the dev server's basicSsl() plugin, accepted once per device.
RUN apk add --no-cache openssl \
    && mkdir -p /etc/nginx/ssl \
    && openssl req -x509 -nodes -newkey rsa:2048 -days 3650 \
       -keyout /etc/nginx/ssl/selfsigned.key \
       -out /etc/nginx/ssl/selfsigned.crt \
       -subj "/CN=smart-ground" \
       -addext "subjectAltName=DNS:localhost,DNS:smart-ground,IP:127.0.0.1"

EXPOSE 80 443

CMD ["nginx", "-g", "daemon off;"]