"use client";

/* Commission report – prototype l-reports/commissions.html (Guide 68; BRD L5, H5, L6). By guide / driver and by staff
   member, for any date range: earned, taken back after returns, paid, not yet paid. The branch at the top filters. */
import Link from "next/link";
import { useState } from "react";
import { buttonClass } from "@/components/ui/button";
import { Card, CardContent, Kpi } from "@/components/ui/card";
import { DataTable, ExportButtons, Filters } from "@/components/ui/data-table";
import { Field, Input, Options, Select } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { api } from "@/lib/api/client";
import { inCurrency, todayInShop } from "@/lib/format";
import { type CommissionPersonRow, daysAgo, useReportDownload } from "@/lib/reports";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function CommissionReportScreen() {
  const { branch, currency, rates } = useSession();
  const cur = (n: number | string) => inCurrency(n, currency, rates);
  const [from, setFrom] = useState(() => daysAgo(29));
  const [to, setTo] = useState(() => todayInShop());
  const [payee, setPayee] = useState("");
  const filters = { from: from || undefined, to: to || undefined, payee: payee || undefined };
  const { data: r } = useLoad(
    () => api.GET("/api/reports/commissions", { params: { query: filters } }),
    [from, to, payee, branch],
  );
  const download = useReportDownload("commissions", "Commission report", filters);

  return (
    <>
      <PageHead
        title="Commission report"
        sub="By guide/driver and by staff member, for any date range."
        actions={
          <ExportButtons
            name="Commission report"
            onPdf={() => void download("pdf")}
            onExcel={() => void download("xlsx")}
          />
        }
      />
      <Card className="mb">
        <CardContent>
          <Filters>
            <Field label="From" htmlFor="mf">
              <Input id="mf" type="date" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)} />
            </Field>
            <Field label="To" htmlFor="mt">
              <Input id="mt" type="date" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} />
            </Field>
            <Field label="Show" htmlFor="mp">
              <Select id="mp" value={payee} onChange={(e) => setPayee(e.target.value)}>
                <Options
                  items={[
                    { value: "", label: "Guides & staff" },
                    { value: "GUIDE", label: "Guides & drivers" },
                    { value: "STAFF", label: "Staff" },
                  ]}
                />
              </Select>
            </Field>
          </Filters>
        </CardContent>
      </Card>
      {!r ? (
        <p className="muted">Loading…</p>
      ) : (
        <>
          <div className="g4 mb grid">
            <Kpi label="Commission earned" value={cur(r.earned)} />
            <Kpi label="Reversed (returns)" value={cur(r.reversed)} />
            <Kpi label="Paid" value={cur(r.paid)} />
            <Kpi label="Not yet paid" value={cur(r.unpaid)} accent />
          </div>
          <Card>
            <DataTable<CommissionPersonRow>
              id="rT"
              rows={r.rows}
              rowKey={(x) => `${x.payee}-${x.id}`}
              stackOnPhone
              empty="No commission in this period."
              columns={[
                {
                  key: "n",
                  header: "Name",
                  cell: (x) => (
                    <>
                      <b>{x.name}</b>
                      <br />
                      <small className="muted">{x.detail}</small>
                    </>
                  ),
                },
                { key: "s", header: "Sales", num: true, cell: (x) => x.sales },
                { key: "sa", header: "Sales amount", num: true, cell: (x) => cur(x.salesAmount) },
                { key: "e", header: "Earned", num: true, cell: (x) => cur(x.earned) },
                {
                  key: "r",
                  header: "Reversed",
                  num: true,
                  cell: (x) => (Number(x.reversed) ? <span className="text-danger">{cur(x.reversed)}</span> : "—"),
                },
                { key: "p", header: "Paid", num: true, cell: (x) => cur(x.paid) },
                { key: "u", header: "Not yet paid", num: true, cell: (x) => <b>{cur(x.unpaid)}</b> },
                {
                  key: "sl",
                  header: "",
                  cell: (x) =>
                    x.payee === "GUIDE" ? (
                      <Link className={buttonClass("outline", "sm")} href={`/people/commissions/slip/${x.id}`}>
                        Slip
                      </Link>
                    ) : null,
                },
              ]}
              footer={[
                "Total",
                r.sales,
                cur(r.salesAmount),
                cur(r.earned),
                cur(r.reversed),
                cur(r.paid),
                cur(r.unpaid),
                "",
              ]}
            />
          </Card>
        </>
      )}
    </>
  );
}
