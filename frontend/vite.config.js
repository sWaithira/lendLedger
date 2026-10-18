import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The browser talks to Vite (5173) only. Vite forwards /api to Spring Boot (8080), so the session and
// CSRF cookies belong to one origin, exactly as they will in production behind a single domain.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
})
