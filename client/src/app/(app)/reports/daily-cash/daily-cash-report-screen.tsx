"use client";

/* Daily closing report – prototype l-reports/daily-closing.html (Guide 65; BRD L5, I3, I4, L6). Every day closing,
   counted against expected, differences highlighted. The branch at the top filters. */
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, Kpi } from "@/components/ui/card";
import { DataTable, ExportButtons, Filters } from "@/components/ui/data-table";
import { Check, Field, Input } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { api } from "@/lib/api/client";
import { date, lkr, num, time, todayInShop } from "@/lib/format";
import { type CashReportRow, daysAgo, useReportDownload } from "@/lib/reports";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

type Key = "lkr" | "usd" | "eur" | "card";

function Counted({ row, k }: { row: CashReportRow; k: Key }) {
  const d = Math.round((Number(row.counted[k]) - Number(row.expected[k])) * 100) / 100;
  return (
    <>
      {num(row.counted[k])}
      {Math.abs(d) >= 0.005 && (
        <>
          <br />
          <Badge tone="danger">
            {d > 0 ? "+" : ""}
            {num(d)}
          </Badge>
        </>
      )}
    </>
  );
}

export function DailyCashReportScreen() {
  const { branch } = useSession();
  const [from, setFrom] = useState(() => daysAgo(30));
  const [to, setTo] = useState(() => todayInShop());
  const [only, setOnly] = useState(false);
  const filters = { from: from || undefined, to: to || undefined, onlyDifferences: only };
  const { data: r } = useLoad(
    () => api.GET("/api/reports/daily-cash", { params: { query: filters } }),
    [from, to, only, branch],
  );
  const download = useReportDownload("daily-cash", "Daily cash report", filters);

  return (
    <>
      <PageHead
        title="Daily closing report"
        sub="Every day-end closing, with any difference highlighted. Sent to the owner automatically."
        actions={
          <ExportButtons
            name="Daily cash report"
            onPdf={() => void download("pdf")}
            onExcel={() => void download("xlsx")}
          />
        }
      />
      <Card className="mb">
        <CardContent>
          <Filters>
            <Field label="From" htmlFor="cf">
              <Input id="cf" type="date" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)} />
            </Field>
            <Field label="To" htmlFor="ct">
              <Input id="ct" type="date" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} />
            </Field>
            <div style={{ alignSelf: "center" }}>
              <Check checked={only} onChange={setOnly}>
                Only days with a difference
              </Check>
            </div>
          </Filters>
        </CardContent>
      </Card>
      {!r ? (
        <p className="muted">Loading…</p>
      ) : (
        <>
          <div className="g3 mb grid">
            <Kpi label="Days closed" value={r.days} />
            <Kpi label="Days with a difference" value={r.daysWithDifference} accent={r.daysWithDifference > 0} />
            <Kpi label="Net difference" value={lkr(r.netDifference)} sub="all currencies in LKR" />
          </div>
          <Card>
            <DataTable<CashReportRow>
              id="rT"
              rows={r.rows}
              rowKey={(c) => c.number}
              stackOnPhone
              empty="No closings in this period."
              marked={(c) => !c.balanced}
              columns={[
                {
                  key: "d",
                  header: "Date",
                  cell: (c) => (
                    <>
                      <b>{date(c.day)}</b>
                      <br />
                      <small className="muted">
                        {c.branch} · {c.closedBy} {time(c.closedAt)}
                      </small>
                    </>
                  ),
                },
                { key: "l", header: "LKR counted", num: true, cell: (c) => <Counted row={c} k="lkr" /> },
                { key: "u", header: "USD counted", num: true, cell: (c) => <Counted row={c} k="usd" /> },
                { key: "e", header: "EUR counted", num: true, cell: (c) => <Counted row={c} k="eur" /> },
                { key: "c", header: "Card machine", num: true, cell: (c) => <Counted row={c} k="card" /> },
                {
                  key: "df",
                  header: "Difference (LKR)",
                  num: true,
                  cell: (c) =>
                    c.balanced ? (
                      <Badge tone="success">Balanced</Badge>
                    ) : (
                      <b className="text-danger">{lkr(c.difference)}</b>
                    ),
                },
                { key: "n", header: "Note", cell: (c) => c.note ?? "" },
                { key: "o", header: "Owner", cell: (c) => (c.sentToOwner ? <Badge tone="blue">Sent ✓</Badge> : "") },
              ]}
            />
          </Card>
        </>
      )}
    </>
  );
}
