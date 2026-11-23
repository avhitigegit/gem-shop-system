"use client";

/* Commission rules – prototype a-setup/commission-rules.html (Guide 8). Guides / drivers get a % of the sale; staff
   get a fixed amount by price level. Changes apply to new sales (each sale keeps what was worked out then).
   Each guide has their own % (listed here and on the guide's page); new guides start with the default %. */
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import { Alert, Field, Input, InputGroup, Options, Select } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { lkr, num } from "@/lib/format";
import { useLoad } from "@/lib/use-load";

type CommissionView = components["schemas"]["CommissionView"];
type Level = { upTo: string; amount: string };

const toLevels = (l: CommissionView["levels"]): Level[] =>
  l.map((x) => ({ upTo: x.upTo != null ? String(Number(x.upTo)) : "", amount: String(Number(x.amount)) }));

/** Same rule as the backend: the first level whose "up to" the total does not pass; the last one has no limit. */
function levelIndex(levels: Level[], total: number) {
  for (let i = 0; i < levels.length; i++) {
    if (i === levels.length - 1 || total <= Number(levels[i].upTo)) return i;
  }
  return levels.length - 1;
}

export function CommissionScreen() {
  const { data, setData } = useLoad(() => api.GET("/api/settings/commission"), []);
  return (
    <>
      <PageHead
        title="Commission rules"
        sub="Guide/driver percentage and staff fixed-amount levels. The owner can change them at any time; changes apply to new sales."
      />
      <Alert tone="info" className="mb">
        Commission is worked out per sale: a percentage for guides/drivers, a fixed amount by price level for staff.
      </Alert>
      {data ? <Rules key={JSON.stringify(data)} rules={data} onSaved={setData} /> : <p className="muted">Loading…</p>}
    </>
  );
}

function Rules({ rules, onSaved }: { rules: CommissionView; onSaved: (r: CommissionView) => void }) {
  const toast = useToast();
  const [pct, setPct] = useState(String(Number(rules.defaultGuidePct)));
  const [levels, setLevels] = useState<Level[]>(() => toLevels(rules.levels));
  const [amount, setAmount] = useState("300000");
  const [guidePcts, setGuidePcts] = useState<Record<string, string>>(() =>
    Object.fromEntries(rules.guides.map((g) => [g.code, String(Number(g.pct))])),
  );
  const [calcGuide, setCalcGuide] = useState(rules.guides[0]?.code ?? "");
  const [guidePct, setGuidePct] = useState(() => String(Number(rules.guides[0]?.pct ?? rules.defaultGuidePct)));
  const [bad, setBad] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);

  const change = (i: number, k: keyof Level, v: string) => {
    setLevels((l) => l.map((x, n) => (n === i ? { ...x, [k]: v } : x)));
    setBad(null);
  };

  const addLevel = () =>
    setLevels((l) => {
      const before = l.length >= 2 ? Number(l[l.length - 2].upTo) || 0 : 0;
      const next = [...l];
      next.splice(l.length - 1, 0, { upTo: String(before + 100000), amount: l[l.length - 1].amount });
      return next;
    });

  const save = async () => {
    for (let i = 1; i < levels.length - 1; i++) {
      if (!(Number(levels[i].upTo) > Number(levels[i - 1].upTo))) {
        setBad(i);
        return toast("Levels must go up", "Each “up to” amount must be higher than the one before.", "error");
      }
    }
    const badGuide = rules.guides.find((g) => {
      const v = Number(guidePcts[g.code]);
      return guidePcts[g.code] === "" || !(v >= 0 && v <= 50);
    });
    if (badGuide) return toast("Check the guide %", `${badGuide.name}: 0 to 50.`, "error");
    setBusy(true);
    const { data, error } = await api.PUT("/api/settings/commission", {
      body: {
        defaultGuidePct: Number(pct),
        levels: levels.map((l, i) => ({
          upTo: i === levels.length - 1 ? undefined : Number(l.upTo),
          amount: Number(l.amount || 0),
        })),
        guides: rules.guides.map((g) => ({ code: g.code, pct: Number(guidePcts[g.code]) })),
      },
    });
    setBusy(false);
    if (!data) return toast("Not saved", errorMessage(error), "error");
    toast("Commission rules saved", "Applies to new sales from now on.", "success");
    onSaved(data);
  };

  const total = Number(amount) || 0;
  const lvl = levelIndex(levels, total);
  const guideComm = (total * (Number(guidePct) || 0)) / 100;
  const staffComm = Number(levels[lvl]?.amount) || 0;

  return (
    <div className="g2 grid">
      <div className="stack">
        <Card>
          <CardHeader level={3}>
            <div>
              <h3>Guides &amp; drivers</h3>
              <p>Percentage of the selling price. Each guide can have their own %.</p>
            </div>
          </CardHeader>
          <CardContent>
            <Field label="Default % for new guides" htmlFor="defPct" required hint="New guides start with this %.">
              <InputGroup addon="%">
                <Input
                  id="defPct"
                  type="number"
                  min={0}
                  max={50}
                  step={0.5}
                  value={pct}
                  onChange={(e) => setPct(e.target.value)}
                />
              </InputGroup>
            </Field>
            <ul className="list mt" id="guidePcts">
              {rules.guides.map((g) => (
                <li key={g.code}>
                  <div style={{ flex: 1 }}>
                    {g.name} <small className="muted">{g.vehicle}</small>
                  </div>
                  <Input
                    className="right"
                    type="number"
                    style={{ width: 80 }}
                    min={0}
                    max={50}
                    step={0.5}
                    data-g={g.code}
                    aria-label={`${g.name} %`}
                    value={guidePcts[g.code] ?? ""}
                    onChange={(e) => setGuidePcts((x) => ({ ...x, [g.code]: e.target.value }))}
                  />
                  <span>%</span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
        <Card>
          <CardHeader
            level={3}
            actions={
              <Button size="sm" variant="outline" onClick={addLevel} disabled={levels.length >= 10}>
                + Level
              </Button>
            }
          >
            <div>
              <h3>Staff price levels</h3>
              <p>A fixed amount that depends on the total selling price of the sale.</p>
            </div>
          </CardHeader>
          <CardContent>
            <table className="table">
              <thead>
                <tr>
                  <th>Level</th>
                  <th>Sale total up to (LKR)</th>
                  <th className="num">Fixed commission (LKR)</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {levels.map((l, i) => {
                  const last = i === levels.length - 1;
                  return (
                    <tr key={i}>
                      <td>{i + 1}</td>
                      <td>
                        {last ? (
                          <b>Above {num(Number(levels[i - 1]?.upTo) || 0, 0)}</b>
                        ) : (
                          <Input
                            type="number"
                            min={1}
                            aria-label={`Level ${i + 1} up to`}
                            className={bad === i ? "invalid" : undefined}
                            style={bad === i ? { borderColor: "var(--danger)" } : undefined}
                            value={l.upTo}
                            onChange={(e) => change(i, "upTo", e.target.value)}
                          />
                        )}
                      </td>
                      <td className="num">
                        <Input
                          className="right"
                          type="number"
                          min={0}
                          aria-label={`Level ${i + 1} amount`}
                          value={l.amount}
                          onChange={(e) => change(i, "amount", e.target.value)}
                        />
                      </td>
                      <td>
                        {!last && levels.length > 2 ? (
                          <Button
                            size="sm"
                            variant="ghost"
                            aria-label={`Remove level ${i + 1}`}
                            onClick={() => setLevels((x) => x.filter((_, n) => n !== i))}
                          >
                            ✕
                          </Button>
                        ) : null}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </CardContent>
          <CardFooter>
            <Button
              variant="outline"
              onClick={() => {
                setLevels(toLevels(rules.exampleLevels));
                toast("BRD example levels loaded", "Press Save to keep them.");
              }}
            >
              Use BRD example levels
            </Button>
            <Button variant="sapphire" disabled={busy} onClick={() => void save()}>
              {busy ? "Saving…" : "Save commission rules"}
            </Button>
          </CardFooter>
        </Card>
      </div>
      <Card style={{ alignSelf: "start" }}>
        <CardHeader level={3}>
          <div>
            <h3>Calculator – worked example</h3>
            <p>A guide (10%) brings a tourist who buys a ring for LKR 300,000.</p>
          </div>
        </CardHeader>
        <CardContent className="stack" style={{ gap: 12 }}>
          <Field label="Sale amount (LKR)" htmlFor="cAmt">
            <Input id="cAmt" size="lg" type="number" value={amount} onChange={(e) => setAmount(e.target.value)} />
          </Field>
          <Field label="Guide" htmlFor="cG">
            <Select
              id="cG"
              value={calcGuide}
              onChange={(e) => {
                setCalcGuide(e.target.value);
                setGuidePct(e.target.value ? (guidePcts[e.target.value] ?? "0") : "0");
              }}
            >
              <Options
                items={[
                  { value: "", label: "Walk-in (no guide)" },
                  ...rules.guides.map((g) => ({ value: g.code, label: `${g.name} · ${guidePcts[g.code] ?? ""}%` })),
                ]}
              />
            </Select>
          </Field>
          <Field label="Guide %" htmlFor="cPct" hint="0 = walk-in (no guide)">
            <InputGroup addon="%">
              <Input
                id="cPct"
                type="number"
                min={0}
                max={50}
                step={0.5}
                value={guidePct}
                onChange={(e) => setGuidePct(e.target.value)}
              />
            </InputGroup>
          </Field>
          <table className="table">
            <tbody>
              <tr>
                <td>Sale amount</td>
                <td className="num">{lkr(total)}</td>
              </tr>
              <tr>
                <td>Guide commission ({Number(guidePct) || 0}%)</td>
                <td className="num">{lkr(guideComm)}</td>
              </tr>
              <tr>
                <td>Staff commission (level {lvl + 1})</td>
                <td className="num">{lkr(staffComm)}</td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <td>Total commission on this sale</td>
                <td className="num">{lkr(guideComm + staffComm)}</td>
              </tr>
            </tfoot>
          </table>
          <p className="muted" style={{ fontSize: 12 }}>
            Commission is worked out on the final agreed price.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
