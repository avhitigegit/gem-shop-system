"use client";

/* Credit notes – prototype e-returns/credit-note.html list (Guide 36; BRD E2). Made automatically when the owner
   approves a return. */
import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { DataTable, ExportButtons, type Column } from "@/components/ui/data-table";
import { GemSvg } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api/client";
import { downloadFile } from "@/lib/files";
import { date, lkr } from "@/lib/format";
import { kindLabel, type CreditNoteView } from "@/lib/sales";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function CreditNotesScreen() {
  const toast = useToast();
  const { branch } = useSession();
  const { data } = useLoad(() => api.GET("/api/credit-notes"), [branch]);

  const download = async (format: "pdf" | "xlsx") => {
    const ok = await downloadFile(`/api/credit-notes/export?format=${format}`, `Credit notes.${format}`);
    if (!ok) toast("Not downloaded", "Please try again.", "error");
  };

  const columns: Column<CreditNoteView>[] = [
    {
      key: "no",
      header: "Credit note",
      cell: (c) => (
        <Link className="mono" href={`/sell/credit-notes/${c.number}`}>
          {c.number}
        </Link>
      ),
    },
    { key: "date", header: "Date", cell: (c) => date(c.issuedAt) },
    {
      key: "gem",
      header: "Gem",
      cell: (c) => (
        <div className="row" style={{ gap: 6, flexWrap: "nowrap" }}>
          <GemSvg gem={c.gem.gemType} type={c.gem.productType} size={28} />
          <span>
            <b>{c.gem.name}</b>
            <br />
            <small className="mono muted">{c.gem.code}</small>
          </span>
        </div>
      ),
    },
    {
      key: "inv",
      header: "Invoice",
      cell: (c) => (
        <>
          <Link className="mono" href={`/sell/invoices/${c.sale}`}>
            {c.sale}
          </Link>
          <br />
          <small className="muted">
            {c.customer.name} · {kindLabel(c.kind)}
          </small>
        </>
      ),
    },
    { key: "amount", header: "Amount", num: true, cell: (c) => lkr(c.amount) },
    {
      key: "comm",
      header: "Commission",
      cell: (c) => (Number(c.guideComm) + Number(c.staffComm) > 0 ? <Badge tone="danger">Reversed</Badge> : "—"),
    },
    {
      key: "used",
      header: "",
      cell: (c) =>
        c.usedSaleNo ? (
          <small className="muted">
            Used on{" "}
            <Link className="mono" href={`/sell/invoices/${c.usedSaleNo}`}>
              {c.usedSaleNo}
            </Link>
          </small>
        ) : null,
    },
  ];

  return (
    <>
      <PageHead
        title="Credit notes"
        sub="Created automatically when the owner approves a return."
        actions={
          <ExportButtons name="Credit notes" onPdf={() => void download("pdf")} onExcel={() => void download("xlsx")} />
        }
      />
      <Card>
        {!data ? (
          <p className="muted" style={{ padding: 16 }}>
            Loading…
          </p>
        ) : (
          <DataTable
            id="cnTable"
            columns={columns}
            rows={data}
            rowKey={(c) => c.number}
            stackOnPhone
            empty="No credit notes yet."
          />
        )}
      </Card>
    </>
  );
}
