import { expect, type APIRequestContext, type Page } from "@playwright/test";

/* Shared steps for the browser tests. Sample logins (local / dev, password from application-local.yml). */
export const PASSWORD = process.env.E2E_PASSWORD ?? "Gem@2026";
export const OWNER = "owner@ceylongemhouse.lk";
export const SALES = "nimal@ceylongemhouse.lk";
export const ACCOUNTANT = "dilani@fernandoassociates.lk";

/** Signs in on the login screen, like a person. */
export async function signIn(page: Page, email: string) {
  await page.goto("/login");
  await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeEnabled();
  // typed before the page is ready, a value can be wiped: type again until both stay
  await expect(async () => {
    await page.locator("#em").fill(email);
    await page.locator("#pw").fill(PASSWORD);
    await expect(page.locator("#em")).toHaveValue(email, { timeout: 500 });
    await expect(page.locator("#pw")).toHaveValue(PASSWORD, { timeout: 500 });
  }).toPass({ timeout: 10_000 });
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page).not.toHaveURL(/\/login/, { timeout: 20_000 });
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any -- test answers are read loosely; the screens are what is tested
type Json = any;

/** A small API client for preparing data and checking results (signs in with its own token). */
export async function apiAs(request: APIRequestContext, email: string) {
  const r = await request.post("/api/auth/login", { data: { email, password: PASSWORD } });
  expect(r.ok(), `API sign-in for ${email}`).toBeTruthy();
  const token = (await r.json()).accessToken as string;
  const headers = (branch?: string) => ({
    Authorization: `Bearer ${token}`,
    ...(branch ? { "X-Branch": branch } : {}),
  });
  return {
    async get(path: string, branch?: string): Promise<Json> {
      const res = await request.get(path, { headers: headers(branch) });
      expect(res.ok(), `GET ${path} → ${res.status()} ${await res.text()}`).toBeTruthy();
      return res.json();
    },
    async post(path: string, data?: unknown, branch?: string) {
      return request.post(path, { headers: headers(branch), data });
    },
  };
}

/** Opens the salesperson's cash day if nobody did yet today (sales need an opened day). */
export async function openDay(api: Awaited<ReturnType<typeof apiAs>>) {
  const day = await api.get("/api/cash/day");
  if (!day.opening) {
    const r = await api.post("/api/cash/opening", { lkr: 1_000_000, usd: 0, eur: 0 });
    expect([200, 201, 409]).toContain(r.status());
  }
  return api.get("/api/cash/day");
}

/** A gem in the salesperson's branch that can be sold, priced between the limits. */
export async function gemToSell(api: Awaited<ReturnType<typeof apiAs>>, min = 100_000, max = 900_000) {
  const page = await api.get("/api/gems?status=IN_STOCK&size=200");
  const gem = (page.items as { code: string; price: number }[])
    .filter((g) => g.price >= min && g.price <= max)
    .sort((a, b) => a.price - b.price)[0];
  expect(gem, "an in-stock gem to sell – reset the sample data if none is left").toBeTruthy();
  return gem;
}

/** Builds the bill on the New sale screen: one gem, a customer who came before, walk-in. */
export async function startBill(page: Page, code: string, agreed: number) {
  await page.goto("/sell/new");
  const scan = page.getByPlaceholder(/Scan the QR \/ barcode tag/);
  await scan.fill(code);
  await scan.press("Enter");
  const price = page.locator(`#agreed-${code}`);
  await expect(price).toBeVisible();
  await price.fill(String(agreed));
  await page.getByRole("button", { name: "Came before" }).click();
  const cust = page.locator("#custSel");
  const first = await cust.locator("option").nth(1).getAttribute("value");
  await cust.selectOption(first!);
}

/** Today in the shop's time zone (Sri Lanka), yyyy-mm-dd. */
export function shopToday() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Colombo" }).format(new Date());
}
