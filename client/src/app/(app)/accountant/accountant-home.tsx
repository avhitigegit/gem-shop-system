"use client";

/* Accountant home – prototype n-accountant/dashboard.html (Guide 72; BRD N1–N3, rules 4 and 11). The accountant opens
   here after sign-in: this month's figures for every branch, the months and whether they are closed, and what is left
   to do at month end. The owner can open it too. */
import Link from "next/link";
import { StatusPill } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, Kpi } from "@/components/ui/card";
import { DataTable } from "@/components/ui/data-table";
import { Alert } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { api } from "@/lib/api/client";
import { inCurrency, month } from "@/lib/format";
import type { MonthRow } from "@/lib/month-end";
import { ACCOUNTANT } from "@/lib/roles";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

const REPORTS = [
  ["/reports/sales", "Sales report"],
  ["/stock/purchases", "Purchases"],
  ["/reports/expenses", "Expense report"],
  ["/reports/profit", "Profit report"],
  ["/reports/commissions", "Commission report"],
];

export function AccountantHome() {
  const { branch, branchName: nameOf, currency, rates, role } = useSession();
  const branchName = nameOf(branch);
  const cur = (n: number) => inCurrency(n, currency, rates);
  const { data: h } = useLoad(() => api.GET("/api/accountant/home"), [branch]);

  return (
    <>
      <PageHead
        title="Accountant home"
        sub={`Sales, purchases, expenses and profit – ${branchName}. Everything for tax work, without asking the shop for papers.`}
        actions={
          <>
            <Button variant="sapphire" asChild>
              <Link href="/reports/tax-pack">⇩ Tax pack</Link>
            </Button>
            <Button variant="outline" asChild>
              <Link href="/money/expenses/new">+ Record expense</Link>
            </Button>
          </>
        }
      />
      {role === ACCOUNTANT && (
        <Alert className="mb">
          You can see every branch and record or correct expenses. You cannot sell, change prices or cancel invoices.
          Closed months are locked.
        </Alert>
      )}
      {!h ? (
        <p className="muted">Loading…</p>
      ) : (
        <>
          <div className="g4 mb grid">
            <Kpi
              label={`Sales – ${month(h.thisMonth.month)}`}
              value={cur(h.thisMonth.netSales)}
              sub={h.lastMonth ? `last month ${cur(h.lastMonth.netSales)}` : undefined}
              accent
            />
            <Kpi label="Invoices" value={h.thisMonth.invoices} sub={month(h.thisMonth.month)} />
            <Kpi label="Expenses" value={cur(h.thisMonth.expenses)} sub="approved, this month" />
            <Kpi
              label="Net profit (month to date)"
              value={cur(h.thisMonth.netProfit)}
              sub="after cost, commissions & expenses"
            />
          </div>
          <div className="g-2-1 grid">
            <Card>
              <CardHeader
                title="Months"
                level={3}
                actions={
                  <Button size="sm" variant="ghost" asChild>
                    <Link href="/reports/close-month">Close the month →</Link>
                  </Button>
                }
              />
              <DataTable<MonthRow>
                rows={h.months}
                rowKey={(m) => m.month}
                stackOnPhone
                columns={[
                  { key: "m", header: "Month", cell: (m) => <b>{month(m.month)}</b> },
                  { key: "i", header: "Invoices", num: true, cell: (m) => m.invoices },
                  { key: "s", header: "Sales", num: true, cell: (m) => cur(m.netSales) },
                  { key: "p", header: "Net profit", num: true, cell: (m) => <b>{cur(m.netProfit)}</b> },
                  { key: "st", header: "Status", cell: (m) => <StatusPill status={m.closed ? "closed" : "open"} /> },
                ]}
              />
              <CardContent>
                <p className="muted" style={{ fontSize: 12 }}>
                  Months are for the whole shop.
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader title="To do" level={3} />
              <CardContent>
                <ul className="list">
                  <ToDo
                    icon="📑"
                    text="Profit & Loss"
                    sub="by month and branch"
                    href="/reports/profit-loss"
                    button="Open"
                  />
                  <ToDo
                    icon="📎"
                    text="Expenses without a bill photo"
                    sub={`${h.billsMissing} found`}
                    href="/money/expenses"
                    button="Check"
                  />
                  <ToDo
                    icon="⏳"
                    text="Expenses waiting for the owner"
                    sub={`${h.expensesWaiting} waiting`}
                    href="/approvals"
                    button="View"
                  />
                  <ToDo
                    icon="🔒"
                    text={`Close ${month(h.toClose.month)}`}
                    sub={
                      h.toClose.closed
                        ? "already closed"
                        : h.toClose.reopenWaiting
                          ? "re-open request waiting"
                          : "ready to close"
                    }
                    href="/reports/close-month"
                    button="Open"
                  />
                </ul>
              </CardContent>
            </Card>
          </div>
          <div className="g3 mt grid">
            {REPORTS.map(([href, label]) => (
              <Link key={href} className="card kpi" href={href} style={{ textDecoration: "none" }}>
                <div className="k-value" style={{ fontSize: 16 }}>
                  {label} →
                </div>
              </Link>
            ))}
          </div>
        </>
      )}
    </>
  );
}

function ToDo({
  icon,
  text,
  sub,
  href,
  button,
}: {
  icon: string;
  text: string;
  sub: string;
  href: string;
  button: string;
}) {
  return (
    <li>
      <span aria-hidden>{icon}</span>
      <div style={{ flex: 1 }}>
        {text}
        <br />
        <small className="muted">{sub}</small>
      </div>
      <Button size="sm" variant="outline" asChild>
        <Link href={href}>{button}</Link>
      </Button>
    </li>
  );
}
