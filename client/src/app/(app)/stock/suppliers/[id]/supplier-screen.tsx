"use client";

/* One supplier – prototype b-purchasing/supplier-detail.html (BRD B1, B3): details, totals, purchase history with the
   gems bought and the bill photo, and gems returned. */
import Link from "next/link";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button, buttonClass } from "@/components/ui/button";
import { Card, CardContent, CardHeader, Kpi } from "@/components/ui/card";
import { DataTable, type Column } from "@/components/ui/data-table";
import { Alert } from "@/components/ui/form";
import { GemSvg } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { api } from "@/lib/api/client";
import { date, inCurrency } from "@/lib/format";
import { refundLabel, type PurchaseRow, type ReturnRow } from "@/lib/purchasing";
import { OWNER } from "@/lib/roles";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";
import { PurchaseModal } from "../../purchases/purchase-modal";
import { SupplierFormModal } from "../suppliers-screen";

export function SupplierScreen({ code }: { code: string }) {
  const { role, branch, currency, rates } = useSession();
  const owner = role === OWNER;
  const {
    data: v,
    error,
    reload,
  } = useLoad(() => api.GET("/api/suppliers/{code}", { params: { path: { code } } }), [code, branch]);
  const [editing, setEditing] = useState(false);
  const [open, setOpen] = useState<string | null>(null);
  const money = (n: number | string) => inCurrency(n, currency, rates);

  if (error) return <Alert tone="danger">Supplier {code} not found.</Alert>;
  if (!v) return <p className="muted">Loading…</p>;
  const s = v.supplier;

  const purchases: Column<PurchaseRow>[] = [
    {
      key: "no",
      header: "Purchase",
      cell: (p) => (
        <>
          <b className="mono nowrap">{p.number}</b>
          <br />
          <small className="muted">{date(p.date)}</small>
        </>
      ),
    },
    {
      key: "gems",
      header: "Gems",
      cell: (p) => (
        <span className="row" style={{ gap: 6 }}>
          {p.gems.map((g) => (
            <Link key={g.code} href={`/stock/gems/${g.code}`} className="row" style={{ gap: 3, color: "inherit" }}>
              <GemSvg gem={g.gemType} type={g.productType} size={20} />
              <span className="mono" style={{ fontSize: 11 }}>
                {g.code}
              </span>
              {g.status === "SOLD" ? (
                <Badge tone="navy" className="xs">
                  sold
                </Badge>
              ) : null}
            </Link>
          ))}
        </span>
      ),
    },
    {
      key: "bill",
      header: "Bill photo",
      cell: (p) => (
        <Button variant="ghost" size="sm" onClick={() => setOpen(p.number)}>
          {p.hasBill ? "📎 View" : "View"}
        </Button>
      ),
    },
    { key: "total", header: "Total (cash)", num: true, cell: (p) => <b>{money(p.total)}</b> },
  ];

  const returns: Column<ReturnRow>[] = [
    {
      key: "no",
      header: "No.",
      cell: (r) => (
        <>
          <span className="mono">{r.number}</span>
          <br />
          <small className="muted">{date(r.returnedAt)}</small>
        </>
      ),
    },
    {
      key: "gem",
      header: "Gem",
      cell: (r) => (
        <Link href={`/stock/gems/${r.gemCode}`}>
          {r.gemName} <small className="mono muted">{r.gemCode}</small>
        </Link>
      ),
    },
    { key: "reason", header: "Reason", cell: (r) => r.reason },
    {
      key: "refund",
      header: "Refund",
      num: true,
      cell: (r) => (
        <>
          {money(r.refund)}
          <br />
          <small className="muted">{refundLabel(r.refundMethod)}</small>
        </>
      ),
    },
  ];

  return (
    <>
      <PageHead
        title={s.name}
        sub={`${s.address} · ${s.phone}`}
        actions={
          owner ? (
            <>
              <Link className={buttonClass("sapphire")} href={`/stock/purchases/new?supplier=${s.code}`}>
                + Buy gems
              </Link>
              <Link className={buttonClass("outline")} href={`/stock/supplier-returns?supplier=${s.code}`}>
                Return to supplier
              </Link>
            </>
          ) : undefined
        }
      />
      <div className="g4 mb grid">
        <Kpi label="Purchases" value={s.purchases} sub="cash" />
        <Kpi label="Total paid" value={money(s.totalBought)} />
        <Kpi label="Gems bought" value={v.gemsBought} sub={`${v.gemsSold} sold so far`} />
        <Kpi label="Returned" value={v.returns} sub={`${money(v.refunded)} refunded`} />
      </div>
      <div className="g-1-2 grid">
        <Card>
          <CardHeader
            title="Details"
            level={3}
            actions={
              owner ? (
                <Button variant="ghost" size="sm" onClick={() => setEditing(true)}>
                  Edit
                </Button>
              ) : undefined
            }
          />
          <CardContent>
            <dl className="dl" style={{ gridTemplateColumns: "120px 1fr" }}>
              <dt>Code</dt>
              <dd className="mono">{s.code}</dd>
              <dt>Contact</dt>
              <dd>{s.contact || "—"}</dd>
              <dt>Phone</dt>
              <dd>{s.phone}</dd>
              <dt>Address</dt>
              <dd>{s.address}</dd>
              <dt>{s.idType === "NIC" ? "NIC" : "Business reg."}</dt>
              <dd className="mono">{s.idNo}</dd>
              <dt>Notes</dt>
              <dd>{s.notes || "—"}</dd>
            </dl>
          </CardContent>
        </Card>
        <div className="stack">
          <Card>
            <CardHeader title="Purchase history" level={3} />
            <DataTable
              columns={purchases}
              rows={v.purchases}
              rowKey={(p) => p.number}
              stackOnPhone
              empty="Nothing bought from this supplier yet."
            />
          </Card>
          {v.returnList.length ? (
            <Card>
              <CardHeader title="Returned to this supplier" level={3} />
              <DataTable columns={returns} rows={v.returnList} rowKey={(r) => r.number} stackOnPhone />
            </Card>
          ) : null}
        </div>
      </div>
      <PurchaseModal number={open} onClose={() => setOpen(null)} />
      <SupplierFormModal
        key={editing ? "edit" : "closed"}
        supplier={editing ? s : null}
        onClose={() => setEditing(false)}
        onSaved={() => {
          setEditing(false);
          reload();
        }}
      />
    </>
  );
}
