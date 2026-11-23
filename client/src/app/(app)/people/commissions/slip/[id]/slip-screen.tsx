"use client";

/* Commission slip – prototype h-commissions/commission-slip.html (Guide 41; BRD H4). A payment (CP-…) or a guide's
   statement of the last 30 days (G-…), to print and sign. */
import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import { downloadFile } from "@/lib/files";
import { date, lkr, num } from "@/lib/format";
import { useLoad } from "@/lib/use-load";

const STATUS_TEXT: Record<string, string> = { UNPAID: "Not yet paid", PAID: "Paid", REVERSED: "Taken back" };

export function SlipScreen({ id }: { id: string }) {
  const toast = useToast();
  const { data: s, error } = useLoad(() => api.GET("/api/commissions/slips/{id}", { params: { path: { id } } }), [id]);
  const [busy, setBusy] = useState(false);

  if (error && !s)
    return (
      <Card>
        <div className="empty">
          {errorMessage(error)} <Link href="/people/commissions/pay">Pay guides &amp; drivers</Link>
        </div>
      </Card>
    );
  if (!s) return <p className="muted">Loading…</p>;
  const shop = s.shop;

  const pdf = async () => {
    setBusy(true);
    const ok = await downloadFile(
      `/api/commissions/slips/${encodeURIComponent(id)}/pdf?download=true`,
      `Commission slip ${id}.pdf`,
    );
    setBusy(false);
    if (!ok) toast("Not downloaded", "Please try again.", "error");
  };

  return (
    <>
      <div className="doc-toolbar no-print">
        <Button variant="outline" disabled={busy} onClick={() => void pdf()}>
          ⇩ Download PDF
        </Button>
        <Button variant="sapphire" onClick={() => window.print()}>
          🖨 Print
        </Button>
      </div>
      <div className="doc" id="slipDoc" style={{ maxWidth: 640 }}>
        <div className="doc-head">
          <div className="logo">
            {/* eslint-disable-next-line @next/next/no-img-element -- the shop logo */}
            <img src={shop.logoUrl ?? "/logo.svg"} width={54} height={54} alt="" style={{ objectFit: "contain" }} />
            <div>
              <h1 style={{ color: "var(--navy)" }}>{shop.name}</h1>
              <div style={{ fontSize: 12, color: "#555" }}>
                {[shop.tagline, shop.address, [shop.phone, shop.email].filter(Boolean).join(" · ")]
                  .filter(Boolean)
                  .map((l) => (
                    <div key={l}>{l}</div>
                  ))}
              </div>
            </div>
          </div>
          <div style={{ textAlign: "right" }}>
            <h1 style={{ color: "var(--sapphire)", fontSize: 20, letterSpacing: ".04em" }}>COMMISSION SLIP</h1>
            <div style={{ fontSize: 12, marginTop: 6 }}>
              <b>Slip:</b> <span className="mono">{s.number ?? "STATEMENT"}</span>
              <br />
              <b>Date:</b> {date(s.date)}
              {s.method ? (
                <>
                  <br />
                  <b>Paid by:</b> {s.method}
                </>
              ) : null}
            </div>
          </div>
        </div>
        <p>
          <b>Paid to:</b> {s.payee}
          {s.guideVehicle
            ? ` · ${s.guideVehicle} ${s.guideVehicleNo ?? ""} · ${s.guidePhone ?? ""} · ${num(s.guidePct ?? 0, 0)}%`
            : ""}
        </p>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Invoice</th>
                <th style={{ textAlign: "right" }}>Sale amount</th>
                <th style={{ textAlign: "right" }}>Commission</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {s.lines.map((l, i) => (
                <tr key={i}>
                  <td>{date(l.date)}</td>
                  <td className="mono">{l.sale}</td>
                  <td className="right">{lkr(l.saleAmount)}</td>
                  <td className="right">{lkr(l.amount)}</td>
                  <td>{STATUS_TEXT[l.status] ?? l.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <table className="totals">
          <tbody>
            <tr className="grand">
              <td>Total</td>
              <td className="right" id="slipTotal">
                {lkr(s.total)}
              </td>
            </tr>
          </tbody>
        </table>
        <div className="row between" style={{ marginTop: 50 }}>
          <div style={{ borderTop: "1px solid #333", width: 200, paddingTop: 4, fontSize: 11, textAlign: "center" }}>
            Received by: {s.payee}
          </div>
          <div style={{ borderTop: "1px solid #333", width: 200, paddingTop: 4, fontSize: 11, textAlign: "center" }}>
            Paid by: {s.paidBy}
          </div>
        </div>
        <div className="foot">
          Commission is calculated per sale (rule 5). Keep this slip for the daily cash closing and the accountant.
        </div>
      </div>
    </>
  );
}
