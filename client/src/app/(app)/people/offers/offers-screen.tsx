"use client";

/* Offer results – prototype j-customers-promotions/campaign-report.html (Guide 48; BRD J6). Owner only.
   Sent, delivered, opened (email: picture loaded; WhatsApp: read receipt) and clicked (email link) per offer, unsubscribes,
   drafts with Edit / Delete, and the delivery funnel. ?code=OF-… marks the offer just sent. */
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, Kpi } from "@/components/ui/card";
import { DataTable, ExportButtons, type Column } from "@/components/ui/data-table";
import { useConfirm } from "@/components/ui/dialog";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import { dateTime } from "@/lib/format";
import { pct, type OfferRow } from "@/lib/offers";
import { useLoad } from "@/lib/use-load";

export function OffersScreen() {
  const mark = useSearchParams().get("code");
  const toast = useToast();
  const confirm = useConfirm();
  const { data, reload } = useLoad(() => api.GET("/api/offers"), []);
  const t = data?.totals;

  const remove = async (o: OfferRow) => {
    if (!(await confirm({ title: `Delete draft ${o.code}?`, text: o.name, yes: "Delete draft", danger: true }))) return;
    const { error } = await api.DELETE("/api/offers/{code}", { params: { path: { code: o.code } } });
    if (error) return toast("Not deleted", errorMessage(error), "error");
    toast("Draft deleted", o.code, "success");
    void reload();
  };

  const columns: Column<OfferRow>[] = [
    {
      key: "name",
      header: "Offer",
      cell: (o) => (
        <>
          <b>{o.name}</b>
          <br />
          <small className="muted">
            {o.code}
            {o.subject ? ` · ${o.subject}` : ""}
          </small>
        </>
      ),
    },
    {
      key: "channel",
      header: "Channel",
      cell: (o) =>
        o.channel === "EMAIL" ? <Badge tone="blue">✉ Email</Badge> : <Badge tone="success">💬 WhatsApp</Badge>,
    },
    { key: "aud", header: "Audience", cell: (o) => o.audience },
    { key: "on", header: "Sent on", cell: (o) => (o.sentAt ? dateTime(o.sentAt) : "—") },
    { key: "sent", header: "Sent", num: true, cell: (o) => o.sent },
    {
      key: "del",
      header: "Delivered",
      num: true,
      cell: (o) => (
        <>
          {o.delivered} <small className="muted">{pct(o.delivered, o.sent)}</small>
        </>
      ),
    },
    {
      key: "open",
      header: "Opened",
      num: true,
      cell: (o) => (
        <>
          {o.opened} <small className="muted">{pct(o.opened, o.delivered)}</small>
        </>
      ),
    },
    {
      key: "click",
      header: "Clicked",
      num: true,
      cell: (o) => (o.channel === "EMAIL" ? o.clicked : <small className="muted">n/a</small>),
    },
    { key: "unsub", header: "Unsub.", num: true, cell: (o) => o.unsubscribed },
    {
      key: "status",
      header: "Status",
      cell: (o) =>
        o.status === "DRAFT" ? (
          <div className="row" style={{ gap: 6 }}>
            <Badge tone="outline">Draft</Badge>
            <Button variant="outline" size="sm" asChild>
              <Link href={`/people/offers/${o.channel === "EMAIL" ? "email" : "whatsapp"}?code=${o.code}`}>Edit</Link>
            </Button>
            <Button variant="ghost" size="sm" onClick={() => void remove(o)}>
              Delete
            </Button>
          </div>
        ) : (
          <Badge tone="success">Sent</Badge>
        ),
    },
  ];

  const funnel: [string, number][] = t
    ? [
        ["Sent", t.sent],
        ["Delivered", t.delivered],
        ["Opened", t.opened],
        ["Clicked (email)", t.clicked],
      ]
    : [];

  return (
    <>
      <PageHead
        title="Offer results"
        sub="How many offers were sent, delivered and opened (where available)."
        actions={
          <>
            <Button variant="sapphire" asChild>
              <Link href="/people/offers/email">+ Email offer</Link>
            </Button>
            <Button variant="success" asChild>
              <Link href="/people/offers/whatsapp">+ WhatsApp offer</Link>
            </Button>
            <ExportButtons tableId="offersT" name="Offer results" />
          </>
        }
      />
      {t && (
        <div className="g4 mb grid">
          <Kpi label="Messages sent" value={t.sent} sub={`${t.offers} offers`} />
          <Kpi label="Delivered" value={t.delivered} sub={pct(t.delivered, t.sent)} />
          <Kpi label="Opened" value={t.opened} sub={`${pct(t.opened, t.delivered)} of delivered`} />
          <Kpi label="Unsubscribed" value={t.unsubscribed} sub="removed from future offers" />
        </div>
      )}
      <Card>
        <DataTable
          id="offersT"
          columns={columns}
          rows={data?.offers ?? []}
          rowKey={(o) => o.code}
          empty={data ? "No offers yet." : "Loading…"}
          stackOnPhone
          marked={(o) => o.code === mark}
        />
      </Card>
      {t && (
        <Card className="mt">
          <CardHeader level={3} title="Delivery funnel" />
          <CardContent className="stack" style={{ gap: 10 }}>
            {funnel.map(([label, n]) => (
              <div key={label} className="row" style={{ flexWrap: "nowrap" }}>
                <span style={{ width: 120 }}>{label}</span>
                <div className="bar" style={{ flex: 1 }}>
                  <i style={{ width: `${t.sent ? (n / t.sent) * 100 : 0}%` }} />
                </div>
                <b style={{ width: 40 }} className="right">
                  {n}
                </b>
              </div>
            ))}
            <p className="muted" style={{ fontSize: 12 }}>
              “Opened” for email counts the picture being loaded; for WhatsApp it is the read receipt from Meta. Until
              the real accounts are connected, “delivered” means accepted by the test server.
            </p>
          </CardContent>
        </Card>
      )}
    </>
  );
}
