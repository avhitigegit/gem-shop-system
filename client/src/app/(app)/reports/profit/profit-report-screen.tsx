"use client";

/* Profit report – prototype l-reports/profit.html (Guide 64; BRD L2, L6, N1; rule 1). Per gem sold: selling price −
   cost − its share of the bill's commission. Owner and accountant only (cost prices). The branch at the top filters. */
import Link from "next/link";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, Kpi } from "@/components/ui/card";
import { DataTable, ExportButtons, Filters } from "@/components/ui/data-table";
import { Field, Input, Options, Select } from "@/components/ui/form";
import { GemPicture } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { api } from "@/lib/api/client";
import { date, inCurrency, pct, todayInShop } from "@/lib/format";
import { daysAgo, type ProfitRow, useReportDownload } from "@/lib/reports";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function ProfitReportScreen() {
  const { branch, currency, rates } = useSession();
  const cur = (n: number | string) => inCurrency(n, currency, rates);
  const [from, setFrom] = useState(() => daysAgo(29));
  const [to, setTo] = useState(() => todayInShop());
  const [gemType, setGemType] = useState("");
  const filters = { from: from || undefined, to: to || undefined, gemType: gemType || undefined };
  const { data: r } = useLoad(
    () => api.GET("/api/reports/profit", { params: { query: filters } }),
    [from, to, gemType, branch],
  );
  const download = useReportDownload("profit", "Profit report", filters);

  return (
    <>
      <PageHead
        title="Profit report"
        sub="Selling price minus cost and commissions, per product and in total."
        actions={
          <ExportButtons
            name="Profit report"
            onPdf={() => void download("pdf")}
            onExcel={() => void download("xlsx")}
          />
        }
      />
      <Card className="mb">
        <CardContent>
          <Filters>
            <Field label="From" htmlFor="pf">
              <Input id="pf" type="date" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)} />
            </Field>
            <Field label="To" htmlFor="pt">
              <Input id="pt" type="date" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} />
            </Field>
            <Field label="Gem type" htmlFor="pg">
              <Select id="pg" value={gemType} onChange={(e) => setGemType(e.target.value)}>
                <Options placeholder="All" items={r?.gemTypes ?? []} />
              </Select>
            </Field>
          </Filters>
        </CardContent>
      </Card>
      {!r ? (
        <p className="muted">Loading…</p>
      ) : (
        <>
          <div className="g4 mb grid">
            <Kpi label="Sales" value={cur(r.sales)} sub={`${r.rows.length} products sold`} />
            <Kpi label="Cost of products sold" value={cur(r.cost)} />
            <Kpi label="Commissions" value={cur(r.commission)} sub="guides + staff" />
            <Kpi label="Gross profit" value={cur(r.profit)} sub={`${pct(r.margin)} margin`} accent />
          </div>
          <Card>
            <DataTable<ProfitRow>
              id="rT"
              rows={r.rows}
              rowKey={(x) => `${x.invoice}-${x.gem}`}
              stackOnPhone
              empty="No sales in this period."
              columns={[
                {
                  key: "p",
                  header: "Product",
                  cell: (x) => (
                    <div className="row" style={{ gap: 8, flexWrap: "nowrap" }}>
                      <GemPicture
                        gem={x.gemType}
                        type={x.productType}
                        photoUrl={x.photo}
                        size={30}
                        box={36}
                        alt={x.name}
                      />
                      <span>
                        <b>{x.name}</b>
                        <br />
                        <Link className="mono" style={{ fontSize: 11 }} href={`/stock/gems/${x.gem}`}>
                          {x.gem}
                        </Link>
                      </span>
                    </div>
                  ),
                },
                {
                  key: "s",
                  header: "Sold",
                  cell: (x) => (
                    <>
                      {date(x.soldAt)}
                      <br />
                      <Link className="mono" style={{ fontSize: 11 }} href={`/sell/invoices/${x.invoice}`}>
                        {x.invoice}
                      </Link>
                    </>
                  ),
                },
                { key: "pr", header: "Selling price", num: true, cell: (x) => cur(x.price) },
                { key: "c", header: "Cost", num: true, cell: (x) => cur(x.cost) },
                { key: "cm", header: "Commission", num: true, cell: (x) => cur(x.commission) },
                {
                  key: "pf",
                  header: "Profit",
                  num: true,
                  cell: (x) => <b className={Number(x.profit) < 0 ? "text-danger" : "text-success"}>{cur(x.profit)}</b>,
                },
                {
                  key: "m",
                  header: "Margin",
                  num: true,
                  cell: (x) => (
                    <>
                      {pct(x.margin)}
                      {x.belowMin && (
                        <>
                          <br />
                          <Badge tone="warning">below min</Badge>
                        </>
                      )}
                    </>
                  ),
                },
              ]}
              footer={["Total", "", cur(r.sales), cur(r.cost), cur(r.commission), cur(r.profit), pct(r.margin)]}
            />
          </Card>
          <p className="muted mt-s" style={{ fontSize: 12 }}>
            Commissions are shared across the items of a bill by price. Shop expenses are not included here – see{" "}
            <Link href="/reports/profit-loss">Profit &amp; Loss</Link> for net profit.
          </p>
        </>
      )}
    </>
  );
}
