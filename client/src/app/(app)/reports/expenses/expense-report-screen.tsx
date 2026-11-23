"use client";

/* Expense report – prototype m-expenses/expense-report.html (Guide 67; BRD M7, L6). Approved expenses by month, expense
   type, payment method and branch. The branch at the top filters (All branches = the whole shop). PDF / Excel come
   from the backend (P5-02). */
import { Card, CardHeader, Kpi } from "@/components/ui/card";
import { DataTable, ExportButtons } from "@/components/ui/data-table";
import { PageHead } from "@/components/ui/steps";
import { api } from "@/lib/api/client";
import { type ExpenseReport, methodLabel } from "@/lib/expenses";
import { lkr, month, pct } from "@/lib/format";
import { useReportDownload } from "@/lib/reports";
import { ALL_BRANCHES, useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

type TypeRow = ExpenseReport["types"][number];

export function ExpenseReportScreen() {
  const { branch, branchName } = useSession();
  const { data: r } = useLoad(() => api.GET("/api/expenses/report"), [branch]);
  const download = useReportDownload("expenses", "Expense report", {});
  const title = `Expense report – ${branch === ALL_BRANCHES ? "all branches" : branchName(branch)}`;
  if (!r)
    return (
      <>
        <PageHead title={title} />
        <p className="muted">Loading…</p>
      </>
    );
  const max = Math.max(1, ...r.months.map((m) => Number(m.total)));
  const total = Number(r.total);

  return (
    <>
      <PageHead
        title={title}
        sub="By month, branch and expense type (approved expenses)."
        actions={
          <ExportButtons
            name="Expense report"
            onPdf={() => void download("pdf")}
            onExcel={() => void download("xlsx")}
          />
        }
      />
      <div className="g3 mb grid">
        {r.months.map((m, i) => (
          <Kpi
            key={m.month}
            label={month(m.month)}
            value={lkr(m.total)}
            accent={i === r.months.length - 1}
            sub={
              <div className="bar gold" style={{ marginTop: 6 }}>
                <i style={{ width: `${(Number(m.total) / max) * 100}%` }} />
              </div>
            }
          />
        ))}
      </div>
      <div className="stack">
        <Card>
          <CardHeader title="By expense type and month" level={3} />
          <DataTable<TypeRow>
            id="erT"
            rows={r.types}
            rowKey={(t) => t.expenseType}
            stackOnPhone
            empty="No approved expenses yet."
            columns={[
              { key: "t", header: "Expense type", cell: (t) => <b>{t.expenseType}</b> },
              ...r.months.map((m, i) => ({
                key: m.month,
                header: month(m.month),
                num: true,
                cell: (t: TypeRow) => (Number(t.amounts[i]) ? lkr(t.amounts[i]) : "—"),
              })),
              { key: "tot", header: "Total", num: true, cell: (t) => <b>{lkr(t.total)}</b> },
            ]}
            footer={["Total", ...r.months.map((m) => lkr(m.total)), lkr(r.total)]}
          />
        </Card>
        <div className="g2 grid">
          <Card>
            <CardHeader title="By payment method" level={3} />
            <DataTable
              rows={r.methods}
              rowKey={(m) => m.method}
              columns={[
                { key: "m", header: "Method", cell: (m) => methodLabel(m.method) },
                { key: "c", header: "Expenses", num: true, cell: (m) => m.count },
                { key: "a", header: "Amount", num: true, cell: (m) => lkr(m.total) },
                { key: "p", header: "Share", num: true, cell: (m) => pct(total ? (Number(m.total) / total) * 100 : 0) },
              ]}
            />
          </Card>
          <Card>
            <CardHeader title="By branch" level={3} />
            <DataTable
              rows={r.branches}
              rowKey={(b) => b.branch}
              columns={[
                { key: "b", header: "Branch", cell: (b) => b.branch },
                { key: "a", header: "Amount", num: true, cell: (b) => lkr(b.total) },
                { key: "p", header: "Share", num: true, cell: (b) => pct(total ? (Number(b.total) / total) * 100 : 0) },
              ]}
            />
          </Card>
        </div>
      </div>
    </>
  );
}
