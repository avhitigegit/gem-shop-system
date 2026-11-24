import { expect, test } from "@playwright/test";
import { apiAs, openDay, SALES, signIn } from "./helpers";

/* P6-01 browser test 4: closing the day with exactly the expected cash and card gives no difference.
   Runs last (file name starts with z-): after closing there are no more sales today at this branch. */
test("close the day with the counted cash", async ({ page, request }) => {
  const sales = await apiAs(request, SALES);
  const day = await openDay(sales);
  test.skip(!!day.closing, "the day is already closed – reset the sample data to run again");
  const e = day.expected;

  await signIn(page, SALES);
  await page.goto("/money/close-day");
  await page.locator('input[name="LKR"]').fill(String(e.expected.lkr));
  await page.locator('input[name="USD"]').fill(String(e.expected.usd));
  await page.locator('input[name="EUR"]').fill(String(e.expected.eur));
  await page.locator('input[name="card"]').fill(String(e.card));
  await expect(page.getByText("0.00 ✓").first()).toBeVisible();
  await page.locator("#closeDay").click();
  const dialog = page.getByRole("dialog");
  if (await dialog.isVisible().catch(() => false)) await dialog.getByRole("button").last().click();
  await expect(page.getByText("Day closed").first()).toBeVisible();
});
