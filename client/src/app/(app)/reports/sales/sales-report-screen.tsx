"use client";

/* Sales report – prototype l-reports/sales.html (Guide 63; BRD L1, L6). By day, month, branch, gem type, salesperson,
   guide and payment method. Cancelled invoices and returned gems are left out. The branch at the top filters. */
import { useState } from "react";
import { Card, CardContent, Kpi } from "@/components/ui/card";
import { DataTable, ExportButtons, Filters } from "@/components/ui/data-table";
import { Field, Input, Options, Select } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { api } from "@/lib/api/client";
import { date, inCurrency, todayInShop } from "@/lib/format";
import { daysAgo, SALES_BY, type SalesBy, type SalesRow, useReportDownload } from "@/lib/reports";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function SalesReportScreen() {
  const { branch, currency, rates } = useSession();
  const cur = (n: number | string) => inCurrency(n, currency, rates);
  const [from, setFrom] = useState(() => daysAgo(29));
  const [to, setTo] = useState(() => todayInShop());
  const [gemType, setGemType] = useState("");
  const [staff, setStaff] = useState("");
  const [guide, setGuide] = useState("");
  const [pay, setPay] = useState("");
  const [by, setBy] = useState<SalesBy>("DAY");
  const filters = {
    from: from || undefined,
    to: to || undefined,
    gemType: gemType || undefined,
    staff: staff ? Number(staff) : undefined,
    guide: guide || undefined,
    pay: pay || undefined,
    by,
  };
  const { data: r } = useLoad(
    () => api.GET("/api/reports/sales", { params: { query: filters } }),
    [from, to, gemType, staff, guide, pay, by, branch],
  );
  const download = useReportDownload("sales", "Sales report", filters);
  const byLabel = SALES_BY.find((b) => b.value === by)?.label ?? "";
  const max = Math.max(1, ...(r?.rows ?? []).map((x) => Number(x.total)));
  const days = by === "DAY" && r ? [...r.rows].reverse() : [];
  const dayMax = Math.max(1, ...days.map((x) => Number(x.total)));

  return (
    <>
      <PageHead
        title="Sales report"
        sub="By day, month, branch, gem type, salesperson, guide and payment method. Cancelled invoices are excluded."
        actions={
          <ExportButtons name="Sales report" onPdf={() => void download("pdf")} onExcel={() => void download("xlsx")} />
        }
      />
      <Card className="mb">
        <CardContent>
          <Filters>
            <Field label="From" htmlFor="sf">
              <Input id="sf" type="date" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)} />
            </Field>
            <Field label="To" htmlFor="st">
              <Input id="st" type="date" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} />
            </Field>
            <Field label="Gem type" htmlFor="sg">
              <Select id="sg" value={gemType} onChange={(e) => setGemType(e.target.value)}>
                <Options placeholder="All" items={r?.gemTypes ?? []} />
              </Select>
            </Field>
            <Field label="Salesperson" htmlFor="ss">
              <Select id="ss" value={staff} onChange={(e) => setStaff(e.target.value)}>
                <Options placeholder="All" items={r?.staff ?? []} />
              </Select>
            </Field>
            <Field label="Guide" htmlFor="sgu">
              <Select id="sgu" value={guide} onChange={(e) => setGuide(e.target.value)}>
                <Options placeholder="All" items={[{ value: "walkin", label: "Walk-in only" }, ...(r?.guides ?? [])]} />
              </Select>
            </Field>
            <Field label="Payment" htmlFor="sp">
              <Select id="sp" value={pay} onChange={(e) => setPay(e.target.value)}>
                <Options placeholder="All" items={r?.payTypes ?? []} />
              </Select>
            </Field>
            <Field label="Group by" htmlFor="sb">
              <Select id="sb" value={by} onChange={(e) => setBy(e.target.value as SalesBy)}>
                <Options items={SALES_BY} />
              </Select>
            </Field>
          </Filters>
        </CardContent>
      </Card>
      {!r ? (
        <p className="muted">Loading…</p>
      ) : (
        <>
          <div className="g2 mb grid">
            <Kpi label="Sales" value={cur(r.total)} sub={`${r.bills} bills · ${r.items} items`} accent />
            <Kpi label="Average bill" value={cur(r.averageBill)} />
          </div>
          {days.length > 0 && (
            <Card className="mb">
              <CardContent>
                <div className="chart-bars">
                  {days.map((d) => (
                    <div className="cb" key={d.key} title={`${date(d.key)}: ${cur(d.total)}`}>
                      <i style={{ height: `${Math.round((Number(d.total) / dayMax) * 100)}%` }} />
                      <span>{d.key.slice(8)}</span>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}
          <Card>
            <DataTable<SalesRow>
              id="rT"
              rows={r.rows}
              rowKey={(x) => x.key}
              empty="No sales for these filters."
              columns={[
                { key: "k", header: byLabel, cell: (x) => <b>{x.label}</b> },
                { key: "b", header: "Bills", num: true, cell: (x) => x.bills },
                { key: "i", header: "Items", num: true, cell: (x) => x.items },
                { key: "t", header: "Total", num: true, cell: (x) => <b>{cur(x.total)}</b> },
                {
                  key: "s",
                  header: "Share",
                  cell: (x) => (
                    <div className="bar" style={{ width: 120 }}>
                      <i style={{ width: `${(Number(x.total) / max) * 100}%` }} />
                    </div>
                  ),
                },
              ]}
              footer={["Total", r.bills, r.items, cur(r.total), ""]}
            />
          </Card>
        </>
      )}
    </>
  );
}
