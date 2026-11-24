import { expect, test } from "@playwright/test";
import { apiAs, gemToSell, openDay, OWNER, SALES, signIn, startBill } from "./helpers";

/* P6-01 browser tests 1 + 2: a sale paid with USD cash + card, then the customer brings the gem back (refund, approved
   by the owner). Run in order: the return uses the sale. */
test.describe.configure({ mode: "serial" });

let invoice = "";
let gemCode = "";

test("sale with mixed payment (USD cash + card)", async ({ page, request }) => {
  const sales = await apiAs(request, SALES);
  const before = (await openDay(sales)).expected;
  const gem = await gemToSell(sales);
  gemCode = gem.code;

  await signIn(page, SALES);
  await startBill(page, gem.code, gem.price);
  await page.locator("#payBtn").click();
  await expect(page.getByRole("dialog", { name: "Take payment" })).toBeVisible();
  await page.getByRole("button", { name: "Split: USD cash + card" }).click();
  await expect(page.locator("#payDone")).toBeEnabled();
  await page.locator("#payDone").click();

  await expect(page.getByText("Sale completed ✓")).toBeVisible();
  const desc = await page
    .getByText(/Invoice INV-\d+/)
    .first()
    .textContent();
  invoice = /INV-\d+/.exec(desc ?? "")![0];

  // the drawer and the card total both moved; the card fee is not shown to the salesperson
  const after = (await sales.get("/api/cash/day")).expected;
  expect(after.card).toBeGreaterThan(before.card);
  expect(after.expected.usd).toBeGreaterThan(before.expected.usd);
  await expect(page.getByText(/card fee/i)).toHaveCount(0);
});

test("return with the owner's approval gives a credit note", async ({ page }) => {
  test.skip(!invoice, "needs the sale from the first test");
  await signIn(page, OWNER);
  await page.goto("/sell/returns/new");
  await page.locator("#q").fill(invoice);
  await page.locator(`[data-sale="${invoice}"]`).click();
  // one gem on the bill: the screen moves to "Which gem?" and picks it by itself
  const next = page.locator("#wizNext");
  const gem = page.locator(`[data-pid="${gemCode}"]`);
  if (await gem.isVisible()) await gem.click();
  await page.locator('[data-kind="REFUND"]').click();
  await next.click();
  await page.locator("#rr").fill("Browser test: customer changed their mind");
  // the remaining steps up to "send to the owner"
  for (let i = 0; i < 4 && !(await page.locator("#approveNow").isVisible()); i++) {
    if (await next.isVisible()) await next.click();
    await page.waitForTimeout(400);
  }
  await page.locator("#approveNow").click();
  await expect(page.locator("#answerTitle")).toContainText(/approved/i);
  await expect(page.getByText(/CN-\d+/).first()).toBeVisible();
});
