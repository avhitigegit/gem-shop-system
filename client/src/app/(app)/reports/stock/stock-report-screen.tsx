"use client";

/* Stock report – prototype l-reports/stock.html (Guide 66; BRD L3, D6, L6; rule 1). Gems still in the shop with photo,
   days in stock and value; slow-moving = not sold for more than N days. Owner only (cost prices). The branch at the
   top filters. "Show" is a drop-down (no tab strips – keep the screens simple). */
import Link from "next/link";
import { useState } from "react";
import { StatusPill } from "@/components/ui/badge";
import { buttonClass } from "@/components/ui/button";
import { Card, CardContent, Kpi } from "@/components/ui/card";
import { DataTable, ExportButtons, Filters } from "@/components/ui/data-table";
import { Alert, Field, Input, Options, Select } from "@/components/ui/form";
import { GemPicture } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { api } from "@/lib/api/client";
import { carat, date, inCurrency } from "@/lib/format";
import { type StockReportRow, useReportDownload } from "@/lib/reports";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function StockReportScreen() {
  const { branch, branchName, currency, rates } = useSession();
  const cur = (n: number | string) => inCurrency(n, currency, rates);
  const [show, setShow] = useState<"all" | "slow">("all");
  const [gemType, setGemType] = useState("");
  const [days, setDays] = useState("60");
  const slowDays = Math.min(3650, Math.max(1, Number(days) || 60));
  const slow = show === "slow";
  const filters = { gemType: gemType || undefined, slow, slowDays: slow ? slowDays : undefined };
  const { data: r } = useLoad(
    () => api.GET("/api/reports/stock", { params: { query: filters } }),
    [gemType, slow, slowDays, branch],
  );
  const download = useReportDownload("stock", slow ? "Slow-moving stock" : "Stock report", filters);

  return (
    <>
      <PageHead
        title="Stock report"
        sub="Stock with photos and value; slow-moving stock (items not sold for a long time)."
        actions={
          <>
            <Link className={buttonClass("outline")} href="/reports/stock/value">
              Value by gem type
            </Link>
            <ExportButtons
              name="Stock report"
              onPdf={() => void download("pdf")}
              onExcel={() => void download("xlsx")}
            />
          </>
        }
      />
      <Card className="mb">
        <CardContent>
          <Filters>
            <Field label="Show" htmlFor="ks">
              <Select id="ks" value={show} onChange={(e) => setShow(e.target.value as "all" | "slow")}>
                <Options
                  items={[
                    { value: "all", label: "All stock" },
                    { value: "slow", label: "Slow-moving" },
                  ]}
                />
              </Select>
            </Field>
            <Field label="Gem type" htmlFor="kg">
              <Select id="kg" value={gemType} onChange={(e) => setGemType(e.target.value)}>
                <Options placeholder="All" items={r?.gemTypes ?? []} />
              </Select>
            </Field>
            {slow && (
              <Field label="Not sold for more than (days)" htmlFor="kd">
                <Input
                  id="kd"
                  type="number"
                  min={1}
                  max={3650}
                  value={days}
                  onChange={(e) => setDays(e.target.value)}
                />
              </Field>
            )}
          </Filters>
        </CardContent>
      </Card>
      {!r ? (
        <p className="muted">Loading…</p>
      ) : (
        <>
          <div className="g4 mb grid">
            <Kpi label="Items" value={r.items} />
            <Kpi label="Value at cost" value={cur(r.cost)} />
            <Kpi label="Value at selling price" value={cur(r.price)} />
            <Kpi label="Average age" value={`${r.averageDays} days`} sub={slow ? "consider a promotion" : undefined} />
          </div>
          {slow && r.items > 0 && (
            <Alert tone="warning" className="mb">
              {r.items} item(s) have not sold for more than {r.slowDays} days.{" "}
              <Link href="/people/offers/email">Create a promotion →</Link>
            </Alert>
          )}
          <Card>
            <DataTable<StockReportRow>
              id="rT"
              rows={r.rows}
              rowKey={(g) => g.code}
              stackOnPhone
              empty="No items."
              columns={[
                {
                  key: "p",
                  header: "Photo / product",
                  cell: (g) => (
                    <div className="row" style={{ gap: 8, flexWrap: "nowrap" }}>
                      <GemPicture
                        gem={g.gemType}
                        type={g.productType}
                        photoUrl={g.photo}
                        size={30}
                        box={36}
                        alt={g.name}
                      />
                      <span>
                        <b>{g.name}</b>
                        <br />
                        <Link className="mono" style={{ fontSize: 11 }} href={`/stock/gems/${g.code}`}>
                          {g.code}
                        </Link>
                      </span>
                    </div>
                  ),
                },
                {
                  key: "g",
                  header: "Gem / type",
                  cell: (g) => (
                    <>
                      {g.gemType}
                      <br />
                      <small className="muted">
                        {g.productType} · {carat(g.carat)}
                      </small>
                    </>
                  ),
                },
                { key: "b", header: "Branch", cell: (g) => branchName(g.branch) },
                { key: "s", header: "Status", cell: (g) => <StatusPill status={g.status} /> },
                { key: "a", header: "In stock since", cell: (g) => date(g.addedAt) },
                {
                  key: "d",
                  header: "Days",
                  num: true,
                  cell: (g) => <b className={g.days > 60 ? "text-warning" : undefined}>{g.days}</b>,
                },
                { key: "c", header: "Cost", num: true, cell: (g) => cur(g.cost) },
                { key: "pr", header: "Selling", num: true, cell: (g) => cur(g.price) },
              ]}
              footer={[`${r.items} items`, "", "", "", "", "", cur(r.cost), cur(r.price)]}
            />
          </Card>
        </>
      )}
    </>
  );
}
