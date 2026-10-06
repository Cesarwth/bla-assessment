import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// The API lives on another port in development; the proxy keeps the browser on a single origin (no CORS),
// exactly like the nginx container does in production.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    restoreMocks: true,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/main.tsx', 'src/vite-env.d.ts', 'src/test/**', 'src/**/*.test.{ts,tsx}', 'src/**/types.ts'],
      // Same idea as the JaCoCo gate in the backend: `npm run test:coverage` fails if coverage drops below these floors.
      thresholds: { lines: 90, statements: 90, functions: 90, branches: 85 },
    },
  },
})
