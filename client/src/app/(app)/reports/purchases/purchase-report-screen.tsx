"use client";

/* Purchases by supplier – prototype l-reports/purchases.html (Guide 70; BRD L5, B3, L6). What was bought from each
   supplier, how much of it has been sold, and what went back to the supplier. The branch at the top filters. */
import Link from "next/link";
import { useState } from "react";
import { Card, CardContent, Kpi } from "@/components/ui/card";
import { DataTable, ExportButtons, Filters } from "@/components/ui/data-table";
import { Field, Input } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { api } from "@/lib/api/client";
import { date, inCurrency, todayInShop } from "@/lib/format";
import { daysAgo, type SupplierReportRow, useReportDownload } from "@/lib/reports";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function PurchaseReportScreen() {
  const { branch, currency, rates } = useSession();
  const cur = (n: number | string) => inCurrency(n, currency, rates);
  const [from, setFrom] = useState(() => daysAgo(180));
  const [to, setTo] = useState(() => todayInShop());
  const filters = { from: from || undefined, to: to || undefined };
  const { data: r } = useLoad(
    () => api.GET("/api/reports/purchases", { params: { query: filters } }),
    [from, to, branch],
  );
  const download = useReportDownload("purchases", "Purchases by supplier", filters);
  const max = Math.max(1, ...(r?.rows ?? []).map((x) => Number(x.cost)));

  return (
    <>
      <PageHead
        title="Purchases by supplier"
        sub="What was bought from each supplier, and how much of it has been sold."
        actions={
          <ExportButtons
            name="Purchases by supplier"
            onPdf={() => void download("pdf")}
            onExcel={() => void download("xlsx")}
          />
        }
      />
      <Card className="mb">
        <CardContent>
          <Filters>
            <Field label="From" htmlFor="uf">
              <Input id="uf" type="date" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)} />
            </Field>
            <Field label="To" htmlFor="ut">
              <Input id="ut" type="date" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} />
            </Field>
          </Filters>
        </CardContent>
      </Card>
      {!r ? (
        <p className="muted">Loading…</p>
      ) : (
        <>
          <div className="g3 mb grid">
            <Kpi label="Total bought (cash)" value={cur(r.cost)} sub={`${r.purchases} purchases`} />
            <Kpi label="Products" value={r.products} sub={`${r.sold} sold`} />
            <Kpi label="Returned to suppliers" value={cur(r.returned)} />
          </div>
          <Card>
            <DataTable<SupplierReportRow>
              id="rT"
              rows={r.rows}
              rowKey={(x) => x.code}
              stackOnPhone
              empty="No purchases in this period."
              columns={[
                {
                  key: "s",
                  header: "Supplier",
                  cell: (x) => (
                    <>
                      <Link href={`/stock/suppliers/${x.code}`}>
                        <b>{x.name}</b>
                      </Link>
                      {x.address ? (
                        <>
                          <br />
                          <small className="muted">{x.address}</small>
                        </>
                      ) : null}
                    </>
                  ),
                },
                { key: "p", header: "Purchases", num: true, cell: (x) => x.purchases },
                { key: "i", header: "Products", num: true, cell: (x) => x.products },
                {
                  key: "so",
                  header: "Sold",
                  num: true,
                  cell: (x) => (
                    <>
                      {x.sold}{" "}
                      <small className="muted">({Math.round((x.sold / Math.max(1, x.products)) * 100)}%)</small>
                    </>
                  ),
                },
                { key: "c", header: "Total cost", num: true, cell: (x) => <b>{cur(x.cost)}</b> },
                { key: "r", header: "Returned", num: true, cell: (x) => (Number(x.returned) ? cur(x.returned) : "—") },
                { key: "l", header: "Last purchase", cell: (x) => date(x.lastPurchase) },
                {
                  key: "b",
                  header: "",
                  cell: (x) => (
                    <div className="bar gold" style={{ width: 100 }}>
                      <i style={{ width: `${(Number(x.cost) / max) * 100}%` }} />
                    </div>
                  ),
                },
              ]}
              footer={["Total", r.purchases, r.products, r.sold, cur(r.cost), cur(r.returned), "", ""]}
            />
          </Card>
        </>
      )}
    </>
  );
}
