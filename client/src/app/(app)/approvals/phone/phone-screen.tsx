"use client";

/* Owner phone view – prototype k-owner-control/owner-mobile.html (Guide 54; BRD K1). Today at a glance for one branch
   or all: sales and profit, approvals waiting (approve / reject right here), cash, card, stock value, gems sold today and
   the newest alerts. Installed on the phone's home screen it opens like an app; push alerts are set on Alerts. */
import Link from "next/link";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import { Alert, Options, Select } from "@/components/ui/form";
import { GemPicture } from "@/components/ui/gem-picture";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { ALERT_ICON } from "@/lib/alerts";
import { refreshWaiting } from "@/lib/approvals";
import { ago, date, inCurrency, num, time, todayInShop } from "@/lib/format";
import { ALL_BRANCHES, useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

type ApprovalView = components["schemas"]["ApprovalView"];

export function PhoneScreen() {
  const toast = useToast();
  const { shop, branch: topBranch, branches, currency, rates } = useSession();
  const [branch, setBranch] = useState(topBranch);
  const [busy, setBusy] = useState<string | null>(null);
  const { data: v, reload } = useLoad(() => api.GET("/api/home/phone", { params: { query: { branch } } }), [branch]);
  const cur = (x: number | string) => inCurrency(x, currency, rates);
  const inCur = (x: number | string) => num(currency === "LKR" ? Number(x) : Number(x) / rates[currency], 0);

  const decide = async (a: ApprovalView, ok: boolean) => {
    setBusy(a.number);
    const path = { number: a.number };
    const { error } = ok
      ? await api.POST("/api/approvals/{number}/approve", { params: { path }, body: {} })
      : await api.POST("/api/approvals/{number}/reject", { params: { path }, body: {} });
    setBusy(null);
    if (error) return toast("Not saved", errorMessage(error), "error");
    toast(ok ? "Approved ✓" : "Rejected", a.title, ok ? "success" : "error");
    refreshWaiting();
    reload();
  };

  return (
    <div className="phone-frame">
      <div
        className="card"
        style={{ background: "linear-gradient(150deg,var(--navy),var(--sapphire))", color: "#fff", border: 0 }}
      >
        <CardContent>
          <small style={{ color: "var(--gold)" }}>{shop.name} · owner view</small>
          <h2 style={{ color: "#fff", margin: "2px 0 0" }}>{date(todayInShop())}</h2>
          <div className="mt-s" style={{ maxWidth: 220 }}>
            <Select id="phBranch" aria-label="Branch" value={branch} onChange={(e) => setBranch(e.target.value)}>
              <Options
                items={[
                  { value: ALL_BRANCHES, label: "All branches" },
                  ...branches.map((b) => ({ value: b.code, label: b.shortName || b.name })),
                ]}
              />
            </Select>
          </div>
          <div style={{ fontSize: 12, color: "#c9d5ee", marginTop: 12 }}>Today&apos;s sales</div>
          <div style={{ fontSize: 30, fontWeight: 800 }} id="phSales">
            {v ? cur(v.salesTotal) : "…"}
          </div>
          {v ? (
            <div style={{ fontSize: 12, color: "#c9d5ee" }}>
              {v.bills} bills · {v.items} items · profit {cur(v.profit)}
            </div>
          ) : null}
        </CardContent>
      </div>
      {v ? (
        <>
          {v.approvalsWaiting ? (
            <Link href="/approvals" className="alert warning mt" style={{ textDecoration: "none" }}>
              <b>
                🔔 {v.approvalsWaiting} approval{v.approvalsWaiting > 1 ? "s" : ""} waiting
              </b>
              <span style={{ marginLeft: "auto" }}>Open →</span>
            </Link>
          ) : null}
          <div className="stack mt" style={{ gap: 10 }}>
            {v.approvals.map((a) => (
              <Card key={a.number} data-approval={a.number}>
                <CardContent style={{ padding: "12px 14px" }}>
                  <small className="muted">
                    {a.typeLabel} · {ago(a.requestedAt)}
                  </small>
                  <br />
                  <b style={{ fontSize: 13 }}>{a.title}</b>
                  {a.amount != null ? <div className="bold mt-s">{cur(a.amount)}</div> : null}
                </CardContent>
                <CardFooter style={{ padding: "8px 14px" }}>
                  <Button
                    variant="destructive"
                    style={{ flex: 1 }}
                    disabled={busy === a.number}
                    onClick={() => void decide(a, false)}
                  >
                    Reject
                  </Button>
                  <Button
                    variant="success"
                    style={{ flex: 1 }}
                    data-approve={a.number}
                    disabled={busy === a.number}
                    onClick={() => void decide(a, true)}
                  >
                    Approve
                  </Button>
                </CardFooter>
              </Card>
            ))}
          </div>
          <div className="g2 mt grid" id="phKpis" style={{ gridTemplateColumns: "1fr 1fr", gap: 10 }}>
            <div className="card kpi">
              <div className="k-label">Cash LKR</div>
              <div className="k-value" style={{ fontSize: 17 }}>
                {num(v.cash.lkr, 0)}
              </div>
            </div>
            <div className="card kpi">
              <div className="k-label">Cash USD / EUR</div>
              <div className="k-value" style={{ fontSize: 17 }}>
                {num(v.cash.usd, 0)} / {num(v.cash.eur, 0)}
              </div>
            </div>
            <div className="card kpi">
              <div className="k-label">Card today</div>
              <div className="k-value" style={{ fontSize: 17 }}>
                {num(v.cash.card, 0)}
              </div>
            </div>
            <div className="card kpi">
              <div className="k-label">Stock value</div>
              <div className="k-value" style={{ fontSize: 17 }}>
                {inCur(v.stockValue)}
              </div>
              <div className="k-sub">
                {currency} · {v.stockCount} items · cost {inCur(v.stockCost)}
              </div>
            </div>
          </div>
          <Card className="mt">
            <CardHeader title="Sold today" level={3} actions={<span className="muted">{v.soldToday.length}</span>} />
            <CardContent>
              <ul className="list" id="phSold">
                {v.soldToday.length ? (
                  v.soldToday.map((s) => (
                    <li key={`${s.invoice}-${s.gem}`}>
                      <GemPicture gem={s.gemType} photoUrl={s.photo} size={40} box={46} alt={s.name} />
                      <div style={{ flex: 1, fontSize: 12 }}>
                        <b>{s.name}</b>
                        <br />
                        <span className="muted">
                          {time(s.soldAt)} · {s.staff.split(" ")[0]} · {s.guide ?? "Walk-in"}
                        </span>
                      </div>
                      <div className="right" style={{ fontSize: 12 }}>
                        <b>{cur(s.agreed)}</b>
                        {s.belowMin ? (
                          <>
                            <br />
                            <Badge tone="warning">below min</Badge>
                          </>
                        ) : null}
                      </div>
                    </li>
                  ))
                ) : (
                  <li className="muted">No sales yet today</li>
                )}
              </ul>
            </CardContent>
          </Card>
          <Card className="mt">
            <CardHeader
              title="Alerts"
              level={3}
              actions={
                <Link href="/approvals/alerts" className="btn btn-sm btn-ghost">
                  All
                </Link>
              }
            />
            <CardContent>
              <ul className="list" id="phAlerts">
                {v.alerts.length ? (
                  v.alerts.map((a) => (
                    <li key={a.id} style={{ fontSize: 12 }}>
                      <span aria-hidden>{ALERT_ICON[a.type] ?? "🔔"}</span>
                      <div style={{ flex: 1 }}>
                        {a.link ? <Link href={a.link}>{a.text}</Link> : a.text}
                        <br />
                        <small className="muted">{ago(a.at)}</small>
                      </div>
                    </li>
                  ))
                ) : (
                  <li className="muted">No alerts</li>
                )}
              </ul>
            </CardContent>
          </Card>
          <Alert className="mt">
            <span style={{ fontSize: 12 }}>
              Install this page on the phone&apos;s home screen (Android: menu → Install app; iPhone: Share → Add to
              Home Screen) and turn on <Link href="/approvals/alerts#pushCard">push alerts</Link> for instant alerts of
              sales, approvals and cash differences.
            </span>
          </Alert>
        </>
      ) : (
        <p className="muted mt">Loading…</p>
      )}
    </div>
  );
}
