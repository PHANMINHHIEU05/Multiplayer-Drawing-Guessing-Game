/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    // Worklet modules must remain fetchable scripts in production too.
    assetsInlineLimit: (filePath) => filePath.endsWith("voiceProcessor.worklet.js") ? false : undefined,
  },
  server: {
    port: 3000,
    host: true,
  },
  test: {
    environment: 'jsdom',
    globals: true,
  },
});
