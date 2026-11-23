"use client";

/* Tax pack – prototype n-accountant/tax-pack.html (Guide 74; BRD N5). Every report for any months in one ZIP folder:
   Excel and / or PDF, with the bill photos of the expenses and purchases. The branch at the top filters (All branches
   = the whole shop). "Earlier downloads" are remembered in this browser; downloading one again builds it fresh. */
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import { Check, Field, FormGrid, Options, Select } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api/client";
import { downloadFile } from "@/lib/files";
import { dateTime, lkr, month, todayInShop } from "@/lib/format";
import { query } from "@/lib/reports";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

const PARTS = [
  ["SALES", "Sales & invoices"],
  ["PURCHASES", "Purchases by supplier"],
  ["EXPENSES", "Expenses"],
  ["PROFIT_LOSS", "Profit & Loss"],
  ["COMMISSIONS", "Commissions"],
  ["CLOSINGS", "Daily closings"],
  ["CREDIT_NOTES", "Credit notes"],
] as const;
type Part = (typeof PARTS)[number][0];
type Format = "BOTH" | "XLSX" | "PDF";
const FORMATS = [
  { value: "BOTH", label: "Excel (.xlsx) + PDF" },
  { value: "XLSX", label: "Excel only" },
  { value: "PDF", label: "PDF only" },
];

type Pack = {
  name: string;
  at: string;
  from: string;
  to: string;
  parts: Part[];
  format: Format;
  photos: boolean;
  branch: string;
};
const KEY = "gemshop.taxpacks";

function lastMonth() {
  const [y, m] = todayInShop().split("-").map(Number);
  const d = new Date(Date.UTC(y, m - 2, 1));
  return d.toISOString().slice(0, 7);
}

function readHistory(): Pack[] {
  try {
    return JSON.parse(localStorage.getItem(KEY) ?? "[]") as Pack[];
  } catch {
    return [];
  }
}

export function TaxPackScreen() {
  const { branch, branchName: nameOf } = useSession();
  const branchName = nameOf(branch);
  const toast = useToast();
  const [from, setFrom] = useState(lastMonth);
  const [to, setTo] = useState(lastMonth);
  const [parts, setParts] = useState<Part[]>(PARTS.map((p) => p[0]));
  const [format, setFormat] = useState<Format>("BOTH");
  const [photos, setPhotos] = useState(true);
  const [busy, setBusy] = useState(false);
  const [history, setHistory] = useState<Pack[]>(readHistory);

  const { data: pv } = useLoad(
    () => api.GET("/api/tax-pack/preview", { params: { query: { from, to } } }),
    [from, to, branch],
  );
  const months = (pv?.months ?? [from]).map((m) => ({ value: m, label: month(m) }));

  const build = async (p: Omit<Pack, "name" | "at" | "branch">) => {
    if (!p.parts.length) {
      toast("Choose at least one part", "", "error");
      return;
    }
    setBusy(true);
    try {
      const name = `Tax pack ${p.from === p.to ? p.from : `${p.from} to ${p.to}`}`;
      const q = query({
        from: p.from,
        to: p.to,
        parts: p.parts.join(","),
        format: p.format,
        photos: p.photos ? "true" : "false",
      });
      if (!(await downloadFile(`/api/tax-pack?${q}`, `${name}.zip`))) {
        toast("Not downloaded", "Please try again.", "error");
        return;
      }
      toast("Tax pack ready", `${name} – ${p.parts.length} report(s)${p.photos ? " + bill photos" : ""}`, "success");
      const next = [{ ...p, name, at: new Date().toISOString(), branch: branchName }, ...history].slice(0, 8);
      setHistory(next);
      try {
        localStorage.setItem(KEY, JSON.stringify(next));
      } catch {
        /* private window – the list is only a convenience */
      }
    } finally {
      setBusy(false);
    }
  };

  const toggle = (p: Part, on: boolean) => setParts((x) => (on ? [...x, p] : x.filter((y) => y !== p)));
  const fmtLabel = (f: Format) => FORMATS.find((x) => x.value === f)?.label ?? f;

  return (
    <>
      <PageHead
        title="Tax pack"
        sub="Download every report for any months (Excel / PDF), with the bill photos, in one ZIP folder."
      />
      <div className="g-2-1 grid">
        <Card>
          <CardContent>
            <FormGrid>
              <Field label="From month" htmlFor="tf" required>
                <Select id="tf" value={from} onChange={(e) => setFrom(e.target.value)}>
                  <Options items={months} />
                </Select>
              </Field>
              <Field label="To month" htmlFor="tt" required>
                <Select id="tt" value={to} onChange={(e) => setTo(e.target.value)}>
                  <Options items={months} />
                </Select>
              </Field>
              <Field label="Branch" htmlFor="tb">
                <div id="tb" className="muted" style={{ paddingTop: 8 }}>
                  {branchName} <small>(change it at the top)</small>
                </div>
              </Field>
              <Field label="Format" htmlFor="tfm">
                <Select id="tfm" value={format} onChange={(e) => setFormat(e.target.value as Format)}>
                  <Options items={FORMATS} />
                </Select>
              </Field>
              <div className="field full">
                <label>Include</label>
                <div className="g2 grid" style={{ gap: 6 }}>
                  {PARTS.map(([k, l]) => (
                    <Check key={k} checked={parts.includes(k)} onChange={(v) => toggle(k, v)}>
                      {l}
                    </Check>
                  ))}
                </div>
              </div>
              <div className="full">
                <Check checked={photos} onChange={setPhotos}>
                  Include the bill photos of expenses and purchases
                </Check>
              </div>
            </FormGrid>
          </CardContent>
          <CardFooter>
            <Button variant="sapphire" disabled={busy} onClick={() => void build({ from, to, parts, format, photos })}>
              {busy ? "Preparing…" : "⇩ Download tax pack"}
            </Button>
          </CardFooter>
        </Card>
        <Card style={{ alignSelf: "start" }}>
          <CardHeader title="Preview" level={3} />
          <CardContent>
            {!pv ? (
              <p className="muted">Loading…</p>
            ) : (
              <dl className="dl" style={{ gridTemplateColumns: "1fr auto" }}>
                <dt>Months</dt>
                <dd className="right">
                  {month(pv.from)}
                  {pv.from !== pv.to ? ` – ${month(pv.to)}` : ""}
                </dd>
                <dt>Branch</dt>
                <dd className="right">{branchName}</dd>
                <dt>Invoices</dt>
                <dd className="right">{pv.invoices}</dd>
                <dt>Net sales</dt>
                <dd className="right">{lkr(pv.netSales)}</dd>
                <dt>Net profit</dt>
                <dd className="right">{lkr(pv.netProfit)}</dd>
                <dt>Bill photos</dt>
                <dd className="right">{pv.billPhotos}</dd>
              </dl>
            )}
          </CardContent>
        </Card>
      </div>
      <Card className="mt">
        <CardHeader title="Earlier downloads" level={3} />
        <CardContent>
          {!history.length ? (
            <div className="muted">None yet on this computer.</div>
          ) : (
            <ul className="list">
              {history.map((h) => (
                <li key={h.at}>
                  <span aria-hidden>📦</span>
                  <div style={{ flex: 1 }}>
                    <b>{h.name}</b>
                    <br />
                    <small className="muted">
                      {dateTime(h.at)} · {h.branch} · {fmtLabel(h.format)} · {h.parts.length} report(s)
                      {h.photos ? " + bill photos" : ""}
                    </small>
                  </div>
                  <Button size="sm" variant="outline" disabled={busy} onClick={() => void build(h)}>
                    Download again
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </>
  );
}
