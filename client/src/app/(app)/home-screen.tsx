"use client";

/* Home – the prototype landing page (dashboard/index.html): greeting, branch and date, big tiles per role.
   Today's numbers, the licence warning, the cash-day step bar and "Sold today" come with
   P4-01 (Home and owner control), when their data exists. The Accountant home is P5-03. */
import { HomeTile } from "@/components/ui/card";
import { PageHead } from "@/components/ui/steps";
import { date, hourInShop, todayInShop } from "@/lib/format";
import { menuSections, sectionHref } from "@/lib/nav";
import type { Role } from "@/lib/roles";
import { useSession } from "@/lib/session";

type Tile = { href: string; icon: string; title: string; sub: string; primary?: boolean };

const TILES: Partial<Record<Role, Tile[]>> = {
  OWNER: [
    { href: "/sell/new", icon: "💎", title: "New sale", sub: "Scan a tag and sell", primary: true },
    { href: "/approvals", icon: "✅", title: "Approvals", sub: "Requests waiting for you" },
    { href: "/stock/gems/new", icon: "➕", title: "Add a gem", sub: "New stock with photo" },
    { href: "/stock/gems", icon: "🔍", title: "Find a gem", sub: "Search or scan a tag" },
    { href: "/money/expenses/new", icon: "🧾", title: "Add expense", sub: "With bill photo" },
    { href: "/people/commissions/pay", icon: "🚐", title: "Pay commissions", sub: "Guides & drivers" },
    { href: "/reports", icon: "📊", title: "Reports", sub: "Sales, profit, month end" },
  ],
  SALESPERSON: [
    { href: "/sell/new", icon: "💎", title: "New sale", sub: "Scan a tag and sell", primary: true },
    { href: "/stock/gems", icon: "🔍", title: "Find a gem", sub: "Search or scan a tag" },
    { href: "/sell/invoices", icon: "🧾", title: "Invoices", sub: "Print or send again" },
    { href: "/sell/returns/new", icon: "↩️", title: "Gem brought back", sub: "Refund or exchange" },
    { href: "/sell/reserved", icon: "📌", title: "Reserved & quotes", sub: "Kept for customers" },
    { href: "/people/customers", icon: "👥", title: "Customers", sub: "Contacts and past purchases" },
  ],
  STAFF: [
    { href: "/stock/gems", icon: "🔍", title: "Find a gem", sub: "Search or scan a tag", primary: true },
    { href: "/stock/check", icon: "📋", title: "Stock check", sub: "Scan every tag" },
    { href: "/stock/tags", icon: "🏷", title: "Print tags", sub: "QR / barcode labels" },
    { href: "/stock/transfers", icon: "🚚", title: "Transfers", sub: "Send or receive" },
    { href: "/stock/corrections", icon: "✎", title: "Fix stock", sub: "Owner will approve" },
  ],
};

export function HomeScreen() {
  const { user, role, branch, branchName } = useSession();
  const hour = hourInShop();
  // Accountant home comes in P5-03; until then the accountant gets a tile per menu section.
  const tiles =
    TILES[role] ??
    menuSections(role)
      .filter((s) => !s.home)
      .map((s, i) => ({
        href: sectionHref(s, role)!,
        icon: s.icon,
        title: s.title,
        sub: s.intro ?? "",
        primary: i === 0,
      }));
  return (
    <>
      <PageHead
        title={`Good ${hour < 12 ? "morning" : hour < 17 ? "afternoon" : "evening"}, ${user.name.split(" ")[0]}`}
        sub={`${branchName(branch)} · ${date(todayInShop())}`}
      />
      <div className="home-tiles mb">
        {tiles.map((t) => (
          <HomeTile key={t.href} href={t.href} icon={t.icon} title={t.title} sub={t.sub} primary={t.primary} />
        ))}
      </div>
    </>
  );
}
