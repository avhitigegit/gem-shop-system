"use client";

/* Add an expense / Correct an expense – prototype m-expenses/add-expense.html (Guide 50; BRD M2, M3, M6, N2, rule 11).
   Date, branch, expense type (Settings → Drop-down lists), paid to, amount, bank transfer or card, a note and the bill
   photo. Above the owner's limit an expense by the accountant waits for the owner. A correction is kept in the activity
   log. Cash from the drawer is paid on Pay out cash from the drawer. */
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import { Alert, Field, FormGrid, Input, Options, Select, Textarea } from "@/components/ui/form";
import { useToast } from "@/components/ui/toast";
import { UploadBox } from "@/components/ui/upload-box";
import { api, errorMessage } from "@/lib/api/client";
import { type ExpenseDetail, PAID_BY, methodLabel } from "@/lib/expenses";
import { ACCEPT, discardFile, uploadFile } from "@/lib/files";
import { lkr, todayInShop } from "@/lib/format";
import { ALL_BRANCHES, branchLabel, useSession } from "@/lib/session";

type Form = {
  spentOn: string;
  branch: string;
  expenseType: string;
  amount: string;
  paidTo: string;
  method: string;
  note: string;
};
type Errors = Partial<Record<keyof Form | "bill", string>>;

const toDay = (iso: string) => todayInShop(new Date(iso));

export function ExpenseForm({
  types,
  limit,
  edit,
  onSaved,
}: {
  types: string[];
  limit: number;
  /** Correct this expense (empty = add a new one). */
  edit?: ExpenseDetail;
  /** After a correction (the detail page shows the new values). */
  onSaved?: (d: ExpenseDetail) => void;
}) {
  const router = useRouter();
  const toast = useToast();
  const { branch, branches, role } = useSession();
  const x = edit?.expense;
  const firstBranch =
    branch !== ALL_BRANCHES ? branch : (branches.find((b) => b.status === "ACTIVE") ?? branches[0])?.code;
  const [f, setF] = useState<Form>({
    spentOn: x ? toDay(x.spentAt) : todayInShop(),
    branch: x?.branch ?? firstBranch ?? "",
    expenseType: x?.expenseType ?? "",
    amount: x ? String(Number(x.amount)) : "",
    paidTo: x?.paidTo ?? "",
    method: x?.method ?? "BANK",
    note: x?.note ?? "",
  });
  const [bill, setBill] = useState<{ id: string; name: string } | null>(null);
  const [errors, setErrors] = useState<Errors>({});
  const [busy, setBusy] = useState(false);
  const drawer = x?.method === "DRAWER";
  const set = (k: keyof Form, v: string) => setF((o) => ({ ...o, [k]: v }));
  const savedBill = edit?.bill?.name ?? edit?.sampleBill ?? null;
  const big = role !== "OWNER" && Number(f.amount) > limit;

  const onBill = async (files: File[]) => {
    const { data, error } = await uploadFile(files[0], "BILL_PHOTO");
    if (!data) return toast("Bill not uploaded", errorMessage(error), "error");
    if (bill) void discardFile(bill.id);
    setBill({ id: data.id, name: data.name });
    setErrors((o) => ({ ...o, bill: "" }));
  };

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    const bad: Errors = {};
    if (!f.spentOn) bad.spentOn = "Enter the date.";
    else if (f.spentOn > todayInShop()) bad.spentOn = "The date cannot be in the future.";
    if (!f.branch) bad.branch = "Choose the branch.";
    if (!f.expenseType) bad.expenseType = "Choose the expense type.";
    if (!f.paidTo.trim()) bad.paidTo = "Who was paid?";
    if (!(Number(f.amount) >= 1)) bad.amount = "Enter the amount on the bill.";
    if (!bill && !(edit && (edit.bill || edit.sampleBill))) bad.bill = "Every expense needs a bill photo (rule 11).";
    setErrors(bad);
    if (Object.keys(bad).length) return;
    setBusy(true);
    const body = {
      spentOn: f.spentOn,
      branch: f.branch,
      expenseType: f.expenseType,
      amount: Number(f.amount),
      paidTo: f.paidTo.trim(),
      method: f.method,
      note: f.note.trim() || undefined,
      bill: bill?.id,
    };
    const { data, error } = x
      ? await api.PUT("/api/expenses/{number}", { params: { path: { number: x.number } }, body })
      : await api.POST("/api/expenses", { body });
    setBusy(false);
    if (!data) {
      const fe = (error as { fieldErrors?: { field: string; message: string }[] } | undefined)?.fieldErrors;
      if (fe?.length) setErrors(Object.fromEntries(fe.map((v) => [v.field, v.message])));
      return toast("Not saved", errorMessage(error), "error");
    }
    const waiting = data.expense.status === "PENDING";
    toast(
      waiting ? "Sent for owner approval" : x ? "Expense corrected" : "Expense saved",
      `${data.expense.number} · ${lkr(data.expense.amount)}`,
      waiting ? "warning" : "success",
    );
    if (onSaved) onSaved(data);
    else router.push(`/money/expenses/${encodeURIComponent(data.expense.number)}`);
  };

  return (
    <form className="card" noValidate onSubmit={(e) => void save(e)} style={{ maxWidth: 760 }}>
      <CardHeader title={x ? `Correct expense ${x.number}` : "Expense details"} level={3} />
      <CardContent className="stack" style={{ gap: 12 }}>
        {drawer ? (
          <Alert tone="info">
            Paid in cash from the drawer on {toDay(x.spentAt)}. The date, branch and payment method stay as they are,
            because the cash was counted on that day.
          </Alert>
        ) : null}
        <FormGrid>
          <Field label="Date" htmlFor="xDate" required error={errors.spentOn}>
            <Input
              id="xDate"
              type="date"
              max={todayInShop()}
              value={f.spentOn}
              disabled={drawer}
              onChange={(e) => set("spentOn", e.target.value)}
            />
          </Field>
          <Field label="Branch" htmlFor="xBranch" required error={errors.branch}>
            <Select id="xBranch" value={f.branch} disabled={drawer} onChange={(e) => set("branch", e.target.value)}>
              <Options items={branches.map((b) => ({ value: b.code, label: branchLabel(b) }))} />
            </Select>
          </Field>
          <Field
            label="Expense type"
            htmlFor="xType"
            required
            error={errors.expenseType}
            hint={role === "OWNER" ? <Link href="/settings/lists">Edit expense types</Link> : undefined}
          >
            <Select id="xType" value={f.expenseType} onChange={(e) => set("expenseType", e.target.value)}>
              <Options
                placeholder="Select…"
                items={(types.includes(f.expenseType) || !f.expenseType ? types : [f.expenseType, ...types]).map(
                  (t) => ({ value: t, label: t }),
                )}
              />
            </Select>
          </Field>
          <Field label="Paid to" htmlFor="xTo" required error={errors.paidTo}>
            <Input
              id="xTo"
              placeholder="e.g. Ceylon Electricity Board"
              value={f.paidTo}
              onChange={(e) => set("paidTo", e.target.value)}
            />
          </Field>
          <Field label="Amount (LKR, total on bill)" htmlFor="xAmt" required error={errors.amount}>
            <Input
              id="xAmt"
              type="number"
              min={1}
              inputMode="decimal"
              value={f.amount}
              onChange={(e) => set("amount", e.target.value)}
            />
          </Field>
          <Field
            label="Payment method"
            htmlFor="xMethod"
            required
            hint={
              drawer ? undefined : (
                <>
                  Paid in cash from the drawer? Use <Link href="/money/pay-out">Pay out cash from the drawer</Link>.
                </>
              )
            }
          >
            <Select id="xMethod" value={f.method} disabled={drawer} onChange={(e) => set("method", e.target.value)}>
              <Options
                items={
                  drawer || (x && !PAID_BY.some((p) => p.value === x.method))
                    ? [{ value: x!.method, label: methodLabel(x!.method) }, ...(drawer ? [] : PAID_BY)]
                    : PAID_BY
                }
              />
            </Select>
          </Field>
        </FormGrid>
        <Field label="Note" htmlFor="xNote">
          <Textarea id="xNote" rows={2} value={f.note} onChange={(e) => set("note", e.target.value)} />
        </Field>
        <Field label="Bill photo" required error={errors.bill}>
          <UploadBox
            accept={ACCEPT.BILL_PHOTO}
            capture="environment"
            done={bill?.name ?? savedBill ?? undefined}
            onFiles={(fl) => void onBill(fl)}
          >
            ⇪ Take a photo of the bill / upload it
          </UploadBox>
        </Field>
        {big ? (
          <Alert tone="warning">Above {lkr(limit)}: this expense will wait for the owner&apos;s approval (M6).</Alert>
        ) : null}
      </CardContent>
      <CardFooter className="row" style={{ justifyContent: "space-between" }}>
        <span className="muted" style={{ fontSize: 12 }}>
          Approval limit: {lkr(limit)}. Expenses by the owner need no approval.
        </span>
        <span className="row">
          <Button type="button" variant="ghost" onClick={() => (onSaved && edit ? onSaved(edit) : router.back())}>
            Cancel
          </Button>
          <Button type="submit" variant="sapphire" id="saveExpense" disabled={busy}>
            {busy ? "Saving…" : x ? "Save correction" : "Save expense"}
          </Button>
        </span>
      </CardFooter>
    </form>
  );
}
