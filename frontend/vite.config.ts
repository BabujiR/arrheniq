import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Dev server on :5173. Everything the backend serves is proxied to :8080 *without* changing the Host header,
 * so the browser sees one origin (ADR 0006): the session cookie, the CSRF cookie and the OIDC redirect URI
 * (http://localhost:5173/login/oauth2/code/arrheniq, allowed in the Keycloak dev realm) all just work.
 */
const backend = 'http://localhost:8080';
const proxied = { target: backend, changeOrigin: false };

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': proxied,
      '/auth': proxied,
      '/oauth2': proxied,
      '/login/oauth2': proxied,
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
});
