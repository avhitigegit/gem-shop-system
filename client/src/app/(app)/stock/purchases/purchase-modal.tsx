"use client";

/* One purchase in a window – the supplier's bill photo and the gems bought (prototype "Supplier bill" modal). */
import Link from "next/link";
import { StatusPill } from "@/components/ui/badge";
import { Modal } from "@/components/ui/dialog";
import { Alert } from "@/components/ui/form";
import { GemSvg } from "@/components/ui/gem-picture";
import { api } from "@/lib/api/client";
import { carat, date, lkr } from "@/lib/format";
import { useLoad } from "@/lib/use-load";

export function PurchaseModal({ number, onClose }: { number: string | null; onClose: () => void }) {
  return (
    <Modal open={!!number} onOpenChange={(o) => !o && onClose()} title={`Purchase ${number ?? ""}`} size="lg">
      {number ? <PurchaseBody number={number} /> : null}
    </Modal>
  );
}

function PurchaseBody({ number }: { number: string }) {
  const { data: p, error } = useLoad(
    () => api.GET("/api/purchases/{number}", { params: { path: { number } } }),
    [number],
  );
  if (error) return <Alert tone="danger">This purchase could not be opened.</Alert>;
  if (!p) return <p className="muted">Loading…</p>;
  const image = p.bill?.contentType.startsWith("image/");
  return (
    <div className="stack">
      <p className="muted" style={{ margin: 0 }}>
        {date(p.date)} · <Link href={`/stock/suppliers/${p.supplierCode}`}>{p.supplierName}</Link> · {p.branchName} ·
        paid in cash · recorded by {p.recordedBy}
      </p>
      {p.bill ? (
        image ? (
          <a href={p.bill.url} target="_blank" rel="noreferrer">
            {/* eslint-disable-next-line @next/next/no-img-element -- bill photo from the file store */}
            <img src={p.bill.url} alt={`Bill ${p.number}`} className="bill-photo" />
          </a>
        ) : (
          <a href={p.bill.url} target="_blank" rel="noreferrer">
            📎 Open the bill ({p.bill.name})
          </a>
        )
      ) : (
        <Alert tone="info">No bill photo for this purchase (sample data).</Alert>
      )}
      <ul className="list">
        {p.gems.map((g) => (
          <li key={g.code}>
            <Link
              href={`/stock/gems/${g.code}`}
              className="row"
              style={{ flexWrap: "nowrap", flex: 1, color: "inherit" }}
            >
              <GemSvg gem={g.gemType} type={g.productType} size={28} />
              <span style={{ flex: 1 }}>
                <b>{g.name}</b>
                <br />
                <small className="muted mono">
                  {g.code} · {carat(g.carat)}
                </small>
              </span>
            </Link>
            <span className="right">
              <b>{lkr(g.cost)}</b>
              <br />
              <StatusPill status={g.status} />
            </span>
          </li>
        ))}
      </ul>
      <div className="row between">
        <span>Total paid in cash</span>
        <b style={{ fontSize: 18 }}>{lkr(p.total)}</b>
      </div>
      {p.notes ? <p className="muted">Notes: {p.notes}</p> : null}
    </div>
  );
}
