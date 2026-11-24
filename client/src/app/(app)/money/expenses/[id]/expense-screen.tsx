"use client";

/* One expense – its bill photo, who added it, and Correct (Guide 51; BRD M2, N2). A monthly bill that was added by itself
   gets its bill photo here. Sample data has only the prototype's bill name, shown as a sample receipt. */
import Link from "next/link";
import { useState } from "react";
import { StatusPill } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Alert } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { UploadBox } from "@/components/ui/upload-box";
import { api, errorMessage } from "@/lib/api/client";
import { type ExpenseDetail, methodLabel } from "@/lib/expenses";
import { ACCEPT, uploadFile } from "@/lib/files";
import { date, dateTime, lkr } from "@/lib/format";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";
import { ExpenseForm } from "../expense-form";

export function ExpenseScreen({ id }: { id: string }) {
  const { data, error, setData } = useLoad(
    () => api.GET("/api/expenses/{number}", { params: { path: { number: id } } }),
    [id],
  );
  const { data: list } = useLoad(() => api.GET("/api/expenses", { params: { query: { month: "0000-00" } } }), []);
  if (!data)
    return (
      <>
        <PageHead title={`Expense ${id}`} />
        {error ? <Alert tone="danger">{errorMessage(error)}</Alert> : <p className="muted">Loading…</p>}
      </>
    );
  return (
    <Expense d={data} types={list?.expenseTypes ?? []} limit={Number(list?.largeExpenseLimit ?? 0)} setD={setData} />
  );
}

function Expense({
  d,
  types,
  limit,
  setD,
}: {
  d: ExpenseDetail;
  types: string[];
  limit: number;
  setD: (d: ExpenseDetail) => void;
}) {
  const toast = useToast();
  const { branchName } = useSession();
  const [editing, setEditing] = useState(false);
  const x = d.expense;

  const attach = async (files: File[]) => {
    const up = await uploadFile(files[0], "BILL_PHOTO");
    if (!up.data) return toast("Bill not uploaded", errorMessage(up.error), "error");
    const { data, error } = await api.POST("/api/expenses/{number}/bill", {
      params: { path: { number: x.number } },
      body: { bill: up.data.id },
    });
    if (!data) return toast("Not saved", errorMessage(error), "error");
    toast("Bill photo added", x.number, "success");
    setD(data);
  };

  if (editing)
    return (
      <>
        <PageHead title={`Correct expense ${x.number}`} sub="Every change is kept in the activity log (N2)." />
        <ExpenseForm
          types={types}
          limit={limit}
          edit={d}
          onSaved={(n) => {
            setEditing(false);
            setD(n);
          }}
        />
      </>
    );

  return (
    <>
      <PageHead
        title={`Expense ${x.number}`}
        sub={`${x.expenseType} · ${date(x.spentAt)}`}
        actions={
          d.canCorrect ? (
            <Button variant="outline" id="correct" onClick={() => setEditing(true)}>
              ✎ Correct
            </Button>
          ) : undefined
        }
      />
      {d.locked ? (
        <Alert tone="info" className="mb">
          🔒 {d.locked}
        </Alert>
      ) : null}
      {x.status === "PENDING" ? (
        <Alert tone="warning" className="mb">
          Waiting for the owner&apos;s approval{x.approvalNo ? ` (${x.approvalNo})` : ""}.{" "}
          <Link href="/approvals">Approvals</Link>
        </Alert>
      ) : null}
      <div className="g2 grid">
        <Card>
          <CardHeader title="Details" level={3} actions={<StatusPill status={x.status} />} />
          <CardContent>
            <dl className="dl" style={{ gridTemplateColumns: "130px 1fr" }}>
              <dt>Date</dt>
              <dd>{dateTime(x.spentAt)}</dd>
              <dt>Branch</dt>
              <dd>{branchName(x.branch)}</dd>
              <dt>Expense type</dt>
              <dd>{x.expenseType}</dd>
              <dt>Paid to</dt>
              <dd>{x.paidTo}</dd>
              <dt>Amount</dt>
              <dd>
                <b>{lkr(x.amount)}</b>
              </dd>
              <dt>Payment method</dt>
              <dd>{methodLabel(x.method)}</dd>
              {x.monthlyBill ? (
                <>
                  <dt>Monthly bill</dt>
                  <dd>
                    <Link href="/money/monthly-bills">{x.monthlyBill}</Link> (added by itself)
                  </dd>
                </>
              ) : null}
              {x.note ? (
                <>
                  <dt>Note</dt>
                  <dd>{x.note}</dd>
                </>
              ) : null}
              <dt>Added by</dt>
              <dd>{x.by ?? "—"}</dd>
            </dl>
          </CardContent>
        </Card>
        <Card>
          <CardHeader title="Bill photo" level={3} />
          <CardContent className="stack">
            {d.bill ? (
              <a href={d.bill.url} target="_blank" rel="noreferrer">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={d.bill.thumbUrl ?? d.bill.url}
                  loading="lazy"
                  alt={`Bill photo ${x.number}`}
                  style={{ maxWidth: "100%", maxHeight: 360, borderRadius: 8, border: "1px solid var(--line)" }}
                />
              </a>
            ) : d.sampleBill ? (
              <div className="card" style={{ padding: 16, background: "var(--paper, #fffdf6)", maxWidth: 320 }}>
                <div className="muted" style={{ fontSize: 12 }}>
                  Sample data – no real photo
                </div>
                <div style={{ fontWeight: 600, margin: "6px 0" }}>{x.paidTo}</div>
                <div className="mono" style={{ fontSize: 12 }}>
                  {d.sampleBill}
                </div>
                <div className="row" style={{ justifyContent: "space-between", marginTop: 10 }}>
                  <span>{x.expenseType}</span>
                  <b>{lkr(x.amount)}</b>
                </div>
              </div>
            ) : (
              <Alert tone="warning">No bill photo yet. Every expense needs one (rule 11).</Alert>
            )}
            {!d.bill && x.status !== "REJECTED" ? (
              <UploadBox accept={ACCEPT.BILL_PHOTO} capture="environment" onFiles={(f) => void attach(f)}>
                ⇪ Attach the bill photo
              </UploadBox>
            ) : null}
          </CardContent>
        </Card>
      </div>
    </>
  );
}
