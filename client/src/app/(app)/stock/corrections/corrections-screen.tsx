"use client";

/* Fix a stock mistake – prototype d-stock/stock-correction.html (BRD D7, rule 7). Status (lost / damaged / found),
   branch (wrong place) or carat (data error), always with a reason. Nothing changes until the owner approves. */
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { Badge, StatusPill } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { DataTable, type Column } from "@/components/ui/data-table";
import { Alert, Field, Input, Options, Select, Textarea } from "@/components/ui/form";
import { GemSvg } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage, fieldErrors } from "@/lib/api/client";
import { carat, dateTime } from "@/lib/format";
import type { GemView } from "@/lib/gems";
import { branchLabel, useSession } from "@/lib/session";
import {
  CORRECTION_KINDS,
  CORRECTION_STATUS,
  correctionState,
  type CorrectionKind,
  type CorrectionView,
} from "@/lib/stock";
import { useLoad } from "@/lib/use-load";

export function CorrectionsScreen() {
  const params = useSearchParams();
  const toast = useToast();
  const { branch, branches, branchName } = useSession();
  const { data: list, reload } = useLoad(() => api.GET("/api/stock-corrections"), [branch]);
  const [code, setCode] = useState(params.get("gem") ?? "");
  const [gem, setGem] = useState<GemView | null>(null);
  const [looked, setLooked] = useState("");
  const [kind, setKind] = useState<CorrectionKind | "">("");
  const [to, setTo] = useState("");
  const [reason, setReason] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  /** Looks the gem up when the code is complete (Enter, leaving the box, or opened from a stock check). */
  const lookUp = async (text: string) => {
    const t = text.trim();
    if (!t || t === looked) return;
    setLooked(t);
    const { data } = await api.GET("/api/gems/{code}", { params: { path: { code: t } } });
    setGem(data ?? null);
    setErrors((o) => ({ ...o, gem: data ? "" : "Unknown gem code" }));
  };
  // a code from the stock check (?gem=GEM-00116) is shown until the box is changed
  const asked = params.get("gem");
  const { data: askedGem } = useLoad(
    async () => (asked ? api.GET("/api/gems/{code}", { params: { path: { code: asked } } }) : { data: null }),
    [asked],
  );
  const shown = gem ?? (asked && code === asked && !looked ? (askedGem ?? null) : null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const missing: Record<string, string> = {};
    if (!code.trim()) missing.gem = "Required";
    if (!kind) missing.kind = "Required";
    if (!to.trim()) missing.to = "Required";
    if (!reason.trim()) missing.reason = "A reason is required.";
    if (Object.keys(missing).length) {
      setErrors(missing);
      return toast("Please fill in the marked fields", undefined, "error");
    }
    setBusy(true);
    const { data, error } = await api.POST("/api/stock-corrections", {
      body: { gem: code.trim(), kind: kind as CorrectionKind, to: to.trim(), reason: reason.trim() },
    });
    setBusy(false);
    if (!data) {
      setErrors(fieldErrors(error));
      return toast("Not sent", errorMessage(error), "error");
    }
    toast("Sent for owner approval", `${data.number} – nothing changes until the owner approves.`, "warning");
    setCode("");
    setGem(null);
    setLooked("");
    setKind("");
    setTo("");
    setReason("");
    setErrors({});
    reload();
  };

  const columns: Column<CorrectionView>[] = [
    {
      key: "no",
      header: "No.",
      cell: (c) => (
        <>
          <span className="mono nowrap">{c.number}</span>
          <br />
          <small className="muted">{dateTime(c.requestedAt)}</small>
        </>
      ),
    },
    {
      key: "gem",
      header: "Gem",
      cell: (c) => (
        <Link
          href={`/stock/gems/${c.gem.code}`}
          className="row"
          style={{ flexWrap: "nowrap", gap: 8, color: "inherit" }}
        >
          <GemSvg gem={c.gem.gemType} type={c.gem.productType} size={26} />
          <span>
            <b>{c.gem.name}</b>
            <br />
            <small className="mono muted">{c.gem.code}</small>
          </span>
        </Link>
      ),
    },
    { key: "change", header: "Change", cell: (c) => <b>{c.change}</b> },
    {
      key: "reason",
      header: "Reason",
      cell: (c) => (
        <>
          {c.reason}
          <br />
          <small className="muted">by {c.requestedBy}</small>
        </>
      ),
    },
    {
      key: "status",
      header: "Status",
      cell: (c) => {
        const s = correctionState(c.status);
        return (
          <>
            <Badge tone={s.tone}>{s.label}</Badge>
            {c.approvalNo ? (
              <>
                <br />
                <small className="muted mono">{c.approvalNo}</small>
              </>
            ) : null}
          </>
        );
      },
    },
  ];

  return (
    <>
      <PageHead
        title="Fix a stock mistake"
        sub="Every correction needs a reason and the owner's approval, so every change can be traced."
      />
      <div className="g-1-2 grid">
        <Card>
          <CardHeader title="New correction" level={3} />
          <form noValidate onSubmit={submit}>
            <CardContent className="stack" style={{ gap: 12 }}>
              <Field
                label="Gem"
                htmlFor="gem"
                required
                error={errors.gem}
                hint={
                  shown ? (
                    <span className="row" style={{ gap: 6 }}>
                      {shown.name} · {carat(shown.carat)} · {branchName(shown.branch)}{" "}
                      <StatusPill status={shown.status} />
                    </span>
                  ) : (
                    "Scan or type the code, e.g. GEM-00116"
                  )
                }
              >
                <Input
                  id="gem"
                  className="mono"
                  value={code}
                  placeholder="Scan or type GEM-…"
                  onChange={(e) => {
                    setCode(e.target.value);
                    setGem(null);
                    setLooked("");
                  }}
                  onBlur={() => void lookUp(code)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      e.preventDefault();
                      void lookUp(code);
                    }
                  }}
                />
              </Field>
              <Field label="What to correct" htmlFor="kind" required error={errors.kind}>
                <Select
                  id="kind"
                  value={kind}
                  onChange={(e) => {
                    setKind(e.target.value as CorrectionKind | "");
                    setTo("");
                    setErrors((o) => ({ ...o, kind: "", to: "" }));
                  }}
                >
                  <Options items={CORRECTION_KINDS} placeholder="Select…" />
                </Select>
              </Field>
              <Field label="New value" htmlFor="to" required error={errors.to}>
                {kind === "STATUS" ? (
                  <Select id="to" value={to} onChange={(e) => setTo(e.target.value)}>
                    <Options items={CORRECTION_STATUS} placeholder="Select…" />
                  </Select>
                ) : kind === "BRANCH" ? (
                  <Select id="to" value={to} onChange={(e) => setTo(e.target.value)}>
                    <Options
                      items={branches
                        .filter((b) => b.status !== "INACTIVE")
                        .map((b) => ({ value: b.code, label: branchLabel(b) }))}
                      placeholder="Select…"
                    />
                  </Select>
                ) : kind === "CARAT" ? (
                  <Input
                    id="to"
                    type="number"
                    step="0.01"
                    min="0.01"
                    inputMode="decimal"
                    placeholder={shown ? `currently ${shown.carat}` : ""}
                    value={to}
                    onChange={(e) => setTo(e.target.value)}
                  />
                ) : (
                  <Input id="to" disabled placeholder="Choose what to correct first" />
                )}
              </Field>
              <Field label="Reason" htmlFor="reason" required error={errors.reason}>
                <Textarea
                  id="reason"
                  maxLength={500}
                  placeholder="e.g. Found broken during stock check SCK-0002"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                />
              </Field>
              <Button variant="sapphire" type="submit" disabled={busy}>
                {busy ? "Sending…" : "Send for owner approval"}
              </Button>
              <Alert tone="info">Returns, cancellations and stock corrections need the owner&apos;s approval.</Alert>
            </CardContent>
          </form>
        </Card>
        <Card>
          <CardHeader title="Correction history" level={3} />
          {list ? (
            <DataTable columns={columns} rows={list} rowKey={(c) => c.number} stackOnPhone empty="No correction yet." />
          ) : (
            <p className="muted card-content">Loading…</p>
          )}
        </Card>
      </div>
    </>
  );
}
