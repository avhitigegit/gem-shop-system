"use client";

/* Profit & Loss – prototype n-accountant/profit-loss.html (Guide 73; BRD N4). For a month: sales − returns − cost of
   products sold − commissions − card bank fees − expenses = net profit. Every month next to it, and the month per
   branch. The branch at the top filters. Owner and accountant. */
import { useState } from "react";
import { StatusPill } from "@/components/ui/badge";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { DataTable, ExportButtons, Filters } from "@/components/ui/data-table";
import { Field, Options, Select } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { api } from "@/lib/api/client";
import { inCurrency, lkr, month } from "@/lib/format";
import type { PlBranch, PlMonth } from "@/lib/month-end";
import { useReportDownload } from "@/lib/reports";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function ProfitLossScreen() {
  const { branch, branchName: nameOf, currency, rates } = useSession();
  const branchName = nameOf(branch);
  const cur = (n: number) => inCurrency(n, currency, rates);
  const [chosen, setChosen] = useState("");
  const { data: r } = useLoad(
    () => api.GET("/api/reports/profit-loss", { params: { query: { month: chosen || undefined } } }),
    [chosen, branch],
  );
  const download = useReportDownload("profit-loss", "Profit and Loss", { month: chosen || r?.figures.month });
  const m = r?.figures;
  const neg = (n: number) => (n ? `− ${lkr(n)}` : lkr(0));

  return (
    <>
      <PageHead
        title="Profit & Loss"
        sub="Sales − cost of products sold − commissions − card bank fees − expenses = net profit, by month and branch."
        actions={
          <ExportButtons
            name="Profit and Loss"
            onPdf={() => void download("pdf")}
            onExcel={() => void download("xlsx")}
          />
        }
      />
      <Card className="mb">
        <CardContent>
          <Filters>
            <Field label="Month" htmlFor="plm">
              <Select id="plm" value={m?.month ?? ""} onChange={(e) => setChosen(e.target.value)}>
                <Options items={(r?.months ?? []).map((x) => ({ value: x.month, label: month(x.month) }))} />
              </Select>
            </Field>
          </Filters>
        </CardContent>
      </Card>
      {!r || !m ? (
        <p className="muted">Loading…</p>
      ) : (
        <>
          <div className="g-1-2 grid">
            <Card>
              <CardHeader title={`${month(m.month)} · ${branchName}`} level={3}>
                <StatusPill status={m.closed ? "closed" : "open"} />
              </CardHeader>
              <CardContent>
                <table className="table" id="plT">
                  <tbody>
                    <Line label={<b>Sales</b>} value={<b>{lkr(m.sales)}</b>} />
                    <Line label="Less: returns (credit notes)" value={neg(m.returns)} />
                    <Line label={<b>Net sales</b>} value={<b>{lkr(m.netSales)}</b>} />
                    <Line label="Less: cost of products sold" value={neg(m.cost)} />
                    <Line label={<b>Gross profit</b>} value={<b>{lkr(m.grossProfit)}</b>} />
                    <Line label="Less: commissions (guides & staff)" value={neg(m.commissions)} />
                    <Line
                      label={`Less: card bank fees (${Number(r.cardFeePct)}%, paid by the shop)`}
                      value={neg(m.cardFees)}
                    />
                    <Line label="Less: shop expenses (rent, electricity, salaries…)" value={neg(m.expenses)} />
                    {r.expenseTypes.map((t) => (
                      <Line
                        key={t.expenseType}
                        label={
                          <span className="muted" style={{ paddingLeft: 16 }}>
                            {t.expenseType}
                          </span>
                        }
                        value={<span className="muted">{lkr(t.total)}</span>}
                      />
                    ))}
                  </tbody>
                  <tfoot>
                    <tr>
                      <td>Net profit for the month</td>
                      <td
                        className="num"
                        style={{ fontSize: 18, color: m.netProfit < 0 ? "var(--danger)" : "var(--success)" }}
                      >
                        {lkr(m.netProfit)}
                      </td>
                    </tr>
                  </tfoot>
                </table>
              </CardContent>
            </Card>
            <Card>
              <CardHeader title="Month by month" level={3} />
              <DataTable<PlMonth>
                rows={r.months}
                rowKey={(x) => x.month}
                stackOnPhone
                marked={(x) => x.month === m.month}
                onRowClick={(x) => setChosen(x.month)}
                columns={[
                  { key: "m", header: "Month", cell: (x) => <b>{month(x.month)}</b> },
                  { key: "s", header: "Net sales", num: true, cell: (x) => cur(x.netSales) },
                  { key: "c", header: "Cost", num: true, cell: (x) => cur(x.cost) },
                  { key: "cm", header: "Commissions", num: true, cell: (x) => cur(x.commissions) },
                  { key: "f", header: "Card fees", num: true, cell: (x) => cur(x.cardFees) },
                  { key: "e", header: "Expenses", num: true, cell: (x) => cur(x.expenses) },
                  {
                    key: "n",
                    header: "Net profit",
                    num: true,
                    cell: (x) => <b className={x.netProfit < 0 ? "text-danger" : "text-success"}>{cur(x.netProfit)}</b>,
                  },
                ]}
              />
              <CardContent>
                <h3>By branch – {month(m.month)}</h3>
                <DataTable<PlBranch>
                  rows={r.branches}
                  rowKey={(b) => b.code}
                  empty="Nothing sold or spent in this month."
                  columns={[
                    { key: "b", header: "Branch", cell: (b) => b.name },
                    { key: "s", header: "Net sales", num: true, cell: (b) => cur(b.netSales) },
                    { key: "n", header: "Net profit", num: true, cell: (b) => cur(b.netProfit) },
                  ]}
                />
              </CardContent>
            </Card>
          </div>
          <p className="muted mt-s" style={{ fontSize: 12 }}>
            {r.monthToDate ? "This is the current month – the figures are month to date. " : ""}
            Returns: the credit notes given in the month; their gems go back in stock, so their cost comes off. Only
            approved expenses count.
          </p>
        </>
      )}
    </>
  );
}

function Line({ label, value }: { label: React.ReactNode; value: React.ReactNode }) {
  return (
    <tr>
      <td>{label}</td>
      <td className="num">{value}</td>
    </tr>
  );
}
