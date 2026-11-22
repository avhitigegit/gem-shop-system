"use client";

/* Invoice – prototype g-payments/invoice.html (Guide 28; BRD G3–G6; rule 4). Print, download the PDF, send by email /
   WhatsApp, or ask the owner to cancel. No VAT line and no card fee line: the customer pays the same price. */
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button, buttonClass } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/form";
import { GemPicture } from "@/components/ui/gem-picture";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import { downloadFile } from "@/lib/files";
import { carat, date, dateTime, lkr, num } from "@/lib/format";
import { ACCOUNTANT } from "@/lib/roles";
import { payLabel } from "@/lib/sales";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";
import { SendModal } from "../send-modal";
import { useCancelInvoice } from "../use-cancel";

export function InvoiceScreen({ number }: { number: string }) {
  const toast = useToast();
  const params = useSearchParams();
  const { role, branchName } = useSession();
  const canAct = role !== ACCOUNTANT;
  const cancelInvoice = useCancelInvoice();
  const {
    data: s,
    error,
    reload,
  } = useLoad(() => api.GET("/api/sales/{number}", { params: { path: { number } } }), [number]);
  const [sending, setSending] = useState(false);
  const [busy, setBusy] = useState(false);
  const printed = useRef(false);

  const waiting = s?.cancel?.status === "PENDING" && s.status === "COMPLETED";
  // while the owner decides a cancellation, look again every 5 seconds
  useEffect(() => {
    if (!waiting) return;
    const t = setInterval(() => reload(), 5000);
    return () => clearInterval(t);
  }, [waiting, reload]);

  // opened with ?print=1 (Print on the Invoices list)
  useEffect(() => {
    if (!s || printed.current || !params.get("print")) return;
    printed.current = true;
    setTimeout(() => window.print(), 500);
  }, [s, params]);

  if (error && !s)
    return (
      <Card>
        <div className="empty">
          {errorMessage(error)} <Link href="/sell/invoices">All invoices</Link>
        </div>
      </Card>
    );
  if (!s) return <p className="muted">Loading…</p>;

  const cancelled = s.status === "CANCELLED";
  const shop = s.shop;
  const pdf = async () => {
    setBusy(true);
    const ok = await downloadFile(
      `/api/sales/${encodeURIComponent(s.number)}/pdf?download=true`,
      `Invoice ${s.number}.pdf`,
    );
    setBusy(false);
    if (!ok) toast("Not downloaded", "Please try again.", "error");
  };
  const ask = async () => {
    if (await cancelInvoice(s.number)) reload();
  };
  const approx = (rate: number | null | undefined) => (rate ? num(Number(s.total) / Number(rate)) : null);

  return (
    <>
      <div className="doc-toolbar no-print">
        <div style={{ flex: 1 }} />
        {canAct && !cancelled ? (
          <Button variant="outline" id="sendBtn" onClick={() => setSending(true)}>
            ✉ Send invoice (email / WhatsApp)
          </Button>
        ) : null}
        <Button variant="outline" disabled={busy} onClick={() => void pdf()}>
          ⇩ Download PDF
        </Button>
        {canAct && !cancelled && s.items.some((i) => !i.released) ? (
          <Link className={buttonClass("outline")} href={`/sell/returns/new?inv=${s.number}`}>
            ↩️ Customer brings it back
          </Link>
        ) : null}
        {canAct && !cancelled && !waiting ? (
          <Button variant="outline" id="cancelBtn" style={{ color: "var(--danger)" }} onClick={() => void ask()}>
            Request cancellation
          </Button>
        ) : null}
        <Button variant="sapphire" onClick={() => window.print()}>
          🖨 Print
        </Button>
      </div>
      <div className="doc-toolbar no-print" style={{ display: "block" }}>
        <Alert tone="info">
          Every sale gets an invoice with a running number. Completed invoices cannot be edited or deleted – only
          cancelled with the owner&apos;s approval.
        </Alert>
      </div>
      {waiting ? (
        <div className="doc-toolbar no-print" style={{ display: "block" }}>
          <Alert tone="warning" className="pending-pulse">
            Cancellation requested ({s.cancel?.number}) by {s.cancel?.requestedBy} – waiting for the owner&apos;s
            approval. Reason: “{s.cancel?.reason}”
          </Alert>
        </div>
      ) : null}
      {s.cancel?.status === "REJECTED" && !cancelled ? (
        <div className="doc-toolbar no-print" style={{ display: "block" }}>
          <Alert tone="danger">
            The owner rejected the cancellation ({s.cancel.number}){s.cancel.note ? `: “${s.cancel.note}”` : "."}
          </Alert>
        </div>
      ) : null}

      <div className={cancelled ? "doc watermark" : "doc"} data-wm="CANCELLED" id="invoiceDoc">
        <div className="doc-head">
          <div className="logo">
            {/* eslint-disable-next-line @next/next/no-img-element -- the shop logo */}
            <img src={shop.logoUrl ?? "/logo.svg"} width={54} height={54} alt="" style={{ objectFit: "contain" }} />
            <div>
              <h1 style={{ color: "var(--navy)" }}>{shop.name}</h1>
              <div style={{ fontSize: 12, color: "#555" }}>
                {shop.tagline ? (
                  <>
                    {shop.tagline}
                    <br />
                  </>
                ) : null}
                {shop.address ? (
                  <>
                    {shop.address}
                    <br />
                  </>
                ) : null}
                {[shop.phone, shop.email].filter(Boolean).join(" · ")}
                {shop.licenceNo ? (
                  <>
                    <br />
                    <b>NGJA Licence:</b> {shop.licenceNo}
                  </>
                ) : null}
              </div>
            </div>
          </div>
          <div style={{ textAlign: "right" }}>
            <h1 style={{ color: "var(--sapphire)", fontSize: 20, letterSpacing: ".04em" }}>INVOICE</h1>
            <div style={{ fontSize: 12, marginTop: 6 }}>
              <b>Invoice No:</b> <span className="mono">{s.number}</span>
              <br />
              <b>Date:</b> {dateTime(s.soldAt)}
              <br />
              <b>Branch:</b> {branchName(s.branch)}
              <br />
              <b>Served by:</b> {s.staff}
            </div>
          </div>
        </div>

        <div className="g2 grid" style={{ marginBottom: 14 }}>
          <div>
            <div className="muted" style={{ fontSize: 11, textTransform: "uppercase" }}>
              Bill to
            </div>
            <b>{s.customer.name}</b>
            <br />
            {s.customer.country}
            {s.customer.email ? (
              <>
                <br />
                {s.customer.email}
              </>
            ) : null}
            {s.customer.whatsapp ? (
              <>
                <br />
                {s.customer.whatsapp}
              </>
            ) : null}
          </div>
          <div style={{ textAlign: "right", fontSize: 12 }}>
            <div className="muted" style={{ fontSize: 11, textTransform: "uppercase" }}>
              Currency
            </div>
            Amounts in Sri Lankan Rupees (LKR)
            {s.rateUsd || s.rateEur ? (
              <>
                <br />
                {[s.rateUsd ? `USD ${num(s.rateUsd)}` : "", s.rateEur ? `EUR ${num(s.rateEur)}` : ""]
                  .filter(Boolean)
                  .join(" · ")}{" "}
                rate of the day
              </>
            ) : null}
            {cancelled ? (
              <>
                <br />
                <Badge tone="danger">
                  Cancelled {date(s.cancelledAt)}: {s.cancelReason}
                </Badge>
              </>
            ) : null}
          </div>
        </div>

        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>Photo</th>
                <th>Gem details</th>
                <th>Certificate</th>
                <th style={{ textAlign: "right" }}>Amount</th>
              </tr>
            </thead>
            <tbody>
              {s.items.map((i, n) => (
                <tr key={i.code}>
                  <td>{n + 1}</td>
                  <td style={{ width: 70 }}>
                    <GemPicture gem={i.gemType} type={i.productType} photoUrl={i.photo} size={56} box={60} />
                  </td>
                  <td>
                    <b>{i.name}</b>
                    {i.released && !cancelled ? (
                      <>
                        {" "}
                        <Badge tone="blue">Returned – see credit note</Badge>
                      </>
                    ) : null}
                    <br />
                    <span className="mono" style={{ fontSize: 11 }}>
                      {i.code}
                    </span>
                    <br />
                    <span style={{ fontSize: 12 }}>
                      {i.gemType} · {i.productType} · {carat(i.carat)} · {i.colour} · {i.shape}
                      <br />
                      Treatment: <b>{i.treatment}</b>
                    </span>
                  </td>
                  <td style={{ fontSize: 12 }}>
                    {i.certType === "LAB" ? (
                      <>
                        Lab certificate
                        <br />
                        <b>{i.certLab}</b>
                        {i.certNo ? (
                          <>
                            <br />
                            No. <span className="mono">{i.certNo}</span>
                          </>
                        ) : null}
                      </>
                    ) : i.certType === "SHOP" ? (
                      <>
                        Shop certificate
                        <br />
                        No. <span className="mono">{i.certNo}</span>
                      </>
                    ) : (
                      "No certificate"
                    )}
                  </td>
                  <td className="right">
                    <b>{lkr(i.agreed)}</b>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <table className="totals">
          <tbody>
            <tr className="grand">
              <td>TOTAL</td>
              <td className="right" id="invTotal">
                {lkr(s.total)}
              </td>
            </tr>
            {approx(s.rateUsd) || approx(s.rateEur) ? (
              <tr>
                <td colSpan={2} className="right" style={{ fontSize: 11, color: "#555" }}>
                  ≈{" "}
                  {[approx(s.rateUsd) && `USD ${approx(s.rateUsd)}`, approx(s.rateEur) && `EUR ${approx(s.rateEur)}`]
                    .filter(Boolean)
                    .join(" · ")}
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>

        <div className="g2 mt grid">
          <div>
            <b style={{ fontSize: 12 }}>Payment received</b>
            <table style={{ fontSize: 12 }}>
              <tbody>
                {s.payments.map((p, k) => (
                  <tr key={k}>
                    <td>{payLabel(p)}</td>
                    <td className="right">
                      {p.currency !== "LKR" ? `${p.currency} ${num(p.amount)} @ ${num(p.rate)} = ` : ""}
                      {lkr(p.lkr)}
                    </td>
                  </tr>
                ))}
                {Number(s.change) > 0 ? (
                  <tr>
                    <td>Change given</td>
                    <td className="right">{lkr(s.change)}</td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
          <div style={{ fontSize: 12 }}>
            {s.guide ? (
              <>
                <b>Introduced by:</b> {s.guide.name}
                {s.guide.vehicle ? ` (${s.guide.vehicle})` : ""}
                <br />
              </>
            ) : null}
            <b>Language:</b> English
            <br />
            Goods sold are genuine natural gemstones as described. Treatment is disclosed above.
          </div>
        </div>
        <div className="foot">
          This is a computer-generated invoice. Returns accepted within {shop.returnDays} days with this invoice,
          subject to owner approval.
          {shop.licenceNo
            ? ` Licensed gem dealer – National Gem & Jewellery Authority, licence ${shop.licenceNo}${
                shop.licenceExpiry ? ` (valid until ${date(shop.licenceExpiry)})` : ""
              }.`
            : ""}{" "}
          Thank you for shopping at {shop.name}, {branchName(s.branch)}!
        </div>
      </div>
      <div className="doc-toolbar no-print mt">
        <span className="muted" style={{ fontSize: 12 }} id="sentLine">
          Sent: {s.sentEmail ? "✉ Email ✓" : "✉ Email –"} · {s.sentWhatsapp ? "WhatsApp ✓" : "WhatsApp –"}
        </span>
      </div>

      {sending ? (
        <SendModal
          sale={s}
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
