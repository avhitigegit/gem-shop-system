"use client";

/* Return a gem to the supplier – prototype b-purchasing/supplier-returns.html (BRD B4). A wrong or faulty gem goes
   back with a reason and the refund; it leaves stock ("Returned to supplier") automatically. */
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { DataTable, type Column } from "@/components/ui/data-table";
import { useConfirm } from "@/components/ui/dialog";
import { Field, FormGrid, Input, Options, Select, Textarea } from "@/components/ui/form";
import { GemSvg } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage, fieldErrors } from "@/lib/api/client";
import { date, inCurrency, lkr } from "@/lib/format";
import { REFUND_METHODS, refundLabel, RETURN_REASONS, type RefundMethod, type ReturnRow } from "@/lib/purchasing";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

type Form = { gem: string; reasonType: string; details: string; refund: string; refundMethod: RefundMethod };
const EMPTY: Form = { gem: "", reasonType: "", details: "", refund: "", refundMethod: "CASH" };

export function ReturnsScreen() {
  const params = useSearchParams();
  const supplier = params.get("supplier") ?? "";
  const toast = useToast();
  const confirm = useConfirm();
  const { branch, currency, rates } = useSession();
  const { data: gems, reload: reloadGems } = useLoad(
    () => api.GET("/api/supplier-returns/gems", { params: { query: { supplier: supplier || undefined } } }),
    [supplier, branch],
  );
  const { data: returns, reload } = useLoad(() => api.GET("/api/supplier-returns"), []);
  const [f, setF] = useState<Form>(EMPTY);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const picked = gems?.find((g) => g.code === f.gem);

  const set = <K extends keyof Form>(k: K, v: Form[K]) => {
    setF((o) => ({ ...o, [k]: v }));
    setErrors((o) => ({ ...o, [k]: "" }));
  };
  const pick = (code: string) => {
    const g = gems?.find((x) => x.code === code);
    setF((o) => ({ ...o, gem: code, refund: g ? String(g.cost) : "" }));
    setErrors((o) => ({ ...o, gem: "", refund: "" }));
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const missing: Record<string, string> = {};
    for (const k of ["gem", "reasonType", "details", "refund"] as const) if (!f[k].trim()) missing[k] = "Required";
    if (Object.keys(missing).length) {
      setErrors(missing);
      return toast("Please fill in the marked fields", undefined, "error");
    }
    if (!picked) return;
    const ok = await confirm({
      title: `Return ${picked.code} to ${picked.supplierName}?`,
      text: "The gem will be removed from stock (status “Returned to supplier”).",
      yes: "Record return",
    });
    if (!ok) return;
    setBusy(true);
    const { data, error } = await api.POST("/api/supplier-returns", {
      body: {
        gem: f.gem,
        reasonType: f.reasonType,
        details: f.details.trim(),
        refund: Number(f.refund),
        refundMethod: f.refundMethod,
      },
    });
    setBusy(false);
    if (!data) {
      setErrors(fieldErrors(error));
      return toast("Not saved", errorMessage(error), "error");
    }
    toast("Returned to supplier", `${data.number} – stock corrected automatically`, "success");
    setF(EMPTY);
    reload();
    reloadGems();
  };

  const columns: Column<ReturnRow>[] = [
    {
      key: "no",
      header: "No.",
      cell: (r) => (
        <>
          <span className="mono">{r.number}</span>
          <br />
          <small className="muted">{date(r.returnedAt)}</small>
        </>
      ),
    },
    {
      key: "gem",
      header: "Gem",
      cell: (r) => (
        <Link
          href={`/stock/gems/${r.gemCode}`}
          className="row"
          style={{ flexWrap: "nowrap", gap: 8, color: "inherit" }}
        >
          <GemSvg gem={r.gemType} type={r.productType} size={26} />
          <span>
            <b>{r.gemName}</b>
            <br />
            <small className="muted mono">{r.gemCode}</small>
          </span>
        </Link>
      ),
    },
    {
      key: "supplier",
      header: "Supplier",
      cell: (r) => <Link href={`/stock/suppliers/${r.supplierCode}`}>{r.supplierName}</Link>,
    },
    { key: "reason", header: "Reason", cell: (r) => r.reason },
    {
      key: "refund",
      header: "Refund",
      num: true,
      cell: (r) => (
        <>
          {inCurrency(r.refund, currency, rates)}
          <br />
          <small className="muted">{refundLabel(r.refundMethod)}</small>
        </>
      ),
    },
  ];

  return (
    <>
      <PageHead
        title="Return a gem to the supplier"
        sub="A wrong or faulty gem can be returned to the supplier with a reason. Stock is corrected automatically."
      />
      <div className="g-1-2 grid">
        <Card>
          <CardHeader title="New supplier return" level={3} />
          <form noValidate onSubmit={submit}>
            <CardContent className="stack" style={{ gap: 12 }}>
              <Field
                label="Gem (in stock)"
                htmlFor="gem"
                required
                error={errors.gem}
                hint={
                  picked
                    ? `Cost ${lkr(picked.cost)} · bought ${date(picked.addedAt)} (${picked.purchaseNo ?? "—"})`
                    : supplier
                      ? "Only gems bought from this supplier are shown."
                      : undefined
                }
              >
                <Select id="gem" value={f.gem} onChange={(e) => pick(e.target.value)}>
                  <Options
                    items={(gems ?? []).map((g) => ({
                      value: g.code,
                      label: `${g.code} · ${g.name} · ${g.supplierName}`,
                    }))}
                    placeholder={gems && !gems.length ? "No gem can be returned" : "Select…"}
                  />
                </Select>
              </Field>
              <Field label="Reason" htmlFor="reasonType" required error={errors.reasonType}>
                <Select id="reasonType" value={f.reasonType} onChange={(e) => set("reasonType", e.target.value)}>
                  <Options items={RETURN_REASONS} placeholder="Select…" />
                </Select>
              </Field>
              <Field label="Details" htmlFor="details" required error={errors.details}>
                <Textarea
                  id="details"
                  maxLength={400}
                  placeholder="Describe the problem"
                  value={f.details}
                  onChange={(e) => set("details", e.target.value)}
                />
              </Field>
              <FormGrid>
                <Field label="Refund received (LKR)" htmlFor="refund" required error={errors.refund}>
                  <Input
                    id="refund"
                    type="number"
                    min="0"
                    inputMode="numeric"
                    value={f.refund}
                    onChange={(e) => set("refund", e.target.value)}
                  />
                </Field>
                <Field label="Refund method" htmlFor="method">
                  <Select
                    id="method"
                    value={f.refundMethod}
                    onChange={(e) => set("refundMethod", e.target.value as RefundMethod)}
                  >
                    <Options items={REFUND_METHODS} />
                  </Select>
                </Field>
              </FormGrid>
              <Button variant="sapphire" type="submit" disabled={busy}>
                {busy ? "Saving…" : "Record return & remove from stock"}
              </Button>
            </CardContent>
          </form>
        </Card>
        <Card>
          <CardHeader title="Returns to suppliers" level={3} />
          {returns ? (
            <DataTable
              columns={columns}
              rows={returns}
              rowKey={(r) => r.number}
              stackOnPhone
              empty="No gem returned to a supplier yet."
            />
          ) : (
            <p className="muted card-content">Loading…</p>
          )}
        </Card>
      </div>
    </>
  );
}
