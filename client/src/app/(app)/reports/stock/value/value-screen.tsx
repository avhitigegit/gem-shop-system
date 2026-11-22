"use client";

/* Value by gem type – prototype d-stock/stock-value.html (BRD D6). Stock value at cost and at selling price, by gem
   type, product type and branch. Owner only (rule 1: only the owner sees cost prices). */
import { Card, CardHeader, Kpi } from "@/components/ui/card";
import { DataTable, ExportButtons, type Column } from "@/components/ui/data-table";
import { Alert } from "@/components/ui/form";
import { GemSvg } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { api } from "@/lib/api/client";
import { inCurrency, num, pct } from "@/lib/format";
import { ALL_BRANCHES, useSession } from "@/lib/session";
import type { ValueRow } from "@/lib/stock";
import { useLoad } from "@/lib/use-load";

type By = "gem" | "type" | "branch";

export function ValueScreen() {
  const { branch, branchName, currency, rates } = useSession();
  const { data: v } = useLoad(() => api.GET("/api/stock/value"), [branch]);
  const money = (n: number | string) => inCurrency(n, currency, rates);

  if (!v) return <p className="muted">Loading…</p>;
  const t = v.total;
  const margin = Number(t.price) - Number(t.cost);
  const max = Math.max(1, ...v.byGemType.map((r) => Number(r.price)));

  const table = (by: By, label: string, rows: ValueRow[], id?: string) => {
    const columns: Column<ValueRow>[] = [
      {
        key: "group",
        header: label,
        cell: (r) =>
          by === "gem" ? (
            <span className="row" style={{ gap: 6, flexWrap: "nowrap" }}>
              <GemSvg gem={r.group} type="Loose gem" size={22} />
              {r.group}
            </span>
          ) : by === "branch" ? (
            branchName(r.group)
          ) : (
            r.group
          ),
      },
      { key: "items", header: "Items", num: true, cell: (r) => r.items },
      { key: "carats", header: "Carats", num: true, cell: (r) => num(r.carats) },
      { key: "cost", header: "At cost", num: true, cell: (r) => money(r.cost) },
      { key: "price", header: "At selling price", num: true, cell: (r) => <b>{money(r.price)}</b> },
      { key: "margin", header: "Margin", num: true, cell: (r) => money(Number(r.price) - Number(r.cost)) },
      ...(by === "gem"
        ? [
            {
              key: "bar",
              header: "",
              cell: (r: ValueRow) => (
                <div className="bar gold" style={{ width: 120 }}>
                  <i style={{ width: `${(Number(r.price) / max) * 100}%` }} />
                </div>
              ),
            },
          ]
        : []),
    ];
    const footer = [
      "Total",
      t.items,
      num(t.carats),
      money(t.cost),
      money(t.price),
      money(margin),
      ...(by === "gem" ? [""] : []),
    ];
    return <DataTable id={id} columns={columns} rows={rows} rowKey={(r) => r.group} footer={footer} stackOnPhone />;
  };

  return (
    <>
      <PageHead
        title={`Stock value – ${branch === ALL_BRANCHES ? "all branches" : branchName(branch)}`}
        sub="Stock value at cost and at selling price. Owner only."
        actions={<ExportButtons tableId="stockValue" name="Stock value" />}
      />
      <div className="g3 mb grid">
        <Kpi label="At cost" value={money(t.cost)} sub={`${t.items} items`} accent />
        <Kpi label="At selling price" value={money(t.price)} />
        <Kpi
          label="Potential margin"
          value={money(margin)}
          sub={`${pct(Number(t.price) ? (margin / Number(t.price)) * 100 : 0)} of selling price`}
        />
      </div>
      <div className="stack">
        <Card>
          <CardHeader title="By gem type" level={3} />
          {table("gem", "Gem type", v.byGemType, "stockValue")}
        </Card>
        <div className="g2 grid">
          <Card>
            <CardHeader title="By product type" level={3} />
            {table("type", "Product type", v.byProductType)}
          </Card>
          <Card>
            <CardHeader title="By branch" level={3} />
            {table("branch", "Branch", v.byBranch)}
          </Card>
        </div>
        <Alert tone="info">
          Stock = every gem the shop still has (in stock, not ready, reserved, on hold, back from a customer, on the
          way). Only the owner can see cost prices.
        </Alert>
      </div>
    </>
  );
}
