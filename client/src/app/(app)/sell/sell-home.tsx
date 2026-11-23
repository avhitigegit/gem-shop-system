"use client";

/* Sell home – prototype hub/sell.html: held sales (approved → finish; waiting → owner approves), sales today on
   Invoices, and the latest invoices. Reserved & quotes shows the active count; Returns the ones waiting for the owner. */
import Link from "next/link";
import { SectionHome, type Attention } from "@/components/shell/section-home";
import { Badge } from "@/components/ui/badge";
import { Card, CardHeader } from "@/components/ui/card";
import { GemSvg } from "@/components/ui/gem-picture";
import { api } from "@/lib/api/client";
import { ago, dateTime, inCurrency } from "@/lib/format";
import { OWNER } from "@/lib/roles";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function SellHome() {
  const { role, branch, currency, rates } = useSession();
  const owner = role === OWNER;
  const { data: s } = useLoad(() => api.GET("/api/sales/summary"), [branch]);
  const attention: Attention[] = [];
  const counts: Record<string, string> = {};
  if (s) {
    for (const h of s.held) {
      const gem = h.items[0]?.name ?? h.number;
      if (h.status === "APPROVED")
        attention.push({
          tone: "success",
          icon: "✅",
          text: (
            <>
              <b>Owner approved the price</b> for {gem} – finish the sale and take payment.
            </>
          ),
          href: role === "ACCOUNTANT" ? undefined : `/sell/new?held=${h.number}`,
          button: "Finish sale",
        });
      else
        attention.push({
          tone: "warning",
          icon: "⏳",
          text: (
            <>
              <b>{owner ? "Price needs your approval" : "Waiting for the owner"}</b> – below minimum for {gem} (
              {ago(h.heldAt)}).
            </>
          ),
          href: owner ? "/approvals" : role === "ACCOUNTANT" ? undefined : `/sell/new?held=${h.number}`,
          button: owner ? "Approve" : "Open",
        });
    }
    for (const r of s.returnsWaiting)
      attention.push({
        tone: "warning",
        icon: "↩️",
        text: (
          <>
            <b>{owner ? "Return needs your approval" : "Return waiting for the owner"}</b> – {r.gem} (
            {r.kind === "EXCHANGE" ? "exchange" : "refund"}).
          </>
        ),
        href: role === "ACCOUNTANT" ? undefined : `/sell/returns/new?id=${r.number}`,
        button: owner ? "Approve" : "View",
      });
    counts["/sell/invoices"] = `${s.salesToday} today`;
    counts["/sell/returns"] = s.returnsWaiting.length ? `${s.returnsWaiting.length} waiting` : "none waiting";
    counts["/sell/reserved"] = `${s.reservations + s.quotations} active`;
  }
  return (
    <SectionHome
      sectionKey="sell"
      attention={attention}
      counts={counts}
      side={
        s?.latest.length ? (
          <Card>
            <CardHeader title="Latest invoices" level={3} />
            <ul className="list hub-recent">
              {s.latest.map((x) => (
                <li key={x.number}>
                  <Link href={`/sell/invoices/${x.number}`}>
                    <GemSvg gem={x.gemType} type={x.productType} size={34} />
                    <span style={{ flex: 1 }}>
                      <b>{x.customer}</b> <small className="muted">{x.country}</small>
                      <br />
                      <small className="muted mono">
                        {x.number} · {dateTime(x.soldAt)} · {x.items} gem(s)
                      </small>
                    </span>
                    <span className="right">
                      <b>{inCurrency(x.total, currency, rates)}</b>
                      <br />
                      {x.status === "COMPLETED" ? (
                        <Badge tone="success">Completed</Badge>
                      ) : (
                        <Badge tone="danger">Cancelled</Badge>
                      )}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          </Card>
        ) : null
      }
    />
  );
}
