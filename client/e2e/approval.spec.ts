import { expect, test } from "@playwright/test";
import { apiAs, gemToSell, openDay, OWNER, SALES, signIn, startBill } from "./helpers";

/* P6-01 browser test 3: a price below the minimum waits for the owner; after the owner approves on the Approvals
   screen, the salesperson takes the payment. Two people = two browser windows. */
test("price below the minimum needs the owner's approval", async ({ browser, request }) => {
  const sales = await apiAs(request, SALES);
  const owner = await apiAs(request, OWNER);
  const day = await openDay(sales);
  const gem = await gemToSell(sales, 200_000, 2_000_000);
  const full = await owner.get(`/api/gems?q=${gem.code}&size=5`, day.branch);
  const min = Number(full.items.find((g: { code: string }) => g.code === gem.code)?.min);
  expect(min, "the owner sees the minimum price").toBeGreaterThan(0);

  const salesCtx = await browser.newContext();
  const salesPage = await salesCtx.newPage();
  await signIn(salesPage, SALES);
  await startBill(salesPage, gem.code, Math.floor(min - 1000));
  await expect(salesPage.getByText(/Below the minimum price/)).toBeVisible();
  await salesPage.locator("#payBtn").click();
  await expect(salesPage.getByText(/Waiting for owner approval/).first()).toBeVisible();
  const text =
    (await salesPage
      .getByText(/Approval request AP-\d+/)
      .first()
      .textContent()) ?? "";
  const approval = /AP-\d+/.exec(text)![0];

  const ownerCtx = await browser.newContext();
  const ownerPage = await ownerCtx.newPage();
  await signIn(ownerPage, OWNER);
  await ownerPage.goto(`/approvals#${approval}`);
  const note = ownerPage.getByLabel(`Note for ${approval}`);
  await note.fill("OK for this customer");
  await ownerPage
    .locator(".card", { has: note })
    .getByRole("button", { name: /Approve/ })
    .click();
  await expect(ownerPage.getByText("Approved ✓").first()).toBeVisible();

  // the salesperson's screen notices the answer by itself
  await expect(salesPage.locator("#payBtn")).not.toHaveText(/Waiting/, { timeout: 30_000 });
  await expect(salesPage.locator("#payBtn")).toBeEnabled();
  await salesPage.locator("#payBtn").click();
  await salesPage.getByRole("button", { name: "Cash LKR" }).click();
  await salesPage.locator("#payDone").click();
  await expect(salesPage.getByText("Sale completed ✓")).toBeVisible();
  await salesCtx.close();
  await ownerCtx.close();
});
