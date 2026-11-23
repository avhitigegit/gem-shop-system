"use client";

/* Expenses – prototype m-expenses/expenses.html (Guide 51; BRD M2, M3, M4, rule 11). Every shop cost of the branch at the
   top, by month, expense type, payment method and status. Open one to see its bill photo or correct it. */
import Link from "next/link";
import { useState } from "react";
import { StatusPill } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { DataTable, ExportButtons } from "@/components/ui/data-table";
import { Alert, Field, FormGrid, Options, Select } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { api } from "@/lib/api/client";
import { EXPENSE_STATUS, METHOD_LABEL, methodLabel } from "@/lib/expenses";
import { date, lkr, month as monthName, todayInShop } from "@/lib/format";
import { ALL_BRANCHES, useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function ExpensesScreen() {
  const { branch, branchName } = useSession();
  const [month, setMonth] = useState(todayInShop().slice(0, 7));
  const [type, setType] = useState("");
  const [method, setMethod] = useState("");
  const [status, setStatus] = useState("");
  const { data } = useLoad(
    () =>
      api.GET("/api/expenses", {
        params: {
          query: {
            month: month || undefined,
            type: type || undefined,
            method: method || undefined,
            status: status || undefined,
          },
        },
      }),
    [branch, month, type, method, status],
  );
  const months = data?.months ?? [];
  const monthItems = [
    { value: "", label: "All months" },
    ...(months.some((m) => m.month === month) || !month ? [] : [{ value: month, label: monthName(month) }]),
    ...months.map((m) => ({ value: m.month, label: `${monthName(m.month)} (${m.count})` })),
  ];
  const all = branch === ALL_BRANCHES;

  return (
    <>
      <PageHead
        title="Expenses"
        sub="Every shop cost, so the owner and the accountant see the true profit."
        actions={
          <>
            <Button asChild variant="sapphire">
              <Link href="/money/expenses/new">+ Add expense</Link>
            </Button>
            <ExportButtons tableId="exT" name="Expenses" />
          </>
        }
      />
      <Alert tone="info" className="mb">
        Every expense has an expense type and a bill photo (rule 11). Cash paid from the drawer is recorded on{" "}
        <Link href="/money/pay-out">Pay out cash from the drawer</Link> and counted when the day is closed.
      </Alert>
      <Card className="mb">
        <FormGrid style={{ gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", padding: 16 }}>
          <Field label="Month" htmlFor="fMonth">
            <Select id="fMonth" value={month} onChange={(e) => setMonth(e.target.value)}>
              <Options items={monthItems} />
            </Select>
          </Field>
          <Field label="Expense type" htmlFor="fType">
            <Select id="fType" value={type} onChange={(e) => setType(e.target.value)}>
              <Options
                items={[
                  { value: "", label: "All types" },
                  ...(data?.expenseTypes ?? []).map((t) => ({ value: t, label: t })),
                ]}
              />
            </Select>
          </Field>
          <Field label="Payment method" htmlFor="fMethod">
            <Select id="fMethod" value={method} onChange={(e) => setMethod(e.target.value)}>
              <Options
                items={[
                  { value: "", label: "All" },
                  ...Object.entries(METHOD_LABEL).map(([value, label]) => ({ value, label })),
                ]}
              />
            </Select>
          </Field>
          <Field label="Status" htmlFor="fStatus">
            <Select id="fStatus" value={status} onChange={(e) => setStatus(e.target.value)}>
              <Options items={[{ value: "", label: "All" }, ...EXPENSE_STATUS]} />
            </Select>
          </Field>
        </FormGrid>
      </Card>
      <Card>
        <CardHeader
          title={month ? monthName(month) : "All months"}
          level={3}
          actions={data ? <b>{lkr(data.total)}</b> : undefined}
        />
        {data ? (
          <DataTable
            id="exT"
            rows={data.expenses}
            rowKey={(x) => x.number}
            stackOnPhone
            marked={(x) => x.status === "PENDING"}
            empty="No expenses for this choice."
            columns={[
              {
                key: "d",
                header: "Date",
                cell: (x) => (
                  <>
                    {date(x.spentAt)}{" "}
                    <Link className="mono muted" href={`/money/expenses/${x.number}`} style={{ fontSize: 12 }}>
                      {x.number}
                    </Link>
                  </>
                ),
              },
              ...(all
                ? [{ key: "br", header: "Branch", cell: (x: (typeof data.expenses)[number]) => branchName(x.branch) }]
                : []),
              {
                key: "t",
                header: "Expense type",
                cell: (x) => (
                  <>
                    {x.expenseType}
                    {x.monthlyBill ? (
                      <span className="muted" style={{ fontSize: 12 }}>
                        {" "}
                        · monthly
                      </span>
                    ) : null}
                  </>
                ),
              },
              { key: "p", header: "Paid to", cell: (x) => x.paidTo },
              { key: "m", header: "Method", cell: (x) => methodLabel(x.method) },
              {
                key: "b",
                header: "Bill",
                cell: (x) =>
                  x.hasBill ? (
                    <Link href={`/money/expenses/${x.number}`} aria-label={`Bill photo ${x.number}`}>
                      📎
                    </Link>
                  ) : (
                    <Link href={`/money/expenses/${x.number}`} className="text-danger">
                      Attach
                    </Link>
                  ),
              },
              { key: "a", header: "Amount", num: true, cell: (x) => lkr(x.amount) },
              {
                key: "s",
                header: "Status",
                cell: (x) => (
                  <StatusPill status={x.status} label={x.status === "PENDING" ? "Waiting for owner" : undefined} />
                ),
              },
              { key: "by", header: "By", cell: (x) => x.by ?? "—" },
              {
                key: "o",
                header: "",
                label: "Open",
                cell: (x) => (
                  <Button asChild size="sm" variant="outline">
                    <Link href={`/money/expenses/${x.number}`}>Open</Link>
                  </Button>
                ),
              },
            ]}
            footer={["Total (not rejected)", ...(all ? [""] : []), "", "", "", "", lkr(data.total), "", "", ""]}
          />
        ) : (
          <p className="muted" style={{ padding: 16 }}>
            Loading…
          </p>
        )}
      </Card>
    </>
  );
}
