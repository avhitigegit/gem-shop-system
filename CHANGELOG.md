# Prototype change log

Each version is saved in git with a tag (`git tag` lists them).
To see an old version: `git checkout v3` (go back with `git checkout main`).

## v4 – 14/11/2026 – menu matches the demo guide, duplicates removed
**Menu (left side)**
- The open section now lists **every** page of that section, with a thin line between
  "What do you want to do?", "Look up" and "Other tasks". Nothing is hidden behind a fold.
- Removed the "More in …" link (it only went back to the section home).
- **Settings:** Shop details, Staff logins, Exchange rates, Commission, **Branches**, **Drop-down lists**.
- **Cash & expenses:** Start the day, Pay out cash from the drawer, Close the day, Add an expense,
  Expenses, Monthly bills, **Expense types**.
- Section home pages: the folded "Less often" list is now an open "Other tasks" card group.

**Duplicates removed (one way to each job)**
- *Payment screen (practice)* – removed from the menu; payment is the last step of **New sale**.
- *Print shop certificate* – was listed twice in Gems & stock; now one entry (Owner, Salesperson).
- *History of one gem* – removed from the menu; open it with the **History** button on the gem page.
- *Stock value* – removed from Gems & stock; it is now **Reports → Stock & value → Value by gem type**
  (the same totals were on two pages).
- *VAT detail* – removed from the Reports menu; open it from **VAT for the month → Invoice list**.
- Header buttons that only repeated a menu page (e.g. "Invoices" and "Reservations" on New sale,
  "Credit notes" on Returns) are hidden. Main action buttons stay.
- *Start the day* – removed the "Drawer expenses →" and "Day-end closing →" buttons; the
  Today's cash step bar already does this.

**Fixes**
- Detail pages (invoice, gem, customer, guide, supplier, quotation, slip) show the right path in
  the top bar, e.g. "Sell › Invoices › Invoice", and the menu highlights the parent list.
- Approvals: the back button no longer points to the same page.
- Version number shown at the bottom of the menu.

**Demo guide PDF:** not changed yet. Pages to update: Payment, Branches, Drop-down lists,
Expense types (now in the menu), Stock value and VAT detail (new place), History of one gem.

## v3 – 13/11/2026 – section home pages
- Each menu item opens a section home page (needs attention, task buttons, look-up cards).
- Every screen on its own page with a back bar; no tab strip.
- Step bars: Today's cash, New gems coming in, Month end, the sale, the return.
- New step-by-step page: Customer brings a gem back.
