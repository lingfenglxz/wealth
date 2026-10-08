import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './tests/e2e', timeout: 300000, workers: 1,
  use: { baseURL: process.env.SITE_URL || 'http://127.0.0.1:4173', headless: true, channel: process.env.CI ? 'chromium' : 'msedge' },
  webServer: process.env.SITE_URL ? undefined : { command: `${process.platform === 'win32' ? 'npm.cmd' : 'npm'} run preview -- --port 4173`, url: 'http://127.0.0.1:4173', reuseExistingServer: true },
});
