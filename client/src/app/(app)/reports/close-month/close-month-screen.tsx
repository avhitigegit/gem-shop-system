"use client";

/* Close the month – prototype n-accountant/month-closing.html (Guide 75; BRD N6, K3; rule 11). A closed month cannot
   change: sales cancellations, expenses, purchases and commission payments dated in it are locked. The owner re-opens
   with a reason; the accountant asks the owner. Months are for the whole shop (not the branch at the top). */
import Link from "next/link";
import { useState } from "react";
import { Badge, StatusPill } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Alert } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { api } from "@/lib/api/client";
import { date, inCurrency, month } from "@/lib/format";
import { type MonthRow, monthChecks, useMonthActions } from "@/lib/month-end";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function CloseMonthScreen() {
  const { currency, rates } = useSession();
  const cur = (n: number) => inCurrency(n, currency, rates);
  const { data, reload } = useLoad(() => api.GET("/api/months"), []);
  const { close, reopen, isOwner } = useMonthActions();
  const [busy, setBusy] = useState<string | null>(null);

  const act = async (m: MonthRow, f: (m: MonthRow) => Promise<boolean>) => {
    setBusy(m.month);
    try {
      if (await f(m)) await reload();
    } finally {
      setBusy(null);
    }
  };

  return (
    <>
      <PageHead
        title="Close the month"
        sub="A closed month cannot be changed without the owner's approval. Sales, expenses, purchases and payments dated in a closed month are locked."
      />
      <Alert className="mb" icon="🔒">
        Close a month after its expenses and bills are checked and the <Link href="/reports/tax-pack">tax pack</Link> is
        downloaded. Months are for the whole shop – every branch.
      </Alert>
      {!data ? (
        <p className="muted">Loading…</p>
      ) : (
        <div className="stack">
          {data.months.map((m) => (
            <Card key={m.month} className={m.closed ? "locked-banner" : undefined}>
              <CardHeader>
                <div>
                  <h2>
                    {m.closed ? "🔒 " : ""}
                    {month(m.month)}
                  </h2>
                  <p>
                    {m.invoices} invoices · net sales {cur(m.netSales)} · net profit {cur(m.netProfit)}
                  </p>
                </div>
                <StatusPill
                  status={m.closed ? "closed" : "open"}
                  label={
                    m.closed
                      ? `Closed ${date(m.closedAt)}${m.closedBy ? ` by ${m.closedBy}` : ""}`
                      : m.reopenedAt
                        ? `Re-opened ${date(m.reopenedAt)}`
                        : "Open"
                  }
                />
              </CardHeader>
              <CardContent className="row" style={{ alignItems: "flex-start", justifyContent: "space-between" }}>
                <div style={{ minWidth: 260 }}>
                  <ul className="list" style={{ fontSize: 12 }}>
                    {monthChecks(m).map((c) => (
                      <li key={c.text} style={{ padding: "4px 0" }}>
                        {c.ok ? <span className="text-success">✓</span> : <span className="text-warning">!</span>}{" "}
                        {c.text}
                      </li>
                    ))}
                  </ul>
                  {!m.closed && m.reopenReason && (
                    <p className="muted" style={{ fontSize: 12 }}>
                      Re-opened by {m.reopenedBy}: {m.reopenReason}
                    </p>
                  )}
                </div>
                <div className="row">
                  {m.closed ? (
                    m.reopenWaiting ? (
                      <Badge tone="warning">Re-open request waiting for the owner ({m.reopenWaiting})</Badge>
                    ) : (
                      <Button variant="outline" disabled={busy === m.month} onClick={() => void act(m, reopen)}>
                        {isOwner ? "Re-open month" : "Ask the owner to re-open"}
                      </Button>
                    )
                  ) : (
                    <Button
                      variant="sapphire"
                      disabled={!m.ended || busy === m.month}
                      title={m.ended ? undefined : "The month has not ended yet"}
                      onClick={() => void act(m, close)}
                    >
                      Close month
                    </Button>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </>
  );
}
