/* The menu – defined ONCE here, the same as the prototype (prototype/assets/js/layout.js → SECTIONS).
   8 big menu items. Each opens a section home with big "do this" tiles and "look up" cards.
   Each task opens as its own page with a back bar ("💎 Sell › Invoices"). No tab strips.
   Work done in order (cash day, new stock, month end) shows a step bar (FLOWS). */
import { ACCOUNTANT, ALL_ROLES, OWNER, type Role, SALESPERSON, STAFF } from "@/lib/roles";

const O = OWNER,
  SP = SALESPERSON,
  ST = STAFF,
  AC = ACCOUNTANT;

export type NavItem = {
  href: string;
  label: string;
  icon: string;
  desc: string;
  roles: Role[];
  /** The prototype screen this page copies. */
  proto: string;
  /** false = not listed in the side menu (still a tile on the section home). */
  side?: boolean;
};
export type NavGroup = {
  name: string;
  /** tiles = big "do this" buttons · cards = "look up" with a count · links = less used. */
  show: "tiles" | "cards" | "links";
  /** Listed in the side menu under the section. */
  side?: boolean;
  items: NavItem[];
};
/** Detail / print pages: not in the menu, the back link goes to the parent page. ":id" = any value. */
export type HiddenPage = { path: string; parent: string | null; label: string; proto: string };
export type Section = {
  key: string;
  icon: string;
  title: string;
  roles: Role[];
  home?: boolean;
  /** Section home page. Sections without one open their first page. */
  hub?: string;
  intro?: string;
  /** Shows the "waiting for approval" count in the menu. */
  count?: boolean;
  groups: NavGroup[];
  hidden: HiddenPage[];
};

const it = (
  href: string,
  label: string,
  icon: string,
  desc: string,
  roles: Role[],
  proto: string,
  side?: boolean,
): NavItem => ({
  href,
  label,
  icon,
  desc,
  roles,
  proto,
  ...(side === false ? { side } : {}),
});

export const SECTIONS: Section[] = [
  {
    key: "home",
    icon: "🏠",
    title: "Home",
    roles: ALL_ROLES,
    home: true,
    groups: [],
    hidden: [
      { path: "/", parent: null, label: "Home", proto: "dashboard/index.html" },
      { path: "/design", parent: null, label: "Design kit", proto: "assets/css/app.css" },
      { path: "/not-allowed", parent: null, label: "Not allowed", proto: "assets/js/app.js accessDenied" },
    ],
  },
  {
    key: "sell",
    icon: "💎",
    title: "Sell",
    roles: [O, SP, AC],
    hub: "/sell",
    intro: "Sell gems, keep a gem for a customer or give a quote (both from New sale), and handle gems that come back.",
    groups: [
      {
        name: "What do you want to do?",
        show: "tiles",
        side: true,
        items: [
          it(
            "/sell/new",
            "New sale",
            "💎",
            "Scan the tag, agree the price, take payment",
            [O, SP],
            "f-pos/pos.html",
            false,
          ),
          it(
            "/sell/returns/new",
            "Customer brings a gem back",
            "↩️",
            "Refund or exchange – the owner approves",
            [O, SP],
            "e-returns/new-return.html",
          ),
        ],
      },
      {
        name: "Look up",
        show: "cards",
        side: true,
        items: [
          it(
            "/sell/invoices",
            "Invoices",
            "🧾",
            "Print or send again, cancel",
            [O, SP, AC],
            "g-payments/invoices.html",
          ),
          it(
            "/sell/reserved",
            "Reserved & quotes",
            "📌",
            "Gems kept for customers",
            [O, SP],
            "f-pos/reservations.html",
          ),
          it("/sell/returns", "Returns", "↩️", "All refunds and exchanges", [O, SP], "e-returns/returns.html"),
          it("/sell/credit-notes", "Credit notes", "📄", "Money given back", [O, SP, AC], "e-returns/credit-note.html"),
        ],
      },
    ],
    hidden: [
      { path: "/sell/invoices/:id", parent: "/sell/invoices", label: "Invoice", proto: "g-payments/invoice.html" },
      {
        path: "/sell/reserved/:id/print",
        parent: "/sell/reserved",
        label: "Quotation",
        proto: "f-pos/quotation-print.html",
      },
      {
        path: "/sell/credit-notes/:id",
        parent: "/sell/credit-notes",
        label: "Credit note",
        proto: "e-returns/credit-note.html",
      },
    ],
  },
  {
    key: "stock",
    icon: "📦",
    title: "Gems & stock",
    roles: [O, SP, ST, AC],
    hub: "/stock",
    intro: "Buy gems, add them with a photo, print tags and keep the stock right.",
    groups: [
      {
        name: "What do you want to do?",
        show: "tiles",
        side: true,
        items: [
          it(
            "/stock/purchases/new",
            "Buy gems from a supplier",
            "🛒",
            "Cash purchase with bill photo",
            [O],
            "b-purchasing/new-purchase.html",
          ),
          it(
            "/stock/gems/new",
            "Add a gem (not bought)",
            "➕",
            "Only for gems not from a supplier purchase",
            [O],
            "c-products/product-form.html",
          ),
          it("/stock/tags", "Print tags", "🏷", "QR / barcode labels", [O, ST], "c-products/tag-print.html"),
          it("/stock/check", "Stock check", "📋", "Scan every tag on the shelf", [O, ST], "d-stock/stock-check.html"),
          it(
            "/stock/shop-certificate",
            "Print shop certificate",
            "📜",
            "For gems without a lab certificate",
            [O, SP],
            "c-products/shop-certificate.html",
          ),
        ],
      },
      {
        name: "Look up",
        show: "cards",
        side: true,
        items: [
          it(
            "/stock/gems",
            "All gems",
            "🔍",
            "Search or scan a tag, every branch",
            [O, SP, ST],
            "c-products/products.html",
          ),
          it("/stock/suppliers", "Suppliers", "🤝", "Who we buy from", [O, AC], "b-purchasing/suppliers.html"),
          it("/stock/purchases", "Purchases", "🧾", "Everything bought", [O, AC], "b-purchasing/purchase-history.html"),
        ],
      },
      {
        name: "Other tasks",
        show: "cards",
        items: [
          it(
            "/stock/transfers",
            "Send / receive between branches",
            "🚚",
            "Move gems to the other branch",
            [O, ST],
            "d-stock/transfers.html",
          ),
          it(
            "/stock/corrections",
            "Fix a stock mistake",
            "✎",
            "Owner approves",
            [O, ST],
            "d-stock/stock-correction.html",
          ),
          it(
            "/stock/supplier-returns",
            "Return a gem to the supplier",
            "↩️",
            "Wrong or faulty gem",
            [O],
            "b-purchasing/supplier-returns.html",
          ),
        ],
      },
    ],
    hidden: [
      { path: "/stock/gems/:id", parent: "/stock/gems", label: "Gem details", proto: "c-products/product-detail.html" },
      { path: "/stock/gems/:id/edit", parent: "/stock/gems", label: "Edit gem", proto: "c-products/product-form.html" },
      {
        path: "/stock/gems/:id/history",
        parent: "/stock/gems",
        label: "Gem history",
        proto: "d-stock/product-history.html",
      },
      {
        path: "/stock/suppliers/:id",
        parent: "/stock/suppliers",
        label: "Supplier",
        proto: "b-purchasing/supplier-detail.html",
      },
    ],
  },
  {
    key: "people",
    icon: "👥",
    title: "Customers & guides",
    roles: [O, SP, AC],
    hub: "/people",
    intro: "Customers, the guides and drivers who bring them, and their commission.",
    groups: [
      {
        name: "What do you want to do?",
        show: "tiles",
        side: true,
        items: [
          it(
            "/people/commissions/pay",
            "Pay guides & drivers",
            "🚐",
            "Pay what they earned",
            [O, AC],
            "h-commissions/commissions-payable.html",
          ),
          it(
            "/people/offers/email",
            "Send an offer by email",
            "✉️",
            "Only to customers who agreed",
            [O],
            "j-customers-promotions/email-campaign.html",
          ),
          it(
            "/people/offers/whatsapp",
            "Send an offer on WhatsApp",
            "💬",
            "Only to customers who agreed",
            [O],
            "j-customers-promotions/whatsapp-campaign.html",
          ),
        ],
      },
      {
        name: "Look up",
        show: "cards",
        side: true,
        items: [
          it(
            "/people/customers",
            "Customers",
            "👥",
            "Contacts and past purchases",
            [O, SP],
            "j-customers-promotions/customers.html",
          ),
          it(
            "/people/guides",
            "Guides & drivers",
            "🚐",
            "Who brings customers",
            [O, SP, AC],
            "h-commissions/guides.html",
          ),
          it(
            "/people/commissions",
            "Commission per sale",
            "🧮",
            "How each amount was worked out",
            [O, AC],
            "h-commissions/commission-breakdown.html",
          ),
          it(
            "/people/offers",
            "Offer results",
            "📈",
            "Opened, clicked, sold",
            [O],
            "j-customers-promotions/campaign-report.html",
          ),
        ],
      },
      {
        name: "Other tasks",
        show: "cards",
        items: [
          it(
            "/people/message-settings",
            "Email & WhatsApp settings",
            "⚙️",
            "Turn messages on or off",
            [O],
            "j-customers-promotions/message-settings.html",
          ),
        ],
      },
    ],
    hidden: [
      {
        path: "/people/customers/:id",
        parent: "/people/customers",
        label: "Customer",
        proto: "j-customers-promotions/customer-profile.html",
      },
      {
        path: "/people/guides/:id",
        parent: "/people/guides",
        label: "Guide / driver",
        proto: "h-commissions/guide-profile.html",
      },
      {
        path: "/people/commissions/slip/:id",
        parent: "/people/commissions/pay",
        label: "Commission slip",
        proto: "h-commissions/commission-slip.html",
      },
    ],
  },
  {
    key: "money",
    icon: "💰",
    title: "Cash & expenses",
    roles: [O, SP, AC],
    hub: "/money",
    intro: "The cash drawer from morning to evening, and the shop’s bills.",
    groups: [
      {
        name: "What do you want to do?",
        show: "tiles",
        side: true,
        items: [
          it(
            "/money/start-day",
            "Start the day",
            "☀️",
            "Count the cash in the drawer",
            [O, SP],
            "i-daily-closing/opening-cash.html",
          ),
          it(
            "/money/pay-out",
            "Pay out cash from the drawer",
            "💵",
            "Small expenses paid in cash",
            [O, SP],
            "i-daily-closing/drawer-expenses.html",
          ),
          it(
            "/money/close-day",
            "Close the day",
            "🌙",
            "Count the cash, send to owner",
            [O, SP],
            "i-daily-closing/day-end.html",
          ),
          it(
            "/money/expenses/new",
            "Add an expense",
            "🧾",
            "Bank or card bills, with photo",
            [O, AC],
            "m-expenses/add-expense.html",
          ),
        ],
      },
      {
        name: "Look up",
        show: "cards",
        side: true,
        items: [
          it("/money/expenses", "Expenses", "📒", "All shop expenses", [O, AC], "m-expenses/expenses.html"),
          it(
            "/money/monthly-bills",
            "Monthly bills",
            "🔁",
            "Rent, phone, salaries",
            [O, AC],
            "m-expenses/recurring.html",
          ),
        ],
      },
    ],
    hidden: [
      { path: "/money/expenses/:id", parent: "/money/expenses", label: "Expense", proto: "m-expenses/expenses.html" },
    ],
  },
  {
    key: "reports",
    icon: "📊",
    title: "Reports",
    roles: [O, AC],
    hub: "/reports",
    intro: "See how the shop is doing, and the monthly papers for the accountant.",
    groups: [
      {
        name: "How is the shop doing?",
        show: "cards",
        side: true,
        items: [
          it("/reports/sales", "Sales", "💎", "By day, gem, staff, country", [O, AC], "l-reports/sales.html"),
          it("/reports/profit", "Profit", "📈", "Sale price minus cost", [O, AC], "l-reports/profit.html"),
          it("/reports/daily-cash", "Daily cash", "🌙", "Every day’s closing", [O, AC], "l-reports/daily-closing.html"),
          it("/reports/expenses", "Expenses", "🧾", "By type and month", [O, AC], "m-expenses/expense-report.html"),
        ],
      },
      {
        name: "Month end (with the accountant)",
        show: "cards",
        side: true,
        items: [
          it(
            "/reports/profit-loss",
            "Profit & Loss",
            "📑",
            "Income minus all costs",
            [O, AC],
            "n-accountant/profit-loss.html",
          ),
          it(
            "/reports/tax-pack",
            "Tax pack",
            "📦",
            "Download everything for tax",
            [O, AC],
            "n-accountant/tax-pack.html",
          ),
          it(
            "/reports/close-month",
            "Close the month",
            "🔒",
            "Lock it so nothing changes",
            [O, AC],
            "n-accountant/month-closing.html",
          ),
        ],
      },
      {
        name: "More reports",
        show: "cards",
        items: [
          it("/reports/stock", "Stock & value", "📦", "Value, photos, slow-moving gems", [O], "l-reports/stock.html"),
          it(
            "/reports/commissions",
            "Commissions",
            "🚐",
            "By guide and by staff",
            [O, AC],
            "l-reports/commissions.html",
          ),
          it(
            "/reports/purchases",
            "Purchases by supplier",
            "🛒",
            "Bought and sold per supplier",
            [O, AC],
            "l-reports/purchases.html",
          ),
        ],
      },
    ],
    hidden: [
      {
        path: "/reports/stock/value",
        parent: "/reports/stock",
        label: "Value by gem type",
        proto: "d-stock/stock-value.html",
      },
    ],
  },
  {
    key: "approvals",
    icon: "✅",
    title: "Approvals",
    roles: ALL_ROLES,
    count: true,
    groups: [
      {
        name: "Approvals",
        show: "cards",
        side: true,
        items: [
          it("/approvals", "Waiting for approval", "✅", "", ALL_ROLES, "k-owner-control/approvals.html"),
          it("/approvals/alerts", "Alerts", "🔔", "", [O], "k-owner-control/alerts.html"),
          it("/approvals/phone", "Phone view", "📱", "", [O], "k-owner-control/owner-mobile.html"),
          it("/approvals/activity", "Activity log", "🕘", "", [O], "k-owner-control/activity-log.html"),
        ],
      },
    ],
    hidden: [],
  },
  {
    key: "settings",
    icon: "⚙️",
    title: "Settings",
    roles: [O],
    hub: "/settings",
    intro: "Set up the shop once. Change these only when something changes.",
    groups: [
      {
        name: "Shop set-up",
        show: "cards",
        side: true,
        items: [
          it("/settings/shop", "Shop details", "🏪", "Name, address, gem licence", [O], "a-setup/shop-vat.html"),
          it("/settings/users", "Staff logins", "🔑", "Who can log in and what they see", [O], "a-setup/users.html"),
          it("/settings/rates", "Exchange rates", "💱", "USD and EUR rate of the day", [O], "a-setup/currencies.html"),
          it(
            "/settings/commission",
            "Commission",
            "🚐",
            "Guide % and staff levels",
            [O],
            "a-setup/commission-rules.html",
          ),
          it(
            "/settings/branches",
            "Branches",
            "🏬",
            "Ella now, Branch 2 from January 2027",
            [O],
            "a-setup/branches.html",
          ),
          it(
            "/settings/lists",
            "Drop-down lists",
            "📃",
            "Gem types, colours, labs, expense types…",
            [O],
            "a-setup/lists.html",
          ),
        ],
      },
    ],
    hidden: [],
  },
];

/* ---------- step bars: work that happens in order ---------- */
export type FlowStep = { href: string; label: string; note: string; optional?: boolean; bar?: boolean };
export type Flow = { title: string; steps: FlowStep[] };
export const FLOWS: Record<string, Flow> = {
  day: {
    title: "Today’s cash",
    steps: [
      { href: "/money/start-day", label: "Start the day", note: "Count the drawer" },
      { href: "/money/pay-out", label: "Cash paid out", note: "Only if needed", optional: true },
      { href: "/money/close-day", label: "Close the day", note: "At closing time" },
    ],
  },
  newstock: {
    title: "New gems coming in",
    steps: [
      { href: "/stock/purchases/new", label: "Buy from supplier", note: "Gems go into stock" },
      { href: "/stock/tags", label: "Print the tags", note: "Stick on the box" },
      { href: "/stock/gems", label: "Ready to sell", note: "Shows in All gems" },
    ],
  },
  monthend: {
    title: "Month end",
    steps: [
      { href: "/money/expenses", label: "Check expenses", note: "Bills & photos", bar: false },
      { href: "/reports/profit-loss", label: "Profit & Loss", note: "The month result" },
      { href: "/reports/tax-pack", label: "Tax pack", note: "Download" },
      { href: "/reports/close-month", label: "Close the month", note: "Lock it so nothing changes" },
    ],
  },
};
/** Which step bar shows on a page (a list page like All gems shows none). */
export function flowOf(path: string): string | null {
  if (path === "/stock/gems") return null;
  for (const [key, f] of Object.entries(FLOWS)) if (f.steps.some((s) => s.href === path && s.bar !== false)) return key;
  return null;
}

/* ---------- helpers ---------- */
export const allItems = (s: Section) => s.groups.flatMap((g) => g.items);
export const visibleItems = (items: NavItem[], role: Role) => items.filter((i) => i.roles.includes(role));

export function firstPage(s: Section, role: Role): string | null {
  for (const g of s.groups) {
    const v = visibleItems(g.items, role);
    if (v.length) return v[0].href;
  }
  return null;
}

/** Where a big menu item goes: Home, the section home, or (no section home) its first page. */
export function sectionHref(s: Section, role: Role): string | null {
  if (s.home) return "/";
  if (s.hub && s.groups.some((g) => visibleItems(g.items, role).length)) return s.hub;
  return firstPage(s, role);
}

export const menuSections = (role: Role) => SECTIONS.filter((s) => s.roles.includes(role) && sectionHref(s, role));

const matches = (pattern: string, path: string) =>
  new RegExp("^" + pattern.replace(/[.*+?^${}()|[\]\\]/g, "\\$&").replace(/:id/g, "[^/]+") + "$").test(path);

export type PageInfo = {
  section: Section;
  item?: NavItem;
  hidden?: HiddenPage;
  onHub: boolean;
  /** Who may open the page; null = everyone who can open the section. */
  roles: Role[] | null;
};

/** Finds the menu entry for a path: a menu page, a detail page, or a section home. */
export function pageInfo(path: string): PageInfo {
  const clean = path.replace(/\/+$/, "") || "/";
  for (const s of SECTIONS) {
    const item = allItems(s).find((i) => i.href === clean);
    if (item) return { section: s, item, onHub: false, roles: item.roles };
  }
  for (const s of SECTIONS) {
    if (s.hub === clean) return { section: s, onHub: true, roles: s.roles };
  }
  for (const s of SECTIONS) {
    const hidden = s.hidden.find((h) => matches(h.path, clean));
    if (hidden) {
      const parent = hidden.parent ? allItems(s).find((i) => i.href === hidden.parent) : undefined;
      return { section: s, hidden, onHub: false, roles: parent ? parent.roles : s.roles };
    }
  }
  const bySegment =
    SECTIONS.find((s) => s.hub && clean.startsWith(s.hub + "/")) ??
    SECTIONS.find((s) => s.key === "approvals" && clean.startsWith("/approvals/"));
  return { section: bySegment ?? SECTIONS[0], onHub: false, roles: bySegment ? bySegment.roles : null };
}

/** Every page in the menu – used for the "built in a later step" page and the tests. */
export const ALL_PAGES: NavItem[] = SECTIONS.flatMap(allItems).filter(
  (i, n, a) => a.findIndex((x) => x.href === i.href) === n,
);
