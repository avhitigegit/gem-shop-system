# Gem Shop System – Master Task Breakdown

Built from: Prototype v12 (`prototype/`), Demo guide v12 (75 screens), BRD v1.3, Tech stack.
Stack: **Backend** Spring Boot (Java 21) + PostgreSQL · **Frontend** Next.js + TypeScript + Tailwind + shadcn/ui (PWA).

## How to use

1. Copy a heading (for example `P3-01 New sale (POS)`) and paste it to Claude.
2. Claude creates the branch from `dev`, does every task under that heading, and opens a pull request into `dev`.
3. Claude ticks the finished tasks `- [x]` and changes **Status**.

Status: `To do` → `In progress` → `In review` → `Merged to dev` → `In UAT` → `Done`

## Before the first heading (you do this once)

- [x] GitHub repository: https://github.com/avhitigegit/gem-shop-system (public).
- [x] Code folder: `E:\Software Apps\Sales and Inventory Management\Gold House` (repository root: `client/`, `server/`, `prototype/`, `infra/`, `doc/`).

Then paste `P0-01 Repository and branches`.

## Branch rules

- `main` = production · `dev` = development (default) · `uat` = client testing, **created near the end** (P6-02).
- **Every branch starts from `dev`** and goes back to `dev` by pull request.
- Most headings use **one branch**. Big headings have **one branch for Backend and one for Frontend**, shown under each part.
- All development and testing is done **locally** on `dev`. No UAT release per feature or per phase.
- Near the end: everything goes `dev` → `uat` **at once** for client testing. Go-live: `uat` → `main` (tag `v1.0.0`).
- Real email (Amazon SES) and WhatsApp are connected near the end (P6-03). Until then: test email (MailHog) and a WhatsApp test mode.
- After go-live only: urgent production fixes use `hotfix/*` from `main`.

## Rules for every screen

- Look and work the same as the prototype screen and the demo guide.
- No VAT anywhere.
- The customer pays the same price by cash or card. The 3% card bank fee is a shop cost.
- Offers and birthday wishes only to customers who gave permission.
- Every API checks role and branch. Cost and profit only for the owner (and accountant where the BRD says).
- Money as exact decimals. Currencies LKR, USD, EUR only.

## Progress

Paste the headings in this order.

- [x] P0-01 Repository and branches
- [ ] P0-02 Project skeleton
- [ ] P0-03 CI checks
- [ ] P0-04 Test plan
- [ ] P1-01 Database base and sample data
- [ ] P1-03 Design and app frame
- [ ] P1-02 Login and permissions
- [ ] P1-04 Activity log, approvals and alerts
- [ ] P1-05 Files, PDFs and numbers
- [ ] P1-06 Settings
- [ ] P2-01 Gems
- [ ] P2-02 Tags and shop certificate
- [ ] P2-03 Suppliers and purchases
- [ ] P2-04 Stock
- [ ] P3-01 New sale (POS)
- [ ] P3-02 Payment and invoices
- [ ] P3-03 Keep a gem / give a quote
- [ ] P3-04 Returns
- [ ] P3-05 Guides and commission
- [ ] P3-06 Daily cash
- [ ] P4-01 Home and owner control
- [ ] P4-02 Phone app and push alerts
- [ ] P4-03 Customers
- [ ] P4-04 Messages and birthday wishes
- [ ] P4-05 Offers
- [ ] P5-01 Expenses
- [ ] P5-02 Reports
- [ ] P5-03 Accountant and month end
- [ ] P6-01 Tests, security and speed
- [ ] P6-02 UAT server and release to UAT
- [ ] P6-03 Connect real email, Amazon SES and WhatsApp
- [ ] P6-04 Production server and backups
- [ ] P6-05 Go-live
- [ ] P6-06 Training and handover

---

# P0 Setup

## P0-01 Repository and branches
**Branch:** first commit on `main`, then create `dev`
**Repository:** https://github.com/avhitigegit/gem-shop-system (public) · folder `Gold House`
**Status:** Done

### Tasks
- [x] Repository in the `Gold House` folder: `client/` (frontend), `server/` (backend), `prototype/` (with its full history, tags `prototype-v3` … `prototype-v12`), `infra/`, `doc/`.
- [x] Add README, `.gitignore`, `.editorconfig` (and `.gitattributes`).
- [x] Link it to the GitHub repository and push the first commit to `main`.
- [x] Create `dev` from `main`, push it, make `dev` the default branch (merged branches are deleted automatically).
- [x] Protect `main` and `dev` on GitHub: pull request only, no force push, no delete. "Checks must pass" is added with the CI in P0-03.
- [x] Add a pull request template; this file is kept in `doc/` inside the repository.

## P0-02 Project skeleton
**Branch:** `feature/p0-02-skeleton`
**Status:** To do

### Backend
- [ ] Spring Boot 3, Java 21, one package per module.
- [ ] PostgreSQL, Spring Data JPA, Flyway migrations.
- [ ] Swagger / OpenAPI and `/api/health`.
- [ ] One error format for all APIs.
- [ ] Settings per environment: local, dev, uat, prod.

### Frontend
- [ ] Next.js + TypeScript, Tailwind, shadcn/ui, ESLint, Prettier.
- [ ] API client generated from the backend OpenAPI.
- [ ] Health page that calls the backend.

### Local setup
- [ ] Docker Compose: database, backend, frontend, Nginx, MailHog (test emails).
- [ ] One command starts everything.

## P0-03 CI checks
**Branch:** `feature/p0-03-ci`
**Status:** To do

### Tasks
- [ ] GitHub Actions on every pull request to `dev`: backend build and tests, frontend lint and build.
- [ ] Build Docker images (checked, not deployed yet).
- [ ] Local "reset sample data" command for testing.

## P0-04 Test plan
**Branch:** `feature/p0-04-test-plan`
**Status:** To do

### Tasks
- [ ] One UAT test per demo guide screen (75), grouped by guide chapter.
- [ ] One test per business rule (13).
- [ ] Role test list: what each role can and cannot do.
- [ ] Bug report template and UAT sign-off form.

---

# P1 Foundation

## P1-01 Database base and sample data
**Branch:** `feature/p1-01-base-data`
**Status:** To do

### Backend
- [ ] Base fields on every table: created / updated at and by, version.
- [ ] Tables: branch, user, shop settings.
- [ ] Money: exact decimal in LKR + currency + rate + foreign amount.
- [ ] Branch on every business record; "my branch / all branches" filter.
- [ ] Sample data loader = prototype data (2 branches, 4 users, gems, suppliers, guides, 25 customers with birthdays, sales, expenses).
- [ ] Sample data only on local and UAT – never production.

## P1-03 Design and app frame
**Branch:** `feature/p1-03-app-frame`
**Screens:** `assets/css/app.css`, `assets/js/layout.js` · Guide "How the screens work"
**Status:** To do

### Frontend
- [ ] Copy the prototype colours, fonts and spacing into the theme.
- [ ] Cards, number tiles, status pills, buttons.
- [ ] Forms: required star, hints, error messages.
- [ ] Dialogs, confirm dialog, reason dialog, toasts.
- [ ] Gem picture (coloured by gem type) when there is no photo.
- [ ] Formats: `LKR 1,234.00`, `dd/mm/yyyy`; currency switch LKR / USD / EUR.
- [ ] Menu with the 8 sections, sub-items by role.
- [ ] Gold **+ New sale** button.
- [ ] Top bar: branch switch, currency switch, bell, user menu.
- [ ] Back bar, breadcrumbs and step bars.
- [ ] Section home page layout (big cards with counts).
- [ ] Phone layout.
- [ ] "Not allowed" page.
- [ ] Table with filters, empty state, Export PDF / Excel buttons.
- [ ] Photo upload box and scan box (USB scanner).

## P1-02 Login and permissions
**Screens:** `auth/login.html`, `auth/signup.html`, `auth/forgot-password.html` · Guide 1–3
**Status:** To do

### Backend — branch `feature/p1-02-be-auth`
- [ ] Login with JWT + refresh token; logout.
- [ ] BCrypt passwords; lock after 5 wrong tries.
- [ ] Short idle timeout for shop computers.
- [ ] Roles: Owner, Salesperson, Shop Staff, Accountant; user linked to a branch.
- [ ] Role and branch check on every API.
- [ ] Cost price, profit and stock value removed for non-owners.
- [ ] Email sender (shop SMTP).
- [ ] Forgot password: reset link by email, one use, expires in 1 hour.
- [ ] Owner first-time setup (owner, shop, first branch, rates).
- [ ] Tests: allowed and blocked calls for each role.

### Frontend — branch `feature/p1-02-fe-auth`
- [ ] Login screen.
- [ ] Forgot password and reset password screens.
- [ ] Owner first-time setup screen with step bar.
- [ ] Auto refresh of login; logout when idle.
- [ ] Accountant opens Accountant home; others open Home.

## P1-04 Activity log, approvals and alerts
**Branch:** `feature/p1-04-log-approvals-alerts`
**Status:** To do

### Backend
- [ ] Activity log: time, user (or "System (automatic)"), action, detail, branch.
- [ ] Log cannot be edited or deleted (no API, database blocks it).
- [ ] Approval requests: below minimum price, return, invoice cancel, stock correction, large expense, change in closed month.
- [ ] Owner approves or rejects with a note; approved action then runs.
- [ ] Status check for the waiting screen (every few seconds).
- [ ] Alerts per branch: sale, below-minimum request, return, cancellation, stock change, cash difference.
- [ ] Unread count and mark as read.

## P1-05 Files, PDFs and numbers
**Branch:** `feature/p1-05-files-pdfs`
**Status:** To do

### Backend
- [ ] Upload photos, bill photos, certificate scans (type and size check).
- [ ] Resize to about 300 KB and make a thumbnail.
- [ ] Private download links.
- [ ] PDF with shop header and footer (OpenPDF).
- [ ] QR / barcode (ZXing).
- [ ] Excel export (Apache POI).
- [ ] Running numbers without gaps: `GEM-00001`, `INV-000001`, credit note, purchase, quote, held sale, expense, transfer.
- [ ] Encrypt passport, phone, email; show passport masked.

## P1-06 Settings
**Screens:** `hub/settings.html`, `a-setup/*.html` · Guide 5–12
**Status:** To do

### Backend — branch `feature/p1-06-be-settings`
- [ ] Branches: add, edit, active / inactive.
- [ ] Staff: add with role and branch, edit, reset password, switch off.
- [ ] Shop details: name, address, phone, email, logo, NGJA licence + expiry, return days, card bank fee % (paid by the shop). No VAT.
- [ ] Exchange rates USD / EUR per day with history; "today's rate" for sales.
- [ ] Drop-down lists: gem types, product types, colours, shapes, labs, expense types.
- [ ] Commission rules: guide / driver %, staff amount by price level.

### Frontend — branch `feature/p1-06-fe-settings`
- [ ] Settings home.
- [ ] Shop details.
- [ ] Staff logins.
- [ ] Exchange rates.
- [ ] Commission rules.
- [ ] Branches.
- [ ] Drop-down lists (including Expense types).

---

# P2 Gems & stock

## P2-01 Gems
**Screens:** `hub/stock.html`, `c-products/products.html`, `product-form.html`, `product-detail.html` · Guide 16, 20, 22, 23
**Status:** To do

### Backend — branch `feature/p2-01-be-gems`
- [ ] Gem: auto code, name, product type, gem type, carat, colour, shape, treatment, description, branch.
- [ ] Prices: cost (owner only), selling, minimum.
- [ ] Certificate: Lab / Shop / None, lab, number, date, scan; sapphire suggests Lab.
- [ ] Several photos per gem.
- [ ] Status: In stock, Not ready to sell, Reserved, Sold, Returned, On the way.
- [ ] Mark ready to sell.
- [ ] Search: branch, gem type, product type, status, text, tag scan.
- [ ] Only the owner adds gems and changes prices; every change logged.

### Frontend — branch `feature/p2-01-fe-gems`
- [ ] Gems & stock home.
- [ ] All gems: photo grid, table, filters, scan box.
- [ ] Add / edit gem form (owner).
- [ ] One gem page with **Mark ready to sell**.

## P2-02 Tags and shop certificate
**Branch:** `feature/p2-02-tags-certificate`
**Screens:** `c-products/tag-print.html`, `shop-certificate.html` · Guide 21, 30
**Status:** To do

### Backend
- [ ] Tag data with QR and barcode.
- [ ] Shop certificate PDF: photo, gem details, code, shop details.

### Frontend
- [ ] Print tags screen (choose gems, label size).
- [ ] Shop certificate print / download.

## P2-03 Suppliers and purchases
**Screens:** `b-purchasing/*.html` · Guide 17, 18, 19, 60, 61
**Status:** To do

### Backend — branch `feature/p2-03-be-purchasing`
- [ ] Suppliers: name, phone, address, NIC / business registration.
- [ ] Cash purchase: supplier, date, branch, gems with details and prices, total, bill photo.
- [ ] Saving adds every gem to stock with its own code (one step, no second entry).
- [ ] "Not ready to sell" tick per gem.
- [ ] Purchase history by supplier and date.
- [ ] Return to supplier with reason; gem leaves stock.

### Frontend — branch `feature/p2-03-fe-purchasing`
- [ ] Suppliers list and One supplier.
- [ ] Buy gems from a supplier (step bar).
- [ ] Purchases list.
- [ ] Return a gem to the supplier.

## P2-04 Stock
**Screens:** `d-stock/*.html` · Guide 57, 58, 59, 62, 69
**Status:** To do

### Backend — branch `feature/p2-04-be-stock`
- [ ] Transfer to the other branch (On the way) and receive.
- [ ] Stock check: scan tags; found / missing / wrong branch / unknown.
- [ ] History of one gem from the activity log.
- [ ] Stock value at cost and selling price (owner only).
- [ ] Stock correction with reason; changes only after owner approval.

### Frontend — branch `feature/p2-04-fe-stock`
- [ ] Send / receive between branches.
- [ ] Stock check.
- [ ] History of one gem.
- [ ] Value by gem type.
- [ ] Fix a stock mistake.

---

# P3 Selling

## P3-01 New sale (POS)
**Screens:** `hub/sell.html`, `f-pos/pos.html`, `k-owner-control/approvals.html` · Guide 24, 25, 26
**Status:** To do

### Backend — branch `feature/p3-01-be-new-sale`
- [ ] Add gems by code or scan (In stock only); many gems per bill.
- [ ] Agreed price per gem; prices in LKR, USD, EUR (same for cash or card).
- [ ] Customer: old or new (name, country, email, WhatsApp, birthday day + month, permission).
- [ ] Guide / driver or walk-in.
- [ ] Below minimum: sale is held and the owner is asked.
- [ ] Sold gem can never be sold again.
- [ ] Complete sale: invoice number, gem Sold, log, alert.
- [ ] Commission: guide % + staff amount by price level.

### Frontend — branch `feature/p3-01-fe-new-sale`
- [ ] Sell home.
- [ ] New sale with 4 steps and green ticks.
- [ ] Scan box; warning for a sold gem.
- [ ] New customer form with Birthday and permission tick.
- [ ] **Send for owner approval** button and waiting screen.
- [ ] Held sales list with Resume.
- [ ] Unfinished sale kept after refresh.

## P3-02 Payment and invoices
**Screens:** `g-payments/payment.html`, `invoice.html`, `invoices.html` · Guide 27, 28, 29
**Status:** To do

### Backend — branch `feature/p3-02-be-payment`
- [ ] Pay by cash LKR / USD / EUR, card, or a mix.
- [ ] USD / EUR changed to LKR at today's rate; change due.
- [ ] Card: same price; 3% bank fee saved as a shop cost.
- [ ] Total paid must equal the agreed total.
- [ ] Invoice PDF (no VAT line, no card fee line).
- [ ] Send invoice by email and WhatsApp.
- [ ] Cancel invoice only with owner approval; never delete.

### Frontend — branch `feature/p3-02-fe-payment`
- [ ] Take payment window with change due and "same price" note.
- [ ] Invoice page: print, send by email / WhatsApp.
- [ ] Invoices list with Cancel (reason → approval).

## P3-03 Keep a gem / give a quote
**Branch:** `feature/p3-03-reserve-quote`
**Screens:** `f-pos/pos.html?intent=reserve`, `reservations.html`, `quotation-print.html` · Guide 31, 32, 33
**Status:** To do

### Backend
- [ ] Reserve a gem for a customer until a date.
- [ ] Quote with expiry date, PDF, send by email / WhatsApp.
- [ ] Daily job: expired reservations free the gem.
- [ ] Turn a reservation or quote into a sale.

### Frontend
- [ ] Keep / quote mode on New sale.
- [ ] Reserved & quotes list.
- [ ] Quote print.

## P3-04 Returns
**Branch:** `feature/p3-04-returns`
**Screens:** `e-returns/*.html` · Guide 34, 35, 36
**Status:** To do

### Backend
- [ ] Return or exchange needs a reason and owner approval.
- [ ] On approval: gem back in stock, credit note, commissions reversed.
- [ ] Refund = price paid; card fee is not returned (shop cost).
- [ ] Exchange uses the credit note in a new sale.

### Frontend
- [ ] Customer brings a gem back (steps).
- [ ] Returns list.
- [ ] Credit note print.

## P3-05 Guides and commission
**Branch:** `feature/p3-05-commission`
**Screens:** `h-commissions/*.html` · Guide 37–41
**Status:** To do

### Backend
- [ ] Guides & drivers: name, phone, vehicle, number, commission %.
- [ ] Commission per sale with filters: From, To, Guide (or Walk-in only), Salesperson.
- [ ] Amounts owed; record payment; mark Paid.
- [ ] Commission slip PDF.

### Frontend
- [ ] Guides & drivers list and One guide.
- [ ] Commission per sale.
- [ ] Pay guides & drivers.
- [ ] Commission slip print.

## P3-06 Daily cash
**Screens:** `hub/money.html`, `i-daily-closing/*.html` · Guide 14, 15, 49, 53
**Status:** To do

### Backend — branch `feature/p3-06-be-daily-cash`
- [ ] Opening cash per currency (once per branch per day).
- [ ] Cash paid out from the drawer (small expenses, guide commission).
- [ ] Expected cash = opening + cash sales − refunds − pay-outs.
- [ ] Card machine expected = card sales; card bank fee line (shop pays).
- [ ] Close the day: counted vs expected, difference; day locked.
- [ ] Closing report sent to the owner.

### Frontend — branch `feature/p3-06-fe-daily-cash`
- [ ] Cash & expenses home.
- [ ] Start the day.
- [ ] Pay out cash from the drawer.
- [ ] Close the day (difference highlighted).

---

# P4 Owner & customers

## P4-01 Home and owner control
**Screens:** `dashboard/index.html`, `k-owner-control/*.html` · Guide 4, 26, 54, 55, 56
**Status:** To do

### Backend — branch `feature/p4-01-be-owner`
- [ ] Home figures per role: today's sales, cash, card, approvals waiting, sold today, licence expiry.
- [ ] Phone view figures per branch: sales, cash, sold gems, stock value.
- [ ] Activity log search (date, user, action, branch).

### Frontend — branch `feature/p4-01-fe-owner`
- [ ] Home page for each role.
- [ ] Approvals screen (approve / reject with note).
- [ ] Alerts screen.
- [ ] Owner phone view.
- [ ] Activity log screen.

## P4-02 Phone app and push alerts
**Branch:** `feature/p4-02-pwa-push`
**Status:** To do

### Backend
- [ ] Web Push keys (VAPID) and device sign-up.
- [ ] Push for new sale, approval request, cash difference.
- [ ] On / off per push type.

### Frontend
- [ ] App can be added to the phone home screen (PWA).
- [ ] Ask for push permission; tapping a push opens the right screen.

## P4-03 Customers
**Branch:** `feature/p4-03-customers`
**Screens:** `hub/people.html`, `j-customers-promotions/customers.html`, `customer-profile.html` · Guide 42, 43, 44
**Status:** To do

### Backend
- [ ] Customer: name, country, email, WhatsApp, passport (encrypted), birthday (day + month, optional), permission + date, unsubscribed + date.
- [ ] Purchases, quotes and messages per customer.
- [ ] Unsubscribe link and page; WhatsApp STOP.

### Frontend
- [ ] Customers & guides home.
- [ ] Customers list with Birthday column and "Birthdays this week".
- [ ] Add customer (with birthday).
- [ ] One customer: details, birthday, permission switch, purchases, messages, Edit.

## P4-04 Messages and birthday wishes
**Screens:** `j-customers-promotions/message-settings.html` · Guide 13, 45
**Status:** To do

### Backend — branch `feature/p4-04-be-messages`
- [ ] Build and test with MailHog and a WhatsApp test mode (real accounts connected in P6-03).
- [ ] Email settings (SMTP) and Send test email.
- [ ] Amazon SES settings for offers.
- [ ] WhatsApp Business settings and **Verify connection**.
- [ ] Email / WhatsApp on-off switches.
- [ ] Automatic messages: invoice, thank-you next day, day-end report to owner; message log.
- [ ] Birthday wishes settings: on / off, email / WhatsApp, time, subject, message with `{name}` `{shop}`.
- [ ] Daily birthday job: today's birthdays, permission only, once a year, logged as "System (automatic)".

### Frontend — branch `feature/p4-04-fe-messages`
- [ ] Email & WhatsApp settings screen.
- [ ] Automatic message switches.
- [ ] Birthday wishes card: settings, preview, next 30 days list, "Send today's wishes now".

## P4-05 Offers
**Branch:** `feature/p4-05-offers`
**Note:** built with test email and WhatsApp test mode; real SES / WhatsApp in P6-03
**Screens:** `email-campaign.html`, `whatsapp-campaign.html`, `campaign-report.html` · Guide 46, 47, 48
**Status:** To do

### Backend
- [ ] Choose customers by country and gem bought; permission only.
- [ ] Send email offer through Amazon SES with unsubscribe link.
- [ ] Send WhatsApp offer with an approved template.
- [ ] Results: sent, delivered, opened, clicked, unsubscribed.

### Frontend
- [ ] Send an offer by email.
- [ ] Send an offer on WhatsApp.
- [ ] Offer results.

---

# P5 Money & reports

## P5-01 Expenses
**Branch:** `feature/p5-01-expenses`
**Screens:** `m-expenses/*.html` · Guide 50, 51, 52, 67
**Status:** To do

### Backend
- [ ] Expense: date, branch, expense type, amount, paid to, method, bill photo (required).
- [ ] Above the owner's limit needs approval.
- [ ] Drawer cash expenses counted in Close the day.
- [ ] Monthly bills added automatically.
- [ ] Expenses report by month, branch, type.

### Frontend
- [ ] Add an expense.
- [ ] Expenses list.
- [ ] Monthly bills.
- [ ] Expenses report.

## P5-02 Reports
**Screens:** `hub/reports.html`, `l-reports/*.html` · Guide 63–66, 68, 70, 71
**Status:** To do

### Backend — branch `feature/p5-02-be-reports`
- [ ] Sales report: day, month, branch, gem type, salesperson, guide, payment method.
- [ ] Profit report (owner and accountant).
- [ ] Daily cash report.
- [ ] Stock & value report; slow-moving gems.
- [ ] Commission report by guide and staff.
- [ ] Purchases by supplier.
- [ ] PDF and Excel download for every report.

### Frontend — branch `feature/p5-02-fe-reports`
- [ ] Reports home.
- [ ] Sales report.
- [ ] Profit report.
- [ ] Daily cash.
- [ ] Stock & value.
- [ ] Commission report.
- [ ] Purchases by supplier.

## P5-03 Accountant and month end
**Branch:** `feature/p5-03-accountant`
**Screens:** `n-accountant/*.html` · Guide 72–75
**Status:** To do

### Backend
- [ ] Accountant sees all branches; can add / correct expenses; cannot sell, change prices or delete invoices.
- [ ] Profit & Loss: sales − returns − cost − commissions − card bank fees − expenses.
- [ ] Tax pack: all reports + bill photos in one ZIP for any period.
- [ ] Close the month; later changes need owner approval.

### Frontend
- [ ] Accountant home.
- [ ] Profit & Loss.
- [ ] Tax pack.
- [ ] Close the month.

---


# P6 Testing & go-live

## P6-01 Tests, security and speed
**Branch:** `feature/p6-01-quality`
**Status:** To do

### Backend
- [ ] Integration tests: money, commission, approvals, birthday job.
- [ ] Security check: roles on every API, uploads, login limits, encrypted fields.
- [ ] Database indexes for speed.

### Frontend
- [ ] Automatic browser tests: sale with mixed payment, approval, return, close the day, month lock.
- [ ] New sale opens in under 2 seconds; photos load lazily.
- [ ] Check every screen against the prototype and the demo guide (75 screens) locally.

## P6-02 UAT server and release to UAT
**Branch:** create `uat` from `dev`
**Needs from you:** where the UAT server runs (server or AWS access)
**Status:** To do

### Tasks
- [ ] Set up the UAT server (Docker, HTTPS, own database, sample data only).
- [ ] Create the `uat` branch from `dev` and protect it.
- [ ] Automatic deploy to the UAT server on every merge to `uat`.
- [ ] Release everything at once: `uat` = `dev`, tag `v0.9`.
- [ ] Client tests all 75 demo guide screens with the test plan.
- [ ] Fix bugs on `bugfix/*` branches from `dev`, then pull request `dev` → `uat` again.
- [ ] Client sign-off.

## P6-03 Connect real email, Amazon SES and WhatsApp
**Branch:** `feature/p6-03-real-messaging`
**Needs from you:** shop email (SMTP) details, Amazon SES account, WhatsApp Business account details
**Status:** To do

### Backend
- [ ] Shop SMTP for invoices, quotes, password reset, owner reports.
- [ ] Amazon SES for offers: domain, SPF / DKIM, delivery and open events.
- [ ] WhatsApp Cloud API: approved templates (invoice, thank-you, birthday wish, offer), delivery webhooks.
- [ ] Switch off test mode for UAT / production settings.

### Frontend
- [ ] Settings screen shows real connection status (Verify connection, Send test email).

### Tasks
- [ ] Release to UAT (`dev` → `uat`) and client tests guide screens 13, 28, 45–48.

## P6-04 Production server and backups
**Branch:** `feature/p6-04-production`
**Needs from you:** AWS account for production
**Status:** To do

### Tasks
- [ ] AWS EC2 t4g.small (Mumbai), 40 GB disk, Docker, Nginx + HTTPS.
- [ ] Deploy only from a version tag on `main`, with approval.
- [ ] Daily database backup + nightly copy with photos to Amazon S3.
- [ ] Test a full restore.
- [ ] Uptime and disk alerts to NUVI.

## P6-05 Go-live
**Branch:** pull request `uat` → `main`, tag `v1.0.0`
**Status:** To do

### Tasks
- [ ] Final client sign-off on UAT.
- [ ] Pull request `uat` → `main`, tag `v1.0.0`, deploy to production.
- [ ] Owner sets up shop, branches, staff, rates.
- [ ] Client enters real gems, suppliers, guides.
- [ ] First real sale checked end to end.

## P6-06 Training and handover
**Status:** To do

### Tasks
- [ ] Train staff with the demo guide (each role).
- [ ] Set up the owner's phone (app + push).
- [ ] Handover notes (logins, backups, support).
- [ ] 2 weeks support after go-live.
