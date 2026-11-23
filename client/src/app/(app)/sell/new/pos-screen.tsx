"use client";

/* New sale – prototype f-pos/pos.html (BRD F1–F6, G1, H3; rules 2, 3, 5, 10).
   1 Add the gems (scan or search) · 2 Who is buying · 3 Guide or walk-in · 4 Total & payment.
   Below the minimum the salesperson sends the bill to the owner and waits here; the screen checks every 3 seconds.
   The unfinished bill is kept in the browser, so a refresh does not lose it. */
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Badge, CertBadge, StatusPill } from "@/components/ui/badge";
import { Button, buttonClass } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { DataTable, type Column } from "@/components/ui/data-table";
import { Modal, useConfirm } from "@/components/ui/dialog";
import { Alert, Check, Field, Input, Options, Select } from "@/components/ui/form";
import { GemPicture, GemSvg } from "@/components/ui/gem-picture";
import { ScanBox } from "@/components/ui/scan-box";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage, fieldErrors } from "@/lib/api/client";
import { ago, carat, date, dateTime, lkr, num } from "@/lib/format";
import { certKey } from "@/lib/gems";
import { OWNER } from "@/lib/roles";
import {
  blankDraft,
  COUNTRIES,
  customerErrors,
  customerForm,
  loadDraft,
  MONTHS,
  pad2,
  saveDraft,
  type BillView,
  type Draft,
  type GemCard,
  type HeldView,
  type PaymentForm,
  type QuoteKind,
  type SaleView,
} from "@/lib/sales";
import { ALL_BRANCHES, useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";
import { cn } from "@/lib/utils";
import { PaymentModal } from "./payment-modal";
import { QuoteModal, type QuoteChoice } from "./quote-modal";

export function PosScreen() {
  const router = useRouter();
  const params = useSearchParams();
  const toast = useToast();
  const confirm = useConfirm();
  const { role, user, branch, branchName, rates } = useSession();
  const owner = role === OWNER;
  // "New sale" on a customer's page opens with that customer chosen (?customer=C-001).
  const [d, setD] = useState<Draft>(() => {
    const draft = loadDraft();
    const code = params.get("customer");
    return code ? { ...draft, custMode: "existing", customer: code } : draft;
  });
  const [check, setCheck] = useState<BillView | null>(null);
  const [held, setHeld] = useState<HeldView | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [paying, setPaying] = useState(false);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState<SaleView | null>(null);
  const [q, setQ] = useState("");
  const [making, setMaking] = useState<QuoteKind | null>(null);
  const [quoteErrors, setQuoteErrors] = useState<Record<string, string>>({});
  const [hits, setHits] = useState<GemCard[]>([]);
  const asked = useRef(0);
  const searched = useRef(0);

  const { data: customers, reload: reloadCustomers } = useLoad(() => api.GET("/api/sales/customers"), []);
  const { data: guides } = useLoad(() => api.GET("/api/sales/guides"), []);
  const { data: heldList, reload: reloadHeld } = useLoad(() => api.GET("/api/held-sales"), [branch]);

  /** Every change of the bill is kept in the browser. */
  const update = (next: Draft | ((o: Draft) => Draft)) =>
    setD((o) => {
      const n = typeof next === "function" ? next(o) : next;
      saveDraft(n);
      return n;
    });

  const heldOpen = !!held && d.held === held.number && (held.status === "PENDING" || held.status === "APPROVED");
  const locked = heldOpen;
  const total = d.items.reduce((s, i) => s + (Number(i.agreed) || 0), 0);
  const below = (code: string) => check?.lines.find((l) => l.gem === code)?.belowMin ?? false;
  const anyBelow = d.items.some((i) => below(i.gem.code));
  const guide = guides?.find((g) => g.code === d.guide);
  const chosen = customers?.find((c) => c.code === d.customer);
  const custReady = d.custMode === "existing" ? !!d.customer : !!(d.newCust.name.trim() && d.newCust.country);

  // ---------------------------------------------------------------- the held sale: open from ?held=, ask every 3 s
  const heldParam = params.get("held");
  const intentParam = params.get("intent");
  const intent = intentParam === "reserve" || intentParam === "quote" ? intentParam : null;
  useEffect(() => {
    if (!heldParam) return;
    let stop = false;
    void (async () => {
      const { data: h } = await api.GET("/api/held-sales/{number}", { params: { path: { number: heldParam } } });
      if (!h || stop) return;
      const gems = await Promise.all(
        h.items.map((i) => api.GET("/api/sales/gems/{code}", { params: { path: { code: i.code } } })),
      );
      if (stop) return;
      const items = h.items
        .map((i, k) => ({ gem: gems[k].data?.gem, agreed: Number(i.agreed) }))
        .filter((x): x is { gem: GemCard; agreed: number } => !!x.gem);
      update({
        ...blankDraft(),
        items,
        custMode: "existing",
        customer: h.customer.code,
        guide: h.guide?.code ?? "",
        held: h.number,
      });
      setHeld(h);
      toast(`Resumed ${h.number}`, h.status === "APPROVED" ? "Approved – take payment." : undefined);
      router.replace("/sell/new");
    })();
    return () => {
      stop = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [heldParam]);

  // ---------------------------------------------------------------- exchange: ?credit=CN-…&customer=C-… (P3-04)
  const creditParam = params.get("credit");
  const customerParam = params.get("customer");
  useEffect(() => {
    if (!creditParam) return;
    void (async () => {
      const { data, error } = await api.GET("/api/credit-notes/{number}", {
        params: { path: { number: creditParam } },
      });
      router.replace("/sell/new");
      if (!data) return toast("Credit note not found", errorMessage(error), "error");
      update({
        ...blankDraft(),
        credit: data.number,
        custMode: "existing",
        customer: customerParam ?? data.customer.code,
      });
      setHeld(null);
      toast(`Exchange – ${data.number}`, `Pays ${lkr(data.amount)} of the new bill.`);
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [creditParam]);
  const [creditNote, setCredit] = useState<{ number: string; amount: number; used: string | null } | null>(null);
  const credit = d.credit && creditNote?.number === d.credit ? creditNote : null;
  useEffect(() => {
    if (!d.credit) return;
    const no = d.credit;
    void (async () => {
      const { data, error } = await api.GET("/api/credit-notes/{number}", { params: { path: { number: no } } });
      if (!data) return toast("Credit note not found", errorMessage(error), "error");
      setCredit({ number: data.number, amount: Number(data.amount), used: data.usedSaleNo ?? null });
    })();
  }, [d.credit, toast]);

  // ---------------------------------------------------------------- "Sell now" on a reservation / quotation: ?quote=
  const quoteParam = params.get("quote");
  useEffect(() => {
    if (!quoteParam) return;
    let stop = false;
    void (async () => {
      const { data: qv, error } = await api.GET("/api/quotes/{number}", { params: { path: { number: quoteParam } } });
      if (stop) return;
      if (!qv) return toast("Not found", errorMessage(error), "error");
      if (qv.status !== "ACTIVE") {
        router.replace("/sell/new");
        return toast(`${qv.number} cannot be sold`, `It is ${qv.status.toLowerCase()}.`, "error");
      }
      const gems = await Promise.all(
        qv.items.map((i) => api.GET("/api/sales/gems/{code}", { params: { path: { code: i.code } } })),
      );
      if (stop) return;
      const items = qv.items
        .map((i, k) => ({ gem: gems[k].data?.gem, agreed: Number(i.agreed) }))
        .filter((x): x is { gem: GemCard; agreed: number } => !!x.gem && x.gem.status !== "SOLD");
      update({ ...blankDraft(), items, custMode: "existing", customer: qv.customer.code, quote: qv.number });
      setHeld(null);
      toast(
        `Loaded ${qv.number}`,
        `Gems and prices from the ${qv.kind === "RESERVATION" ? "reservation" : "quotation"}.`,
      );
      router.replace("/sell/new");
    })();
    return () => {
      stop = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [quoteParam]);

  useEffect(() => {
    if (!d.held) return;
    const no = d.held;
    let last = "";
    const ask = async () => {
      const { data: h } = await api.GET("/api/held-sales/{number}", { params: { path: { number: no } } });
      if (!h) return;
      if (last && last !== h.status) {
        if (h.status === "APPROVED") toast("Owner approved ✓", "You can complete the sale now.", "success");
        if (h.status === "REJECTED") toast("Owner rejected the price", h.note ?? undefined, "error");
      }
      last = h.status;
      setHeld(h);
    };
    void ask();
    const t = setInterval(() => void ask(), 3000);
    return () => clearInterval(t);
  }, [d.held, toast]);

  // ---------------------------------------------------------------- prices, minimum and commission from the server
  const sig = JSON.stringify([d.items.map((i) => [i.gem.code, i.agreed]), d.guide, d.held, d.quote]);
  useEffect(() => {
    const items = d.items
      .filter((i) => Number(i.agreed) > 0)
      .map((i) => ({ gem: i.gem.code, agreed: Number(i.agreed) }));
    const n = ++asked.current;
    const t = setTimeout(async () => {
      const { data } = await api.POST("/api/sales/check", {
        body: {
          items,
          guide: d.guide || undefined,
          branch: branch !== ALL_BRANCHES ? branch : undefined,
          held: d.held ?? undefined,
          quote: d.quote ?? undefined,
        },
      });
      if (n === asked.current) setCheck(data ?? null);
    }, 250);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sig, branch]);

  // ---------------------------------------------------------------- 1 · add the gems
  const add = (g: GemCard) => {
    if (locked) return toast("This bill is waiting for the owner", "Start a new bill to add gems.", "warning");
    if (d.items.some((i) => i.gem.code === g.code)) return toast("Already on this bill", g.code, "warning");
    if (d.items.length && d.items[0].gem.branch !== g.branch)
      return toast("Another branch", `${g.code} is at ${branchName(g.branch)}. One bill is for one branch.`, "error");
    update((o) => ({ ...o, items: [...o.items, { gem: g, agreed: Number(g.price) }] }));
    setQ("");
    setHits([]);
    toast(`Added ${g.code}`, `${g.name} · ${lkr(g.price)}`, "success");
    setTimeout(() => (document.querySelector(`[data-agreed="${g.code}"]`) as HTMLInputElement | null)?.select(), 60);
  };

  const scan = async (text: string) => {
    const hit = hits.find((h) => h.code === text.toUpperCase());
    const code = hit?.code ?? (/^(GEM-?)?\d+([|].*)?$/i.test(text) ? text.split("|")[0] : (hits[0]?.code ?? text));
    const { data, error } = await api.GET("/api/sales/gems/{code}", { params: { path: { code } } });
    if (!data) return toast("Not found", errorMessage(error) || `No gem with code ${code}.`, "error");
    if (!data.sellable) {
      const sold = data.gem.status === "SOLD";
      return toast(
        sold ? "Already sold – cannot sell again" : "Cannot be sold now",
        `${data.gem.code} is ${data.note}`,
        "error",
      );
    }
    add(data.gem);
  };

  const search = async (text: string) => {
    setQ(text);
    const n = ++searched.current;
    if (text.trim().length < 2) return setHits([]);
    const { data } = await api.GET("/api/gems", { params: { query: { q: text.trim(), size: 8 } } });
    if (n === searched.current) setHits(data?.items ?? []);
  };

  // ---------------------------------------------------------------- 4 · send to the owner / take payment
  const checkCustomer = () => {
    const e = customerErrors(d);
    setErrors(e);
    if (Object.keys(e).length) {
      toast("Customer name and country are needed", "They go on the invoice.", "error");
      document.getElementById(d.custMode === "existing" ? "custSel" : "custName")?.focus();
      return false;
    }
    return true;
  };

  const onPay = async () => {
    if (!d.items.length) return;
    if (d.items.some((i) => !(Number(i.agreed) > 0)))
      return toast("Enter the agreed price for every gem", undefined, "error");
    if (heldOpen && held?.status === "PENDING") return;
    if (!checkCustomer()) return;
    if (anyBelow && !owner && !(heldOpen && held?.status === "APPROVED")) {
      setBusy(true);
      const { data, error } = await api.POST("/api/held-sales", {
        body: {
          items: d.items.map((i) => ({ gem: i.gem.code, agreed: Number(i.agreed) })),
          customer: customerForm(d),
          guide: d.guide || undefined,
          branch: branch !== ALL_BRANCHES ? branch : undefined,
        },
      });
      setBusy(false);
      if (!data) {
        setErrors(fieldErrors(error));
        return toast("Not sent", errorMessage(error), "error");
      }
      update((o) => ({ ...o, held: data.number, custMode: "existing", customer: data.customer.code }));
      setHeld(data);
      reloadHeld();
      reloadCustomers();
      return toast(
        "Sent to the owner for approval",
        `Sale held: ${data.number}. You will see the answer here.`,
        "warning",
      );
    }
    setPaying(true);
  };

  const complete = async (payments: PaymentForm[]) => {
    setBusy(true);
    const { data, error } = await api.POST("/api/sales", {
      body: {
        items: d.items.map((i) => ({ gem: i.gem.code, agreed: Number(i.agreed) })),
        customer: customerForm(d),
        guide: d.guide || undefined,
        held: heldOpen ? (d.held ?? undefined) : undefined,
        quote: d.quote ?? undefined,
        creditNote: d.credit ?? undefined,
        branch: branch !== ALL_BRANCHES ? branch : undefined,
        payments,
      },
    });
    setBusy(false);
    if (!data) {
      setErrors(fieldErrors(error));
      return toast("Sale not completed", errorMessage(error), "error");
    }
    setPaying(false);
    setDone(data);
    update(blankDraft());
    setHeld(null);
    setCheck(null);
    reloadHeld();
    reloadCustomers();
  };

  // ---------------------------------------------------------------- keep for customer / give a quote
  const askQuote = (kind: QuoteKind) => {
    if (!d.items.length || locked) return;
    if (d.items.some((i) => !(Number(i.agreed) > 0)))
      return toast("Enter the agreed price for every gem", undefined, "error");
    if (anyBelow && !owner)
      return toast(
        "A price is below the minimum",
        "Reservations and quotes must be at or above the minimum price.",
        "error",
      );
    if (!checkCustomer()) return;
    setQuoteErrors({});
    setMaking(kind);
  };

  const makeQuote = async (c: QuoteChoice) => {
    if (!making) return;
    setBusy(true);
    const { data, error } = await api.POST("/api/quotes", {
      body: {
        kind: making,
        items: d.items.map((i) => ({ gem: i.gem.code, agreed: Number(i.agreed) })),
        customer: customerForm(d),
        branch: branch !== ALL_BRANCHES ? branch : undefined,
        expires: c.expires,
        deposit: c.deposit,
        notes: c.notes || undefined,
      },
    });
    setBusy(false);
    if (!data) {
      const fe = fieldErrors(error);
      setQuoteErrors(fe);
      setErrors(fe);
      return toast("Not saved", errorMessage(error), "error");
    }
    setMaking(null);
    update(blankDraft());
    setCheck(null);
    reloadCustomers();
    if (data.kind === "RESERVATION") {
      toast(`${data.number} created`, `Kept for ${data.customer.name} until ${date(data.expires)}.`, "success");
    } else {
      toast(`${data.number} created`, "Opening the quotation…", "success");
      router.push(`/sell/reserved/${data.number}/print`);
    }
  };

  const clear = async () => {
    if (d.items.length && !locked) {
      const ok = await confirm({
        title: "Clear this bill?",
        text: "The gems and the customer are removed from the bill.",
        yes: "Clear",
      });
      if (!ok) return;
    }
    update(blankDraft());
    setHeld(null);
    setErrors({});
    document.getElementById("scan")?.focus();
  };

  const cancelHeld = async () => {
    if (!held) return;
    const ok = await confirm({
      title: `Cancel ${held.number}?`,
      text: "The gems go back in stock. Start again with a price at or above the minimum.",
      yes: "Cancel held sale",
    });
    if (!ok) return;
    const { error } = await api.POST("/api/held-sales/{number}/cancel", { params: { path: { number: held.number } } });
    if (error) return toast("Not cancelled", errorMessage(error), "error");
    update(blankDraft());
    setHeld(null);
    reloadHeld();
    toast("Held sale cancelled", "The gems are back in stock.");
  };

  // ---------------------------------------------------------------- the steps of this sale
  const waiting = heldOpen && held?.status === "PENDING";
  const steps: [string, string, boolean][] = [
    ["Add the gems", d.items.length ? `${d.items.length} gem(s)` : "Scan a tag", d.items.length > 0],
    [
      "Customer",
      custReady ? (d.custMode === "existing" ? (chosen?.name ?? d.customer) : d.newCust.name) : "Name & country",
      custReady,
    ],
    ["Guide / driver", guide ? guide.name : "Walk-in if none", d.items.length > 0 && custReady],
    [
      waiting ? "Owner approval" : "Take payment",
      waiting ? "Waiting…" : heldOpen && held?.status === "APPROVED" ? "Approved ✓" : "Cash, card or both",
      false,
    ],
    ["Invoice", "Print or send", false],
  ];
  const next = steps.findIndex((s) => !s[2]);
  const jump = (i: number) => {
    const id = ["scan", "custBox", "guide", "payBtn", "payBtn"][i];
    const el = document.getElementById(id);
    el?.scrollIntoView({ behavior: "smooth", block: "center" });
    setTimeout(
      () => (el?.matches("input,select,button") ? el : el?.querySelector<HTMLElement>("input,select"))?.focus(),
      300,
    );
  };

  const payLabel = waiting
    ? "⏳ Waiting for owner approval…"
    : heldOpen && held?.status === "APPROVED"
      ? "✓ Approved – take payment"
      : anyBelow && !owner
        ? "Send for owner approval (below minimum)"
        : "Complete sale & take payment";

  const others = (heldList ?? []).filter((h) => h.number !== d.held);
  const heldColumns: Column<HeldView>[] = [
    {
      key: "no",
      header: "Held",
      cell: (h) => (
        <>
          <span className="mono">{h.number}</span>
          <br />
          <small className="muted">{dateTime(h.heldAt)}</small>
        </>
      ),
    },
    {
      key: "gem",
      header: "Gem",
      cell: (h) => (
        <span className="row" style={{ flexWrap: "nowrap", gap: 8 }}>
          <GemSvg gem={h.items[0]?.gemType} type={h.items[0]?.productType} size={28} />
          <span>
            <b>{h.items[0]?.name}</b>
            {h.items.length > 1 ? <small className="muted"> +{h.items.length - 1}</small> : null}
            <br />
            <small className="muted">{h.customer.name}</small>
          </span>
        </span>
      ),
    },
    { key: "agreed", header: "Agreed", num: true, cell: (h) => lkr(h.total) },
    {
      key: "status",
      header: "Status",
      cell: (h) =>
        h.status === "PENDING" ? (
          <Badge tone="warning">Waiting for owner</Badge>
        ) : (
          <Badge tone="success">Approved</Badge>
        ),
    },
    {
      key: "go",
      header: "",
      cell: (h) => (
        <Link className={buttonClass("outline", "sm")} href={`/sell/new?held=${h.number}`}>
          Resume
        </Link>
      ),
    },
  ];

  return (
    <>
      <PageHead
        title={`${intent === "reserve" ? "Keep a gem for a customer" : intent === "quote" ? "Give a price quote" : "New sale"} – ${branch === ALL_BRANCHES ? (d.items[0] ? branchName(d.items[0].gem.branch) : "all branches") : branchName(branch)}`}
        sub={`Salesperson: ${user.name} · ${date(new Date())} · Rates: USD ${num(rates.USD)} · EUR ${num(rates.EUR)}`}
        actions={
          <Link className={buttonClass("outline")} href="/sell/invoices">
            Invoices
          </Link>
        }
      />
      {intent && !d.quote ? (
        <div className="intent-banner mb">
          <span style={{ fontSize: 24 }} aria-hidden="true">
            {intent === "reserve" ? "📌" : "📝"}
          </span>
          <div>
            <b>{intent === "reserve" ? "Keep a gem for a customer" : "Give a price quote"}</b>
            <br />① Add the gem(s) and the agreed price · ② choose the customer · ③ press{" "}
            <b>{intent === "reserve" ? "📌 Keep for customer" : "📝 Give a quote"}</b> at the bottom right.
          </div>
          <Link className={buttonClass("ghost", "sm")} href="/sell/new" style={{ marginLeft: "auto" }}>
            This is a normal sale
          </Link>
        </div>
      ) : null}
      {d.credit ? (
        <Alert tone={credit?.used ? "danger" : "info"} className="mb">
          <b>Exchange – credit note {d.credit}</b>
          {credit ? (
            credit.used ? (
              <> was already used on {credit.used}.</>
            ) : (
              <> pays {lkr(credit.amount)} of this bill. Scan the new gem; the rest is paid as usual.</>
            )
          ) : null}{" "}
          <a
            href="#"
            onClick={(e) => {
              e.preventDefault();
              update({ ...d, credit: null });
            }}
          >
            Remove
          </a>
        </Alert>
      ) : null}
      {d.quote ? (
        <Alert tone="info" className="mb">
          <b>Selling {d.quote}</b> – the gems and prices come from the{" "}
          {d.quote.startsWith("RS") ? "reservation" : "quotation"}. Take payment as usual.{" "}
          <Link href="/sell/reserved">Reserved &amp; quotes</Link>
        </Alert>
      ) : null}
      <nav className="flowbar no-print mb" aria-label="This sale">
        <span className="fb-title">This sale</span>
        <ol>
          {steps.map(([label, note, ok], i) => (
            <li key={label} className={cn(ok && "done", i === next && "here")}>
              <a
                href="#"
                onClick={(e) => {
                  e.preventDefault();
                  jump(i);
                }}
              >
                <span className="fb-n">{ok ? "✓" : i + 1}</span>
                <span className="fb-t">
                  <b>{label}</b>
                  <small>{note}</small>
                </span>
              </a>
            </li>
          ))}
        </ol>
      </nav>

      <div className="g-2-1 grid">
        <div className="stack">
          <Card>
            <CardHeader title={<StepTitle n={1} ok={d.items.length > 0} text="Add the gems" />} level={2} />
            <CardContent>
              <ScanBox
                placeholder="Scan the QR / barcode tag, or type a code or name…"
                onScan={(c) => void scan(c)}
                onChange={(t) => void search(t)}
                keepFocus
                suggestions={
                  q.trim().length >= 2 ? (
                    <div className="suggest">
                      {hits.length ? (
                        hits.map((g) => (
                          <button key={g.code} type="button" onClick={() => void scan(g.code)}>
                            <GemSvg gem={g.gemType} type={g.productType} size={34} />
                            <div style={{ flex: 1 }}>
                              <b>{g.name}</b>{" "}
                              <span className="mono muted" style={{ fontSize: 11 }}>
                                {g.code}
                              </span>
                              <br />
                              <small className="muted">
                                {carat(g.carat)} · {g.colour} · {g.treatment}
                              </small>
                            </div>
                            <div className="right">
                              <b>{lkr(g.price)}</b>
                              <br />
                              <StatusPill status={g.status} />
                            </div>
                          </button>
                        ))
                      ) : (
                        <div className="empty">No match</div>
                      )}
                    </div>
                  ) : null
                }
              />
              <p className="muted mt-s" style={{ fontSize: 12 }}>
                A USB scanner types the code and presses Enter. You can also type the code or a name.
              </p>
            </CardContent>
          </Card>

          {held && d.held === held.number ? (
            <HeldBanner held={held} owner={owner} onCancel={() => void cancelHeld()} />
          ) : null}

          <Card>
            <CardHeader
              title="On this bill"
              level={2}
              actions={
                <Button variant="ghost" size="sm" onClick={() => void clear()}>
                  {locked ? "Start a new bill" : "Clear bill"}
                </Button>
              }
            >
              <p>More than one gem can be sold on one bill. Enter the agreed price for each gem.</p>
            </CardHeader>
            <CardContent>
              {!d.items.length ? (
                <div className="empty">
                  <GemSvg gem="Blue Sapphire" type="Ring" size={60} />
                  <br />
                  Scan a tag or search to add the first gem.
                </div>
              ) : (
                d.items.map((i) => {
                  const g = i.gem;
                  const low = below(g.code);
                  return (
                    <div key={g.code} className={cn("cart-line", low && "below")}>
                      <GemPicture gem={g.gemType} type={g.productType} photoUrl={g.photo} size={50} box={56} />
                      <div>
                        <b style={{ color: "var(--navy)" }}>{g.name}</b>{" "}
                        <span className="mono muted" style={{ fontSize: 11 }}>
                          {g.code}
                        </span>
                        <br />
                        <small className="muted">
                          {g.productType} · {carat(g.carat)} · {g.colour} · {g.shape} · {g.treatment}
                        </small>{" "}
                        <CertBadge type={certKey(g.certType)} />
                        <br />
                        <small>
                          Selling price: <b>{lkr(g.price)}</b>{" "}
                          <span className="muted">
                            (USD {num(Number(g.price) / rates.USD)} · EUR {num(Number(g.price) / rates.EUR)})
                          </span>
                        </small>
                        {owner && g.min != null ? (
                          <small>
                            <br />
                            Minimum: {lkr(g.min)} · Cost: {lkr(g.cost ?? 0)}
                          </small>
                        ) : null}
                        {low ? (
                          <div className="mt-s">
                            <Badge tone="warning">
                              ⚠ Below the minimum price{owner ? "" : " – owner approval needed"}
                            </Badge>
                          </div>
                        ) : null}
                      </div>
                      <div className="right" style={{ minWidth: 170 }}>
                        <label className="label" htmlFor={`agreed-${g.code}`}>
                          Agreed price (LKR)
                        </label>
                        <Input
                          id={`agreed-${g.code}`}
                          data-agreed={g.code}
                          className="right"
                          type="number"
                          min="1"
                          step="500"
                          inputMode="decimal"
                          readOnly={locked}
                          value={i.agreed || ""}
                          style={{ marginTop: 4 }}
                          onChange={(e) =>
                            update((o) => ({
                              ...o,
                              items: o.items.map((x) =>
                                x.gem.code === g.code ? { ...x, agreed: Number(e.target.value) || 0 } : x,
                              ),
                            }))
                          }
                        />
                        <div className="muted" style={{ fontSize: 11, marginTop: 4 }}>
                          USD {num((Number(i.agreed) || 0) / rates.USD)} · EUR{" "}
                          {num((Number(i.agreed) || 0) / rates.EUR)}
                        </div>
                        {!locked ? (
                          <Button
                            variant="ghost"
                            size="sm"
                            className="mt-s"
                            onClick={() =>
                              update((o) => ({ ...o, items: o.items.filter((x) => x.gem.code !== g.code) }))
                            }
                          >
                            Remove
                          </Button>
                        ) : null}
                      </div>
                    </div>
                  );
                })
              )}
            </CardContent>
          </Card>

          {others.length ? (
            <Card>
              <CardHeader title="Held sales (below minimum)" level={3} />
              <DataTable columns={heldColumns} rows={others} rowKey={(h) => h.number} stackOnPhone />
            </Card>
          ) : null}
        </div>

        <div className="stack">
          <Card>
            <CardHeader
              title={<StepTitle n={2} ok={custReady} text="Who is buying?" />}
              level={2}
              actions={
                <div className="tabs" role="group" aria-label="Customer">
                  {(["new", "existing"] as const).map((m) => (
                    <button
                      key={m}
                      type="button"
                      className={d.custMode === m ? "active" : ""}
                      aria-pressed={d.custMode === m}
                      disabled={locked}
                      onClick={() => update((o) => ({ ...o, custMode: m }))}
                    >
                      {m === "new" ? "New customer" : "Came before"}
                    </button>
                  ))}
                </div>
              }
            />
            <CardContent id="custBox">
              {d.custMode === "existing" ? (
                <>
                  <Field label="Customer" htmlFor="custSel" required error={errors["customer.code"]}>
                    <Select
                      id="custSel"
                      value={d.customer}
                      disabled={locked}
                      onChange={(e) => update((o) => ({ ...o, customer: e.target.value }))}
                    >
                      <Options
                        items={(customers ?? []).map((c) => ({ value: c.code, label: `${c.name} · ${c.country}` }))}
                        placeholder="Select a customer…"
                      />
                    </Select>
                  </Field>
                  {chosen ? (
                    <div className="muted mt-s" style={{ fontSize: 12 }}>
                      {[chosen.email, chosen.whatsapp].filter(Boolean).join(" · ") || "No email or WhatsApp"}
                      <br />
                      Promotions:{" "}
                      {chosen.consent ? (
                        <Badge tone="success">Permission given</Badge>
                      ) : (
                        <Badge>No permission</Badge>
                      )} · {chosen.purchases} previous purchase(s)
                    </div>
                  ) : null}
                </>
              ) : (
                <NewCustomerForm
                  d={d}
                  errors={errors}
                  onChange={(c) => update((o) => ({ ...o, newCust: { ...o.newCust, ...c } }))}
                />
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader
              title={<StepTitle n={3} ok={d.items.length > 0 && custReady} text="Came with a guide or driver?" />}
              level={2}
            />
            <CardContent>
              <Select
                id="guide"
                aria-label="Guide or driver"
                value={d.guide}
                disabled={locked}
                onChange={(e) => update((o) => ({ ...o, guide: e.target.value }))}
              >
                <option value="">Walk-in (no guide or driver)</option>
                {(guides ?? []).map((g) => (
                  <option key={g.code} value={g.code}>
                    {g.name} ({g.vehicle ?? "—"}) · {num(g.pct, 0)}%
                  </option>
                ))}
              </Select>
              <p className="muted mt-s" style={{ fontSize: 12 }}>
                {guide
                  ? `${guide.vehicle ?? ""} ${guide.vehicleNo ?? ""} · ${guide.phone} · commission ${num(guide.pct, 0)}% of the price`
                  : "Walk-in: no guide commission. Staff commission still applies."}
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader title={<StepTitle n={4} ok={false} text="Total & payment" />} level={2} />
            <CardContent>
              <dl className="dl" style={{ gridTemplateColumns: "1fr auto" }}>
                <dt>Gems</dt>
                <dd className="right">{d.items.length}</dd>
                <dt>Agreed total</dt>
                <dd className="right">{lkr(total)}</dd>
              </dl>
              <div className="sep" />
              <div className="row between">
                <b>Total to pay</b>
                <b style={{ fontSize: 20, color: "var(--navy)" }} id="posTotal">
                  {lkr(total)}
                </b>
              </div>
              <div className="right text-gold" style={{ fontSize: 13 }}>
                USD {num(total / rates.USD)} · EUR {num(total / rates.EUR)}
              </div>
              <div className="sep" />
              <dl className="dl" style={{ gridTemplateColumns: "1fr auto", fontSize: 12 }}>
                <dt>Guide commission {guide ? `(${num(guide.pct, 0)}%)` : ""}</dt>
                <dd className="right">{lkr(check?.guideComm ?? 0)}</dd>
                <dt>Staff commission (level {check?.level || "–"})</dt>
                <dd className="right">{lkr(check?.staffComm ?? 0)}</dd>
              </dl>
              <p className="muted" style={{ fontSize: 11, marginTop: 4 }}>
                Commissions are worked out for each sale automatically.
              </p>
            </CardContent>
            <div className="card-footer" style={{ flexDirection: "column", alignItems: "stretch" }}>
              <Button
                id="payBtn"
                size="lg"
                block
                variant={anyBelow && !owner && !(heldOpen && held?.status === "APPROVED") ? "gold" : "success"}
                disabled={!d.items.length || waiting || busy}
                onClick={() => void onPay()}
              >
                {busy ? "Please wait…" : payLabel}
              </Button>
              {!d.quote ? (
                <div className="row" style={{ gap: 8, flexWrap: "nowrap" }}>
                  <Button
                    id="reserveBtn"
                    variant={intent === "reserve" ? "gold" : "outline"}
                    style={{ flex: 1 }}
                    disabled={!d.items.length || locked || busy}
                    onClick={() => askQuote("RESERVATION")}
                  >
                    📌 Keep for customer
                  </Button>
                  <Button
                    id="quoteBtn"
                    variant={intent === "quote" ? "gold" : "outline"}
                    style={{ flex: 1 }}
                    disabled={!d.items.length || locked || busy}
                    onClick={() => askQuote("QUOTATION")}
                  >
                    📝 Give a quote
                  </Button>
                </div>
              ) : null}
            </div>
          </Card>

          <Alert tone="info">
            A gem cannot be sold below its minimum price without the owner&apos;s approval. Each gem is unique: once
            sold it cannot be sold again unless it is returned. Prices and payments use LKR, USD and EUR only.
          </Alert>
        </div>
      </div>

      {making ? (
        <QuoteModal
          kind={making}
          count={d.items.length}
          total={total}
          customer={d.custMode === "existing" ? (chosen?.name ?? d.customer) : d.newCust.name}
          busy={busy}
          errors={quoteErrors}
          onClose={() => setMaking(null)}
          onSave={(c) => void makeQuote(c)}
        />
      ) : null}

      {paying ? (
        <PaymentModal
          total={total}
          credit={credit && !credit.used ? credit : null}
          busy={busy}
          onClose={() => setPaying(false)}
          onPay={(p) => void complete(p)}
        />
      ) : null}

      {done ? (
        <Modal
          open
          onOpenChange={(o) => !o && setDone(null)}
          title="Sale completed ✓"
          desc={`Invoice ${done.number} · ${lkr(done.total)}`}
          footer={
            <>
              <Button
                variant="outline"
                onClick={() => {
                  setDone(null);
                  setTimeout(() => document.getElementById("scan")?.focus(), 50);
                }}
              >
                New sale
              </Button>
              <Link className={buttonClass("sapphire")} href={`/sell/invoices/${done.number}`}>
                Print invoice
              </Link>
            </>
          }
        >
          <dl className="dl" id="doneSale">
            <dt>Invoice</dt>
            <dd className="mono">{done.number}</dd>
            <dt>Customer</dt>
            <dd>
              {done.customer.name} ({done.customer.country})
            </dd>
            <dt>Paid</dt>
            <dd>
              {done.payments
                .map((p) =>
                  p.method === "CARD"
                    ? `Card ${lkr(p.lkr)}`
                    : p.method === "CREDIT"
                      ? `Credit note ${lkr(p.lkr)}`
                      : `Cash ${p.currency} ${num(p.amount)}`,
                )
                .join(" + ")}
            </dd>
            <dt>Change due</dt>
            <dd className="bold text-gold">{lkr(done.change)}</dd>
            <dt>Commissions</dt>
            <dd>
              Guide {lkr(done.guideComm)} · Staff {lkr(done.staffComm)}
            </dd>
          </dl>
          <Alert tone="info" className="mt">
            The owner received a sale alert.
          </Alert>
        </Modal>
      ) : null}
    </>
  );
}

function StepTitle({ n, ok, text }: { n: number; ok: boolean; text: string }) {
  return (
    <>
      <span className={cn("step-n", ok && "ok")}>{ok ? "✓" : n}</span>
      {text}
    </>
  );
}

function HeldBanner({ held, owner, onCancel }: { held: HeldView; owner: boolean; onCancel: () => void }) {
  if (held.status === "PENDING")
    return (
      <Alert tone="warning" className="pending-pulse">
        <b>Waiting for owner approval – {held.number}</b>
        <br />
        The price is below the minimum. Approval request {held.approvalNo} was sent to the owner. This screen checks
        every few seconds (sent {ago(held.heldAt)}).
        <div className="row mt-s">
          {owner ? (
            <Link className={buttonClass("outline", "sm")} href="/approvals">
              Open approvals
            </Link>
          ) : null}
          <Button size="sm" variant="ghost" onClick={onCancel}>
            Cancel held sale
          </Button>
        </div>
      </Alert>
    );
  if (held.status === "APPROVED")
    return (
      <Alert tone="success">
        <b>Owner approved the price</b> – {held.decidedBy}
        {held.decidedAt ? ` at ${dateTime(held.decidedAt)}` : ""}
        {held.note ? ` · “${held.note}”` : ""}
        <br />
        You can now take payment and complete the sale.
      </Alert>
    );
  if (held.status === "REJECTED")
    return (
      <Alert tone="danger">
        <b>Owner rejected the price</b>
        {held.note ? `: “${held.note}”` : ""}
        <br />
        Agree a new price at or above the minimum, or start a new bill.
      </Alert>
    );
  return null;
}

function NewCustomerForm({
  d,
  errors,
  onChange,
}: {
  d: Draft;
  errors: Record<string, string>;
  onChange: (c: Partial<Draft["newCust"]>) => void;
}) {
  const c = d.newCust;
  return (
    <div className="stack" style={{ gap: 10 }}>
      <Field label="Name" htmlFor="custName" required error={errors["customer.name"]}>
        <Input
          id="custName"
          maxLength={120}
          placeholder="e.g. Anna Müller"
          value={c.name}
          onChange={(e) => onChange({ name: e.target.value })}
        />
      </Field>
      <Field label="Country" htmlFor="custCountry" required error={errors["customer.country"]}>
        <Select id="custCountry" value={c.country} onChange={(e) => onChange({ country: e.target.value })}>
          <Options items={COUNTRIES.map((x) => ({ value: x, label: x }))} placeholder="Select country…" />
        </Select>
      </Field>
      <Field label="Email" htmlFor="custEmail" error={errors["customer.email"]}>
        <Input
          id="custEmail"
          type="email"
          maxLength={160}
          placeholder="for the invoice PDF"
          value={c.email}
          onChange={(e) => onChange({ email: e.target.value })}
        />
      </Field>
      <Field label="WhatsApp number" htmlFor="custWa" error={errors["customer.whatsapp"]}>
        <Input
          id="custWa"
          type="tel"
          maxLength={40}
          placeholder="+49 …"
          value={c.whatsapp}
          onChange={(e) => onChange({ whatsapp: e.target.value })}
        />
      </Field>
      <Field
        label={
          <>
            Birthday <small className="muted">(optional)</small>
          </>
        }
        htmlFor="custDay"
        error={errors["customer.birthday"]}
        hint="Day and month only – for the automatic birthday wish."
      >
        <div className="row" style={{ gap: 6, flexWrap: "nowrap" }}>
          <Select
            id="custDay"
            aria-label="Birthday day"
            style={{ width: 84 }}
            value={c.day}
            onChange={(e) => onChange({ day: e.target.value })}
          >
            <Options
              items={Array.from({ length: 31 }, (_, i) => ({ value: pad2(i + 1), label: String(i + 1) }))}
              placeholder="Day"
            />
          </Select>
          <Select
            id="custMonth"
            aria-label="Birthday month"
            style={{ flex: 1 }}
            value={c.month}
            onChange={(e) => onChange({ month: e.target.value })}
          >
            <Options items={MONTHS.map((m, i) => ({ value: pad2(i + 1), label: m }))} placeholder="Month" />
          </Select>
        </div>
      </Field>
      <Check checked={c.consent} onChange={(v) => onChange({ consent: v })}>
        Customer agrees to receive offers and birthday wishes by email / WhatsApp. They can unsubscribe at any time.
      </Check>
    </div>
  );
}
