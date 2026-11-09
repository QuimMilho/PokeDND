import { defineConfig } from '@playwright/test';

/**
 * End-to-end tests (chapter 20.4): a real browser, the real API and a new empty database. `e2e/global-setup.mjs` starts them
 * (see `e2e/stack.mjs`), so build first: `npm run build` here, `./gradlew quarkusBuild` in API/. Run with `npm run test:e2e`.
 *
 * The browser is the Chrome that is installed on the machine (no separate download).
 */
export default defineConfig({
  testDir: 'e2e',
  testMatch: '**/*.e2e.ts',
  globalSetup: './e2e/global-setup.mjs',
  // one stack, tests one after the other: the export job queue handles one job at a time
  fullyParallel: false,
  workers: 1,
  timeout: 5 * 60_000,
  expect: { timeout: 15_000 },
  reporter: [['list']],
  outputDir: 'test-results/playwright',
  use: {
    channel: 'chrome',
    headless: true,
    acceptDownloads: true,
    trace: 'retain-on-failure',
  },
});
