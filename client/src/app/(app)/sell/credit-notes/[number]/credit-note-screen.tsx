"use client";

/* Credit note print – prototype e-returns/credit-note.html?id= (Guide 36; BRD E2, G6; rules 4, 7). Against the
   original invoice, which is kept on record. Print, download the PDF or send it to the customer. */
import Link from "next/link";
import { useState } from "react";
import { Button, buttonClass } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { GemPicture } from "@/components/ui/gem-picture";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import { downloadFile } from "@/lib/files";
import { carat, date, dateTime, lkr } from "@/lib/format";
import { SALESPERSON, OWNER } from "@/lib/roles";
import { kindLabel } from "@/lib/sales";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";
import { SendModal } from "../../send-modal";

export function CreditNoteScreen({ number }: { number: string }) {
  const toast = useToast();
  const { role } = useSession();
  const canSend = role === OWNER || role === SALESPERSON;
  const {
    data: c,
    error,
    reload,
  } = useLoad(() => api.GET("/api/credit-notes/{number}", { params: { path: { number } } }), [number]);
  const [sending, setSending] = useState(false);
  const [busy, setBusy] = useState(false);

  if (error && !c)
    return (
      <Card>
        <div className="empty">
          {errorMessage(error)} <Link href="/sell/credit-notes">Credit notes</Link>
        </div>
      </Card>
    );
  if (!c) return <p className="muted">Loading…</p>;
  const shop = c.shop;
  const taken = [
    c.guide && Number(c.guideComm) > 0 ? `${c.guide} ${lkr(c.guideComm)}` : null,
    Number(c.staffComm) > 0 ? `${c.staff} ${lkr(c.staffComm)}` : null,
  ].filter(Boolean);

  const pdf = async () => {
    setBusy(true);
    const ok = await downloadFile(
      `/api/credit-notes/${encodeURIComponent(c.number)}/pdf?download=true`,
      `${c.number}.pdf`,
    );
    setBusy(false);
    if (!ok) toast("Not downloaded", "Please try again.", "error");
  };

  return (
    <>
      <div className="doc-toolbar no-print">
        {canSend ? (
          <Button variant="outline" id="sendCn" onClick={() => setSending(true)}>
            ✉ Send to customer
          </Button>
        ) : null}
        {c.kind === "EXCHANGE" && !c.usedSaleNo && canSend ? (
          <Link
            className={buttonClass("success")}
            href={`/sell/new?credit=${c.number}&customer=${encodeURIComponent(c.customer.code)}`}
          >
            💎 Use for the new sale
          </Link>
        ) : null}
        <Button variant="outline" disabled={busy} onClick={() => void pdf()}>
          ⇩ Download PDF
        </Button>
        <Button variant="sapphire" onClick={() => window.print()}>
          🖨 Print
        </Button>
      </div>

      <div className="doc" id="cnDoc">
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
                {shop.licenceNo ? (
                  <div>
                    <b>NGJA Licence:</b> {shop.licenceNo}
                  </div>
                ) : null}
              </div>
            </div>
          </div>
          <div style={{ textAlign: "right" }}>
            <h1 style={{ color: "var(--sapphire)", fontSize: 20, letterSpacing: ".04em" }}>CREDIT NOTE</h1>
            <div style={{ fontSize: 12, marginTop: 6 }}>
              <b>No:</b> <span className="mono">{c.number}</span>
              <br />
              <b>Date:</b> {dateTime(c.issuedAt)}
              <br />
              <b>Against invoice:</b>{" "}
              <Link className="mono" href={`/sell/invoices/${c.sale}`}>
                {c.sale}
              </Link>{" "}
              ({date(c.soldAt)})
            </div>
          </div>
        </div>
        <p>
          <b>Customer:</b> {c.customer.name}, {c.customer.country}
        </p>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Photo</th>
                <th>Returned gem</th>
                <th>Reason</th>
                <th style={{ textAlign: "right" }}>Credit</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td style={{ width: 70 }}>
                  <GemPicture gem={c.gem.gemType} type={c.gem.productType} photoUrl={c.gem.photo} size={56} box={60} />
                </td>
                <td>
                  <b>{c.gem.name}</b>
                  <br />
                  <span className="mono" style={{ fontSize: 11 }}>
                    {c.gem.code}
                  </span>
                  <br />
                  <span style={{ fontSize: 12 }}>
                    {carat(c.gem.carat)} · {c.gem.treatment}
                  </span>
                </td>
                <td style={{ fontSize: 12 }}>
                  {kindLabel(c.kind)}: {c.reason}
                </td>
                <td className="right">
                  <b>{lkr(c.amount)}</b>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <table className="totals">
          <tbody>
            <tr className="grand">
              <td>Total credit</td>
              <td className="right" id="cnTotal">
                {lkr(c.amount)}
              </td>
            </tr>
          </tbody>
        </table>
        <div className="mt" style={{ fontSize: 12 }}>
          <b>Approved by:</b> {c.approvedBy} (owner) · <b>Stock:</b> gem returned to stock ·{" "}
          <b>Commissions taken back:</b> {taken.join(", ") || "none"}
          {c.usedSaleNo ? (
            <>
              <br />
              <b>Used for the exchange:</b>{" "}
              <Link className="mono" href={`/sell/invoices/${c.usedSaleNo}`}>
                {c.usedSaleNo}
              </Link>{" "}
              ({lkr(c.usedAmount ?? 0)})
            </>
          ) : null}
        </div>
        <div className="foot">
          This credit note is against the original invoice {c.sale}. The original invoice is kept on record. The amount
          is the price paid for the gem; a card fee is not part of it.
        </div>
      </div>
      <div className="doc-toolbar no-print mt">
        <span className="muted" style={{ fontSize: 12 }} id="sentLine">
          Sent: {c.sentEmail ? "✉ Email ✓" : "✉ Email –"} · {c.sentWhatsapp ? "WhatsApp ✓" : "WhatsApp –"}
        </span>
      </div>

      {sending ? (
        <SendModal
          doc={{ label: "credit note", number: c.number, branch: c.branch, customer: c.customer }}
          onClose={() => setSending(false)}
          onSent={() => {
            setSending(false);
            reload();
          }}
        />
      ) : null}
    </>
  );
}
