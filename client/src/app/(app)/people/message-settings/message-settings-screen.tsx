"use client";

/* Email & WhatsApp settings – prototype j-customers-promotions/message-settings.html (Guide 13, 45; BRD J5, J7, J8). Owner only.
   The shop's SMTP, Amazon SES for offers, WhatsApp Business, the automatic messages and birthday wishes. Until the real accounts
   are connected (P6-03) emails go to the test mail server and WhatsApp is in test mode. Passwords and tokens are never shown. */
import Link from "next/link";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import { DataTable, type Column } from "@/components/ui/data-table";
import { Alert, Check, Field, FormGrid, Input, Options, Select, Switch, Textarea } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage, fieldErrors } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { BirthdayCell } from "../customers/customer-parts";
import { time } from "@/lib/format";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

type Settings = components["schemas"]["MessageSettingsView"];
type Switches = components["schemas"]["SwitchForm"];
type BirthdayRow = components["schemas"]["BirthdayRow"];

const AUTO: [keyof Settings["auto"], keyof Switches, string][] = [
  ["invoiceEmail", "invoiceEmail", "Email the invoice PDF after every purchase"],
  ["invoiceWhatsapp", "invoiceWhatsapp", "WhatsApp the invoice PDF after every purchase (needs WhatsApp Premium)"],
  ["thankYou", "thankYou", "Send a thank-you message the day after purchase"],
  ["ownerClosing", "ownerClosing", "Send the day-end closing report to the owner"],
];

export function MessageSettingsScreen() {
  const { data, setData } = useLoad(() => api.GET("/api/messages/settings"), []);
  return (
    <>
      <PageHead
        title="Email & WhatsApp settings"
        sub="The owner can turn email and WhatsApp messages on or off at any time."
      />
      {data ? <SettingsBody s={data} onSaved={setData} /> : <p className="muted">Loading…</p>}
    </>
  );
}

function SettingsBody({ s, onSaved }: { s: Settings; onSaved: (s: Settings) => void }) {
  const toast = useToast();

  const flip = async (f: Switches, label: string) => {
    const { data, error } = await api.PUT("/api/messages/switches", { body: f });
    if (!data) return toast("Not saved", errorMessage(error), "error");
    onSaved(data);
    const on = Object.values(f)[0];
    toast(`${label} ${on ? "turned ON" : "turned OFF"}`, undefined, on ? "success" : "warning");
  };

  return (
    <>
      {(s.emailTestMode || s.whatsappTestMode) && (
        <Alert tone="info" className="mb">
          <b>Test mode.</b> Until the shop&apos;s real accounts are connected, emails go to the test mail server and
          WhatsApp messages are only recorded – nothing reaches customers yet.
        </Alert>
      )}
      <div className="g2 grid">
        <div className="stack">
          <SmtpCard s={s} onSaved={onSaved} flip={flip} />
          <SesCard s={s} onSaved={onSaved} />
          <Card>
            <CardHeader level={3} title="Automatic messages" />
            <CardContent className="stack" style={{ gap: 10 }}>
              {AUTO.map(([k, sw, label]) => (
                <div key={k} className="row" style={{ justifyContent: "space-between" }}>
                  <span>{label}</span>
                  <Switch label={label} checked={s.auto[k]} onChange={(v) => void flip({ [sw]: v }, label)} />
                </div>
              ))}
              {!s.emailOn && <small className="muted">Email messages are off, so no automatic email goes out.</small>}
            </CardContent>
          </Card>
        </div>
        <div className="stack">
          <BirthdayCard s={s} onSaved={onSaved} flip={flip} />
          <WhatsappCard s={s} onSaved={onSaved} flip={flip} />
        </div>
      </div>
    </>
  );
}

type CardProps = { s: Settings; onSaved: (s: Settings) => void; flip: (f: Switches, label: string) => Promise<void> };

// ------------------------------------------------------------------ SMTP

function SmtpCard({ s, onSaved, flip }: CardProps) {
  const toast = useToast();
  const [f, setF] = useState({
    host: s.smtp.host ?? "",
    port: String(s.smtp.port ?? 587),
    security: s.smtp.security,
    user: s.smtp.user ?? "",
    password: "",
    from: s.smtp.from ?? "",
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof f) => (v: string) => {
    setF((x) => ({ ...x, [k]: v }));
    setErrors((e) => ({ ...e, [k]: "" }));
  };

  const save = async () => {
    setBusy(true);
    const { data, error } = await api.PUT("/api/messages/smtp", {
      body: { ...f, port: Number(f.port), password: f.password || undefined },
    });
    setBusy(false);
    if (!data) {
      setErrors(fieldErrors(error));
      return toast("Not saved", errorMessage(error), "error");
    }
    setF((x) => ({ ...x, password: "" }));
    onSaved(data);
    toast("Email settings saved", undefined, "success");
  };

  const test = async () => {
    setBusy(true);
    const { data, error } = await api.POST("/api/messages/smtp/test");
    setBusy(false);
    if (!data) return toast("Test email not sent", errorMessage(error), "error");
    toast(
      `Test email sent to ${data.to}`,
      data.testMode ? "Test mode: it is in the test mail server, not a real inbox." : `Sent through ${data.via}.`,
      "success",
    );
  };

  return (
    <Card>
      <CardHeader level={3}>
        <div style={{ flex: 1 }}>
          <h3>✉ Email – the shop&apos;s own SMTP</h3>
          <p>Invoices, quotations, password reset, reports to the owner.</p>
        </div>
        <Switch
          label="Email messages"
          checked={s.emailOn}
          onChange={(v) => void flip({ emailOn: v }, "Email messages")}
        />
      </CardHeader>
      <CardContent>
        <FormGrid>
          <Field label="SMTP host" htmlFor="smHost" required error={errors.host}>
            <Input id="smHost" maxLength={120} value={f.host} onChange={(e) => set("host")(e.target.value)} />
          </Field>
          <Field label="Port" htmlFor="smPort" required error={errors.port}>
            <Input
              id="smPort"
              type="number"
              min={1}
              max={65535}
              value={f.port}
              onChange={(e) => set("port")(e.target.value)}
            />
          </Field>
          <Field label="Security" htmlFor="smSec">
            <Select id="smSec" value={f.security} onChange={(e) => set("security")(e.target.value)}>
              <Options
                items={[
                  { value: "STARTTLS", label: "STARTTLS" },
                  { value: "SSL", label: "SSL/TLS" },
                  { value: "NONE", label: "None" },
                ]}
              />
            </Select>
          </Field>
          <Field label="Username" htmlFor="smUser" required error={errors.user}>
            <Input id="smUser" maxLength={160} value={f.user} onChange={(e) => set("user")(e.target.value)} />
          </Field>
          <Field
            label="App password"
            htmlFor="smPw"
            error={errors.password}
            hint={s.smtp.hasPassword ? "Saved. Leave empty to keep it." : "Gmail: use an App Password."}
          >
            <Input
              id="smPw"
              type="password"
              autoComplete="new-password"
              maxLength={200}
              placeholder={s.smtp.hasPassword ? "••••••••" : ""}
              value={f.password}
              onChange={(e) => set("password")(e.target.value)}
            />
          </Field>
          <Field label="From" htmlFor="smFrom" required error={errors.from}>
            <Input id="smFrom" maxLength={200} value={f.from} onChange={(e) => set("from")(e.target.value)} />
          </Field>
        </FormGrid>
      </CardContent>
      <CardFooter>
        <Button variant="outline" disabled={busy || !s.emailOn} onClick={() => void test()}>
          Send test email
        </Button>
        <Button variant="sapphire" disabled={busy} onClick={() => void save()}>
          Save SMTP
        </Button>
      </CardFooter>
    </Card>
  );
}

// ------------------------------------------------------------------ Amazon SES

function SesCard({ s, onSaved }: Omit<CardProps, "flip">) {
  const toast = useToast();
  const [f, setF] = useState({
    enabled: s.ses.enabled,
    domain: s.ses.domain ?? "",
    region: s.ses.region ?? "ap-south-1",
    from: s.ses.from ?? "",
    accessKey: s.ses.accessKey ?? "",
    secret: "",
  });
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof f) => (v: string | boolean) => setF((x) => ({ ...x, [k]: v }));

  const save = async () => {
    setBusy(true);
    const { data, error } = await api.PUT("/api/messages/ses", { body: { ...f, secret: f.secret || undefined } });
    setBusy(false);
    if (!data) return toast("Not saved", errorMessage(error), "error");
    setF((x) => ({ ...x, secret: "" }));
    onSaved(data);
    toast("Amazon SES settings saved", undefined, "success");
  };

  return (
    <Card>
      <CardHeader level={3}>
        <div style={{ flex: 1 }}>
          <h3>✉ Offer emails – Amazon SES</h3>
          <p>Bulk offers to customers. Gmail does not allow bulk marketing.</p>
        </div>
        <Badge tone={s.ses.enabled ? "success" : "outline"}>{s.ses.enabled ? "Connected" : "Off"}</Badge>
      </CardHeader>
      <CardContent>
        <Check checked={f.enabled} onChange={set("enabled")}>
          Use Amazon SES for offers
        </Check>
        <FormGrid className="mt">
          <Field label="Sending domain" htmlFor="sesDomain">
            <Input id="sesDomain" maxLength={120} value={f.domain} onChange={(e) => set("domain")(e.target.value)} />
          </Field>
          <Field label="Region" htmlFor="sesRegion" hint="ap-south-1 = Mumbai, nearest to Sri Lanka">
            <Input id="sesRegion" maxLength={30} value={f.region} onChange={(e) => set("region")(e.target.value)} />
          </Field>
          <Field label="From" htmlFor="sesFrom" full>
            <Input id="sesFrom" maxLength={200} value={f.from} onChange={(e) => set("from")(e.target.value)} />
          </Field>
          <Field label="Access key ID" htmlFor="sesKey">
            <Input id="sesKey" maxLength={64} value={f.accessKey} onChange={(e) => set("accessKey")(e.target.value)} />
          </Field>
          <Field
            label="Secret key"
            htmlFor="sesSecret"
            hint={s.ses.hasSecret ? "Saved. Leave empty to keep it." : undefined}
          >
            <Input
              id="sesSecret"
              type="password"
              autoComplete="new-password"
              maxLength={200}
              placeholder={s.ses.hasSecret ? "••••••••" : ""}
              value={f.secret}
              onChange={(e) => set("secret")(e.target.value)}
            />
          </Field>
        </FormGrid>
        <p className="muted mt" style={{ fontSize: 12 }}>
          SPF {s.ses.spf ? "✓" : "✗"} · DKIM {s.ses.dkim ? "✓" : "✗"} · An unsubscribe link is added to every offer
          email.
        </p>
      </CardContent>
      <CardFooter>
        <Button variant="sapphire" disabled={busy} onClick={() => void save()}>
          Save Amazon SES
        </Button>
      </CardFooter>
    </Card>
  );
}

// ------------------------------------------------------------------ WhatsApp Business

function WhatsappCard({ s, onSaved, flip }: CardProps) {
  const toast = useToast();
  const [f, setF] = useState({ phone: s.whatsapp.phone ?? "", accountId: s.whatsapp.accountId ?? "", token: "" });
  const [busy, setBusy] = useState(false);
  const wa = s.whatsapp;
  const set = (k: keyof typeof f) => (v: string) => setF((x) => ({ ...x, [k]: v }));

  const verify = async () => {
    setBusy(true);
    const { data, error } = await api.PUT("/api/messages/whatsapp", { body: { ...f, token: f.token || undefined } });
    setBusy(false);
    if (!data) return toast("Not verified", errorMessage(error), "error");
    setF((x) => ({ ...x, token: "" }));
    onSaved(data.settings);
    toast(data.verified ? "WhatsApp verified" : "Not verified", data.message, data.verified ? "success" : "error");
  };

  return (
    <Card>
      <CardHeader level={3}>
        <div style={{ flex: 1 }}>
          <h3>💬 WhatsApp Business – Premium</h3>
          <p>Optional. The shop owns the account and pays Meta directly.</p>
        </div>
        <Switch
          label="WhatsApp Premium"
          checked={wa.enabled}
          onChange={(v) => void flip({ whatsappOn: v }, "WhatsApp Premium")}
        />
      </CardHeader>
      <CardContent>
        <Alert tone={wa.enabled && wa.verified ? "success" : wa.enabled ? "info" : "warning"} className="mb">
          {wa.enabled
            ? wa.verified
              ? "✓ Connected and verified. Invoices, quotations, thank-you messages and birthday wishes can go on WhatsApp."
              : "Enter the details below and press “Verify Connection”."
            : "WhatsApp Premium is off. Turn it on to send bills and wishes by WhatsApp."}
        </Alert>
        {wa.enabled && (
          <FormGrid>
            <Field label="Business phone number" htmlFor="waPhone" required>
              <Input
                id="waPhone"
                type="tel"
                maxLength={40}
                value={f.phone}
                onChange={(e) => set("phone")(e.target.value)}
              />
            </Field>
            <Field label="Business Account ID" htmlFor="waAcc" required>
              <Input id="waAcc" maxLength={40} value={f.accountId} onChange={(e) => set("accountId")(e.target.value)} />
            </Field>
            <Field
              label="Access token"
              htmlFor="waToken"
              full
              hint={
                wa.hasToken
                  ? "Saved. Leave empty to keep it."
                  : "Permanent system-user token from Meta Business Manager."
              }
            >
              <Input
                id="waToken"
                type="password"
                autoComplete="new-password"
                maxLength={600}
                placeholder={wa.hasToken ? "••••••••" : ""}
                value={f.token}
                onChange={(e) => set("token")(e.target.value)}
              />
            </Field>
          </FormGrid>
        )}
        <p className="muted mt" style={{ fontSize: 12 }}>
          Utility: invoice, quotation, thank-you (about US$0.005 each). Marketing: offers and birthday wishes –
          Meta-approved templates, permission only.
        </p>
      </CardContent>
      {wa.enabled && (
        <CardFooter>
          <Button variant="sapphire" disabled={busy} onClick={() => void verify()}>
            {busy ? "Checking…" : "Verify Connection"}
          </Button>
        </CardFooter>
      )}
    </Card>
  );
}

// ------------------------------------------------------------------ Birthday wishes

function BirthdayCard({ s, onSaved, flip }: CardProps) {
  const toast = useToast();
  const { shop } = useSession();
  const { data: list, reload } = useLoad(() => api.GET("/api/messages/birthdays"), []);
  const bd = s.birthday;
  const [f, setF] = useState({
    email: bd.email,
    whatsapp: bd.whatsapp,
    time: bd.time,
    subject: bd.subject,
    text: bd.text,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof f) => (v: string | boolean) => {
    setF((x) => ({ ...x, [k]: v }));
    setErrors((e) => ({ ...e, [k]: "" }));
  };
  const waReady = s.whatsapp.enabled && s.whatsapp.verified;
  const fill = (t: string) => t.replace(/\{name\}/g, "Anna").replace(/\{shop\}/g, shop.name);

  const save = async () => {
    if (!f.email && !f.whatsapp) return toast("Choose email or WhatsApp", undefined, "error");
    setBusy(true);
    const { data, error } = await api.PUT("/api/messages/birthday", { body: f });
    setBusy(false);
    if (!data) {
      setErrors(fieldErrors(error));
      return toast("Not saved", errorMessage(error), "error");
    }
    onSaved(data);
    void reload();
    toast("Birthday wishes saved", `Sent every day at ${f.time} Sri Lanka time.`, "success");
  };

  const sendNow = async () => {
    setBusy(true);
    const { data, error } = await api.POST("/api/messages/birthdays/send-now");
    setBusy(false);
    if (!data) return toast("Not sent", errorMessage(error), "error");
    void reload();
    toast(
      data.sent ? `${data.sent} birthday wish${data.sent > 1 ? "es" : ""} sent` : "No wishes to send",
      data.sent ? undefined : "No birthday today with permission, or this year's wish has already gone out.",
      data.sent ? "success" : "info",
    );
  };

  const toggle = async (v: boolean) => {
    await flip({ birthdays: v }, "Birthday wishes");
    void reload();
  };

  const columns: Column<BirthdayRow>[] = [
    {
      key: "cust",
      header: "Customer",
      cell: (c) => (
        <>
          <Link href={`/people/customers/${c.code}`}>{c.name}</Link>
          <br />
          <small className="muted">{c.country}</small>
        </>
      ),
    },
    { key: "bday", header: "Birthday", cell: (c) => <BirthdayCell c={c} /> },
    {
      key: "wish",
      header: "Wish",
      cell: (c) =>
        c.status === "NO_PERMISSION" ? (
          <Badge tone="outline">Not sent – no permission</Badge>
        ) : c.status === "SENT" ? (
          <Badge tone="success">✓ Sent {c.sentAt ? time(c.sentAt) : ""}</Badge>
        ) : c.status === "OFF" ? (
          <span className="muted">Birthday wishes off</span>
        ) : (
          <span>Will send at {bd.time}</span>
        ),
    },
  ];

  return (
    <Card id="birthday">
      <CardHeader level={3}>
        <div style={{ flex: 1 }}>
          <h3>🎂 Birthday wishes</h3>
          <p>Sent on the customer&apos;s birthday – only to customers who agreed to offers. Once a year.</p>
        </div>
        <Switch label="Birthday wishes" checked={bd.on} onChange={(v) => void toggle(v)} />
      </CardHeader>
      <CardContent>
        <Field label="Send by">
          <div className="row" style={{ gap: 16 }}>
            <Check checked={f.email} onChange={set("email")}>
              ✉ Email
            </Check>
            <Check checked={f.whatsapp} onChange={set("whatsapp")}>
              💬 WhatsApp
            </Check>
          </div>
        </Field>
        {f.whatsapp && !waReady && (
          <small className="muted">WhatsApp wishes go out only when WhatsApp Premium is on and verified.</small>
        )}
        <FormGrid className="mt">
          <Field label="Send at" htmlFor="bdTime" required error={errors.time} hint="Sri Lanka time, every day">
            <Input id="bdTime" type="time" value={f.time} onChange={(e) => set("time")(e.target.value)} />
          </Field>
          <Field label="Email subject" htmlFor="bdSubject" required error={errors.subject}>
            <Input id="bdSubject" maxLength={200} value={f.subject} onChange={(e) => set("subject")(e.target.value)} />
          </Field>
          <Field
            label="Message"
            htmlFor="bdText"
            required
            full
            error={errors.text}
            hint="{name} = customer's first name · {shop} = shop name. WhatsApp uses a Meta-approved template with this text."
          >
            <Textarea
              id="bdText"
              rows={3}
              maxLength={1000}
              value={f.text}
              onChange={(e) => set("text")(e.target.value)}
            />
          </Field>
        </FormGrid>
        <Card className="mt" style={{ background: "var(--muted)" }}>
          <CardContent style={{ fontSize: 12 }}>
            <b>Preview</b>
            <br />
            <b>{fill(f.subject)}</b>
            <br />
            {fill(f.text)}
          </CardContent>
        </Card>
        <div className="row mt" style={{ justifyContent: "space-between" }}>
          <b>Next 30 days</b>
          <small className="muted">{list ? `${list.sentThisYear} wish(es) sent this year` : ""}</small>
        </div>
        <DataTable
          columns={columns}
          rows={list?.next30 ?? []}
          rowKey={(c) => c.code}
          empty={list ? "No birthdays in the next 30 days." : "Loading…"}
          stackOnPhone
        />
      </CardContent>
      <CardFooter>
        <Button variant="outline" disabled={busy} onClick={() => void sendNow()}>
          Send today&apos;s wishes now
        </Button>
        <Button variant="sapphire" disabled={busy} onClick={() => void save()}>
          Save birthday wishes
        </Button>
      </CardFooter>
    </Card>
  );
}
