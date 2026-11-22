"use client";

/* Exchange rates – prototype a-setup/currencies.html (Guide 7). LKR, USD and EUR only. The owner enters the rate of
   the day; every new sale and conversion uses it straight away. Earlier sales keep their own rate. */
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import { DataTable } from "@/components/ui/data-table";
import { Alert, Field, Input } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage, fieldErrors } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { date, dateTime, num } from "@/lib/format";
import { reloadSession, useLoad } from "@/lib/use-load";

type RatesView = components["schemas"]["RatesView"];
type RateDay = components["schemas"]["RateDay"];

/** The BRD example ring. */
const EXAMPLE_LKR = 450000;

export function RatesScreen() {
  const { data, setData } = useLoad(() => api.GET("/api/settings/rates"), []);
  return (
    <>
      <PageHead
        title="Currencies & exchange rates"
        sub="Three currencies only: LKR, US Dollar and Euro. USD and EUR cash is changed to LKR at the day's rate."
      />
      <Alert tone="info" className="mb">
        Prices and payments use LKR, USD and EUR only. Other currencies cannot be added.
      </Alert>
      {data ? (
        <Rates key={data.updatedAt ?? "none"} rates={data} onSaved={setData} />
      ) : (
        <p className="muted">Loading…</p>
      )}
    </>
  );
}

function Rates({ rates, onSaved }: { rates: RatesView; onSaved: (r: RatesView) => void }) {
  const toast = useToast();
  const [usd, setUsd] = useState(rates.usd != null ? String(Number(rates.usd)) : "");
  const [eur, setEur] = useState(rates.eur != null ? String(Number(rates.eur)) : "");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const save = async (e: FormEvent) => {
    e.preventDefault();
    const bad: Record<string, string> = {};
    if (!(Number(usd) >= 1)) bad.usd = "Enter the LKR for 1 USD.";
    if (!(Number(eur) >= 1)) bad.eur = "Enter the LKR for 1 EUR.";
    setErrors(bad);
    if (Object.keys(bad).length) return;
    setBusy(true);
    const { data, error } = await api.POST("/api/settings/rates", { body: { usd: Number(usd), eur: Number(eur) } });
    setBusy(false);
    if (!data) {
      setErrors(fieldErrors(error));
      return toast("Not saved", errorMessage(error), "error");
    }
    toast("Rates saved", "Used for all new sales and conversions from now on.", "success");
    onSaved(data);
    void reloadSession(); // the currency switch in the top bar
  };

  const u = Number(usd) || 1;
  const e2 = Number(eur) || 1;
  return (
    <div className="g-1-2 grid">
      <form className="card" noValidate onSubmit={save}>
        <CardHeader level={3} title="Today's rates">
          {rates.updatedAt ? (
            <small className="muted">
              Updated {dateTime(rates.updatedAt)}
              {rates.updatedBy ? ` by ${rates.updatedBy}` : ""}
            </small>
          ) : null}
        </CardHeader>
        <CardContent className="stack" style={{ gap: 14 }}>
          <div className="row between" style={{ flexWrap: "nowrap" }}>
            <div>
              <b>🇱🇰 LKR</b>
              <br />
              <small className="muted">Sri Lankan Rupee – base currency</small>
            </div>
            <span className="badge badge-navy">1.00</span>
          </div>
          <Field label="🇺🇸 1 USD = LKR" htmlFor="usd" required error={errors.usd}>
            <Input
              id="usd"
              size="lg"
              type="number"
              step={0.01}
              min={1}
              value={usd}
              onChange={(e) => setUsd(e.target.value)}
            />
          </Field>
          <Field label="🇪🇺 1 EUR = LKR" htmlFor="eur" required error={errors.eur}>
            <Input
              id="eur"
              size="lg"
              type="number"
              step={0.01}
              min={1}
              value={eur}
              onChange={(e) => setEur(e.target.value)}
            />
          </Field>
          <Alert tone="info">
            Example: a ring at <b>LKR {num(EXAMPLE_LKR)}</b> shows as <b>USD {num(EXAMPLE_LKR / u)}</b> and{" "}
            <b>EUR {num(EXAMPLE_LKR / e2)}</b>.
          </Alert>
        </CardContent>
        <CardFooter>
          <Button variant="sapphire" type="submit" disabled={busy}>
            {busy ? "Saving…" : "Save today's rates"}
          </Button>
        </CardFooter>
      </form>
      <Card>
        <CardHeader level={3} title="Rate history" />
        <DataTable<RateDay>
          columns={[
            { key: "d", header: "Date", cell: (r) => date(r.date) },
            { key: "u", header: "USD", num: true, cell: (r) => (r.usd != null ? num(r.usd) : "—") },
            { key: "e", header: "EUR", num: true, cell: (r) => (r.eur != null ? num(r.eur) : "—") },
            { key: "b", header: "By", cell: (r) => r.by ?? "—" },
          ]}
          rows={rates.history}
          rowKey={(r) => r.date}
          empty="No rates entered yet."
        />
      </Card>
    </div>
  );
}
