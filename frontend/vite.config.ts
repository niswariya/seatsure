import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// In dev, the Vite server proxies API and WebSocket calls to Spring Boot,
// so the browser sees a single origin (same as production behind nginx).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
})
