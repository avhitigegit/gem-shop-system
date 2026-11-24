import { defineConfig } from "@playwright/test";

/* Automatic browser tests (P6-01) – run against the whole app in Docker:
     docker compose up -d --build        (http://localhost:8100, sample data, password Gem@2026)
     npm run e2e
   They use the Chrome installed on the computer (CI installs it). Each test changes sample data, so reset the data
   (infra/scripts/reset-data.ps1) before a full run if a test says a gem is already sold. */
export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: process.env.E2E_URL ?? "http://localhost:8100",
    channel: "chrome",
    viewport: { width: 1366, height: 820 },
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
});
