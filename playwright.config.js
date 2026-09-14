import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tools/e2e',
  timeout: 120_000,
  retries: 0,
  workers: 1,
  use: {
    headless: true,
    viewport: { width: 1440, height: 900 },
    trace: 'off',
  },
});
