"use client";

/* One customer – prototype j-customers-promotions/customer-profile.html (Guide 44; BRD J1, J2, J5, J8, F5): details with
   the birthday, the permission switch, messages sent, purchase history and reservations / quotations. The passport is
   stored encrypted and shown (masked) to the owner only. Purchases count the branch at the top. */
import Link from "next/link";
import { useState } from "react";
import { StatusPill } from "@/components/ui/badge";
import { Button, buttonClass } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { DataTable, type Column } from "@/components/ui/data-table";
import { Badge } from "@/components/ui/badge";
import { GemSvg } from "@/components/ui/gem-picture";
import { Switch } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import { birthdayLong, type CustomerProfile } from "@/lib/customers";
import { date, dateTime, inCurrency, initials } from "@/lib/format";
import { OWNER } from "@/lib/roles";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";
import { BirthdayCell, CustomerModal } from "../customer-parts";

type Purchase = CustomerProfile["purchases"][number];
type QuoteLine = CustomerProfile["quotes"][number];

const ICON: Record<string, string> = { INVOICE: "🧾", RESERVATION: "🔒", QUOTATION: "📝" };

export function CustomerScreen({ code }: { code: string }) {
  const toast = useToast();
  const { role, branch, currency, rates } = useSession();
  const owner = role === OWNER;
  const { data, error, reload, setData } = useLoad(
    () => api.GET("/api/customers/{code}", { params: { path: { code } } }),
    [code, branch],
  );
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const cur = (v: number | string) => inCurrency(v, currency, rates);

  if (error && !data)
    return (
      <Card>
        <div className="empty">
          {errorMessage(error)} <Link href="/people/customers">Customers</Link>
        </div>
      </Card>
    );
  if (!data) return <p className="muted">Loading…</p>;
  const c = data.customer;

  const setConsent = async (on: boolean) => {
    setBusy(true);
    const { data: row, error: err } = await api.PUT("/api/customers/{code}/consent", {
      params: { path: { code: c.code } },
      body: { consent: on },
    });
    setBusy(false);
    if (!row) return toast("Not saved", errorMessage(err), "error");
    setData({ ...data, customer: row, unsubVia: row.unsubscribed ? data.unsubVia : undefined });
    toast(on ? "Permission given" : "Permission withdrawn", c.name, "success");
  };

  const purchases: Column<Purchase>[] = [
    {
      key: "inv",
      header: "Invoice",
      cell: (s) => (
        <>
          <Link className="mono" href={`/sell/invoices/${s.number}`}>
            {s.number}
          </Link>
          <br />
          <small className="muted">
            {date(s.soldAt)} · {s.branch}
          </small>
        </>
      ),
    },
    {
      key: "gems",
      header: "Gems",
      cell: (s) =>
        s.gems.map((g) => (
          <div key={g.code} className="row" style={{ gap: 6, flexWrap: "nowrap" }}>
            <GemSvg gem={g.gemType} type={g.productType} size={28} />
            <span>
              {g.name} <small className="muted mono">{g.code}</small>
            </span>
          </div>
        )),
    },
    { key: "total", header: "Total", num: true, cell: (s) => cur(s.total) },
    { key: "st", header: "Status", cell: (s) => <StatusPill status={s.status} /> },
  ];

  const quotes: Column<QuoteLine>[] = [
    {
      key: "no",
      header: "No.",
      cell: (q) => (
        <Link className="mono" href={`/sell/reserved/${q.number}/print`}>
          {q.number}
        </Link>
      ),
    },
    { key: "kind", header: "Kind", cell: (q) => (q.kind === "RESERVATION" ? "Gem kept" : "Quotation") },
    { key: "gems", header: "Gems", cell: (q) => <span className="mono">{q.gems.join(", ")}</span> },
    { key: "exp", header: "Until", cell: (q) => date(q.expires) },
    { key: "st", header: "Status", cell: (q) => <StatusPill status={q.status} /> },
  ];

  const consentText = c.unsubscribed
    ? `Unsubscribed on ${date(c.unsubscribedAt)} ${data.unsubVia === "WHATSAPP" ? "by replying STOP on WhatsApp" : "with the unsubscribe link"}. Turn the switch on only if the customer asks again in person.`
    : c.consent
      ? `Permission recorded on ${dateTime(c.consentAt)}. The customer can unsubscribe at any time with the link in every offer, or by replying STOP on WhatsApp.`
      : "No permission – this customer gets invoices only, never offers or birthday wishes.";

  const birthdayNote = !c.birthday
    ? "No birthday saved – add it with Edit to send a birthday wish."
    : !c.canMessage
      ? "Birthday wish is not sent: no permission for offers."
      : `Birthday wish goes out automatically on ${birthdayLong(c.birthday)}.`;

  return (
    <>
      <PageHead
        title={c.name}
        sub={`${c.country} · customer since ${date(c.joinedAt)}`}
        actions={
          <>
            <Button variant="outline" id="editCustomer" onClick={() => setEditing(true)}>
              Edit
            </Button>
            <Link className={buttonClass("success")} href={`/sell/new?customer=${encodeURIComponent(c.code)}`}>
              New sale
            </Link>
          </>
        }
      />
      <div className="g-1-2 grid">
        <div className="stack">
          <Card>
            <CardContent className="center">
              <div className="avatar" style={{ width: 64, height: 64, fontSize: 22, margin: "0 auto" }}>
                {initials(c.name)}
              </div>
              <h2 className="mt-s">{c.name}</h2>
              <p className="muted">
                {c.country} · <span className="mono">{c.code}</span>
              </p>
            </CardContent>
            <CardContent style={{ borderTop: "1px solid var(--border)" }}>
              <dl className="dl" style={{ gridTemplateColumns: "100px 1fr" }}>
                <dt>Email</dt>
                <dd>{c.email ?? "—"}</dd>
                <dt>WhatsApp</dt>
                <dd>{c.whatsapp ?? "—"}</dd>
                <dt>Birthday</dt>
                <dd>
                  <BirthdayCell c={c} />
                </dd>
                {owner ? (
                  <>
                    <dt>Passport</dt>
                    <dd>
                      {data.passport ? (
                        <>
                          {data.passport} <Badge tone="outline">encrypted</Badge>
                        </>
                      ) : (
                        "—"
                      )}
                    </dd>
                  </>
                ) : null}
                <dt>Spent</dt>
                <dd>
                  <b>{cur(c.spent)}</b> <small className="muted">({c.purchases} invoice(s))</small>
                </dd>
              </dl>
              {data.notes ? <p className="muted mt-s">{data.notes}</p> : null}
            </CardContent>
          </Card>

          <Card>
            <CardHeader title="Offers and birthday wishes" level={3} />
            <CardContent>
              <label className="row between">
                <span>Agrees to receive offers and birthday wishes</span>
                <Switch
                  label="Agrees to receive offers and birthday wishes"
                  checked={c.canMessage}
                  disabled={busy}
                  onChange={(v) => void setConsent(v)}
                />
              </label>
              <p className="muted mt-s" style={{ fontSize: 12 }}>
                {consentText}
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader title="Messages sent" level={3} />
            <CardContent style={{ fontSize: 12 }}>
              {data.messages.length ? (
                <ul className="list">
                  {data.messages.map((m) => (
                    <li key={`${m.kind}-${m.text}-${m.at}`}>
                      {ICON[m.kind] ?? "✉"} {m.text} · {date(m.at)}{" "}
                      {m.email ? <Badge tone="success">✉ email</Badge> : null}{" "}
                      {m.whatsapp ? <Badge tone="success">💬 WhatsApp</Badge> : null}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="muted">None yet.</p>
              )}
              <p className="muted mt-s">{birthdayNote}</p>
            </CardContent>
          </Card>
        </div>

        <div className="stack">
          <Card>
            <CardHeader title="Purchase history" level={3}>
              <span className="muted">{data.purchases.length} invoice(s)</span>
            </CardHeader>
            <DataTable
              columns={purchases}
              rows={data.purchases}
              rowKey={(s) => s.number}
              stackOnPhone
              empty="No purchases yet."
            />
          </Card>
          <Card>
            <CardHeader title="Gems kept & quotations" level={3} />
            <DataTable columns={quotes} rows={data.quotes} rowKey={(q) => q.number} stackOnPhone empty="None." />
          </Card>
        </div>
      </div>
      {editing ? (
        <CustomerModal
          customer={{
            code: c.code,
            name: c.name,
            country: c.country,
            email: c.email,
            whatsapp: c.whatsapp,
            birthday: c.birthday,
            passport: owner ? data.passport : data.hasPassport,
          }}
          countries={[]}
          onClose={() => setEditing(false)}
          onSaved={() => {
            setEditing(false);
            reload();
          }}
        />
      ) : null}
    </>
  );
}
