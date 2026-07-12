import { defineConfig } from 'vite';

const apiTarget = process.env.VITE_API_TARGET || 'http://localhost:8080';

export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': apiTarget
    }
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts'
  }
});
