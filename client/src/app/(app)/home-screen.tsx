"use client";

/* Home – prototype dashboard/index.html (Guide 4): greeting, branch and date, the licence warning (owner), the cash-day
   step bar (owner, salesperson), three numbers per role, big tiles per role and "Sold today". The Accountant home is P5-03;
   until then the accountant gets a tile per menu section. */
import Link from "next/link";
import { Card, CardContent, CardHeader, HomeTile } from "@/components/ui/card";
import { Alert } from "@/components/ui/form";
import { GemPicture } from "@/components/ui/gem-picture";
import { FlowBar, PageHead } from "@/components/ui/steps";
import { api } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { date, hourInShop, inCurrency, lkr, num, time, todayInShop } from "@/lib/format";
import { menuSections, sectionHref } from "@/lib/nav";
import { ACCOUNTANT, OWNER, SALESPERSON, STAFF, type Role } from "@/lib/roles";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

type HomeFigures = components["schemas"]["HomeFigures"];
type Tile = { href: string; icon: string; title: string; sub: string; primary?: boolean; badge?: number };

function tiles(role: Role, h: HomeFigures | undefined): Tile[] | undefined {
  const inStock = h ? `${num(h.gemsInStock, 0)} in stock` : "Search or scan a tag";
  const waiting = h?.approvalsWaiting ?? 0;
  if (role === OWNER)
    return [
      { href: "/sell/new", icon: "💎", title: "New sale", sub: "Scan a tag and sell", primary: true },
      {
        href: "/approvals",
        icon: "✅",
        title: "Approvals",
        sub: waiting ? `${waiting} waiting for you` : "Nothing waiting",
        badge: waiting,
      },
      { href: "/stock/gems/new", icon: "➕", title: "Add a gem", sub: "New stock with photo" },
      { href: "/stock/gems", icon: "🔍", title: "Find a gem", sub: inStock },
      { href: "/money/expenses/new", icon: "🧾", title: "Add expense", sub: "With bill photo" },
      { href: "/people/commissions/pay", icon: "🚐", title: "Pay commissions", sub: "Guides & drivers" },
      { href: "/reports", icon: "📊", title: "Reports", sub: "Sales, profit, month end" },
    ];
  if (role === SALESPERSON)
    return [
      { href: "/sell/new", icon: "💎", title: "New sale", sub: "Scan a tag and sell", primary: true },
      { href: "/stock/gems", icon: "🔍", title: "Find a gem", sub: inStock },
      { href: "/sell/invoices", icon: "🧾", title: "Invoices", sub: "Print or send again" },
      { href: "/sell/returns/new", icon: "↩️", title: "Gem brought back", sub: "Refund or exchange" },
      { href: "/sell/reserved", icon: "📌", title: "Reserved & quotes", sub: "Kept for customers" },
      {
        href: "/people/customers",
        icon: "👥",
        title: "Customers",
        sub: h ? `${num(h.customers, 0)} customers` : "Contacts and past purchases",
      },
    ];
  if (role === STAFF)
    return [
      { href: "/stock/gems", icon: "🔍", title: "Find a gem", sub: inStock, primary: true },
      { href: "/stock/check", icon: "📋", title: "Stock check", sub: "Scan every tag" },
      { href: "/stock/tags", icon: "🏷", title: "Print tags", sub: "QR / barcode labels" },
      {
        href: "/stock/transfers",
        icon: "🚚",
        title: "Transfers",
        sub: h?.onTheWay ? `${h.onTheWay} to receive` : "Send or receive",
        badge: h?.onTheWay ?? 0,
      },
      { href: "/stock/corrections", icon: "✎", title: "Fix stock", sub: "Owner will approve" },
    ];
  return undefined;
}

export function HomeScreen() {
  const { user, role, branch, branchName, currency, rates } = useSession();
  const { data: h } = useLoad(() => api.GET("/api/home"), [branch]);
  const hour = hourInShop();
  const cur = (v: number | string) => inCurrency(v, currency, rates);
  const money = role === OWNER || role === SALESPERSON;
  const list =
    tiles(role, h) ??
    menuSections(role)
      .filter((s) => !s.home)
      .map((s, i) => ({
        href: sectionHref(s, role)!,
        icon: s.icon,
        title: s.title,
        sub: s.intro ?? "",
        primary: i === 0,
        badge: 0,
      }));

  const nums: [string, string, string][] = !h
    ? []
    : role === STAFF
      ? [
          ["Gems in stock", num(h.gemsInStock, 0), branchName(branch)],
          ["On the way", num(h.onTheWay, 0), "Transfers to receive"],
          ["Waiting for owner", num(h.approvalsWaiting, 0), "Requests"],
        ]
      : money && h.cash
        ? [
            ["Today's sales", cur(h.salesTotal ?? 0), `${h.bills} sale${h.bills === 1 ? "" : "s"}`],
            [
              "Cash in drawer",
              lkr(h.cash.lkr),
              Number(h.cash.usd) || Number(h.cash.eur)
                ? `plus USD ${num(h.cash.usd)} · EUR ${num(h.cash.eur)}`
                : "No foreign cash",
            ],
            role === OWNER
              ? ["Waiting for you", num(h.approvalsWaiting, 0), h.approvalsWaiting ? "Tap Approvals" : "All done"]
              : ["Card payments", cur(h.cash.card), "Today"],
          ]
        : [];

  const dayStatus: Record<string, { done?: boolean; note?: string }> = {};
  if (h?.dayStarted) dayStatus["/money/start-day"] = { done: true };
  if (h?.dayClosed) dayStatus["/money/close-day"] = { done: true };

  return (
    <>
      <PageHead
        title={`Good ${hour < 12 ? "morning" : hour < 17 ? "afternoon" : "evening"}, ${user.name.split(" ")[0]}`}
        sub={`${branchName(branch)} · ${date(todayInShop())}`}
      />
      {role === OWNER && h?.licenceDays != null ? (
        <Alert tone="warning" icon="📜" className="mb">
          <b>
            Gem dealer licence{" "}
            {h.licenceDays < 0 ? `expired ${-h.licenceDays} days ago` : `expires in ${h.licenceDays} days`}
          </b>{" "}
          ({date(h.licenceExpiry)}). <Link href="/settings/shop">Update →</Link>
        </Alert>
      ) : null}
      {money ? <FlowBar flow="day" role={role} status={dayStatus} compact /> : null}
      {nums.length ? (
        <div className="g3 mb grid" id="homeNums">
          {nums.map(([label, value, sub]) => (
            <Card key={label}>
              <CardContent>
                <div className="muted" style={{ fontSize: 13 }}>
                  {label}
                </div>
                <div className="big-num">{value}</div>
                <small className="muted">{sub}</small>
              </CardContent>
            </Card>
          ))}
        </div>
      ) : null}
      <div className="home-tiles mb">
        {list.map((t) => (
          <HomeTile
            key={t.href}
            href={t.href}
            icon={t.icon}
            title={t.title}
            sub={t.sub}
            primary={t.primary}
            badge={t.badge || undefined}
          />
        ))}
      </div>
      {money && role !== ACCOUNTANT ? (
        <Card>
          <CardHeader
            title="Sold today"
            actions={
              <Link className="btn btn-sm btn-ghost" href="/sell/invoices">
                All invoices →
              </Link>
            }
          />
          <CardContent>
            {h?.soldToday.length ? (
              <div
                className="prod-grid"
                id="soldToday"
                style={{ gridTemplateColumns: "repeat(auto-fill,minmax(150px,1fr))" }}
              >
                {h.soldToday.map((s) => (
                  <Link
                    key={`${s.invoice}-${s.gem}`}
                    className="card prod-card"
                    href={`/sell/invoices/${s.invoice}`}
                    style={{ textDecoration: "none", color: "inherit" }}
                  >
                    <div className="pic" style={{ height: 100 }}>
                      <GemPicture gem={s.gemType} type={null} photoUrl={s.photo} size={74} alt={s.name} />
                    </div>
                    <div className="body">
                      <span className="name" style={{ fontSize: 13 }}>
                        {s.name}
                      </span>
                      <span className="muted" style={{ fontSize: 11 }}>
                        {time(s.soldAt)}
                        {s.country ? ` · ${s.country}` : ""}
                      </span>
                      <span className="price">{cur(s.agreed)}</span>
                    </div>
                  </Link>
                ))}
              </div>
            ) : (
              <div className="empty">{h ? "No sales yet today." : "Loading…"}</div>
            )}
          </CardContent>
        </Card>
      ) : null}
    </>
  );
}
