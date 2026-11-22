"use client";

/* Reserved & quotes – prototype f-pos/reservations.html (Guide 32; BRD F7; rule 3). Gems kept for a returning customer,
   and price quotes. Both have an expiry date; every night the ones past their date expire and kept gems go back in
   stock. Sell now opens New sale with the gems and prices. */
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button, buttonClass } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { DataTable, type Column } from "@/components/ui/data-table";
import { Modal, useConfirm } from "@/components/ui/dialog";
import { Alert, Field, Input } from "@/components/ui/form";
import { GemSvg } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage, fieldErrors } from "@/lib/api/client";
import { date, lkr, num } from "@/lib/format";
import { inDays, type QuoteKind, type QuoteView } from "@/lib/sales";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function quoteStatus(q: Pick<QuoteView, "status" | "kind">) {
  switch (q.status) {
    case "ACTIVE":
      return <Badge tone="success">Active</Badge>;
    case "CONVERTED":
      return <Badge tone="blue">Sold</Badge>;
    case "RELEASED":
      return <Badge>{q.kind === "RESERVATION" ? "Released" : "Withdrawn"}</Badge>;
    default:
      return <Badge tone="warning">Expired</Badge>;
  }
}

export function ReservedScreen() {
  const router = useRouter();
  const params = useSearchParams();
  const toast = useToast();
  const confirm = useConfirm();
  const { branch, rates } = useSession();
  const kind: QuoteKind = params.get("tab") === "quotation" ? "QUOTATION" : "RESERVATION";
  const reserve = kind === "RESERVATION";
  const { data, reload } = useLoad(() => api.GET("/api/quotes", { params: { query: { kind } } }), [kind, branch]);
  const [extending, setExtending] = useState<QuoteView | null>(null);
  const [newDate, setNewDate] = useState("");
  const [error, setError] = useState("");

  const setTab = (k: QuoteKind) =>
    router.replace(k === "QUOTATION" ? "/sell/reserved?tab=quotation" : "/sell/reserved");

  const release = async (q: QuoteView) => {
    const ok = await confirm({
      title: `${reserve ? "Release" : "Withdraw"} ${q.number}?`,
      text: reserve
        ? "The gems go back to “In stock” and can be sold to anyone."
        : "The quotation will no longer be valid.",
      yes: reserve ? "Release" : "Withdraw",
    });
    if (!ok) return;
    const { error: e } = await api.POST("/api/quotes/{number}/release", { params: { path: { number: q.number } } });
    if (e) return toast("Not done", errorMessage(e), "error");
    toast("Done", `${q.number} ${reserve ? "released" : "withdrawn"}`, "success");
    reload();
  };

  const extend = async () => {
    if (!extending) return;
    if (!newDate || newDate < inDays(0)) return setError("From today");
    const { error: e } = await api.POST("/api/quotes/{number}/extend", {
      params: { path: { number: extending.number } },
      body: { expires: newDate },
    });
    if (e) {
      setError(fieldErrors(e).expires ?? "");
      return toast("Not extended", errorMessage(e), "error");
    }
    toast("Expiry extended", `${extending.number} until ${date(newDate)}`, "success");
    setExtending(null);
    reload();
  };

  const columns: Column<QuoteView>[] = [
    {
      key: "no",
      header: "No.",
      cell: (q) => (
        <>
          <span className="mono">{q.number}</span>
          <br />
          <small className="muted">{date(q.createdOn)}</small>
        </>
      ),
    },
    {
      key: "items",
      header: "Gems",
      cell: (q) =>
        q.items.map((i) => (
          <div key={i.code} className="row" style={{ gap: 6, flexWrap: "nowrap", marginBottom: 4 }}>
            <GemSvg gem={i.gemType} type={i.productType} size={28} />
            <span>
              <b>{i.name}</b>
              <br />
              <small className="mono muted">{i.code}</small>
            </span>
          </div>
        )),
    },
    {
      key: "cust",
      header: "Customer",
      cell: (q) => (
        <>
          {q.customer.name}
          <br />
          <small className="muted">{q.customer.country}</small>
        </>
      ),
    },
    {
      key: "price",
      header: "Price",
      num: true,
      cell: (q) => (
        <>
          <b>{lkr(q.total)}</b>
          <br />
          <small className="muted">USD {num(Number(q.total) / rates.USD)}</small>
        </>
      ),
    },
    {
      key: "dep",
      header: reserve ? "Deposit" : "By",
      cell: (q) => (reserve ? lkr(q.deposit) : q.staff),
    },
    {
      key: "exp",
      header: "Expires",
      cell: (q) => (
        <>
          {date(q.expires)}
          {q.status === "ACTIVE" ? (
            <>
              <br />
              <small className={q.daysLeft <= 1 ? "text-danger" : "muted"}>
                {q.daysLeft <= 0 ? "today" : `in ${q.daysLeft} day${q.daysLeft > 1 ? "s" : ""}`}
              </small>
            </>
          ) : null}
        </>
      ),
    },
    {
      key: "st",
      header: "Status",
      cell: (q) => (
        <>
          {quoteStatus(q)}
          {q.saleNo ? (
            <>
              <br />
              <Link className="mono" href={`/sell/invoices/${q.saleNo}`} style={{ fontSize: 11 }}>
                {q.saleNo}
              </Link>
            </>
          ) : null}
        </>
      ),
    },
    {
      key: "act",
      header: "",
      cell: (q) => (
        <div className="row" style={{ gap: 4 }}>
          {q.status === "ACTIVE" ? (
            <>
              <Link className={buttonClass("success", "sm")} href={`/sell/new?quote=${q.number}`}>
                Sell now
              </Link>
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setNewDate(q.expires);
                  setError("");
                  setExtending(q);
                }}
              >
                Extend
              </Button>
              <Button variant="ghost" size="sm" onClick={() => void release(q)}>
                {reserve ? "Release" : "Withdraw"}
              </Button>
            </>
          ) : null}
          {!reserve ? (
            <Link className={buttonClass("outline", "sm")} href={`/sell/reserved/${q.number}/print`}>
              Print
            </Link>
          ) : null}
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHead
        title="Reserved & quotes"
        sub="Keep a gem for a returning customer, or give a price quote. Both have an expiry date."
        actions={
          <Link className={buttonClass("success")} href={`/sell/new?intent=${reserve ? "reserve" : "quote"}`}>
            + {reserve ? "Keep a gem" : "Give a quote"} (from New sale)
          </Link>
        }
      />
      <div className="row mb">
        <div className="tabs" role="group" aria-label="Show">
          <button type="button" className={reserve ? "active" : ""} onClick={() => setTab("RESERVATION")}>
            Reservations ({data?.reservations ?? 0} active)
          </button>
          <button type="button" className={!reserve ? "active" : ""} onClick={() => setTab("QUOTATION")}>
            Quotations ({data?.quotations ?? 0} active)
          </button>
        </div>
      </div>
      <Card>
        {!data ? (
          <p className="muted" style={{ padding: 16 }}>
            Loading…
          </p>
        ) : (
          <DataTable
            id="quoteTable"
            columns={columns}
            rows={data.items}
            rowKey={(q) => q.number}
            stackOnPhone
            empty={`No ${reserve ? "kept gems" : "quotes"} yet. Make one from New sale.`}
          />
        )}
      </Card>
      <Alert tone="info" className="mt">
        Kept gems cannot be sold to another customer until the reservation is sold, released or expires. Every night the
        ones past their expiry date end automatically.
      </Alert>

      {extending ? (
        <Modal
          open
          onOpenChange={(o) => !o && setExtending(null)}
          title={`Extend ${extending.number}`}
          footer={
            <>
              <Button variant="outline" onClick={() => setExtending(null)}>
                Cancel
              </Button>
              <Button variant="sapphire" id="extendSave" onClick={() => void extend()}>
                Save
              </Button>
            </>
          }
        >
          <Field label="New expiry date" htmlFor="nd" required error={error || undefined}>
            <Input
              id="nd"
              type="date"
              min={inDays(0)}
              max={inDays(90)}
              value={newDate}
              onChange={(e) => setNewDate(e.target.value)}
            />
          </Field>
        </Modal>
      ) : null}
    </>
  );
}
