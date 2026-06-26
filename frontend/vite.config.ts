import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: process.env.LINGUAFRAME_API_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  test: {
    setupFiles: './src/test/setup.ts',
    environmentOptions: {
      jsdom: {
        url: 'http://localhost:5173/'
      }
    }
  }
});
