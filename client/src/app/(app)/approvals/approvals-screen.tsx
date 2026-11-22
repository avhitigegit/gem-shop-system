"use client";

/* Approvals inbox – prototype k-owner-control/approvals.html (BRD K3; rules 2, 7, 11). The owner approves or rejects
   with one tap and an optional note; everyone else sees the status of their requests. Asked again every 4 seconds. */
import { useEffect, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Alert, Input, Select } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import { refreshWaiting, type ApprovalView } from "@/lib/approvals";
import { ago, dateTime, inCurrency } from "@/lib/format";
import { OWNER } from "@/lib/roles";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

type Tab = "pending" | "done";
type Type = ApprovalView["type"];

const ICON: Record<Type, string> = {
  BELOW_MIN: "💎",
  RETURN: "↩",
  CANCEL_INVOICE: "🧾",
  STOCK_CORRECTION: "📦",
  EXPENSE: "💸",
  MONTH_REOPEN: "🔓",
};
const TYPES: { value: Type; label: string }[] = [
  { value: "BELOW_MIN", label: "Below-minimum price" },
  { value: "RETURN", label: "Return / exchange" },
  { value: "CANCEL_INVOICE", label: "Invoice cancellation" },
  { value: "STOCK_CORRECTION", label: "Stock correction" },
  { value: "EXPENSE", label: "Large expense" },
  { value: "MONTH_REOPEN", label: "Re-open closed month" },
];

export function ApprovalsScreen() {
  const toast = useToast();
  const { role, branch, branchName, currency, rates } = useSession();
  const owner = role === OWNER;
  const [tab, setTab] = useState<Tab>("pending");
  const [type, setType] = useState<Type | "">("");
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState<string | null>(null);
  const { data, reload } = useLoad(
    () => api.GET("/api/approvals", { params: { query: { tab, type: type || undefined, size: 50 } } }),
    [tab, type, branch],
  );

  useEffect(() => {
    const t = setInterval(() => reload(), 4000);
    return () => clearInterval(t);
  }, [reload]);

  useEffect(() => {
    if (!data || !location.hash) return;
    document.getElementById(location.hash.slice(1))?.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [data]);

  const decide = async (a: ApprovalView, approve: boolean) => {
    setBusy(a.number);
    const body = { note: notes[a.number]?.trim() || undefined };
    const path = { number: a.number };
    const { error } = approve
      ? await api.POST("/api/approvals/{number}/approve", { params: { path }, body })
      : await api.POST("/api/approvals/{number}/reject", { params: { path }, body });
    setBusy(null);
    if (error) return toast("Not decided", errorMessage(error), "error");
    toast(approve ? "Approved ✓" : "Rejected", a.title, approve ? "success" : "error");
    refreshWaiting();
    reload();
  };

  const hash = typeof location !== "undefined" ? location.hash.slice(1) : "";

  return (
    <div style={{ maxWidth: 760 }}>
      <PageHead
        title="Approvals"
        sub={
          owner
            ? "Approve or reject with one tap – it works on the phone too."
            : "Requests that need the owner. Only the owner can approve or reject."
        }
      />
      <Alert tone="info" className="mb">
        Below-minimum prices, returns, invoice cancellations and stock corrections need the owner&apos;s approval.
      </Alert>
      <div className="row between mb">
        <div className="tabs" role="group" aria-label="Show">
          <button type="button" className={tab === "pending" ? "active" : ""} onClick={() => setTab("pending")}>
            Waiting ({data?.pending ?? 0})
          </button>
          <button type="button" className={tab === "done" ? "active" : ""} onClick={() => setTab("done")}>
            Decided
          </button>
        </div>
        <Select
          aria-label="Type"
          style={{ width: "auto" }}
          value={type}
          onChange={(e) => setType(e.target.value as Type | "")}
        >
          <option value="">All types</option>
          {TYPES.map((t) => (
            <option key={t.value} value={t.value}>
              {t.label}
            </option>
          ))}
        </Select>
      </div>
      <div className="stack">
        {!data ? <p className="muted">Loading…</p> : null}
        {data && !data.items.length ? (
          <Card>
            <div className="empty">{tab === "pending" ? "Nothing waiting for approval 🎉" : "No decisions yet."}</div>
          </Card>
        ) : null}
        {data?.items.map((a) => (
          <Card
            key={a.number}
            id={a.number}
            style={hash === a.number ? { outline: "2px solid var(--gold)" } : undefined}
          >
            <CardContent>
              <div className="row between" style={{ flexWrap: "nowrap", alignItems: "flex-start" }}>
                <div className="row" style={{ gap: 10, flexWrap: "nowrap", alignItems: "flex-start" }}>
                  <div style={{ fontSize: 22 }} aria-hidden="true">
                    {ICON[a.type] ?? "•"}
                  </div>
                  <div>
                    <Badge tone="outline">{a.typeLabel}</Badge>{" "}
                    <span className="mono muted" style={{ fontSize: 11 }}>
                      {a.number}
                    </span>
                    <br />
                    <b style={{ color: "var(--navy)" }}>{a.title}</b>
                  </div>
                </div>
                {a.amount != null ? (
                  <div className="right nowrap">
                    <b>{inCurrency(a.amount, currency, rates)}</b>
                  </div>
                ) : null}
              </div>
              <div className="muted mt-s" style={{ fontSize: 12 }}>
                {a.detail}
              </div>
              <div className="muted mt-s" style={{ fontSize: 11 }}>
                Requested by {a.requestedBy.name} · {branchName(a.branch)} · {dateTime(a.requestedAt)} (
                {ago(a.requestedAt)}){a.ref ? ` · ${a.ref}` : ""}
              </div>
              {a.status !== "PENDING" ? (
                <div className="mt-s">
                  {a.status === "APPROVED" ? (
                    <Badge tone="success">Approved</Badge>
                  ) : (
                    <Badge tone="danger">Rejected</Badge>
                  )}{" "}
                  <small className="muted">
                    by {a.decidedBy} · {dateTime(a.decidedAt)}
                    {a.note ? ` · “${a.note}”` : ""}
                  </small>
                </div>
              ) : null}
            </CardContent>
            {a.status === "PENDING" ? (
              owner ? (
                <div className="card-footer" style={{ justifyContent: "stretch" }}>
                  <Input
                    placeholder="Note (optional)"
                    aria-label={`Note for ${a.number}`}
                    maxLength={500}
                    style={{ flex: 1, minWidth: 140 }}
                    value={notes[a.number] ?? ""}
                    onChange={(e) => setNotes((o) => ({ ...o, [a.number]: e.target.value }))}
                  />
                  <Button
                    variant="destructive"
                    size="lg"
                    disabled={busy === a.number}
                    onClick={() => void decide(a, false)}
                  >
                    ✕ Reject
                  </Button>
                  <Button variant="success" size="lg" disabled={busy === a.number} onClick={() => void decide(a, true)}>
                    ✓ Approve
                  </Button>
                </div>
              ) : (
                <div className="card-footer" style={{ justifyContent: "flex-start" }}>
                  <Badge tone="warning" className="pending-pulse">
                    Waiting for the owner
                  </Badge>
                </div>
              )
            ) : null}
          </Card>
        ))}
      </div>
    </div>
  );
}
