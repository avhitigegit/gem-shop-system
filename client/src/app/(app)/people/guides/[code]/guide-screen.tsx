"use client";

/* One guide / driver – prototype h-commissions/guide-profile.html (Guide 38; BRD H1, H5). Sales brought and the
   commission in a period (last 30 days at first). Amounts for the owner and accountant only. */
import Link from "next/link";
import { useState } from "react";
import { StatusPill } from "@/components/ui/badge";
import { Button, buttonClass } from "@/components/ui/button";
import { Card, CardContent, Kpi } from "@/components/ui/card";
import { DataTable, Filters, type Column } from "@/components/ui/data-table";
import { Field, Input } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { api, errorMessage } from "@/lib/api/client";
import { lineStatus, vehicleIcon, type CommissionRow, type GuideCard } from "@/lib/commissions";
import { date, inCurrency, num, todayInShop } from "@/lib/format";
import { OWNER } from "@/lib/roles";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";
import { GuideModal } from "../guides-screen";

const daysAgo = (n: number) => {
  const d = new Date(`${todayInShop()}T12:00:00`);
  d.setDate(d.getDate() - n);
  return d.toISOString().slice(0, 10);
};

export function GuideScreen({ code }: { code: string }) {
  const { role, branch, currency, rates } = useSession();
  const [from, setFrom] = useState(() => daysAgo(30));
  const [to, setTo] = useState(() => todayInShop());
  const [editing, setEditing] = useState<GuideCard | null>(null);
  const { data, error, reload } = useLoad(
    () => api.GET("/api/guides/{code}", { params: { path: { code }, query: { from, to } } }),
    [code, from, to, branch],
  );
  const cur = (v: number | string) => inCurrency(v, currency, rates);

  if (error && !data)
    return (
      <Card>
        <div className="empty">
          {errorMessage(error)} <Link href="/people/guides">Guides &amp; drivers</Link>
        </div>
      </Card>
    );
  if (!data) return <p className="muted">Loading…</p>;
  const g = data.guide;
  const amounts = data.earned != null;

  const columns: Column<CommissionRow>[] = [
    { key: "date", header: "Date", cell: (c) => date(c.earnedAt) },
    {
      key: "inv",
      header: "Invoice",
      cell: (c) => (
        <Link className="mono" href={`/sell/invoices/${c.sale}`}>
          {c.sale}
        </Link>
      ),
    },
    { key: "cust", header: "Customer", cell: (c) => c.customer },
    { key: "sale", header: "Sale amount", num: true, cell: (c) => cur(c.saleTotal) },
    ...(amounts
      ? [
          {
            key: "comm",
            header: "Commission",
            num: true,
            cell: (c: CommissionRow) => (
              <b className={Number(c.amount) < 0 ? "text-danger" : undefined}>{cur(c.amount ?? 0)}</b>
            ),
          },
        ]
      : []),
    {
      key: "st",
      header: "Status",
      cell: (c) => (
        <>
          <StatusPill status={lineStatus(c)} />
          {c.note ? (
            <>
              <br />
              <small className="muted">{c.note}</small>
            </>
          ) : null}
          {c.paidAt && c.status === "PAID" ? (
            <>
              <br />
              <small className="muted">
                {date(c.paidAt)} · {c.method}
              </small>
            </>
          ) : null}
        </>
      ),
    },
  ];

  return (
    <>
      <PageHead
        title={g.name}
        sub={`${vehicleIcon(g.vehicle)} ${g.vehicle ?? ""} · ${g.vehicleNo ?? ""} · ${g.phone} · commission ${num(g.pct, 0)}% · since ${date(g.since)}`}
        actions={
          <>
            {role === OWNER ? (
              <Button variant="ghost" onClick={() => setEditing(g)}>
                Edit
              </Button>
            ) : null}
            {amounts ? (
              <>
                <Link className={buttonClass("outline")} href={`/people/commissions/slip/${g.code}`} id="slipBtn">
                  Commission slip
                </Link>
                <Link className={buttonClass("sapphire")} href={`/people/commissions/pay?person=G:${g.code}`}>
                  Pay commission
                </Link>
              </>
            ) : null}
          </>
        }
      />
      <div className="g4 mb grid">
        <Kpi label="Sales brought" value={data.sales} sub="in the period" />
        <Kpi label="Sales value" value={cur(data.salesValue)} sub="agreed prices" />
        {amounts ? (
          <>
            <Kpi label="Commission earned" value={cur(data.earned ?? 0)} sub={`${num(g.pct, 0)}% of the price`} />
            <Kpi label="Not yet paid" value={cur(data.unpaid ?? 0)} sub={`${data.unpaidLines} sale(s)`} />
          </>
        ) : null}
      </div>
      <Card>
        <CardContent>
          <Filters>
            <Field label="From" htmlFor="fr">
              <Input
                id="fr"
                type="date"
                value={from}
                max={to}
                onChange={(e) => e.target.value && setFrom(e.target.value)}
              />
            </Field>
            <Field label="To" htmlFor="to">
              <Input
                id="to"
                type="date"
                value={to}
                min={from}
                onChange={(e) => e.target.value && setTo(e.target.value)}
              />
            </Field>
          </Filters>
        </CardContent>
        <DataTable
          id="guideLines"
          columns={columns}
          rows={data.lines}
          rowKey={(c) => String(c.id)}
          stackOnPhone
          empty="No sales in this period."
        />
      </Card>
      {editing ? (
        <GuideModal
          guide={editing}
          defaultPct={Number(g.pct)}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            reload();
          }}
        />
      ) : null}
    </>
  );
}
