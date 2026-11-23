"use client";

/* Monthly bills – prototype m-expenses/recurring.html (Guide 52; BRD M5). Rent, phone, salaries: each active bill is added
   as an expense by itself on its day of the month (a nightly job). The bill photo is attached later on the expense. */
import Link from "next/link";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { DataTable } from "@/components/ui/data-table";
import { Modal, useConfirm } from "@/components/ui/dialog";
import { Alert, Field, FormGrid, Input, Options, Select, Switch } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import { type MonthlyBillRow, PAID_BY, methodLabel } from "@/lib/expenses";
import { date, lkr } from "@/lib/format";
import { ALL_BRANCHES, branchLabel, useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

type Form = { branch: string; expenseType: string; amount: string; paidTo: string; method: string; day: string };

export function MonthlyBillsScreen() {
  const toast = useToast();
  const confirm = useConfirm();
  const { branch, branches, branchName } = useSession();
  const { data, reload } = useLoad(() => api.GET("/api/expenses/monthly-bills"), [branch]);
  const [edit, setEdit] = useState<MonthlyBillRow | "new" | null>(null);
  const [busy, setBusy] = useState(false);

  const body = (b: MonthlyBillRow, active?: boolean) => ({
    branch: b.branch,
    expenseType: b.expenseType,
    amount: Number(b.amount),
    paidTo: b.paidTo,
    method: b.method,
    day: b.day,
    active,
  });

  const toggle = async (b: MonthlyBillRow, on: boolean) => {
    const { data: r, error } = await api.PUT("/api/expenses/monthly-bills/{code}", {
      params: { path: { code: b.code } },
      body: body(b, on),
    });
    if (!r) return toast("Not saved", errorMessage(error), "error");
    toast(on ? "Monthly bill on" : "Monthly bill paused", `${b.expenseType} · ${b.code}`, "success");
    void reload();
  };

  const runNow = async () => {
    if (
      !(await confirm({
        title: "Add this month's bills now?",
        text: "Every active bill that is not added yet this month is added as an expense today (or on its day).",
        yes: "Add now",
      }))
    )
      return;
    setBusy(true);
    const { data: r, error } = await api.POST("/api/expenses/monthly-bills/run");
    setBusy(false);
    if (!r) return toast("Not added", errorMessage(error), "error");
    toast(r.added ? `${r.added} bill(s) added` : "Nothing to add", "Attach the bill photos on Expenses.", "success");
    void reload();
  };

  return (
    <>
      <PageHead
        title="Monthly bills"
        sub="Rent, phone, salaries – added as an expense by themselves each month on the chosen day."
        actions={
          <>
            <Button variant="outline" id="runNow" disabled={busy} onClick={() => void runNow()}>
              ▶ Add this month&apos;s bills now
            </Button>
            <Button variant="sapphire" id="addBill" onClick={() => setEdit("new")}>
              + Add monthly bill
            </Button>
          </>
        }
      />
      {data ? (
        <Card>
          <DataTable
            id="rcT"
            rows={data.bills}
            rowKey={(b) => b.code}
            stackOnPhone
            empty="No monthly bills yet."
            columns={[
              {
                key: "t",
                header: "Expense type",
                cell: (b) => (
                  <>
                    <b>{b.expenseType}</b>{" "}
                    <span className="mono muted" style={{ fontSize: 12 }}>
                      {b.code}
                    </span>
                  </>
                ),
              },
              ...(branch === ALL_BRANCHES
                ? [{ key: "br", header: "Branch", cell: (b: MonthlyBillRow) => branchName(b.branch) }]
                : []),
              { key: "p", header: "Paid to", cell: (b) => b.paidTo },
              { key: "a", header: "Amount", num: true, cell: (b) => lkr(b.amount) },
              { key: "m", header: "Method", cell: (b) => methodLabel(b.method) },
              { key: "d", header: "Every month on", cell: (b) => `day ${b.day}` },
              { key: "l", header: "Last added", cell: (b) => (b.lastAdded ? date(b.lastAdded) : "—") },
              { key: "n", header: "Next", cell: (b) => (b.next ? date(b.next) : "—") },
              {
                key: "s",
                header: "This month",
                cell: (b) =>
                  b.addedThisMonth ? (
                    <Badge tone="success">✓ added</Badge>
                  ) : b.active ? (
                    <Badge tone="warning">due</Badge>
                  ) : (
                    "—"
                  ),
              },
              {
                key: "on",
                header: "Active",
                cell: (b) => (
                  <Switch checked={b.active} label={`${b.expenseType} active`} onChange={(v) => void toggle(b, v)} />
                ),
              },
              {
                key: "e",
                header: "",
                label: "Change",
                cell: (b) => (
                  <Button size="sm" variant="outline" onClick={() => setEdit(b)}>
                    Change
                  </Button>
                ),
              },
            ]}
            footer={[
              "Every month (active)",
              ...(branch === ALL_BRANCHES ? [""] : []),
              "",
              lkr(data.monthlyTotal),
              "",
              "",
              "",
              "",
              "",
              "",
              "",
            ]}
          />
          <CardContent className="muted" style={{ fontSize: 13 }}>
            Each night the system adds every active bill on its day. Attach the new bill photo on{" "}
            <Link href="/money/expenses">Expenses</Link> (rows marked &quot;Attach&quot;).
          </CardContent>
        </Card>
      ) : (
        <p className="muted">Loading…</p>
      )}
      {edit && data ? (
        <BillModal
          bill={edit === "new" ? null : edit}
          types={data.expenseTypes}
          branches={branches.map((b) => ({ value: b.code, label: branchLabel(b) }))}
          defaultBranch={branch !== ALL_BRANCHES ? branch : (branches[0]?.code ?? "")}
          onClose={() => setEdit(null)}
          onSaved={() => {
            setEdit(null);
            void reload();
          }}
        />
      ) : null}
    </>
  );
}

function BillModal({
  bill,
  types,
  branches,
  defaultBranch,
  onClose,
  onSaved,
}: {
  bill: MonthlyBillRow | null;
  types: string[];
  branches: { value: string; label: string }[];
  defaultBranch: string;
  onClose: () => void;
  onSaved: () => void;
}) {
  const toast = useToast();
  const [f, setF] = useState<Form>({
    branch: bill?.branch ?? defaultBranch,
    expenseType: bill?.expenseType ?? "",
    amount: bill ? String(Number(bill.amount)) : "",
    paidTo: bill?.paidTo ?? "",
    method: bill?.method ?? "BANK",
    day: String(bill?.day ?? 1),
  });
  const [errors, setErrors] = useState<Partial<Record<keyof Form, string>>>({});
  const [busy, setBusy] = useState(false);
  const set = (k: keyof Form, v: string) => setF((o) => ({ ...o, [k]: v }));

  const save = async () => {
    const bad: typeof errors = {};
    if (!f.expenseType) bad.expenseType = "Choose the expense type.";
    if (!(Number(f.amount) >= 1)) bad.amount = "Enter the amount.";
    if (!f.paidTo.trim()) bad.paidTo = "Who is paid?";
    const day = Number(f.day);
    if (!(day >= 1 && day <= 28)) bad.day = "A day from 1 to 28.";
    setErrors(bad);
    if (Object.keys(bad).length) return;
    setBusy(true);
    const body = {
      branch: f.branch,
      expenseType: f.expenseType,
      amount: Number(f.amount),
      paidTo: f.paidTo.trim(),
      method: f.method,
      day,
    };
    const { data, error } = bill
      ? await api.PUT("/api/expenses/monthly-bills/{code}", { params: { path: { code: bill.code } }, body })
      : await api.POST("/api/expenses/monthly-bills", { body });
    setBusy(false);
    if (!data) return toast("Not saved", errorMessage(error), "error");
    toast(bill ? "Monthly bill changed" : "Monthly bill added", `${data.expenseType} · ${data.code}`, "success");
    onSaved();
  };

  return (
    <Modal
      open
      onOpenChange={(o) => (o ? null : onClose())}
      title={bill ? `Change monthly bill ${bill.code}` : "Add monthly bill"}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="sapphire" id="saveBill" disabled={busy} onClick={() => void save()}>
            {busy ? "Saving…" : "Save"}
          </Button>
        </>
      }
    >
      <FormGrid>
        <Field label="Branch" htmlFor="rBranch" required>
          <Select id="rBranch" value={f.branch} onChange={(e) => set("branch", e.target.value)}>
            <Options items={branches} />
          </Select>
        </Field>
        <Field label="Expense type" htmlFor="rType" required error={errors.expenseType}>
          <Select id="rType" value={f.expenseType} onChange={(e) => set("expenseType", e.target.value)}>
            <Options placeholder="Select…" items={types.map((t) => ({ value: t, label: t }))} />
          </Select>
        </Field>
        <Field label="Paid to" htmlFor="rTo" required error={errors.paidTo}>
          <Input id="rTo" value={f.paidTo} onChange={(e) => set("paidTo", e.target.value)} />
        </Field>
        <Field label="Amount (LKR)" htmlFor="rAmt" required error={errors.amount}>
          <Input id="rAmt" type="number" min={1} value={f.amount} onChange={(e) => set("amount", e.target.value)} />
        </Field>
        <Field label="Payment method" htmlFor="rMethod" required>
          <Select id="rMethod" value={f.method} onChange={(e) => set("method", e.target.value)}>
            <Options items={PAID_BY} />
          </Select>
        </Field>
        <Field
          label="Every month on day"
          htmlFor="rDay"
          required
          error={errors.day}
          hint="1–28, so every month has it."
        >
          <Input id="rDay" type="number" min={1} max={28} value={f.day} onChange={(e) => set("day", e.target.value)} />
        </Field>
      </FormGrid>
      {!bill ? (
        <Alert tone="info" className="mt">
          It is added for the first time on its next day. Use &quot;Add this month&apos;s bills now&quot; to add it
          today.
        </Alert>
      ) : null}
    </Modal>
  );
}
