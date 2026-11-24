import { expect, test } from "@playwright/test";
import { SALES, signIn } from "./helpers";

/* P6-01 speed: the New sale screen is ready to scan in under 2 seconds after the gold "+ New sale" button is
   pressed (signed in, app already open – like at the counter). */
test("new sale opens in under 2 seconds", async ({ page }) => {
  await signIn(page, SALES);
  const scan = page.getByPlaceholder(/Scan the QR \/ barcode tag/);
  const times: number[] = [];
  await page.goto("/sell/invoices");
  await page.waitForTimeout(1500); // let the app finish signing in again after the page load
  for (let i = 0; i < 3; i++) {
    if (i > 0) {
      await page.goBack(); // back to the invoices list, inside the app (no page load)
      await expect(page).toHaveURL(/\/sell\/invoices/);
    }
    const start = Date.now();
    await page.locator('a.btn-gold[href="/sell/new"]').click();
    await expect(scan).toBeEditable();
    times.push(Date.now() - start);
  }
  console.log(`New sale ready in ${times.join(" / ")} ms`);
  expect(Math.max(...times), `New sale ready in ${times.join(" / ")} ms`).toBeLessThan(2000);
});
