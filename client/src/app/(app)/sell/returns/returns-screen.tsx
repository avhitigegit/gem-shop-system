"use client";

/* Returns & exchanges – prototype e-returns/returns.html (Guide 35; BRD E1, E2; rules 3, 7). A return or exchange
   needs a reason and owner approval. The gem goes back into stock, a credit note is made and the commissions for it
   are taken back. */
import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { buttonClass } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { DataTable, type Column } from "@/components/ui/data-table";
import { Alert } from "@/components/ui/form";
import { GemSvg } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { api } from "@/lib/api/client";
import { dateTime, lkr } from "@/lib/format";
import type { ReturnView } from "@/lib/sales";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function returnStatus(r: Pick<ReturnView, "status">) {
  return r.status === "PENDING" ? (
    <Badge tone="warning">Waiting for owner</Badge>
  ) : r.status === "APPROVED" ? (
    <Badge tone="success">Approved</Badge>
  ) : (
    <Badge tone="danger">Rejected</Badge>
  );
}

export function ReturnsScreen() {
  const { branch } = useSession();
  const { data } = useLoad(() => api.GET("/api/returns"), [branch]);

  const columns: Column<ReturnView>[] = [
    {
      key: "no",
      header: "No.",
      cell: (r) => (
        <>
          <span className="mono">{r.number}</span>
          <br />
          <small className="muted">{dateTime(r.requestedAt)}</small>
        </>
      ),
    },
    {
      key: "gem",
      header: "Gem",
      cell: (r) => (
        <div className="row" style={{ gap: 6, flexWrap: "nowrap" }}>
          <GemSvg gem={r.gem.gemType} type={r.gem.productType} size={28} />
          <span>
            <b>{r.gem.name}</b>
            <br />
            <small className="mono muted">{r.gem.code}</small>
          </span>
        </div>
      ),
    },
    {
      key: "inv",
      header: "Invoice",
      cell: (r) => (
        <>
          <Link className="mono" href={`/sell/invoices/${r.sale}`}>
            {r.sale}
          </Link>
          <br />
          <small className="muted">{r.customer.name}</small>
        </>
      ),
    },
    {
      key: "kind",
      header: "Type",
      cell: (r) => (r.kind === "EXCHANGE" ? <Badge tone="blue">Exchange</Badge> : <Badge tone="outline">Refund</Badge>),
    },
    { key: "reason", header: "Reason", cell: (r) => r.reason },
    { key: "amount", header: "Amount", num: true, cell: (r) => lkr(r.amount) },
    {
      key: "status",
      header: "Status",
      cell: (r) => <Link href={`/sell/returns/new?id=${r.number}`}>{returnStatus(r)}</Link>,
    },
    {
      key: "cn",
      header: "Credit note",
      cell: (r) =>
        r.creditNote ? (
          <Link className={buttonClass("outline", "sm")} href={`/sell/credit-notes/${r.creditNote}`}>
            {r.creditNote}
          </Link>
        ) : (
          "—"
        ),
    },
  ];

  return (
    <>
      <PageHead
        title="Returns & exchanges"
        sub="A return or exchange needs a reason and owner approval. The gem goes back into stock, a credit note is made and the commissions for that gem are taken back."
        actions={
          <>
            <Link className={buttonClass("sapphire")} href="/sell/returns/new">
              ↩️ Customer brings a gem back
            </Link>
            <Link className={buttonClass("outline")} href="/sell/credit-notes">
              Credit notes
            </Link>
          </>
        }
      />
      <Alert tone="info" className="mb">
        Returns need owner approval. Returned gems can be sold again.
      </Alert>
      <Card>
        {!data ? (
          <p className="muted" style={{ padding: 16 }}>
            Loading…
          </p>
        ) : (
          <DataTable
            id="returnTable"
            columns={columns}
            rows={data}
            rowKey={(r) => r.number}
            stackOnPhone
            empty="No returns yet."
          />
        )}
      </Card>
    </>
  );
}
