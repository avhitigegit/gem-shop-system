# Prototype change log

Each version is saved in git with a tag (`git tag` lists them).
To see an old version: `git checkout v3` (go back with `git checkout main`).

## v8 – 15/11/2026 – no VAT anywhere (client request)
The owner's prices already include VAT and VAT is not shown to customers.
- Invoice ("INVOICE", not "TAX INVOICE"), quotation, reservation, credit note, payment and
  New sale: only the price and the total. No "before VAT", no VAT line, no VAT number.
- Removed pages: Monthly VAT summary and VAT report (old links go to Profit & Loss / Sales report).
  Month end steps: Check expenses → Profit & Loss → Tax pack → Close the month.
- Invoices list, Sales report, Accountant home, Tax pack, Month closing, Profit & Loss:
  VAT columns and totals removed.
- Expenses and repeating expenses: no VAT field; only the bill amount.
- Shop details and sign-up: VAT number, VAT rate and "prices include VAT" removed.
- Commission is now worked out on the full agreed price (before, the 18% VAT was taken off first).
- Saved demo data uses a new storage key, so the sample data is rebuilt once without VAT.

## v7 – 15/11/2026 – one way to each job (duplicates removed after the demo)
**Same button twice on one screen**
- A1–A3 Cash & expenses, Reports and Gems & stock home pages: the step bar is removed (the big
  buttons do the same). A notice no longer has a button when that button is already on the page.
- A3/A4 Removed the extra "All gems →" and "All invoices →" links (the cards open the same lists).
- A5 Home: removed the "Close the day" / "Start the day" tile (the Today's cash bar is there).
- A6 New sale: removed the second step bar; the numbered boxes 1–4 stay.
- A7 Invoice, quotation, shop certificate, commission slip: one back button (top bar only).
- A8 Invoices, Suppliers, Guides, Credit notes: rows open by the number/name only
  (removed "View", "Open", "Profile", "View / print"). "Print" on invoices stays.
- A9 Expenses list no longer shows the accountant's Month end bar.

**Two pages for one job**
- B1 Add an expense = bank transfer or card only. Cash from the drawer only through
  "Pay out cash from the drawer" (old drawer expenses can still be corrected).
- B2 Guide/driver commission is paid only in "Pay guides & drivers" (choose Cash (drawer) –
  it still shows on the drawer page and comes off the expected cash).
- B4 "Stock by branch" merged into **All gems** (old address opens All gems).
- B7 "Expense types" moved into **Settings → Drop-down lists** (old address opens it there).

**Menu**
- C1 Sell menu: removed "Keep a gem for a customer" and "Give a price quote" – they are the
  "Keep for customer" and "Give a quote" buttons on the New sale screen.
- C2 Sell menu: removed "New sale" (the gold **+ New sale** button is always at the top of the menu;
  the Sell home page still has the New sale button).

Checked: 77 pages × 4 roles, no errors.

**Demo guide PDF:** updated to v7 on 15/11/2026 (`doc/Ceylon_Gem_House_Demo_Guide.pdf`, 50 pages,
76 screens). New screenshots; "Stock by branch" merged into All gems; Expense types now under
Settings → Drop-down lists; Keep / quote from the New sale buttons; drawer page without commission;
Add an expense = bank or card; How the screens work mentions the menu arrows and the numbered sale
boxes; demo tips use Normal reset / Reset system. Cover and footer say "Prototype v7".

## v6 – 14/11/2026 – two reset buttons (prototype only)
In the user menu (top right, under "Prototype only") and on the sitemap page:
- **↺ Normal reset** – throws away all changes made in this browser and goes back to the
  starting sample data (87 gems, 40 sales, 25 customers …).
- **⌫ Reset system** – an empty shop to try the system from zero. Removes all gems, suppliers,
  purchases, customers, guides, sales, returns, expenses, cash days, approvals and the activity
  log. Keeps the **staff logins** and the **shop set-up** (shop details, branches, drop-down lists,
  commission, exchange rates, email/WhatsApp settings). Numbers start again (first invoice, first gem …).
- Both ask "Are you sure?" first, also clear an unfinished sale, and open the Home page.
- The bottom of the menu shows "empty system" or "sample data only".

**Empty shop fixes** (a real new shop would also hit these):
- Invoice, gem, gem history, shop certificate, quotation, supplier, guide, customer, commission
  per sale and commission slip pages show "📭 Nothing here yet" with a button to the right list
  instead of a blank/broken page.
- Accountant home, VAT for the month, Every VAT invoice, Profit & Loss, Tax pack and Close the month
  always show the current month, even before the first sale.
- Customers: "Top country" shows "—" when there are no customers.
- Checked: 77 pages × 4 roles, with sample data and with an empty system, no errors.

## v5 – 14/11/2026 – menu keeps its place, ⌄ arrows to open / close
- The left menu no longer jumps back to the top when you press a main item (e.g. Reports);
  it stays scrolled where it was.
- Each main item has a small arrow: **›** = closed, **⌄** = open. Press the arrow to show or hide
  that section's pages without leaving the screen you are on. Pressing the name still opens the
  section home page.
- Sections you open or close stay that way while the browser tab is open.
- Checked: 77 pages × 4 roles, no errors.

**Demo guide PDF:** "How the screens work" point 1 should mention the arrows (small text change;
screenshots still match apart from the arrows).

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

**Demo guide PDF:** updated to v4 on 14/11/2026 (`doc/Ceylon_Gem_House_Demo_Guide.pdf`, 51 pages).
New screenshots; menu paths fixed for Payment, Branches, Drop-down lists, Email & WhatsApp settings,
Expense types, transfers, stock mistakes, supplier returns and History of one gem. *Stock value* is now
"Value by gem type" in the Reports chapter; *VAT detail* is now "Every VAT invoice" in Month end.
Cover and page footer show "Prototype v4".

## v3 – 13/11/2026 – section home pages
- Each menu item opens a section home page (needs attention, task buttons, look-up cards).
- Every screen on its own page with a back bar; no tab strip.
- Step bars: Today's cash, New gems coming in, Month end, the sale, the return.
- New step-by-step page: Customer brings a gem back.
