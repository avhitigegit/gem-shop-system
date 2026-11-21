# Prototype change log

Each version is saved in git with a tag (`git tag` lists them).
To see an old version: `git checkout v3` (go back with `git checkout main`).

## v12 – 22/11/2026 – customer birthday and automatic birthday wishes (J8)
The client wants the customer's birthday so the system can send a birthday wish by WhatsApp and email.
- Customer birthday (day and month only – no year, PDPA) is asked on: New sale → step 2 (new
  customer), Customers → + Add customer, and Customer page → Edit. Optional. Wrong dates (e.g. 31 April,
  or only the day chosen) are stopped. 29 February is wished on 28 February in other years.
- Customers list: new "Birthday" column with "🎂 Today" / "in N days", and a "Birthdays this week" box
  (in place of "Top country").
- Customer page: shows the birthday; "Automatic messages" lists the birthday wishes sent (email /
  WhatsApp status) and says when the next one goes out, or why it will not.
- Email & WhatsApp settings: new "Birthday wishes" card – on/off, send by Email and/or WhatsApp, time
  (default 09:00 Sri Lanka time), email subject, message with {name} and {shop}, live preview, the
  birthdays in the next 30 days with what will happen, and "Send today's wishes now".
  Opening the page with `#birthday` puts this card first.
- Rules: only customers who agreed to promotions and have not unsubscribed (rule 8); one wish a year;
  WhatsApp only when WhatsApp Premium is on and verified (a Meta-approved marketing template).
- The daily job (real build: a server task every day at the set time) is simulated: it runs when any
  page is opened after the set time. Each wish is written to the Activity log as "System (automatic)".
- Sample data: birthdays for most customers, two today, a few in the coming days, two wishes already sent.
- Sitemap: J8 added (BRD count J1–J8). Sidebar shows "Prototype v12".
- Saved demo data uses a new storage key, so the sample data is rebuilt once.

**Documents:** Demo guide → v12: new screen "Birthday wishes"; New sale, Customers, One customer and
Email & WhatsApp settings text updated; all screenshots retaken. 50 pages, 75 screens.
- BRD → version 1.3: new J8 Birthday wishes; F5 and J1 include the birthday; rule 8 covers birthday
  wishes; PDPA note; change summary; totals 81 features + 8 system qualities. 16 pages.

## v11 – 21/11/2026 – card bank fee is paid by the shop (client correction)
v9 was wrong: the customer does **not** pay the 3% card fee. The customer always pays the shown or
agreed price, by cash or by card. The bank keeps 3% of card payments and the **shop** pays it.
Example: gem LKR 50,000 → customer pays LKR 50,000 by cash or card; on a card payment the bank
keeps LKR 1,500, which is a shop cost.
- Take payment: "Cash and card: the same price. The 3% bank fee on cards is paid by the shop."
  Card lines say "Charge LKR … on the machine" (the same amount). "Customer pays in total" removed.
- New sale: the cash price / card price line is removed.
- Invoice: no Card fee line, no "TOTAL PAID" – the total is the agreed price; payment shows "Card".
- Quotation: "Same price for cash or card."
- Close the day: the expected card machine total = card sales (no fee added). New line
  "Card bank fee (3%, shop pays)" so the owner sees the cost.
- Profit & Loss: new cost line "Less: card bank fees (3%, paid by the shop)" and a "Card fees"
  column; net profit now takes the fee off (also on Accountant home, Tax pack and Month closing).
- Settings → Shop details: field renamed "Card bank fee (%) – paid by the shop".
- Sidebar shows "Prototype v11" (it still said v9).
- Saved demo data uses a new storage key, so the sample data is rebuilt once.

**Documents:** Demo guide (`doc/Ceylon_Gem_House_Demo_Guide.pdf`) → v11: screens 6, 25, 27, 28, 33,
52, 72 and the demo story changed; all screenshots retaken on 21/11/2026. 49 pages, 74 screens.
- BRD (`doc/Gem_Shop_BRD.pdf`) → version 1.2: card fee paid by the shop (change summary, process step 5,
  A3, E2, F2, G7, H1, I3, rule 12, assumptions, glossary, POS mock-up). 16 pages.

## v10 – 21/11/2026 – prototype and demo guide checked against each other
Every one of the 74 screens in the demo guide was compared with the prototype (page, menu place,
who can use it, buttons and fields). Differences found and fixed:

**Prototype changed (to match the guide)**
- Commission per sale: new filters From / To / Guide (or Walk-in only) / Salesperson, as the guide says.
- Expenses: the field "Category" is now called **Expense type** everywhere (Add an expense – it
  showed "Category (M1)", Pay out cash from the drawer, Expenses list and filter, Monthly bills,
  Expenses report), the same as Settings → Drop-down lists → Expense types.
- Gems moving between branches now show **On the way** (was "In transit") on All gems, the status
  filter and Send / receive between branches – the same words as Home and the guide.

**Demo guide changed (to match the prototype)**
- 13 Email & WhatsApp settings: example is "Email the invoice PDF after every purchase".
- 23 One gem: Print tag is for owner and shop staff; the button is "Shop certificate".
- 39 Commission per sale: press a sale in the list to see how it was worked out.
- 48 / 50 / 51: "expense type"; Expenses list filters are month, expense type, payment method and
  status (branch is chosen at the top).
- 52 Close the day: count each currency and the card machine total (not "card slips").
- 59 Return a gem to the supplier: choose the gem (its supplier is shown), reason, details and refund.
- 60 Purchases: filter by supplier or dates. 65 Daily cash: choose the dates.
- 66 Expenses report: choose the branch; each month has its own column.

**Documents:** Demo guide (`doc/Ceylon_Gem_House_Demo_Guide.pdf`) → v10: 49 pages, 74 screens,
all screenshots retaken on 21/11/2026.

## v9 – 16/11/2026 – 3% card fee (client request)
The bank takes 3% of every card payment in Sri Lanka, so the customer pays it on top.
Cash stays at the shown price. Example: gem LKR 50,000 → cash LKR 50,000, card LKR 51,500.
- Payment window: shows "Card: + 3% bank fee"; each card line shows the fee and the amount to
  charge on the machine; "Customer pays in total" appears when a card is used (also for split payments).
- New sale: under the total it shows the cash price and the card price.
- Invoice: Gem price + Card fee (3% bank charge) = TOTAL PAID; the payment line shows the fee.
- Quotation: "Price for cash. Card payments: + 3% bank fee (amount)".
- Close the day: the expected card machine total includes the fee.
- Settings → Shop details: new field "Card payment fee (%)" (default 3).
- Not changed: sales, profit, commission and refunds use the gem price only (the fee goes to the bank).
- Saved demo data uses a new storage key, so the sample data is rebuilt once with card fees.

**New gems – simpler (also v9)**
- Buying from a supplier already puts every gem into stock, so the "New gems coming in" step bar is now
  just: Buy from supplier → Print the tags → Ready to sell ("Add each gem" removed).
- "Add a gem" is renamed "Add a gem (not bought)" and says it is only for gems not from a supplier
  purchase (e.g. gems the shop already had).
- New: on Buy gems from a supplier, each gem has a tick "Not ready to sell yet" (e.g. needs cutting).
  Such gems are in stock but cannot be sold. On the gem page, Owner or Shop Staff press
  "✓ Mark ready to sell". All gems has a "Not ready to sell" filter; stock check and stock value include them.

**Documents:** updated on 16/11/2026.
- Demo guide (`doc/Ceylon_Gem_House_Demo_Guide.pdf`) → v9: 49 pages, 74 screens, all screenshots retaken.
  Card fee on New sale, Take payment, Invoice, Quotation and Close the day; new-gem chapter says
  buying = adding to stock; "Add a gem (not bought)"; "Not ready to sell" tick and "Mark ready to sell".
- BRD (`doc/Gem_Shop_BRD.pdf`) → version 1.1: no VAT (A3, G3, L4/M3/N3 removed, rules, assumptions,
  laws, glossary), card fee (new G7, rule 12, F2, H1, I3), buying adds to stock (process steps, B2, B3,
  new B5, C1, D2, rule 13). Changed items are marked UPDATED / NEW / REMOVED. 80 features + 8 qualities.

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
- Demo guide (doc/Ceylon_Gem_House_Demo_Guide.pdf) updated to v8: 49 pages, 74 screens, all
  screenshots retaken; the two VAT screens are removed.

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
