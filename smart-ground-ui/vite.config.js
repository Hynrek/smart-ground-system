import { fileURLToPath, URL } from 'node:url';

import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import vueDevTools from 'vite-plugin-vue-devtools';
import basicSsl from '@vitejs/plugin-basic-ssl';

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [
    vue(),
    vueDevTools(),
    // Camera access (getUserMedia, used by the QR scanner) requires a secure
    // context. Plain http://<lan-ip>:5173 on a phone is not secure, so the
    // browser silently refuses the camera. Self-signed https satisfies the
    // secure-context check once the cert warning is accepted on the phone.
    // `--mode preview` (automated browser verification) skips it — headless
    // browsers reject the self-signed cert.
    ...(mode === 'preview' ? [] : [basicSsl()]),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: true,
    proxy: {
      // Same-origin in dev: the SPA calls /api and /ws, Vite forwards them to
      // the backend so the browser never needs to know the backend's host:port.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // Backend CORS only allows the https origin; requests are same-origin
        // for the browser, so rewriting Origin here is transparent to it.
        headers: { origin: 'https://localhost:5173' },
      },
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
        headers: { origin: 'https://localhost:5173' },
      },
      // smart-ground-node's node-api lives on a different origin (HTTPS,
      // self-signed dev cert, port 8443) — proxy it same-origin too, exactly
      // like /api, so the browser never needs to trust that cert directly.
      // `secure: false` disables cert verification on this server-to-server
      // hop only (Vite's Node.js proxy, not the browser). node-api has no
      // CORS filter of its own (NodeApiAuthFilter only checks the bearer
      // JWT), so no `headers.origin` override is needed here.
      '/node-api': {
        target: 'https://localhost:8443',
        changeOrigin: true,
        secure: false,
      },
    },
  },
}));