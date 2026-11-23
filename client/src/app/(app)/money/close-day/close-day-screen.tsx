"use client";

/* Close the day – prototype i-daily-closing/day-end.html (Guide 49; BRD I3, I4). Count the cash in each currency and
   enter the card machine total; the system shows what should be there and the difference (highlighted). A difference
   needs a note. Closing locks the day and sends the closing report to the owner. */
import Link from "next/link";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import { DataTable } from "@/components/ui/data-table";
import { Alert, Field, Input, Textarea } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import { DRAWER, DRAWER_LABEL, TOLERANCE, toLkr, type CashDay, type DrawerKey } from "@/lib/cash";
import { downloadFile } from "@/lib/files";
import { date, lkr, num, time } from "@/lib/format";
import { useSession } from "@/lib/session";
import { CashDayState, useCashDay } from "../use-cash-day";

type Key = DrawerKey | "card";
type Counted = Record<Key, string>;

export function CloseDayScreen() {
  const { data, error, reload } = useCashDay();
  if (!data)
    return (
      <>
        <PageHead title="Day-end closing" sub="Count the cash in each currency and enter the card machine total." />
        <CashDayState error={error} />
      </>
    );
  return (
    <CloseDay
      key={`${data.branch}|${data.day}|${data.closing?.number ?? ""}`}
      day={data}
      onClosed={() => void reload()}
    />
  );
}

function CloseDay({ day, onClosed }: { day: CashDay; onClosed: () => void }) {
  const toast = useToast();
  const { rates } = useSession();
  const done = day.closing;
  const e = day.expected;
  const [c, setC] = useState<Counted>(() =>
    done
      ? {
          lkr: String(Number(done.counted.lkr)),
          usd: String(Number(done.counted.usd)),
          eur: String(Number(done.counted.eur)),
          card: String(Number(done.cardCounted)),
        }
      : { lkr: "", usd: "", eur: "", card: "" },
  );
  const [note, setNote] = useState(done?.note ?? "");
  const [errors, setErrors] = useState<Partial<Record<Key | "note", string>>>({});
  const [busy, setBusy] = useState(false);

  const expectedOf = (k: Key) =>
    Number(done ? (k === "card" ? done.cardExpected : done.expected[k]) : k === "card" ? e.card : e.expected[k]);
  const diffOf = (k: Key) => (c[k] === "" ? null : Math.round((Number(c[k]) - expectedOf(k)) * 100) / 100);
  const entered = (["lkr", "usd", "eur", "card"] as Key[]).some((k) => c[k] !== "");
  const total = (["lkr", "usd", "eur", "card"] as Key[]).reduce((t, k) => {
    const d = diffOf(k);
    return d == null ? t : t + (k === "card" ? d : toLkr(k, d, rates));
  }, 0);
  const balanced = Math.abs(total) < TOLERANCE;

  const close = async (ev: React.FormEvent) => {
    ev.preventDefault();
    const bad: typeof errors = {};
    for (const k of ["lkr", "usd", "eur", "card"] as Key[])
      if (c[k].trim() === "" || Number(c[k]) < 0) bad[k] = "Required";
    if (!Object.keys(bad).length && !balanced && note.trim().length < 3) bad.note = "Please explain the difference.";
    setErrors(bad);
    if (Object.keys(bad).length) {
      if (bad.note) toast("Please explain the difference", "Add a note for the owner.", "error");
      return;
    }
    setBusy(true);
    const { data, error } = await api.POST("/api/cash/close", {
      body: {
        lkr: Number(c.lkr),
        usd: Number(c.usd),
        eur: Number(c.eur),
        card: Number(c.card),
        note: note.trim() || undefined,
      },
    });
    setBusy(false);
    if (!data) return toast("Not closed", errorMessage(error), "error");
    toast("Day closed", "Closing report sent to the owner (I4).", "success");
    onClosed();
  };

  const report = async () => {
    if (!done) return;
    const ok = await downloadFile(`/api/cash/closings/${done.number}/pdf`, `Day closing ${done.number}.pdf`);
    if (!ok) toast("Not downloaded", "Please try again.", "error");
  };

  const diffCell = (k: Key) => {
    const d = diffOf(k);
    if (d == null) return "–";
    if (Math.abs(d) < 0.005) return <Badge tone="success">0.00 ✓</Badge>;
    return (
      <Badge tone={d < 0 ? "danger" : "warning"}>
        {d > 0 ? "+" : ""}
        {num(d)}
      </Badge>
    );
  };

  const input = (k: Key, label: string) => (
    <Field error={errors[k]} className="m-0">
      <Input
        name={k === "card" ? "card" : DRAWER_LABEL[k as DrawerKey]}
        aria-label={`${label} counted`}
        className="right"
        style={{ width: 130 }}
        type="number"
        step="0.01"
        min={0}
        inputMode="decimal"
        value={c[k]}
        readOnly={!!done}
        onChange={(ev) => setC((o) => ({ ...o, [k]: ev.target.value }))}
      />
    </Field>
  );

  return (
    <>
      <PageHead
        title={`Day-end closing – ${date(day.day)}`}
        sub={`${day.branchName} · count the cash in each currency and enter the card machine total. The system shows what should be there and any difference.`}
      />
      {!day.opening && !done ? (
        <Alert tone="warning" className="mb">
          Opening cash was not entered today. <Link href="/money/start-day">Enter it first →</Link>
        </Alert>
      ) : null}
      {done ? (
        <Alert tone="success" className="mb">
          ✓ Day closed at {time(done.closedAt)} by {done.closedBy}.{" "}
          {done.sentToOwner
            ? "The closing report was sent to the owner automatically (I4)."
            : "The closing report is ready for the owner."}
        </Alert>
      ) : null}
      <div className="g-2-1 grid">
        <form className="card" id="f" noValidate onSubmit={(ev) => void close(ev)}>
          <CardHeader title="Count the drawer" level={3} />
          <CardContent>
            <div className="table-wrap">
              <table className="table" id="countT">
                <thead>
                  <tr>
                    <th></th>
                    <th className="num">Opening</th>
                    <th className="num">+ Cash sales</th>
                    <th className="num">Expected</th>
                    <th className="num">Counted</th>
                    <th className="num">Difference</th>
                  </tr>
                </thead>
                <tbody>
                  {DRAWER.map((k) => (
                    <tr key={k}>
                      <td>
                        <b>{DRAWER_LABEL[k]} cash</b>
                      </td>
                      <td className="num">{num(e.opening[k])}</td>
                      <td className="num">{num(Number(e.expected[k]) - Number(e.opening[k]))}</td>
                      <td className="num">
                        <b data-expected={k}>{num(expectedOf(k))}</b>
                      </td>
                      <td className="num">{input(k, `${DRAWER_LABEL[k]} cash`)}</td>
                      <td className="num" data-diff={k}>
                        {diffCell(k)}
                      </td>
                    </tr>
                  ))}
                  <tr>
                    <td>
                      <b>Card machine total</b>
                    </td>
                    <td></td>
                    <td></td>
                    <td className="num">
                      <b data-expected="card">{num(expectedOf("card"))}</b>
                    </td>
                    <td className="num">{input("card", "Card machine")}</td>
                    <td className="num" data-diff="card">
                      {diffCell("card")}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div className="muted" style={{ fontSize: 12 }}>
              LKR expected already deducts change given ({lkr(e.changeGiven)}) and drawer payouts ({lkr(e.drawerOut)}) –{" "}
              <Link href="/money/pay-out">see drawer expenses</Link>.
            </div>
            <div id="diffBox" className="mt">
              {entered ? (
                balanced ? (
                  <Alert tone="success">
                    <b>Balanced.</b> Counted cash matches the expected amounts.
                  </Alert>
                ) : (
                  <Alert tone="danger">
                    <b>
                      Difference: {total > 0 ? "+" : ""}
                      {lkr(total)}
                    </b>{" "}
                    (all currencies converted at today&apos;s rates). The difference is highlighted in the owner&apos;s
                    report.
                  </Alert>
                )
              ) : null}
            </div>
            <Field label="Note for the owner" htmlFor="cNote" className="mt" error={errors.note}>
              <Textarea
                id="cNote"
                value={note}
                readOnly={!!done}
                placeholder="Explain any difference…"
                onChange={(ev) => setNote(ev.target.value)}
              />
            </Field>
          </CardContent>
          <CardFooter>
            {done ? (
              <Button variant="outline" type="button" id="report" onClick={() => void report()}>
                Closing report (PDF)
              </Button>
            ) : (
              <Button variant="success" type="submit" id="closeDay" disabled={busy}>
                {busy ? "Closing…" : "Close the day & send to owner"}
              </Button>
            )}
          </CardFooter>
        </form>
        <div className="stack">
          <Card>
            <CardHeader title="Today in numbers" level={3} />
            <CardContent>
              <dl className="dl" style={{ gridTemplateColumns: "1fr auto" }}>
                <dt>Bills</dt>
                <dd className="right">{e.bills}</dd>
                <dt>Sales total</dt>
                <dd className="right">{lkr(e.salesTotal)}</dd>
                <dt>Cash sales (in LKR)</dt>
                <dd className="right">{lkr(e.cashSalesLkr)}</dd>
                <dt>Card machine</dt>
                <dd className="right">{lkr(e.card)}</dd>
                <dt>Card bank fee (shop pays)</dt>
                <dd className="right">{lkr(e.cardFee)}</dd>
                <dt>Drawer payouts</dt>
                <dd className="right">{lkr(e.drawerOut)}</dd>
              </dl>
            </CardContent>
          </Card>
          <Card>
            <CardHeader title="Payments today" level={3} />
            <DataTable
              id="payT"
              rows={day.payments}
              rowKey={(p) => p.invoice}
              empty="No sales today."
              columns={[
                {
                  key: "i",
                  header: "Invoice",
                  cell: (p) => (
                    <Link className="mono" href={`/sell/invoices/${p.invoice}`}>
                      {p.invoice.slice(-6)}
                    </Link>
                  ),
                },
                { key: "p", header: "Paid", cell: (p) => p.paid },
                { key: "c", header: "Change", num: true, cell: (p) => num(p.change, 0) },
              ]}
            />
          </Card>
        </div>
      </div>
    </>
  );
}
