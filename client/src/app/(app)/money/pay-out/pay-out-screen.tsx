"use client";

/* Pay out cash from the drawer – prototype i-daily-closing/drawer-expenses.html (Guide 15, 53; BRD I2, M4, M6, rule 11).
   A small expense with its bill photo; above the owner's limit it waits for approval. Also shows guide commission paid
   "Cash (drawer)" today and refunds paid back in cash. All of it comes off the expected cash at day end. */
import Link from "next/link";
import { useState } from "react";
import { StatusPill } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { DataTable } from "@/components/ui/data-table";
import { Alert, Field, Input, Options, Select } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { UploadBox } from "@/components/ui/upload-box";
import { api, errorMessage } from "@/lib/api/client";
import type { CashDay } from "@/lib/cash";
import { ACCEPT, discardFile, uploadFile } from "@/lib/files";
import { date, lkr, time } from "@/lib/format";
import { CashDayState, useCashDay } from "../use-cash-day";

type Form = { expenseType: string; amount: string; paidTo: string };
const BLANK: Form = { expenseType: "", amount: "", paidTo: "" };

export function PayOutScreen() {
  const { data, error, reload, setData } = useCashDay();
  if (!data)
    return (
      <>
        <PageHead title="Drawer expenses" sub="Small shop expenses paid in cash from the drawer." />
        <CashDayState error={error} />
      </>
    );
  return <PayOut day={data} reload={() => void reload()} setDay={setData} />;
}

function PayOut({ day, reload, setDay }: { day: CashDay; reload: () => void; setDay: (d: CashDay) => void }) {
  const toast = useToast();
  const closed = !!day.closing;
  const [f, setF] = useState<Form>(BLANK);
  const [bill, setBill] = useState<{ id: string; name: string } | null>(null);
  const [errors, setErrors] = useState<Partial<Record<keyof Form | "bill", string>>>({});
  const [busy, setBusy] = useState(false);
  const limit = Number(day.largeExpenseLimit);
  const sum = (xs: { amount: number | string }[]) => xs.reduce((t, x) => t + Number(x.amount), 0);

  const onBill = async (files: File[]) => {
    const { data, error } = await uploadFile(files[0], "BILL_PHOTO");
    if (!data) return toast("Bill not uploaded", errorMessage(error), "error");
    if (bill) void discardFile(bill.id);
    setBill({ id: data.id, name: data.name });
    setErrors((o) => ({ ...o, bill: "" }));
  };

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    const bad: typeof errors = {};
    if (!f.expenseType) bad.expenseType = "Choose the expense type.";
    if (!(Number(f.amount) >= 1)) bad.amount = "Enter the amount.";
    if (!f.paidTo.trim()) bad.paidTo = "Who was paid?";
    if (!bill) bad.bill = "Every expense needs a bill photo (rule 11).";
    setErrors(bad);
    if (Object.keys(bad).length) {
      if (bad.bill && Object.keys(bad).length === 1) toast("Bill photo required", "Rule 11", "error");
      return;
    }
    setBusy(true);
    const { data, error } = await api.POST("/api/cash/expenses", {
      body: { expenseType: f.expenseType, amount: Number(f.amount), paidTo: f.paidTo.trim(), bill: bill!.id },
    });
    setBusy(false);
    if (!data) return toast("Not saved", errorMessage(error), "error");
    const big = data.status === "PENDING";
    toast(big ? "Sent for owner approval" : "Drawer expense recorded", lkr(data.amount), big ? "warning" : "success");
    setF(BLANK);
    setBill(null);
    reload();
  };

  const payRefund = async (creditNote: string) => {
    const { data, error } = await api.POST("/api/cash/refunds/{creditNote}", { params: { path: { creditNote } } });
    if (!data) return toast("Not saved", errorMessage(error), "error");
    toast("Refund paid from the drawer", creditNote, "success");
    setDay(data);
  };

  return (
    <>
      <PageHead
        title={`Drawer expenses – ${date(day.day)}`}
        sub="Small shop expenses paid in cash from the drawer. They are taken off the expected cash at day end."
      />
      {closed ? (
        <Alert className="mb">Today is already closed. Nothing more can be paid from this drawer today.</Alert>
      ) : null}
      <div className="g-1-2 grid">
        <form className="card" id="f" noValidate onSubmit={(e) => void save(e)}>
          <CardHeader title="Pay from drawer" level={3} />
          <CardContent className="stack" style={{ gap: 12 }}>
            <Field label="Expense type" htmlFor="xType" required error={errors.expenseType}>
              <Select
                id="xType"
                value={f.expenseType}
                disabled={closed}
                onChange={(e) => setF((o) => ({ ...o, expenseType: e.target.value }))}
              >
                <Options placeholder="Select…" items={day.expenseTypes.map((t) => ({ value: t, label: t }))} />
              </Select>
            </Field>
            <Field label="Amount (LKR)" htmlFor="xAmt" required error={errors.amount}>
              <Input
                id="xAmt"
                type="number"
                min={1}
                inputMode="decimal"
                value={f.amount}
                disabled={closed}
                onChange={(e) => setF((o) => ({ ...o, amount: e.target.value }))}
              />
            </Field>
            <Field label="Paid to" htmlFor="xTo" required error={errors.paidTo}>
              <Input
                id="xTo"
                placeholder="e.g. Tea shop, post office"
                value={f.paidTo}
                disabled={closed}
                onChange={(e) => setF((o) => ({ ...o, paidTo: e.target.value }))}
              />
            </Field>
            <Field label="Bill photo" required error={errors.bill}>
              <UploadBox
                accept={ACCEPT.BILL_PHOTO}
                capture="environment"
                done={bill?.name}
                onFiles={(fl) => void onBill(fl)}
              >
                ⇪ Take / upload bill photo
              </UploadBox>
            </Field>
            <Button variant="sapphire" type="submit" id="saveExpense" disabled={busy || closed}>
              {busy ? "Saving…" : "Record drawer expense"}
            </Button>
            <div className="muted" style={{ fontSize: 12 }}>
              Expenses above {lkr(limit)} need owner approval (M6).
            </div>
          </CardContent>
        </form>
        <div className="stack">
          <Card>
            <CardHeader title="Today's drawer expenses" level={3} actions={<b>{lkr(sum(day.expenses))}</b>} />
            <DataTable
              id="exT"
              rows={day.expenses}
              rowKey={(x) => x.number}
              stackOnPhone
              empty="No drawer expenses today."
              columns={[
                { key: "t", header: "Time", cell: (x) => time(x.at) },
                { key: "ty", header: "Expense type", cell: (x) => x.expenseType },
                { key: "to", header: "Paid to", cell: (x) => x.paidTo },
                {
                  key: "b",
                  header: "Bill",
                  cell: (x) =>
                    x.bill ? (
                      <a href={x.bill} target="_blank" rel="noreferrer" aria-label={`Bill photo ${x.number}`}>
                        📎
                      </a>
                    ) : (
                      "—"
                    ),
                },
                { key: "a", header: "Amount", num: true, cell: (x) => lkr(x.amount) },
                {
                  key: "s",
                  header: "Status",
                  cell: (x) => <StatusPill status={x.status === "PENDING" ? "pending" : x.status.toLowerCase()} />,
                },
              ]}
            />
          </Card>
          <Card>
            <CardHeader
              title="Commissions paid in cash from the drawer"
              level={3}
              actions={<b>{lkr(sum(day.commissions))}</b>}
            />
            <DataTable
              id="cmT"
              rows={day.commissions}
              rowKey={(c) => c.payment}
              empty="None today."
              columns={[
                { key: "p", header: "Payee", cell: (c) => c.payee },
                {
                  key: "n",
                  header: "Slip",
                  cell: (c) => (
                    <Link className="mono" href={`/people/commissions/slip/${c.payment}`}>
                      {c.payment}
                    </Link>
                  ),
                },
                { key: "a", header: "Amount", num: true, cell: (c) => lkr(c.amount) },
              ]}
            />
            <CardContent className="muted" style={{ fontSize: 13 }}>
              To pay a guide or driver, use <Link href="/people/commissions/pay">Pay guides &amp; drivers</Link> and
              choose <b>Cash (drawer)</b>. It shows here and comes off the expected cash.
            </CardContent>
          </Card>
          <Card>
            <CardHeader title="Refunds paid in cash" level={3} actions={<b>{lkr(sum(day.refunds))}</b>} />
            <DataTable
              id="rfT"
              rows={[
                ...day.refunds.map((r) => ({ ...r, paid: true })),
                ...day.refundsToPay.map((r) => ({ ...r, paid: false })),
              ]}
              rowKey={(r) => r.creditNote}
              empty="No refunds waiting."
              columns={[
                { key: "c", header: "Credit note", cell: (r) => <span className="mono">{r.creditNote}</span> },
                { key: "cu", header: "Customer", cell: (r) => r.customer },
                { key: "a", header: "Amount", num: true, cell: (r) => lkr(r.amount) },
                {
                  key: "s",
                  header: "",
                  label: "Pay",
                  cell: (r) =>
                    r.paid ? (
                      <StatusPill status="paid" label={`Paid ${time(r.at)}`} />
                    ) : closed ? null : (
                      <Button
                        size="sm"
                        variant="success"
                        data-refund={r.creditNote}
                        onClick={() => void payRefund(r.creditNote)}
                      >
                        Pay from drawer
                      </Button>
                    ),
                },
              ]}
            />
          </Card>
        </div>
      </div>
    </>
  );
}
