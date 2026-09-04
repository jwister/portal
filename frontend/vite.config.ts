import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  // Keep the existing asset folder as Vite's static public directory so
  // /small-logo.png is also available from the packaged Spring Boot app.
  publicDir: 'src/public',
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
  },
})
