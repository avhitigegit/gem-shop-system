"use client";

/* Gems & stock home – prototype hub/stock.html: gems back from customers / not ready to sell, "N in stock" on
   All gems, and the newest gems. Suppliers / purchases counts (P2-03),
   transfers on the way and corrections waiting (P2-04). */
import Link from "next/link";
import { SectionHome, type Attention } from "@/components/shell/section-home";
import { StatusPill } from "@/components/ui/badge";
import { Card, CardHeader } from "@/components/ui/card";
import { GemSvg } from "@/components/ui/gem-picture";
import { api } from "@/lib/api/client";
import { carat, inCurrency } from "@/lib/format";
import { ACCOUNTANT, OWNER, STAFF } from "@/lib/roles";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function StockHome() {
  const { role, branch, currency, rates } = useSession();
  const { data: s } = useLoad(() => api.GET("/api/gems/summary"), [branch]);
  const buyer = role === OWNER || role === ACCOUNTANT;
  const stockPerson = role === OWNER || role === STAFF;
  const { data: w } = useLoad(
    async () => (stockPerson ? api.GET("/api/stock/waiting") : { data: null }),
    [stockPerson, branch],
  );
  const { data: p } = useLoad(async () => (buyer ? api.GET("/api/purchases/summary") : { data: null }), [buyer]);
  const attention: Attention[] = [];
  const counts: Record<string, string> = {};
  if (s) {
    if (s.returned && role !== ACCOUNTANT)
      attention.push({
        tone: "info",
        icon: "↩️",
        text: (
          <>
            <b>{s.returned} gem(s) came back from customers</b> and can be sold again – check the tag is still on.
          </>
        ),
        href: "/stock/gems?status=RETURNED",
        button: "See gems",
      });
    if (s.notReady && role !== ACCOUNTANT)
      attention.push({
        tone: "info",
        icon: "🛠",
        text: (
          <>
            <b>{s.notReady} gem(s) not ready to sell yet.</b> When one is ready, open it and press Mark ready to sell.
          </>
        ),
        href: "/stock/gems?status=NOT_READY",
        button: "See gems",
      });
    counts["/stock/gems"] = `${s.inStock} in stock`;
  }
  if (w?.transfersOnTheWay)
    attention.push({
      tone: "warning",
      icon: "🚚",
      text: (
        <>
          <b>{w.transfersOnTheWay} transfer(s) on the way.</b> Tick or scan each gem when it arrives and confirm.
        </>
      ),
      href: "/stock/transfers",
      button: "Open transfers",
    });
  if (w?.correctionsWaiting)
    attention.push({
      tone: "info",
      icon: "✎",
      text: (
        <>
          <b>{w.correctionsWaiting} stock correction(s) waiting</b> for the owner&apos;s approval.
        </>
      ),
      href: role === OWNER ? "/approvals" : "/stock/corrections",
      button: role === OWNER ? "Approvals" : "See corrections",
    });
  if (p) {
    counts["/stock/suppliers"] = `${p.suppliers} suppliers`;
    counts["/stock/purchases"] = `${p.purchasesThisMonth} this month`;
  }
  const side =
    s && role !== ACCOUNTANT && s.newest.length ? (
      <Card>
        <CardHeader title="Newest gems" level={3} />
        <ul className="list hub-recent">
          {s.newest.map((g) => (
            <li key={g.code}>
              <Link href={`/stock/gems/${g.code}`}>
                <GemSvg gem={g.gemType} type={g.productType} size={34} />
                <span style={{ flex: 1 }}>
                  <b>{g.name}</b>
                  <br />
                  <small className="muted mono">
                    {g.code} · {carat(g.carat)}
                  </small>
                </span>
                <span className="right">
                  <b>{inCurrency(g.price, currency, rates)}</b>
                  <br />
                  <StatusPill status={g.status} />
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </Card>
    ) : undefined;
  return <SectionHome sectionKey="stock" attention={attention} counts={counts} side={side} />;
}
