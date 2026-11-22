"use client";

/* Buy gems from a supplier – prototype b-purchasing/new-purchase.html (BRD B2, B3; Guide 21). One cash purchase: date,
   supplier, branch, the gems bought with their prices, and the photo of the supplier's bill. Saving adds every gem to
   stock with its own code (no second entry). The step bar above comes from the "New gems coming in" flow. */
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { StatusPill } from "@/components/ui/badge";
import { Button, buttonClass } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Modal } from "@/components/ui/dialog";
import { Alert, Check, Field, FormGrid, Input, Options, Select } from "@/components/ui/form";
import { GemPicture, GemSvg } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { UploadBox } from "@/components/ui/upload-box";
import { api, errorMessage, fieldErrors } from "@/lib/api/client";
import { ACCEPT, discardFile, uploadFile } from "@/lib/files";
import { carat, lkr, todayInShop } from "@/lib/format";
import { CERT_OPTIONS, suggestedCert, useLists, type CertType } from "@/lib/gems";
import { suggestPrices, type PurchaseView } from "@/lib/purchasing";
import { ALL_BRANCHES, branchLabel, useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

type Line = {
  key: number;
  gemType: string;
  productType: string;
  name: string;
  carat: string;
  colour: string;
  shape: string;
  treatment: string;
  certType: CertType | "";
  certLab: string;
  certNo: string;
  certDate: string;
  cost: string;
  price: string;
  min: string;
  notReady: boolean;
};
type LineText = Exclude<keyof Line, "key" | "notReady" | "certType">;

const blankLine = (key: number): Line => ({
  key,
  gemType: "",
  productType: "Loose gem",
  name: "",
  carat: "",
  colour: "",
  shape: "",
  treatment: "",
  certType: "",
  certLab: "",
  certNo: "",
  certDate: "",
  cost: "",
  price: "",
  min: "",
  notReady: false,
});

const NEED: (keyof Line)[] = [
  "gemType",
  "productType",
  "name",
  "carat",
  "colour",
  "shape",
  "certType",
  "cost",
  "price",
  "min",
];

export function BuyScreen() {
  const router = useRouter();
  const params = useSearchParams();
  const toast = useToast();
  const lists = useLists();
  const { branch, branches } = useSession();
  const open = branches.filter((b) => b.status !== "INACTIVE");
  const today = todayInShop();
  const { data: suppliers } = useLoad(() => api.GET("/api/suppliers"), []);

  const [head, setHead] = useState(() => ({
    date: today,
    supplier: params.get("supplier") ?? "",
    branch: branch !== ALL_BRANCHES ? branch : (open.find((b) => b.status === "ACTIVE")?.code ?? ""),
    notes: "",
  }));
  const [lines, setLines] = useState<Line[]>(() => [blankLine(1)]);
  const [bill, setBill] = useState<{ id: string; name: string } | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState<PurchaseView | null>(null);

  const treatments = lists.get("TREATMENT");
  const defaultTreatment = treatments.includes("Heated") ? "Heated" : (treatments[0] ?? "");
  const treatmentOf = (l: Line) => l.treatment || defaultTreatment;
  const total = lines.reduce((s, l) => s + (Number(l.cost) || 0), 0);

  const setH = (k: keyof typeof head, v: string) => {
    setHead((o) => ({ ...o, [k]: v }));
    setErrors((o) => ({ ...o, [k]: "" }));
  };
  const err = (i: number, k: string) => errors[`lines[${i}].${k}`];
  const change = (i: number, patch: Partial<Line>) => {
    setLines((o) => o.map((l, j) => (j === i ? { ...l, ...patch } : l)));
    setErrors((o) => {
      const n = { ...o };
      for (const k of Object.keys(patch)) delete n[`lines[${i}].${k}`];
      return n;
    });
  };
  const chooseGem = (i: number, gemType: string) => {
    const l = lines[i];
    change(i, {
      gemType,
      certType: gemType ? suggestedCert(gemType) : l.certType,
      name: l.name || (gemType ? gemType + (l.productType === "Loose gem" ? "" : ` ${l.productType}`) : ""),
    });
  };
  /** Typing the cost suggests the selling and minimum prices once (prototype: ×1.6 and ×1.35). */
  const costDone = (i: number) => {
    const l = lines[i];
    const c = Number(l.cost);
    if (!c || l.price) return;
    change(i, { price: String(suggestPrices(c).price), min: String(suggestPrices(c).min) });
    toast("Prices suggested", "Selling ×1.6, minimum ×1.35 – change as needed.");
  };
  const addLine = () => setLines((o) => [...o, blankLine(Math.max(0, ...o.map((l) => l.key)) + 1)]);
  const removeLine = (i: number) => {
    setLines((o) => o.filter((_, j) => j !== i));
    setErrors({});
  };

  const onBill = async (files: File[]) => {
    const { data, error } = await uploadFile(files[0], "BILL_PHOTO");
    if (!data) return toast("Bill not uploaded", errorMessage(error), "error");
    if (bill) void discardFile(bill.id);
    setBill({ id: data.id, name: data.name });
    setErrors((o) => ({ ...o, billPhoto: "" }));
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const missing: Record<string, string> = {};
    for (const k of ["date", "supplier", "branch"] as const) if (!head[k]) missing[k] = "Required";
    if (!bill) missing.billPhoto = "Bill photo is required.";
    lines.forEach((l, i) => {
      const need: (keyof Line)[] = l.certType === "LAB" ? [...NEED, "certLab", "certNo", "certDate"] : NEED;
      for (const k of need) if (!String(l[k]).trim()) missing[`lines[${i}].${k}`] = "Required";
    });
    if (!lines.length) return toast("Add at least one gem", undefined, "error");
    if (Object.keys(missing).length) {
      setErrors(missing);
      return toast(
        missing.billPhoto && Object.keys(missing).length === 1
          ? "Bill photo required"
          : "Please fill in the marked fields",
        missing.billPhoto ? "Take a photo of the supplier's bill." : undefined,
        "error",
      );
    }
    const wrong = lines.findIndex((l) => Number(l.min) > Number(l.price) || Number(l.min) < Number(l.cost));
    if (wrong >= 0) {
      setErrors({ [`lines[${wrong}].min`]: "Must be between cost and selling price." });
      return toast(
        `Gem ${wrong + 1}: check the minimum price`,
        "Minimum must be between cost and selling price.",
        "error",
      );
    }
    const body = {
      date: head.date,
      supplier: head.supplier,
      branch: head.branch,
      billPhoto: bill!.id,
      notes: head.notes.trim() || undefined,
      lines: lines.map((l) => {
        const lab = l.certType === "LAB";
        return {
          name: l.name.trim(),
          productType: l.productType,
          gemType: l.gemType,
          carat: Number(l.carat),
          colour: l.colour,
          shape: l.shape,
          treatment: treatmentOf(l),
          certType: l.certType as CertType,
          certLab: lab ? l.certLab : undefined,
          certNo: lab ? l.certNo.trim() : undefined,
          certDate: lab ? l.certDate : undefined,
          cost: Number(l.cost),
          price: Number(l.price),
          min: Number(l.min),
          notReady: l.notReady,
        };
      }),
    };
    setBusy(true);
    const { data, error } = await api.POST("/api/purchases", { body });
    setBusy(false);
    if (!data) {
      setErrors(fieldErrors(error));
      return toast("Not saved", errorMessage(error), "error");
    }
    setSaved(data);
  };

  const notReady = saved?.gems.some((g) => g.status === "NOT_READY");

  return (
    <>
      <PageHead
        title="Buy gems from a supplier"
        sub="Record what you bought, what each gem cost and the photo of the supplier's bill. The gems are added to stock at the chosen branch and get their own code."
      />
      <form noValidate className="stack" onSubmit={submit}>
        <Card>
          <CardContent>
            <FormGrid className="g4-form">
              <Field label="Date" htmlFor="date" required error={errors.date}>
                <Input
                  id="date"
                  type="date"
                  max={today}
                  value={head.date}
                  onChange={(e) => setH("date", e.target.value)}
                />
              </Field>
              <Field
                label="Supplier"
                htmlFor="supplier"
                required
                error={errors.supplier}
                hint={
                  <>
                    New supplier? <Link href="/stock/suppliers">Add it in Suppliers</Link>
                  </>
                }
              >
                <Select id="supplier" value={head.supplier} onChange={(e) => setH("supplier", e.target.value)}>
                  <Options
                    items={(suppliers ?? []).map((s) => ({ value: s.code, label: s.name }))}
                    placeholder="Select supplier…"
                  />
                </Select>
              </Field>
              <Field label="Add to branch stock" htmlFor="branch" required error={errors.branch}>
                <Select id="branch" value={head.branch} onChange={(e) => setH("branch", e.target.value)}>
                  <Options items={open.map((b) => ({ value: b.code, label: branchLabel(b) }))} placeholder="Select…" />
                </Select>
              </Field>
              <Field label="Payment">
                <Input value="Cash" readOnly />
              </Field>
            </FormGrid>
          </CardContent>
        </Card>

        <Card>
          <CardHeader
            title="Gems bought"
            actions={
              <Button type="button" variant="outline" onClick={addLine}>
                + Add gem
              </Button>
            }
          >
            <p>Each gem is unique and gets its own code. The certificate type is suggested from the gem type.</p>
          </CardHeader>
          <CardContent>
            {lines.length ? null : <div className="empty">Add the gems you bought.</div>}
            {lines.map((l, i) => (
              <div key={l.key} className="card mb-s buy-line">
                <div className="card-content">
                  <div className="row between mb-s">
                    <span className="row" style={{ gap: 8 }}>
                      <span className="gem-pic" style={{ width: 44, height: 44 }}>
                        <GemPicture gem={l.gemType || "Other"} type={l.productType || "Loose gem"} size={36} box={44} />
                      </span>
                      <b>Gem {i + 1}</b>
                    </span>
                    {lines.length > 1 ? (
                      <Button type="button" variant="ghost" size="sm" onClick={() => removeLine(i)}>
                        Remove
                      </Button>
                    ) : null}
                  </div>
                  <FormGrid className="buy-grid">
                    <Field label="Gem type" htmlFor={`gem${i}`} required error={err(i, "gemType")}>
                      <Select id={`gem${i}`} value={l.gemType} onChange={(e) => chooseGem(i, e.target.value)}>
                        <Options items={lists.get("GEM_TYPE")} placeholder="Select…" />
                      </Select>
                    </Field>
                    <Field label="Product type" htmlFor={`type${i}`} required error={err(i, "productType")}>
                      <Select
                        id={`type${i}`}
                        value={l.productType}
                        onChange={(e) => change(i, { productType: e.target.value })}
                      >
                        <Options items={lists.get("PRODUCT_TYPE")} placeholder="Select…" />
                      </Select>
                    </Field>
                    <Field label="Name" htmlFor={`name${i}`} required error={err(i, "name")}>
                      <Input
                        id={`name${i}`}
                        maxLength={120}
                        value={l.name}
                        onChange={(e) => change(i, { name: e.target.value })}
                      />
                    </Field>
                    <Field label="Carat" htmlFor={`carat${i}`} required error={err(i, "carat")}>
                      <Input
                        id={`carat${i}`}
                        type="number"
                        min="0.01"
                        step="0.01"
                        inputMode="decimal"
                        value={l.carat}
                        onChange={(e) => change(i, { carat: e.target.value })}
                      />
                    </Field>
                    <Field label="Colour" htmlFor={`colour${i}`} required error={err(i, "colour")}>
                      <Select
                        id={`colour${i}`}
                        value={l.colour}
                        onChange={(e) => change(i, { colour: e.target.value })}
                      >
                        <Options items={lists.get("COLOUR")} placeholder="Select…" />
                      </Select>
                    </Field>
                    <Field label="Shape" htmlFor={`shape${i}`} required error={err(i, "shape")}>
                      <Select id={`shape${i}`} value={l.shape} onChange={(e) => change(i, { shape: e.target.value })}>
                        <Options items={lists.get("SHAPE")} placeholder="Select…" />
                      </Select>
                    </Field>
                    <Field label="Treatment" htmlFor={`treat${i}`} error={err(i, "treatment")}>
                      <Select
                        id={`treat${i}`}
                        value={treatmentOf(l)}
                        onChange={(e) => change(i, { treatment: e.target.value })}
                      >
                        <Options items={treatments} />
                      </Select>
                    </Field>
                    <Field label="Certificate" htmlFor={`cert${i}`} required error={err(i, "certType")}>
                      <Select
                        id={`cert${i}`}
                        value={l.certType}
                        onChange={(e) => change(i, { certType: e.target.value as CertType | "" })}
                      >
                        <Options items={CERT_OPTIONS} placeholder="Select…" />
                      </Select>
                    </Field>
                    {l.certType === "LAB" ? (
                      <>
                        <Field label="Lab name" htmlFor={`lab${i}`} required error={err(i, "certLab")}>
                          <Select
                            id={`lab${i}`}
                            value={l.certLab}
                            onChange={(e) => change(i, { certLab: e.target.value })}
                          >
                            <Options items={lists.get("LAB")} placeholder="Select lab…" />
                          </Select>
                        </Field>
                        <Field label="Certificate number" htmlFor={`no${i}`} required error={err(i, "certNo")}>
                          <Input
                            id={`no${i}`}
                            className="mono"
                            maxLength={60}
                            value={l.certNo}
                            onChange={(e) => change(i, { certNo: e.target.value })}
                          />
                        </Field>
                        <Field label="Certificate date" htmlFor={`cdate${i}`} required error={err(i, "certDate")}>
                          <Input
                            id={`cdate${i}`}
                            type="date"
                            max={today}
                            value={l.certDate}
                            onChange={(e) => change(i, { certDate: e.target.value })}
                          />
                        </Field>
                      </>
                    ) : null}
                    {money(i, "cost", "Cost (LKR)")}
                    {money(i, "price", "Selling price")}
                    {money(i, "min", "Minimum price")}
                  </FormGrid>
                  <div className="mt-s">
                    <Check checked={l.notReady} onChange={(v) => change(i, { notReady: v })}>
                      Not ready to sell yet{" "}
                      <small className="muted">(e.g. needs cutting or polishing – tick only if needed)</small>
                    </Check>
                  </div>
                </div>
              </div>
            ))}
          </CardContent>
        </Card>

        <div className="g2 grid">
          <Card>
            <CardHeader title="Supplier's bill photo" level={3} />
            <CardContent>
              <Field label="Bill photo" required error={errors.billPhoto}>
                <UploadBox
                  accept={ACCEPT.BILL_PHOTO}
                  capture="environment"
                  done={bill?.name}
                  onFiles={(fl) => void onBill(fl)}
                >
                  ⇪ Take a photo of the bill / upload
                </UploadBox>
              </Field>
            </CardContent>
          </Card>
          <Card>
            <CardContent>
              <div className="row between">
                <span>Gems</span>
                <b>{lines.length}</b>
              </div>
              <div className="row between mt-s">
                <span>Total paid in cash</span>
                <b style={{ fontSize: 22, color: "var(--navy)" }}>{lkr(total)}</b>
              </div>
              <Field label="Notes" htmlFor="notes" className="mt">
                <Input id="notes" maxLength={500} value={head.notes} onChange={(e) => setH("notes", e.target.value)} />
              </Field>
            </CardContent>
            <div className="card-footer">
              <Link className={buttonClass("outline")} href="/stock/purchases">
                Cancel
              </Link>
              <Button variant="sapphire" type="submit" disabled={busy}>
                {busy ? "Saving…" : "Save purchase & add to stock"}
              </Button>
            </div>
          </Card>
        </div>
      </form>

      <Modal
        open={!!saved}
        onOpenChange={(o) => !o && router.push("/stock/purchases")}
        title="Purchase saved ✓"
        desc={saved ? `${saved.number} · ${saved.gems.length} gem(s) added to ${saved.branchName} stock` : undefined}
        footer={
          saved ? (
            <>
              <Link className={buttonClass("outline")} href="/stock/purchases">
                Purchases
              </Link>
              <Link
                className={buttonClass("sapphire")}
                href={`/stock/tags?gems=${saved.gems.map((g) => g.code).join(",")}`}
              >
                Print tags
              </Link>
            </>
          ) : undefined
        }
      >
        {saved ? (
          <>
            <ul className="list">
              {saved.gems.map((g) => (
                <li key={g.code}>
                  <GemSvg gem={g.gemType} type={g.productType} size={28} />
                  <span style={{ flex: 1 }}>
                    <b>{g.name}</b>
                    <br />
                    <small className="muted mono">
                      {g.code} · {carat(g.carat)}
                    </small>
                  </span>
                  <StatusPill status={g.status} />
                </li>
              ))}
            </ul>
            <Alert tone="info" className="mt">
              {notReady ? (
                <>
                  Gems marked &quot;Not ready to sell&quot; cannot be sold yet. When one is ready, open it in All gems
                  and press <b>Mark ready to sell</b>.
                </>
              ) : (
                "Next: print the tags and stick them on the boxes. The gems are ready to sell."
              )}
            </Alert>
          </>
        ) : null}
      </Modal>
    </>
  );

  function money(i: number, k: "cost" | "price" | "min", label: string) {
    const l = lines[i];
    return (
      <Field label={label} htmlFor={`${k}${i}`} required error={err(i, k)}>
        <Input
          id={`${k}${i}`}
          type="number"
          min={k === "cost" ? "0" : "1"}
          inputMode="numeric"
          value={l[k]}
          onChange={(e) => change(i, { [k]: e.target.value } as Partial<Record<LineText, string>>)}
          onBlur={k === "cost" ? () => costDone(i) : undefined}
        />
      </Field>
    );
  }
}
