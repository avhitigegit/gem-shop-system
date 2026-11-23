"use client";

/* Take payment – prototype app.js paymentDialog (BRD G1, G2). Cash in LKR / USD / EUR, card, or a mix: always the same
   price. USD / EUR count at today's rate; change is given from cash; a card is never charged more than is due. */
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import { Alert, Input, Select } from "@/components/ui/form";
import { lkr, num } from "@/lib/format";
import type { PaymentForm } from "@/lib/sales";
import { useSession } from "@/lib/session";

type Line = { kind: "CASH-LKR" | "CASH-USD" | "CASH-EUR" | "CARD-LKR"; amount: number };

const KINDS: { value: Line["kind"]; label: string }[] = [
  { value: "CASH-LKR", label: "Cash · LKR" },
  { value: "CASH-USD", label: "Cash · USD" },
  { value: "CASH-EUR", label: "Cash · EUR" },
  { value: "CARD-LKR", label: "Card" },
];

const r2 = (n: number) => Math.round(n * 100) / 100;

export function PaymentModal({
  total: bill,
  credit,
  busy,
  onClose,
  onPay,
}: {
  total: number;
  /** Exchange (P3-04): the credit note pays this much of the bill first. */
  credit?: { number: string; amount: number } | null;
  busy: boolean;
  onClose: () => void;
  onPay: (payments: PaymentForm[]) => void;
}) {
  const { rates } = useSession();
  const used = credit ? Math.min(credit.amount, bill) : 0;
  const giveBack = credit ? r2(Math.max(0, credit.amount - bill)) : 0;
  const total = r2(bill - used);
  const [lines, setLines] = useState<Line[]>([]);
  const rate = (k: Line["kind"]) => (k === "CASH-USD" ? rates.USD : k === "CASH-EUR" ? rates.EUR : 1);
  const toLkr = (l: Line) => r2((l.amount || 0) * rate(l.kind));
  const usdFull = Math.ceil(total / rates.USD);
  const eurFull = Math.ceil(total / rates.EUR);

  const paid = r2(lines.reduce((s, l) => s + toLkr(l), 0));
  const cash = r2(lines.filter((l) => l.kind !== "CARD-LKR").reduce((s, l) => s + toLkr(l), 0));
  const remaining = r2(Math.max(0, total - paid));
  const change = r2(Math.max(0, paid - total));
  const cardOver = change > 0 && change > cash + 0.001;

  const quick = (q: "card" | "LKR" | "USD" | "EUR" | "split") => {
    if (q === "card") setLines([{ kind: "CARD-LKR", amount: total }]);
    else if (q === "LKR") setLines([{ kind: "CASH-LKR", amount: Math.ceil(total / 1000) * 1000 }]);
    else if (q === "USD") setLines([{ kind: "CASH-USD", amount: usdFull }]);
    else if (q === "EUR") setLines([{ kind: "CASH-EUR", amount: eurFull }]);
    else {
      const usd = Math.floor((total * 0.4) / rates.USD / 50) * 50 || 50;
      setLines([
        { kind: "CASH-USD", amount: usd },
        { kind: "CARD-LKR", amount: r2(Math.max(0, total - usd * rates.USD)) },
      ]);
    }
  };
  const set = (i: number, l: Partial<Line>) => setLines((o) => o.map((x, j) => (j === i ? { ...x, ...l } : x)));

  const confirm = () =>
    onPay(
      lines
        .filter((l) => l.amount > 0)
        .map((l) => {
          const [method, currency] = l.kind.split("-") as [PaymentForm["method"], PaymentForm["currency"]];
          return { method, currency, amount: r2(l.amount) };
        }),
    );

  return (
    <Modal
      open
      onOpenChange={(o) => !o && !busy && onClose()}
      title="Take payment"
      desc="Cash in LKR / USD / EUR, card, or both – always the same price"
      size="lg"
      footer={
        <>
          <Button variant="outline" disabled={busy} onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="success"
            disabled={busy || remaining > 0 || cardOver || (!lines.length && total > 0)}
            onClick={confirm}
            id="payDone"
          >
            {busy ? "Saving…" : "Confirm payment"}
          </Button>
        </>
      }
    >
      <div className="pay-due">
        {credit ? (
          <div className="muted-on-dark" style={{ fontSize: 12 }} id="creditLine">
            Bill {lkr(bill)} − credit note {credit.number} {lkr(used)}
          </div>
        ) : null}
        <div className="muted-on-dark">Amount to pay</div>
        <div className="pay-big">{lkr(total)}</div>
        <div className="text-gold">
          USD {num(total / rates.USD)} · EUR {num(total / rates.EUR)}
        </div>
        <div style={{ fontSize: 12, marginTop: 6 }}>
          💵 Cash and 💳 card: the same price. The bank fee on cards is paid by the shop.
        </div>
        <div className="muted-on-dark" style={{ fontSize: 11, marginTop: 4 }}>
          Today&apos;s rates: 1 USD = LKR {num(rates.USD)} · 1 EUR = LKR {num(rates.EUR)}
        </div>
      </div>
      <div className="row mt-s">
        <span className="label">Quick:</span>
        <Button size="sm" variant="outline" onClick={() => quick("card")}>
          Full by card
        </Button>
        <Button size="sm" variant="outline" onClick={() => quick("LKR")}>
          Cash LKR
        </Button>
        <Button size="sm" variant="outline" onClick={() => quick("USD")}>
          Cash USD {usdFull}
        </Button>
        <Button size="sm" variant="outline" onClick={() => quick("EUR")}>
          Cash EUR {eurFull}
        </Button>
        <Button size="sm" variant="outline" onClick={() => quick("split")}>
          Split: USD cash + card
        </Button>
      </div>
      <div className="mt">
        {!lines.length ? (
          <p className="muted">No payment lines yet – use a quick button or add a line.</p>
        ) : (
          lines.map((l, i) => (
            <div key={i} className="pay-line">
              <Select
                aria-label={`Payment ${i + 1} type`}
                value={l.kind}
                onChange={(e) => set(i, { kind: e.target.value as Line["kind"] })}
              >
                {KINDS.map((k) => (
                  <option key={k.value} value={k.value}>
                    {k.label}
                  </option>
                ))}
              </Select>
              <Input
                aria-label={`Payment ${i + 1} amount`}
                type="number"
                min="0"
                step="0.01"
                inputMode="decimal"
                value={l.amount || ""}
                onChange={(e) => set(i, { amount: Number(e.target.value) || 0 })}
              />
              <span className="muted">
                {l.kind === "CARD-LKR" ? `Charge ${lkr(l.amount || 0)} on the machine` : `= ${lkr(toLkr(l))}`}
              </span>
              <Button size="sm" variant="ghost" onClick={() => setLines((o) => o.filter((_, j) => j !== i))}>
                Remove
              </Button>
            </div>
          ))
        )}
      </div>
      <Button
        size="sm"
        variant="ghost"
        className="mt-s"
        onClick={() => setLines((o) => [...o, { kind: "CASH-LKR", amount: remaining }])}
      >
        + Add payment line
      </Button>
      <div className="sep" />
      <dl className="dl" id="paySum">
        <dt>Paid towards the bill (LKR)</dt>
        <dd>{lkr(paid)}</dd>
        <dt>Remaining</dt>
        <dd className={remaining > 0 ? "text-danger" : "text-success"}>{lkr(remaining)}</dd>
        <dt>Change due</dt>
        <dd style={{ fontSize: 18 }} className={change > 0 ? "bold text-gold" : ""}>
          {lkr(change)}
          {change > 0 ? <small className="muted"> (USD {num(change / rates.USD)})</small> : null}
        </dd>
      </dl>
      {giveBack > 0 ? (
        <Alert tone="warning" className="mt-s">
          The credit note is worth {lkr(credit?.amount ?? 0)}: give back <b>{lkr(giveBack)}</b> to the customer.
        </Alert>
      ) : null}
      {cardOver ? (
        <Alert tone="danger" className="mt-s">
          Card payments cannot be more than the amount due. Reduce the card amount.
        </Alert>
      ) : null}
    </Modal>
  );
}
