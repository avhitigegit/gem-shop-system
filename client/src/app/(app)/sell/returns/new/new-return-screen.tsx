"use client";

/* Customer brings a gem back – prototype e-returns/new-return.html (Guide 34; BRD E1, E2, D2, K3; rules 3, 7). Four
   short steps: find the sale → which gem, refund or exchange → why → check & send to the owner. Then the page follows
   the owner's answer (asks every 3 s). Approved: gem back in stock, credit note made, commission taken back. */
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button, buttonClass } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { useReason } from "@/components/ui/dialog";
import { Alert, Field, Input, Textarea } from "@/components/ui/form";
import { GemPicture, GemSvg } from "@/components/ui/gem-picture";
import { WizardSteps } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import { carat, date, dateTime, lkr } from "@/lib/format";
import { OWNER } from "@/lib/roles";
import type { ReturnSale, ReturnView } from "@/lib/sales";
import { useSession } from "@/lib/session";

const STEPS = ["Find the sale", "Which gem?", "Why?", "Check & send"];
const REASONS = [
  "Ring size does not fit",
  "Customer changed their mind",
  "Wants a different colour",
  "Wants a bigger / smaller stone",
  "Problem with the stone or setting",
  "Bought by mistake",
];
type Kind = ReturnView["kind"];

export function NewReturnScreen() {
  const params = useSearchParams();
  const router = useRouter();
  const toast = useToast();
  const askReason = useReason();
  const { role, branch } = useSession();
  const owner = role === OWNER;
  const [step, setStep] = useState(0);
  const [q, setQ] = useState("");
  const [hits, setHits] = useState<ReturnSale[] | null>(null);
  const [sale, setSale] = useState<ReturnSale | null>(null);
  const [gem, setGem] = useState<string | null>(null);
  const [kind, setKind] = useState<Kind | null>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [answer, setRet] = useState<ReturnView | null>(null);
  const asked = useRef(0);
  const returnNo = params.get("id");
  const ret = answer && answer.number === returnNo ? answer : null;
  const inv = params.get("inv");

  // ?inv= from the invoice page: open step 2 with that sale
  useEffect(() => {
    if (!inv) return;
    void (async () => {
      const { data, error } = await api.GET("/api/returns/sales/{number}", { params: { path: { number: inv } } });
      if (!data) return toast("Sale not found", errorMessage(error), "error");
      setSale(data);
      setStep(1);
    })();
  }, [inv, toast]);

  // step 1: search as you type
  useEffect(() => {
    if (returnNo) return;
    const n = ++asked.current;
    const t = setTimeout(async () => {
      const { data } = await api.GET("/api/returns/sales", { params: { query: { q: q.trim() || undefined } } });
      if (n === asked.current) setHits(data ?? []);
    }, 250);
    return () => clearTimeout(t);
  }, [q, branch, returnNo]);

  // after sending: follow the owner's answer
  useEffect(() => {
    if (!returnNo) return;
    let last = "";
    const ask = async () => {
      const { data } = await api.GET("/api/returns/{number}", { params: { path: { number: returnNo } } });
      if (!data) return;
      if (last === "PENDING" && data.status !== "PENDING") {
        if (data.status === "APPROVED")
          toast("Owner approved the return ✓", `Credit note ${data.creditNote}`, "success");
        else toast("Owner rejected the return", data.ownerNote ?? undefined, "error");
      }
      last = data.status;
      setRet(data);
    };
    void ask();
    const t = setInterval(() => void ask(), 3000);
    return () => clearInterval(t);
  }, [returnNo, toast]);

  const pickSale = (s: ReturnSale) => {
    if (sale?.number !== s.number) setGem(null);
    setSale(s);
    setStep(1);
  };

  const send = async () => {
    if (!sale || !gem || !kind) return;
    setBusy(true);
    const { data, error } = await api.POST("/api/returns", {
      body: { sale: sale.number, gem, kind, reason: reason.trim() },
    });
    setBusy(false);
    if (!data) return toast("Not sent", errorMessage(error), "error");
    toast("Sent to the owner", data.number, "warning");
    router.replace(`/sell/returns/new?id=${data.number}`);
  };

  const decide = async (approve: boolean) => {
    if (!ret?.approvalNo) return;
    let note: string | undefined;
    if (!approve) {
      const r = await askReason({ title: "Reject this return?", desc: "Tell the salesperson why.", yes: "Reject" });
      if (!r) return;
      note = r;
    }
    const path = { number: ret.approvalNo };
    const { error } = approve
      ? await api.POST("/api/approvals/{number}/approve", { params: { path }, body: {} })
      : await api.POST("/api/approvals/{number}/reject", { params: { path }, body: { note } });
    if (error) return toast("Not done", errorMessage(error), "error");
    const { data } = await api.GET("/api/returns/{number}", { params: { path: { number: ret.number } } });
    if (data) setRet(data);
    if (approve) toast("Return approved", `Gem back in stock · credit note ${data?.creditNote ?? ""}`, "success");
  };

  const head = (
    <div className="page-head">
      <div>
        <h1>Customer brings a gem back</h1>
        <p className="muted" style={{ margin: "4px 0 0" }}>
          Four short steps. The owner approves every return; after that the gem goes back into stock and a credit note
          is made.
        </p>
      </div>
    </div>
  );

  if (returnNo) {
    return (
      <div className="wiz">
        {head}
        {ret ? <Answer r={ret} owner={owner} onDecide={(a) => void decide(a)} /> : <p className="muted">Loading…</p>}
      </div>
    );
  }

  const line = sale?.items.find((i) => i.gem.code === gem);
  const foot = (
    back: boolean,
    next: string | null,
    ok: boolean,
    onNext: () => void,
    variant: "sapphire" | "success" = "sapphire",
  ) => (
    <div className="wiz-foot">
      {back ? (
        <Button variant="outline" size="lg" onClick={() => setStep(step - 1)}>
          ← Back
        </Button>
      ) : (
        <Link className={buttonClass("ghost")} href="/sell">
          Cancel
        </Link>
      )}
      {next ? (
        <Button size="lg" variant={variant} id="wizNext" disabled={!ok || busy} onClick={onNext}>
          {busy ? "Please wait…" : next}
        </Button>
      ) : null}
    </div>
  );

  return (
    <div className="wiz">
      {head}
      <WizardSteps steps={STEPS} current={step} onStep={setStep} />

      {step === 0 ? (
        <>
          <Card>
            <CardHeader
              title={
                <>
                  <span className="step-n">1</span>Find the sale
                </>
              }
            />
            <CardContent>
              <Field label="Scan the gem tag, or type the invoice number or customer name" htmlFor="q">
                <Input
                  id="q"
                  className="input-lg"
                  autoFocus
                  autoComplete="off"
                  value={q}
                  placeholder="e.g. GEM-00121, INV-000…, Müller"
                  onChange={(e) => setQ(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && hits?.[0]) pickSale(hits[0]);
                  }}
                />
              </Field>
              <div className="label mt">{q.trim() ? `${hits?.length ?? 0} sale(s) found` : "Latest sales"}</div>
              <div className="pick-list mt-s" id="hits">
                {hits === null ? (
                  <p className="muted">Loading…</p>
                ) : hits.length ? (
                  hits.map((s) => (
                    <button
                      type="button"
                      key={s.number}
                      className={sale?.number === s.number ? "pick sel" : "pick"}
                      data-sale={s.number}
                      onClick={() => pickSale(s)}
                    >
                      <GemSvg gem={s.items[0]?.gem.gemType} type={s.items[0]?.gem.productType} size={44} />
                      <span style={{ flex: 1 }}>
                        <b>{s.customer.name}</b> <small className="muted">{s.customer.country}</small>
                        <br />
                        <small className="muted">
                          <span className="mono">{s.number}</span> · {date(s.soldAt)} ({s.daysAgo} days ago) ·{" "}
                          {s.items.map((i) => i.gem.name).join(", ")}
                        </small>
                      </span>
                      <span className="right">
                        <b>{lkr(s.total)}</b>
                      </span>
                    </button>
                  ))
                ) : (
                  <div className="empty">No sale found. Check the invoice number on the customer&apos;s receipt.</div>
                )}
              </div>
            </CardContent>
          </Card>
          {foot(false, "Next: which gem →", !!sale, () => setStep(1))}
        </>
      ) : null}

      {step === 1 && sale ? (
        <>
          <Card>
            <CardHeader
              title={
                <>
                  <span className="step-n">2</span>Which gem is coming back?
                </>
              }
            />
            <CardContent>
              <Alert tone={sale.late ? "warning" : "info"} className="mb">
                <b>{sale.customer.name}</b> · invoice <span className="mono">{sale.number}</span> · bought{" "}
                {date(sale.soldAt)} ({sale.daysAgo} days ago).{" "}
                {sale.late ? (
                  <>
                    <b>This is after the {sale.returnDays}-day return time.</b> You can still ask – the owner decides.
                  </>
                ) : (
                  `Inside the ${sale.returnDays}-day return time.`
                )}
              </Alert>
              <div className="pick-list">
                {sale.items.map((i) => {
                  const off = i.returned || !!i.waiting;
                  return (
                    <button
                      type="button"
                      key={i.gem.code}
                      data-pid={i.gem.code}
                      className={gem === i.gem.code ? "pick sel" : "pick"}
                      disabled={off}
                      onClick={() => setGem(i.gem.code)}
                    >
                      <GemPicture
                        gem={i.gem.gemType}
                        type={i.gem.productType}
                        photoUrl={i.gem.photo}
                        size={52}
                        box={52}
                      />
                      <span style={{ flex: 1 }}>
                        <b>{i.gem.name}</b>{" "}
                        <span className="mono muted" style={{ fontSize: 11 }}>
                          {i.gem.code}
                        </span>
                        <br />
                        <small className="muted">
                          {carat(i.gem.carat)} · {i.gem.colour}
                        </small>
                        {off ? (
                          <>
                            <br />
                            <Badge>{i.returned ? "Already returned" : `Return already waiting (${i.waiting})`}</Badge>
                          </>
                        ) : null}
                      </span>
                      <span className="right">
                        Paid
                        <br />
                        <b>{lkr(i.gem.agreed)}</b>
                      </span>
                    </button>
                  );
                })}
              </div>
              <div className="label mt">What does the customer want?</div>
              <div className="choice-2 mt-s">
                <button
                  type="button"
                  data-kind="REFUND"
                  className={kind === "REFUND" ? "choice sel" : "choice"}
                  onClick={() => setKind("REFUND")}
                >
                  <span className="ci">💵</span>
                  <b>Money back (refund)</b>
                  <small>The customer gets the money back with a credit note.</small>
                </button>
                <button
                  type="button"
                  data-kind="EXCHANGE"
                  className={kind === "EXCHANGE" ? "choice sel" : "choice"}
                  onClick={() => setKind("EXCHANGE")}
                >
                  <span className="ci">🔄</span>
                  <b>Swap for another gem (exchange)</b>
                  <small>The credit note is used to pay for the new gem.</small>
                </button>
              </div>
            </CardContent>
          </Card>
          {foot(true, "Next: why? →", !!gem && !!kind, () => setStep(2))}
        </>
      ) : null}

      {step === 2 ? (
        <>
          <Card>
            <CardHeader
              title={
                <>
                  <span className="step-n">3</span>Why is the customer bringing it back?
                </>
              }
            />
            <CardContent>
              <div className="label">Tap one, or write your own</div>
              <div className="chips mt-s">
                {REASONS.map((r) => (
                  <button type="button" key={r} className="chip" onClick={() => setReason(r)}>
                    {r}
                  </button>
                ))}
              </div>
              <Field label="Reason (the owner will read this)" htmlFor="rr" required className="mt">
                <Textarea
                  id="rr"
                  rows={3}
                  maxLength={500}
                  placeholder="Write a few words…"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                />
              </Field>
            </CardContent>
          </Card>
          {foot(true, "Next: check →", reason.trim().length >= 4, () => setStep(3))}
        </>
      ) : null}

      {step === 3 && sale && line ? (
        <>
          <Card>
            <CardHeader
              title={
                <>
                  <span className="step-n">4</span>Check and send to the owner
                </>
              }
            />
            <CardContent>
              <div className="g2 grid">
                <div className="row" style={{ gap: 14, alignItems: "flex-start", flexWrap: "nowrap" }}>
                  <GemPicture
                    gem={line.gem.gemType}
                    type={line.gem.productType}
                    photoUrl={line.gem.photo}
                    size={80}
                    box={80}
                  />
                  <dl className="dl" style={{ gridTemplateColumns: "auto 1fr" }}>
                    <dt>Gem</dt>
                    <dd>
                      <b>{line.gem.name}</b> <span className="mono muted">{line.gem.code}</span>
                    </dd>
                    <dt>Customer</dt>
                    <dd>
                      {sale.customer.name} ({sale.customer.country})
                    </dd>
                    <dt>Invoice</dt>
                    <dd className="mono">
                      {sale.number} · {date(sale.soldAt)}
                    </dd>
                    <dt>Wants</dt>
                    <dd>
                      <b>{kind === "EXCHANGE" ? "🔄 Exchange" : "💵 Refund"}</b>
                    </dd>
                    <dt>Amount</dt>
                    <dd>
                      <b>{lkr(line.gem.agreed)}</b>
                    </dd>
                    <dt>Reason</dt>
                    <dd>{reason.trim()}</dd>
                  </dl>
                </div>
                <div>
                  <div className="label">What happens next</div>
                  <NextSteps r={null} kind={kind} />
                </div>
              </div>
            </CardContent>
          </Card>
          {foot(true, "✓ Send to the owner for approval", true, () => void send(), "success")}
        </>
      ) : null}
    </div>
  );
}

function NextSteps({ r, kind }: { r: ReturnView | null; kind: Kind | null }) {
  const st = r?.status ?? null;
  const exchange = (r?.kind ?? kind) === "EXCHANGE";
  const items: [string, "done" | "now" | "", string][] = [
    ["Request sent to the owner", r ? "done" : "", r ? dateTime(r.requestedAt) : "When you press Send"],
    [
      st === "REJECTED" ? "Owner said no" : "Owner approves",
      st === "APPROVED" || st === "REJECTED" ? "done" : st === "PENDING" ? "now" : "",
      st === "PENDING"
        ? "Waiting… this page updates by itself"
        : st === "REJECTED"
          ? "The gem stays sold"
          : st === "APPROVED"
            ? "Approved"
            : "On the phone or in Approvals",
    ],
    ["Gem goes back into stock", st === "APPROVED" ? "done" : "", "It can be sold again"],
    ["Credit note is made", st === "APPROVED" ? "done" : "", r?.creditNote ?? "Commission for this gem is taken back"],
    [
      exchange ? "Sell the new gem" : "Give the money back",
      r?.usedSaleNo ? "done" : "",
      exchange
        ? r?.usedSaleNo
          ? `Paid on ${r.usedSaleNo}`
          : "Start a new sale for the swap"
        : "Hand over the refund with the credit note",
    ],
  ];
  const shown = st === "REJECTED" ? items.slice(0, 2) : items;
  return (
    <ol className="next-steps">
      {shown.map(([t, cls, note], i) => (
        <li key={t} className={cls}>
          <span className="fb-n">{cls === "done" ? (st === "REJECTED" && i === 1 ? "✖" : "✓") : i + 1}</span>
          <span>
            <b>{t}</b>
            <br />
            <small className="muted">{note}</small>
          </span>
        </li>
      ))}
    </ol>
  );
}

function Answer({ r, owner, onDecide }: { r: ReturnView; owner: boolean; onDecide: (approve: boolean) => void }) {
  const [tone, icon, title, text] =
    r.status === "PENDING"
      ? ([
          "warning",
          "⏳",
          "Sent – waiting for the owner",
          "You can leave this page; the answer also shows in Sell and in the bell.",
        ] as const)
      : r.status === "APPROVED"
        ? ([
            "success",
            "✅",
            `Approved – ${r.kind === "EXCHANGE" ? "now sell the new gem" : "give the refund"}`,
            `Credit note ${r.creditNote} is ready to print.`,
          ] as const)
        : ([
            "danger",
            "✖",
            "The owner did not approve this return",
            r.ownerNote ? `“${r.ownerNote}”` : "The gem stays sold to the customer.",
          ] as const);
  return (
    <Card>
      <CardContent>
        <Alert
          tone={tone}
          icon={<span style={{ fontSize: 24 }}>{icon}</span>}
          className={r.status === "PENDING" ? "pending-pulse" : undefined}
        >
          <b style={{ fontSize: 16 }} id="answerTitle">
            {title}
          </b>
          <br />
          {text}
        </Alert>
        <div className="g2 mt grid">
          <div className="row" style={{ gap: 14, alignItems: "flex-start", flexWrap: "nowrap" }}>
            <GemPicture gem={r.gem.gemType} type={r.gem.productType} photoUrl={r.gem.photo} size={70} box={70} />
            <div>
              <b>{r.gem.name}</b> <span className="mono muted">{r.gem.code}</span>
              <br />
              <small className="muted">
                {r.number} · {r.kind === "EXCHANGE" ? "Exchange" : "Refund"} · {lkr(r.amount)}
              </small>
              <br />
              <small>{r.reason}</small>
            </div>
          </div>
          <div>
            <NextSteps r={r} kind={r.kind} />
          </div>
        </div>
      </CardContent>
      <div className="card-footer" style={{ flexWrap: "wrap" }}>
        {r.status === "PENDING" && owner ? (
          <>
            <Button variant="success" id="approveNow" onClick={() => onDecide(true)}>
              ✓ Approve now (I am the owner)
            </Button>
            <Button variant="outline" onClick={() => onDecide(false)}>
              Reject
            </Button>
          </>
        ) : null}
        {r.status === "APPROVED" && r.creditNote ? (
          <>
            <Link className={buttonClass("sapphire")} href={`/sell/credit-notes/${r.creditNote}`}>
              🖨 Print credit note
            </Link>
            {r.kind === "EXCHANGE" && !r.usedSaleNo ? (
              <Link
                className={buttonClass("success")}
                id="startSwap"
                href={`/sell/new?credit=${r.creditNote}&customer=${encodeURIComponent(r.customer.code)}`}
              >
                💎 Start the new sale
              </Link>
            ) : null}
          </>
        ) : null}
        <div style={{ flex: 1 }} />
        <Link className={buttonClass("outline")} href="/sell/returns/new">
          Another return
        </Link>
        <Link className={buttonClass("ghost")} href="/sell">
          Back to Sell
        </Link>
      </div>
    </Card>
  );
}
