"use client";

/* Quotation print – prototype f-pos/quotation-print.html (Guide 33; BRD F7). Print, download the PDF or send it by
   email / WhatsApp. Prices in LKR with USD / EUR for information; same price for cash or card. */
import Link from "next/link";
import { useState } from "react";
import { Button, buttonClass } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { GemPicture } from "@/components/ui/gem-picture";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import { downloadFile } from "@/lib/files";
import { carat, date, lkr, num } from "@/lib/format";
import { useLoad } from "@/lib/use-load";
import { SendModal } from "../../../send-modal";
import { quoteStatus } from "../../reserved-screen";

export function QuotePrintScreen({ number }: { number: string }) {
  const toast = useToast();
  const {
    data: q,
    error,
    reload,
  } = useLoad(() => api.GET("/api/quotes/{number}", { params: { path: { number } } }), [number]);
  const [sending, setSending] = useState(false);
  const [busy, setBusy] = useState(false);

  if (error && !q)
    return (
      <Card>
        <div className="empty">
          {errorMessage(error)} <Link href="/sell/reserved?tab=quotation">Reserved &amp; quotes</Link>
        </div>
      </Card>
    );
  if (!q) return <p className="muted">Loading…</p>;

  const reserve = q.kind === "RESERVATION";
  const label = reserve ? "reservation" : "quotation";
  const shop = q.shop;
  const active = q.status === "ACTIVE";
  const wm =
    q.status === "CONVERTED" ? "SOLD" : q.status === "RELEASED" ? (reserve ? "RELEASED" : "WITHDRAWN") : "EXPIRED";
  const fx = (lkrAmount: number | string, rate?: number | null) => (rate ? num(Number(lkrAmount) / Number(rate)) : "–");

  const pdf = async () => {
    setBusy(true);
    const ok = await downloadFile(`/api/quotes/${encodeURIComponent(q.number)}/pdf?download=true`, `${q.number}.pdf`);
    setBusy(false);
    if (!ok) toast("Not downloaded", "Please try again.", "error");
  };

  return (
    <>
      <div className="doc-toolbar no-print">
        {active ? (
          <>
            <Button variant="outline" id="sendQ" onClick={() => setSending(true)}>
              ✉ Send {label}
            </Button>
            <Link className={buttonClass("success")} href={`/sell/new?quote=${q.number}`}>
              Sell now
            </Link>
          </>
        ) : null}
        <Button variant="outline" disabled={busy} onClick={() => void pdf()}>
          ⇩ Download PDF
        </Button>
        <Button variant="sapphire" onClick={() => window.print()}>
          🖨 Print
        </Button>
      </div>

      <div className={active ? "doc" : "doc watermark"} data-wm={wm} id="quoteDoc">
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
            <h1 style={{ color: "var(--sapphire)", fontSize: 20, letterSpacing: ".04em" }}>{label.toUpperCase()}</h1>
            <div style={{ fontSize: 12, marginTop: 6 }}>
              <b>No:</b> <span className="mono">{q.number}</span>
              <br />
              <b>Date:</b> {date(q.createdOn)}
              <br />
              <b>Valid until:</b> <span style={{ color: "var(--danger)", fontWeight: 700 }}>{date(q.expires)}</span>
              <br />
              <b>Prepared by:</b> {q.staff}
              <div className="no-print mt-s">{quoteStatus(q)}</div>
            </div>
          </div>
        </div>
        <p>
          <b>For:</b> {q.customer.name}, {q.customer.country}
          {q.customer.email ? ` · ${q.customer.email}` : ""}
        </p>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Photo</th>
                <th>Gem</th>
                <th>Gem details</th>
                <th style={{ textAlign: "right" }}>Price (LKR)</th>
                <th style={{ textAlign: "right" }}>USD</th>
                <th style={{ textAlign: "right" }}>EUR</th>
              </tr>
            </thead>
            <tbody>
              {q.items.map((i) => (
                <tr key={i.code}>
                  <td style={{ width: 70 }}>
                    <GemPicture gem={i.gemType} type={i.productType} photoUrl={i.photo} size={56} box={60} />
                  </td>
                  <td>
                    <b>{i.name}</b>
                    <br />
                    <span className="mono" style={{ fontSize: 11 }}>
                      {i.code}
                    </span>
                  </td>
                  <td style={{ fontSize: 12 }}>
                    {i.gemType} · {carat(i.carat)} · {i.colour} · {i.shape}
                    <br />
                    Treatment: {i.treatment} ·{" "}
                    {i.certType === "LAB"
                      ? `Lab cert. ${i.certLab ?? ""}`
                      : i.certType === "SHOP"
                        ? "Shop certificate"
                        : "No certificate"}
                  </td>
                  <td className="right">
                    <b>{num(i.agreed)}</b>
                  </td>
                  <td className="right">{fx(i.agreed, q.rateUsd)}</td>
                  <td className="right">{fx(i.agreed, q.rateEur)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <table className="totals">
          <tbody>
            <tr className="grand">
              <td>Total</td>
              <td className="right" id="quoteTotal">
                {lkr(q.total)}
              </td>
            </tr>
            <tr>
              <td colSpan={2} className="right" style={{ fontSize: 11 }}>
                ≈ USD {fx(q.total, q.rateUsd)} · EUR {fx(q.total, q.rateEur)} at today&apos;s rates
              </td>
            </tr>
            {reserve && Number(q.deposit) > 0 ? (
              <tr>
                <td colSpan={2} className="right" style={{ fontSize: 12 }}>
                  Deposit received: <b>{lkr(q.deposit)}</b>
                </td>
              </tr>
            ) : null}
            <tr>
              <td colSpan={2} className="right" style={{ fontSize: 11 }}>
                Same price for cash or card.
              </td>
            </tr>
          </tbody>
        </table>
        {q.notes ? (
          <p className="mt" style={{ fontSize: 12 }}>
            <b>Note:</b> {q.notes}
          </p>
        ) : null}
        <div className="foot">
          This {label} is valid until {date(q.expires)}. Prices are in LKR; USD / EUR amounts are for information and
          follow the rate on the day of payment.{" "}
          {reserve
            ? "The gems are held for you until the date above."
            : "Gems are unique and are not held unless reserved."}
        </div>
      </div>
      <div className="doc-toolbar no-print mt">
        <span className="muted" style={{ fontSize: 12 }} id="sentLine">
          Sent: {q.sentEmail ? "✉ Email ✓" : "✉ Email –"} · {q.sentWhatsapp ? "WhatsApp ✓" : "WhatsApp –"}
        </span>
      </div>

      {sending ? (
        <SendModal
          doc={{ label, number: q.number, branch: q.branch, customer: q.customer }}
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
