"use client";

/* Purchases – prototype b-purchasing/purchase-history.html (BRD B3). Kept by supplier and by date; follows the branch
   in the top bar. Tap a purchase to see its gems and the bill photo. */
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { buttonClass } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { DataTable, ExportButtons, Filters, type Column } from "@/components/ui/data-table";
import { Field, Input, Options, Select } from "@/components/ui/form";
import { GemSvg } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { api } from "@/lib/api/client";
import { date, inCurrency, todayInShop } from "@/lib/format";
import type { PurchaseRow } from "@/lib/purchasing";
import { OWNER } from "@/lib/roles";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";
import { PurchaseModal } from "./purchase-modal";

/** "YYYY-MM-DD" a number of days before a "YYYY-MM-DD" day. */
const daysBefore = (day: string, n: number) => {
  const d = new Date(`${day}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() - n);
  return d.toISOString().slice(0, 10);
};

export function PurchasesScreen() {
  const params = useSearchParams();
  const { role, branch, branchName, currency, rates } = useSession();
  const today = todayInShop();
  const [f, setF] = useState({ supplier: params.get("supplier") ?? "", from: daysBefore(today, 180), to: today });
  const [open, setOpen] = useState<string | null>(null);
  const { data: suppliers } = useLoad(() => api.GET("/api/suppliers"), []);
  const { data } = useLoad(
    () =>
      api.GET("/api/purchases", {
        params: { query: { supplier: f.supplier || undefined, from: f.from || undefined, to: f.to || undefined } },
      }),
    [f.supplier, f.from, f.to, branch],
  );
  const money = (n: number | string) => inCurrency(n, currency, rates);

  const columns: Column<PurchaseRow>[] = [
    { key: "no", header: "Purchase", cell: (p) => <b className="mono nowrap">{p.number}</b> },
    { key: "date", header: "Date", cell: (p) => date(p.date) },
    {
      key: "supplier",
      header: "Supplier",
      cell: (p) => (
        <Link href={`/stock/suppliers/${p.supplierCode}`} onClick={(e) => e.stopPropagation()}>
          {p.supplierName}
        </Link>
      ),
    },
    { key: "branch", header: "Branch", cell: (p) => branchName(p.branch) },
    {
      key: "gems",
      header: "Gems",
      cell: (p) => (
        <span className="row" style={{ gap: 2, flexWrap: "nowrap" }}>
          <span style={{ marginRight: 4 }}>{p.gems.length} ·</span>
          {p.gems.slice(0, 4).map((g) => (
            <GemSvg key={g.code} gem={g.gemType} type={g.productType} size={20} />
          ))}
        </span>
      ),
    },
    { key: "bill", header: "Bill", cell: (p) => (p.hasBill ? "📎" : <span className="muted">—</span>) },
    { key: "total", header: "Total (cash)", num: true, cell: (p) => <b>{money(p.total)}</b> },
    {
      key: "month",
      header: "Month",
      cell: (p) =>
        p.monthClosed ? (
          <span title="Month closed – nothing dated in it can change">
            <Badge tone="navy">🔒 closed</Badge>
          </span>
        ) : null,
    },
  ];
  const rows = data?.items ?? [];
  const footer = [`${rows.length} purchases`, "", "", "", `${data?.gems ?? 0} gems`, "", money(data?.total ?? 0), ""];

  return (
    <>
      <PageHead
        title="Purchases"
        sub="Everything bought, by supplier and by date. Paid in cash."
        actions={
          <>
            {role === OWNER ? (
              <Link className={buttonClass("sapphire")} href="/stock/purchases/new">
                + Buy gems
              </Link>
            ) : null}
            <ExportButtons tableId="purchases" name="Purchases" />
          </>
        }
      />
      <Card>
        <CardContent>
          <Filters>
            <Field label="Supplier" htmlFor="sup">
              <Select id="sup" value={f.supplier} onChange={(e) => setF((o) => ({ ...o, supplier: e.target.value }))}>
                <Options
                  items={(suppliers ?? []).map((s) => ({ value: s.code, label: s.name }))}
                  placeholder="All suppliers"
                />
              </Select>
            </Field>
            <Field label="From" htmlFor="from">
              <Input
                id="from"
                type="date"
                value={f.from}
                max={f.to || today}
                onChange={(e) => setF((o) => ({ ...o, from: e.target.value }))}
              />
            </Field>
            <Field label="To" htmlFor="to">
              <Input
                id="to"
                type="date"
                value={f.to}
                max={today}
                onChange={(e) => setF((o) => ({ ...o, to: e.target.value }))}
              />
            </Field>
          </Filters>
        </CardContent>
        {data ? (
          <DataTable
            id="purchases"
            columns={columns}
            rows={rows}
            rowKey={(p) => p.number}
            footer={footer}
            stackOnPhone
            onRowClick={(p) => setOpen(p.number)}
            empty="No purchases in these dates."
          />
        ) : (
          <p className="muted card-content">Loading…</p>
        )}
      </Card>
      <PurchaseModal number={open} onClose={() => setOpen(null)} />
    </>
  );
}
