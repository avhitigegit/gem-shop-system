import { expect, test } from "@playwright/test";
import { ACCOUNTANT, apiAs, OWNER, signIn } from "./helpers";

/* P6-01 browser test 5: the accountant closes an ended month; after that nothing dated in it can be added (here an
   expense by the owner). The month is re-opened at the end so the test can run again. */
let closedMonth = "";

// always re-open the month, also after a failure or a time-out
test.afterEach(async ({ request }) => {
  if (!closedMonth) return;
  const owner = await apiAs(request, OWNER);
  const r = await owner.post(`/api/months/${closedMonth}/reopen`, { reason: "Browser test - opened again" });
  expect(r.ok(), "month re-opened after the test").toBeTruthy();
  closedMonth = "";
});

test("a closed month cannot get new expenses", async ({ browser, page, request }) => {
  const owner = await apiAs(request, OWNER);
  const list = await owner.get("/api/months");
  const m = (list.months as { month: string; closed: boolean; ended: boolean }[]).find((x) => x.ended && !x.closed);
  test.skip(!m, "no ended month is open");
  const month = m!.month;
  const name = monthName(month);

  const accCtx = await browser.newContext();
  const acc = await accCtx.newPage();
  await signIn(acc, ACCOUNTANT);
  await acc.goto("/reports/close-month");
  const row = acc.locator(".card", { hasText: name }).filter({ has: acc.getByRole("button", { name: "Close month" }) });
  await row.first().getByRole("button", { name: "Close month" }).click();
  await acc.getByRole("dialog").getByRole("button", { name: "Close month" }).click();
  await expect(acc.locator(".card", { hasText: name }).first()).toContainText("Closed");
  closedMonth = month;
  await accCtx.close();

  await signIn(page, OWNER);
  await page.goto("/money/expenses/new");
  await page.locator("#xDate").fill(`${month}-15`);
  const branch = page.locator("#xBranch");
  if (await branch.isEnabled()) await branch.selectOption({ index: 1 });
  await page.locator("#xType").selectOption({ index: 1 });
  await page.locator("#xTo").fill("Browser test");
  await page.locator("#xAmt").fill("1500");
  // every expense needs a bill photo: a small white picture
  const uploaded = page.waitForResponse((r) => r.url().includes("/api/files") && r.request().method() === "POST");
  await page
    .locator('input[type="file"]')
    .first()
    .setInputFiles({ name: "bill.png", mimeType: "image/png", buffer: whitePng() });
  expect((await uploaded).ok()).toBeTruthy();
  const saved = page.waitForResponse((r) => r.url().endsWith("/api/expenses") && r.request().method() === "POST");
  await page.locator("#saveExpense").click();
  expect((await saved).status(), "the server refuses an expense in a closed month").toBe(409);
  await expect(page.getByText(/closed/i).first()).toBeVisible();
});

function monthName(ym: string) {
  const [y, mo] = ym.split("-").map(Number);
  return new Date(y, mo - 1, 1).toLocaleString("en-GB", { month: "long" }) + " " + y;
}

/** A 16 × 16 white PNG. */
function whitePng() {
  return Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAIAAACQkWg2AAAAFElEQVR4nGP4TyJgGNUwqmH4agAAr639H708R/EAAAAASUVORK5CYII=",
    "base64",
  );
}
