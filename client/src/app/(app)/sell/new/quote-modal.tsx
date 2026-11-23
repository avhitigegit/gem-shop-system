"use client";

/* Keep for customer / Give a quote – the prototype's window on New sale (BRD F7). Both have an expiry date: a
   reservation 3 days and a quotation 14 days by default. A reservation can record a deposit. */
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import { Field, FormGrid, Input } from "@/components/ui/form";
import { lkr } from "@/lib/format";
import { inDays, type QuoteKind } from "@/lib/sales";

export type QuoteChoice = { expires: string; deposit: number; notes: string };

export function QuoteModal({
  kind,
  count,
  total,
  customer,
  busy,
  errors,
  onClose,
  onSave,
}: {
  kind: QuoteKind;
  count: number;
  total: number;
  customer: string;
  busy: boolean;
  errors: Record<string, string>;
  onClose: () => void;
  onSave: (c: QuoteChoice) => void;
}) {
  const reserve = kind === "RESERVATION";
  const [expires, setExpires] = useState(inDays(reserve ? 3 : 14));
  const [deposit, setDeposit] = useState("");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<Record<string, string>>({});
  const all = { ...errors, ...error };

  const save = () => {
    const e: Record<string, string> = {};
    if (!expires || expires < inDays(0)) e.expires = "From today";
    if (expires > inDays(90)) e.expires = "At most 90 days ahead";
    if (Number(deposit) < 0) e.deposit = "Not below 0";
    if (Number(deposit) > total) e.deposit = `At most ${lkr(total)}`;
    setError(e);
    if (Object.keys(e).length) return;
    onSave({ expires, deposit: Number(deposit) || 0, notes: notes.trim() });
  };

  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title={reserve ? "Keep for a returning customer" : "Give a price quote"}
      desc="Both have an expiry date."
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="sapphire" id="quoteSave" disabled={busy} onClick={save}>
            {busy ? "Please wait…" : reserve ? "Keep for customer" : "Create quotation"}
          </Button>
        </>
      }
    >
      <FormGrid>
        <Field label="Expiry date" htmlFor="qExp" required error={all.expires}>
          <Input
            id="qExp"
            type="date"
            min={inDays(0)}
            max={inDays(90)}
            value={expires}
            onChange={(e) => setExpires(e.target.value)}
          />
        </Field>
        {reserve ? (
          <Field label="Deposit taken (LKR)" htmlFor="qDep" error={all.deposit}>
            <Input
              id="qDep"
              type="number"
              min="0"
              step="500"
              inputMode="decimal"
              placeholder="0"
              value={deposit}
              onChange={(e) => setDeposit(e.target.value)}
            />
          </Field>
        ) : (
          <div />
        )}
        <Field label="Note" htmlFor="qNote" full>
          <Input
            id="qNote"
            maxLength={500}
            placeholder="e.g. coming back on Friday"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />
        </Field>
      </FormGrid>
      <p className="muted mt-s" style={{ fontSize: 12 }}>
        {count} gem(s), {lkr(total)} for {customer}.
        {reserve ? " Kept gems cannot be sold to anyone else until the expiry date." : " The gems are not kept."}
      </p>
    </Modal>
  );
}
