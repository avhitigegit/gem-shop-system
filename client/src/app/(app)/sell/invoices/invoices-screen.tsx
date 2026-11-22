"use client";

/* Invoices – prototype g-payments/invoices.html (Guide 29; BRD G3, G4, G6; rule 4). Running invoice numbers; view,
   print and send – never edit or delete. Cancelling needs the owner's approval and the invoice stays on record. */
import Link from "next/link";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button, buttonClass } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { DataTable, ExportButtons, Filters, type Column } from "@/components/ui/data-table";
import { Alert, Field, Input, Select } from "@/components/ui/form";
import { GemSvg } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api/client";
import { downloadFile } from "@/lib/files";
import { dateTime, inCurrency, todayInShop } from "@/lib/format";
import { ACCOUNTANT } from "@/lib/roles";
import type { InvoiceRow } from "@/lib/sales";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";
import { useCancelInvoice } from "./use-cancel";

type Status = "" | "COMPLETED" | "CANCELLED";
type Pay = "" | "cash" | "card" | "split";

export function InvoicesScreen() {
  const toast = useToast();
  const { role, branch, currency, rates } = useSession();
  const canAct = role !== ACCOUNTANT;
  const cancelInvoice = useCancelInvoice();
  const [q, setQ] = useState("");
  const [from, setFrom] = useState(() => todayInShop(new Date(Date.now() - 30 * 86_400_000)));
  const [to, setTo] = useState(() => todayInShop());
  const [status, setStatus] = useState<Status>("");
  const [pay, setPay] = useState<Pay>("");
  const query = { q: q.trim() || undefined, from, to, status: status || undefined, pay: pay || undefined };
  const { data, reload } = useLoad(
    () => api.GET("/api/sales", { params: { query: { ...query, size: 100 } } }),
    [q, from, to, status, pay, branch],
  );

  const exportAs = async (format: "pdf" | "xlsx") => {
    const p = new URLSearchParams({ format });
    Object.entries(query).forEach(([k, v]) => v && p.set(k, v));
    const ok = await downloadFile(`/api/sales/export?${p}`, `Invoices.${format}`);
    toast(ok ? "Export ready" : "Not exported", ok ? undefined : "Please try again.", ok ? "success" : "error");
  };

  const ask = async (no: string) => {
    if (await cancelInvoice(no)) reload();
  };

  const columns: Column<InvoiceRow>[] = [
    {
      key: "no",
      header: "Invoice",
      cell: (s) => (
        <>
          <Link className="mono" href={`/sell/invoices/${s.number}`}>
            {s.number}
          </Link>
          <br />
          <small className="muted">{dateTime(s.soldAt)}</small>
        </>
      ),
    },
    {
      key: "items",
      header: "Items",
      cell: (s) =>
        s.items.map((i) => (
          <div key={i.code} className="row" style={{ gap: 6, flexWrap: "nowrap" }}>
            <GemSvg gem={i.gemType} type={i.productType} size={24} />
            <span>{i.name}</span>
            {i.released ? <Badge tone="blue">returned</Badge> : null}
          </div>
        )),
    },
    {
      key: "cust",
      header: "Customer",
      cell: (s) => (
        <>
          {s.customer}
          <br />
          <small className="muted">{s.country}</small>
        </>
      ),
    },
    { key: "pay", header: "Payment", cell: (s) => s.payment },
    { key: "total", header: "Total", num: true, cell: (s) => <b>{inCurrency(s.total, currency, rates)}</b> },
    {
      key: "sent",
      header: "Sent",
      cell: (s) => [s.sentEmail ? "✉" : "", s.sentWhatsapp ? "💬" : ""].filter(Boolean).join(" ") || "–",
    },
    {
      key: "status",
      header: "Status",
      cell: (s) => (
        <>
          {s.status === "COMPLETED" ? <Badge tone="success">Completed</Badge> : <Badge tone="danger">Cancelled</Badge>}
          {s.cancelPending ? (
            <>
              <br />
              <Badge tone="warning">Cancel requested</Badge>
            </>
          ) : null}
        </>
      ),
    },
    {
      key: "act",
      header: "",
      cell: (s) => (
        <div className="row" style={{ gap: 4, flexWrap: "nowrap" }}>
          <Link className={buttonClass("outline", "sm")} href={`/sell/invoices/${s.number}?print=1`}>
            Print
          </Link>
          {canAct && s.status === "COMPLETED" && !s.cancelPending ? (
            <Button variant="ghost" size="sm" style={{ color: "var(--danger)" }} onClick={() => void ask(s.number)}>
              Cancel…
            </Button>
          ) : null}
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHead
        title="Invoices"
        sub="Running invoice numbers. Invoices can be viewed, printed and sent – never edited or deleted."
        actions={
          <>
            {canAct ? (
              <Link className={buttonClass("success")} href="/sell/new">
                + New sale
              </Link>
            ) : null}
            <ExportButtons name="Invoices" onPdf={() => void exportAs("pdf")} onExcel={() => void exportAs("xlsx")} />
          </>
        }
      />
      <Alert tone="info" className="mb">
        Every sale gets an invoice with a running number. Invoices are never deleted – there is no delete button.
        Cancelling needs the owner&apos;s approval and the invoice stays on record as CANCELLED.
      </Alert>
      <Card>
        <CardContent>
          <Filters>
            <Field label="Search" htmlFor="fq" className="grow">
              <Input
                id="fq"
                placeholder="Invoice no, customer, gem code…"
                value={q}
                onChange={(e) => setQ(e.target.value)}
              />
            </Field>
            <Field label="From" htmlFor="ffrom">
              <Input id="ffrom" type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
            </Field>
            <Field label="To" htmlFor="fto">
              <Input id="fto" type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
            </Field>
            <Field label="Status" htmlFor="fst">
              <Select id="fst" value={status} onChange={(e) => setStatus(e.target.value as Status)}>
                <option value="">All</option>
                <option value="COMPLETED">Completed</option>
                <option value="CANCELLED">Cancelled</option>
              </Select>
            </Field>
            <Field label="Payment" htmlFor="fpay">
              <Select id="fpay" value={pay} onChange={(e) => setPay(e.target.value as Pay)}>
                <option value="">All</option>
                <option value="card">Card</option>
                <option value="cash">Cash</option>
                <option value="split">Split</option>
              </Select>
            </Field>
          </Filters>
        </CardContent>
        {!data ? (
          <p className="muted" style={{ padding: 16 }}>
            Loading…
          </p>
        ) : (
          <DataTable
            id="invTable"
            columns={columns}
            rows={data.items}
            rowKey={(s) => s.number}
            stackOnPhone
            empty="No invoices match the filters."
            footer={[
              `Total ${data.total}`,
              "",
              "",
              "",
              <b key="t">{inCurrency(data.amount, currency, rates)}</b>,
              "",
              "",
              "",
            ]}
          />
        )}
        {data && data.total > data.items.length ? (
          <p className="muted" style={{ padding: "8px 16px", fontSize: 12 }}>
            Showing the newest {data.items.length} of {data.total}. Narrow the dates or search to find older invoices.
          </p>
        ) : null}
      </Card>
    </>
  );
}
