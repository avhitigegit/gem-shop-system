"use client";

/* Start the day – prototype i-daily-closing/opening-cash.html (Guide 14; BRD I1, rule 9). The cash in the drawer per
   currency, suggested from the last closing's counted cash. Can be corrected until the day is closed. */
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import { DataTable } from "@/components/ui/data-table";
import { Alert, Field, Input } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import { DRAWER, DRAWER_LABEL, drawerLkr, type CashDay, type DrawerAmounts, type DrawerKey } from "@/lib/cash";
import { date, lkr, num, time } from "@/lib/format";
import { useSession } from "@/lib/session";
import { CashDayState, useCashDay } from "../use-cash-day";

export function StartDayScreen() {
  const { data, error, setData } = useCashDay();
  if (!data)
    return (
      <>
        <PageHead title="Opening cash" sub="Count the drawer at the start of the day, for each currency." />
        <CashDayState error={error} />
      </>
    );
  return (
    <OpeningForm key={`${data.branch}|${data.day}|${data.opening?.enteredAt ?? ""}`} day={data} onSaved={setData} />
  );
}

const asText = (a: DrawerAmounts | null | undefined): Record<DrawerKey, string> => ({
  lkr: a ? String(Number(a.lkr)) : "0",
  usd: a ? String(Number(a.usd)) : "0",
  eur: a ? String(Number(a.eur)) : "0",
});

function OpeningForm({ day, onSaved }: { day: CashDay; onSaved: (d: CashDay) => void }) {
  const toast = useToast();
  const { rates } = useSession();
  const closed = !!day.closing;
  const [v, setV] = useState(() => asText(day.opening?.cash ?? day.lastCounted));
  const [errors, setErrors] = useState<Partial<Record<DrawerKey, string>>>({});
  const [busy, setBusy] = useState(false);
  const total = drawerLkr({ lkr: Number(v.lkr) || 0, usd: Number(v.usd) || 0, eur: Number(v.eur) || 0 }, rates);

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    const bad: Partial<Record<DrawerKey, string>> = {};
    for (const k of DRAWER) if (v[k].trim() === "" || Number(v[k]) < 0) bad[k] = "Enter the cash (0 if none).";
    setErrors(bad);
    if (Object.keys(bad).length) return;
    setBusy(true);
    const { data, error } = await api.POST("/api/cash/opening", {
      body: { lkr: Number(v.lkr), usd: Number(v.usd), eur: Number(v.eur) },
    });
    setBusy(false);
    if (!data) return toast("Not saved", errorMessage(error), "error");
    toast("Opening cash saved", undefined, "success");
    onSaved(data);
  };

  const last = day.lastCounted;
  return (
    <>
      <PageHead
        title={`Opening cash – ${date(day.day)}`}
        sub={`${day.branchName} · count the drawer at the start of the day, for each currency.`}
      />
      <div className="g-2-1 grid">
        <form className="card" id="f" noValidate onSubmit={(e) => void save(e)}>
          <CardHeader
            title="Opening cash in the drawer"
            level={3}
            actions={
              day.opening ? (
                <Badge tone="success">
                  Entered {time(day.opening.enteredAt)} by {day.opening.enteredBy}
                </Badge>
              ) : (
                <Badge tone="warning">Not entered yet</Badge>
              )
            }
          />
          <CardContent>
            {closed ? <Alert className="mb">Today is already closed. The opening cash cannot be changed.</Alert> : null}
            <div className="g3 grid">
              {DRAWER.map((k) => (
                <Field key={k} label={`${DRAWER_LABEL[k]} cash`} htmlFor={`o-${k}`} required error={errors[k]}>
                  <Input
                    id={`o-${k}`}
                    name={DRAWER_LABEL[k]}
                    size="lg"
                    className="right"
                    type="number"
                    min={0}
                    step={k === "lkr" ? 100 : 1}
                    inputMode="decimal"
                    value={v[k]}
                    readOnly={closed}
                    onChange={(e) => setV((o) => ({ ...o, [k]: e.target.value }))}
                  />
                </Field>
              ))}
            </div>
            <div className="muted mt" id="tot">
              Total value ≈ {lkr(total)} at today&apos;s rates
            </div>
            {last ? (
              <div className="muted mt-s" style={{ fontSize: 12 }}>
                Last counted closing cash: LKR {num(last.lkr)} · USD {num(last.usd)} · EUR {num(last.eur)}
              </div>
            ) : null}
          </CardContent>
          {!closed ? (
            <CardFooter>
              <Button variant="sapphire" type="submit" id="saveOpening" disabled={busy}>
                {busy ? "Saving…" : day.opening ? "Update opening cash" : "Save opening cash"}
              </Button>
            </CardFooter>
          ) : null}
        </form>
        <Card>
          <CardHeader title="Recent openings" level={3} />
          <DataTable
            id="recentT"
            rows={day.recentOpenings}
            rowKey={(r) => r.day}
            empty="No openings yet."
            columns={[
              { key: "d", header: "Date", cell: (r) => date(r.day) },
              { key: "l", header: "LKR", num: true, cell: (r) => num(r.cash.lkr) },
              { key: "u", header: "USD", num: true, cell: (r) => num(r.cash.usd) },
              { key: "e", header: "EUR", num: true, cell: (r) => num(r.cash.eur) },
            ]}
          />
        </Card>
      </div>
      <p className="muted mt" style={{ fontSize: 12 }}>
        Each branch has its own stock and cash.
      </p>
    </>
  );
}
