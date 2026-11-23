"use client";

/* Commission per sale – prototype h-commissions/commission-breakdown.html (Guide 39; BRD H1, H2, H3; rule 5). Filters
   From / To / Guide (or Walk-in only) / Salesperson; the sales on the left, how the chosen sale's commission was worked
   out on the right. */
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { StatusPill } from "@/components/ui/badge";
import { buttonClass } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { DataTable, EmptyState, Filters } from "@/components/ui/data-table";
import { Alert, Field, Input, Options, Select } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { api } from "@/lib/api/client";
import { lineStatus, payeeLabel } from "@/lib/commissions";
import { date, dateTime, inCurrency, lkr, num } from "@/lib/format";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function BreakdownScreen() {
  const { branch, currency, rates } = useSession();
  const params = useSearchParams();
  const [f, setF] = useState({ from: "", to: "", guide: "", staff: "" });
  const [picked, setPicked] = useState<string | null>(params.get("id"));
  const { data } = useLoad(
    () =>
      api.GET("/api/commissions/sales", {
        params: {
          query: {
            from: f.from || undefined,
            to: f.to || undefined,
            guide: f.guide || undefined,
            staff: f.staff ? Number(f.staff) : undefined,
          },
        },
      }),
    [f.from, f.to, f.guide, f.staff, branch],
  );
  const sales = data?.sales ?? [];
  const sel = sales.find((s) => s.number === picked)?.number ?? sales[0]?.number ?? null;
  const { data: s } = useLoad(
    () =>
      sel
        ? api.GET("/api/commissions/sales/{number}", { params: { path: { number: sel } } })
        : Promise.resolve({ data: undefined, error: undefined }),
    [sel],
  );
  const set = (k: keyof typeof f, v: string) => setF((x) => ({ ...x, [k]: v }));

  const levelLabel = (i: number) => {
    const l = s?.levels ?? [];
    if (l[i].upTo == null) return `Above LKR ${num(l[i - 1]?.upTo ?? 0, 0)}`;
    if (i === 0) return `Up to LKR ${num(l[i].upTo ?? 0, 0)}`;
    return `LKR ${num(Number(l[i - 1].upTo) + 1, 0)} – ${num(l[i].upTo ?? 0, 0)}`;
  };

  return (
    <>
      <PageHead
        title="Commission per sale"
        sub="Commission is calculated on every single sale – a percentage for the guide/driver plus a fixed amount by price level for the salesperson."
      />
      <Card className="mb">
        <CardContent>
          <Filters>
            <Field label="From" htmlFor="cFrom">
              <Input
                id="cFrom"
                type="date"
                value={f.from}
                max={f.to || undefined}
                onChange={(e) => set("from", e.target.value)}
              />
            </Field>
            <Field label="To" htmlFor="cTo">
              <Input
                id="cTo"
                type="date"
                value={f.to}
                min={f.from || undefined}
                onChange={(e) => set("to", e.target.value)}
              />
            </Field>
            <Field label="Guide" htmlFor="cGuide">
              <Select id="cGuide" value={f.guide} onChange={(e) => set("guide", e.target.value)}>
                <Options
                  placeholder="All"
                  items={[
                    { value: "walkin", label: "Walk-in only" },
                    ...(data?.guides ?? []).map((g) => ({ value: g.key.slice(2), label: g.name })),
                  ]}
                />
              </Select>
            </Field>
            <Field label="Salesperson" htmlFor="cStaff">
              <Select id="cStaff" value={f.staff} onChange={(e) => set("staff", e.target.value)}>
                <Options
                  placeholder="All"
                  items={(data?.staff ?? []).map((u) => ({ value: u.key.slice(2), label: u.name }))}
                />
              </Select>
            </Field>
          </Filters>
        </CardContent>
      </Card>
      {!data ? (
        <p className="muted">Loading…</p>
      ) : !sales.length ? (
        f.from || f.to || f.guide || f.staff ? (
          <Card>
            <CardContent className="muted">No sales for these filters.</CardContent>
          </Card>
        ) : (
          <EmptyState
            title="No sales yet"
            text="Commission per sale appears here after the first sale."
            action={
              <Link className={buttonClass("sapphire")} href="/people/commissions/pay">
                Go to Pay guides &amp; drivers
              </Link>
            }
          />
        )
      ) : (
        <>
          <Alert tone="info" className="mb">
            Commission is calculated per sale: a percentage for guides/drivers, a fixed amount by price level for staff.
            Calculated on the agreed price.
          </Alert>
          <div className="g-1-2 grid">
            <Card>
              <CardHeader title="Sales" level={3} />
              <div className="card-content" id="saleList" style={{ maxHeight: "70vh", overflow: "auto", padding: 0 }}>
                {sales.map((x) => (
                  <button
                    key={x.number}
                    type="button"
                    data-s={x.number}
                    className="pick-row"
                    onClick={() => setPicked(x.number)}
                    style={{
                      all: "unset",
                      boxSizing: "border-box",
                      display: "flex",
                      gap: 8,
                      width: "100%",
                      padding: "10px 14px",
                      borderBottom: "1px solid var(--border)",
                      cursor: "pointer",
                      background: x.number === sel ? "var(--sapphire-soft)" : undefined,
                    }}
                  >
                    <span className="mono" style={{ fontSize: 12 }}>
                      {x.number}
                    </span>
                    <span className="muted" style={{ fontSize: 12, flex: 1 }}>
                      {date(x.soldAt)}
                    </span>
                    <b style={{ fontSize: 12 }}>{inCurrency(x.commission, currency, rates)}</b>
                  </button>
                ))}
              </div>
            </Card>
            {s && s.number === sel ? (
              <Card id="saleCommission">
                <CardHeader
                  actions={
                    <Link className={buttonClass("outline", "sm")} href={`/sell/invoices/${s.number}`}>
                      Invoice
                    </Link>
                  }
                >
                  <div>
                    <h2>
                      <span className="mono">{s.number}</span>
                    </h2>
                    <p>
                      {dateTime(s.soldAt)} · {s.customer} · sold by {s.staff}
                    </p>
                  </div>
                </CardHeader>
                <CardContent>
                  <table className="table">
                    <tbody>
                      <tr>
                        <td>
                          <b>Agreed sale amount</b> (commission base)
                        </td>
                        <td className="num">
                          <b>{lkr(s.total)}</b>
                        </td>
                      </tr>
                      <tr>
                        <td>
                          Guide / driver:{" "}
                          {s.guide
                            ? `${s.guide} (${s.guideVehicle ?? ""}) × ${num(s.guidePct, 0)}%`
                            : "Walk-in – no guide"}
                        </td>
                        <td className="num">{lkr(s.guideComm)}</td>
                      </tr>
                      <tr>
                        <td>
                          Staff: {s.staff} – level {s.level + 1} ({levelLabel(s.level)})
                        </td>
                        <td className="num">{lkr(s.staffComm)}</td>
                      </tr>
                    </tbody>
                    <tfoot>
                      <tr>
                        <td>Total commission on this sale</td>
                        <td className="num" id="saleCommTotal">
                          {lkr(Number(s.guideComm) + Number(s.staffComm))}
                        </td>
                      </tr>
                    </tfoot>
                  </table>
                  <h3 className="mt">Commission records</h3>
                  <DataTable
                    id="saleLines"
                    columns={[
                      {
                        key: "p",
                        header: "Payee",
                        cell: (c) => (
                          <>
                            {c.name} <span className="badge badge-outline">{payeeLabel(c.payee)}</span>
                          </>
                        ),
                      },
                      {
                        key: "a",
                        header: "Amount",
                        num: true,
                        cell: (c) => (
                          <b className={Number(c.amount) < 0 ? "text-danger" : undefined}>{lkr(c.amount ?? 0)}</b>
                        ),
                      },
                      {
                        key: "s",
                        header: "Status",
                        cell: (c) => (
                          <>
                            <StatusPill status={lineStatus(c)} />
                            {c.note ? <small className="muted"> {c.note}</small> : null}
                          </>
                        ),
                      },
                    ]}
                    rows={s.lines}
                    rowKey={(c) => String(c.id)}
                    empty="No commission records."
                  />
                  <h3 className="mt">Staff price levels</h3>
                  <table className="table">
                    <thead>
                      <tr>
                        <th>Level</th>
                        <th>Total selling price of the sale</th>
                        <th className="num">Fixed commission</th>
                      </tr>
                    </thead>
                    <tbody>
                      {s.levels.map((l, i) => (
                        <tr key={i} style={i === s.level ? { background: "var(--gold-soft)" } : undefined}>
                          <td>
                            {i === s.level ? "▶ " : ""}Level {i + 1}
                          </td>
                          <td>{levelLabel(i)}</td>
                          <td className="num">{lkr(l.amount)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </CardContent>
              </Card>
            ) : (
              <p className="muted">Loading…</p>
            )}
          </div>
        </>
      )}
    </>
  );
}
