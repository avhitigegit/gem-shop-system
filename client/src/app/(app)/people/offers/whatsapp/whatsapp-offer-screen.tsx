"use client";

/* Send an offer on WhatsApp – prototype j-customers-promotions/whatsapp-campaign.html (Guide 47; BRD J4, J2). Owner only.
   A Meta-approved template with the shop's text, an optional gem picture, who receives it, and a phone preview. Every message
   says "Reply STOP to unsubscribe"; only customers who gave permission (rule 8). ?code=OF-… opens a draft. */
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import { useConfirm } from "@/components/ui/dialog";
import { Field, Input, Options, Select, Textarea } from "@/components/ui/form";
import { GemPicture } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage, fieldErrors } from "@/lib/api/client";
import { time } from "@/lib/format";
import { gemPng, OFFER_PICTURES, type OfferAudience, type OfferOptions } from "@/lib/offers";
import { useLoad } from "@/lib/use-load";
import { AudiencePicker, ChannelNotice, RuleNote } from "../offer-parts";

export function WhatsappOfferScreen() {
  const code = useSearchParams().get("code");
  const { data: options } = useLoad(() => api.GET("/api/offers/options"), []);
  const { data: draft, error } = useLoad(
    () =>
      code
        ? api.GET("/api/offers/{code}", { params: { path: { code } } })
        : Promise.resolve({ data: null, error: undefined }),
    [code],
  );
  return (
    <>
      <PageHead
        title="Send an offer on WhatsApp"
        sub="Offers and new arrivals to customers' WhatsApp, using Meta-approved templates."
        actions={
          <Button variant="outline" asChild>
            <Link href="/people/offers">Offer results</Link>
          </Button>
        }
      />
      {error ? (
        <p className="muted">This offer was not found.</p>
      ) : options && draft !== undefined ? (
        <WhatsappForm key={draft?.code ?? "new"} options={options} draft={draft} />
      ) : (
        <p className="muted">Loading…</p>
      )}
    </>
  );
}

type Draft = {
  code: string;
  name: string;
  template?: string;
  body: string;
  picture?: string;
  countries: string[];
  gems: string[];
} | null;

function WhatsappForm({ options, draft }: { options: OfferOptions; draft: Draft }) {
  const toast = useToast();
  const confirm = useConfirm();
  const router = useRouter();
  const [code, setCode] = useState(draft?.code);
  const [name, setName] = useState(draft?.name ?? "");
  const [template, setTemplate] = useState(draft?.template ?? "new_arrivals");
  const [text, setText] = useState(
    draft?.body ?? "Unheated blue sapphires and rare padparadscha, with lab certificates.",
  );
  const [picture, setPicture] = useState(draft ? (draft.picture ?? "") : "Blue Sapphire");
  const [countries, setCountries] = useState<string[]>(draft?.countries ?? []);
  const [gems, setGems] = useState<string[]>(draft?.gems ?? []);
  const [audience, setAudience] = useState<OfferAudience | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const tpl = options.templates.find((t) => t.key === template) ?? options.templates[0];
  const message = (tpl?.text ?? "{text}")
    .replace("{text}", text)
    .replace("{name}", audience?.firstName ?? "Emma")
    .replace("{shop}", options.shopName);

  const form = async () => ({
    code,
    channel: "WHATSAPP",
    name,
    template,
    body: text,
    picture: picture || undefined,
    photo: picture ? await gemPng(picture) : undefined,
    countries,
    gems,
  });

  const check = () => {
    const e: Record<string, string> = {};
    if (!name.trim()) e.name = "Enter a name for the offer.";
    if (!text.trim()) e.body = "Enter your text.";
    setErrors(e);
    return !Object.keys(e).length;
  };

  const fail = (title: string, error: unknown) => {
    setErrors(fieldErrors(error));
    toast(title, errorMessage(error), "error");
  };

  const save = async () => {
    if (!check()) return;
    setBusy(true);
    const { data, error } = await api.POST("/api/offers/draft", { body: await form() });
    setBusy(false);
    if (!data) return fail("Not saved", error);
    setCode(data.code);
    router.replace(`/people/offers/whatsapp?code=${data.code}`);
    toast("Draft saved", `${data.code} · ${data.name}`, "success");
  };

  const test = async () => {
    if (!check()) return;
    setBusy(true);
    const { data, error } = await api.POST("/api/offers/test", { body: await form() });
    setBusy(false);
    if (!data) return fail("Test not sent", error);
    toast("Test message sent", `To ${data.to}${data.testMode ? " (test mode – only recorded)" : ""}.`, "success");
  };

  const send = async () => {
    if (!check()) return;
    if (!audience?.recipients)
      return toast("No recipients", "No customers with permission match these filters.", "error");
    const ok = await confirm({
      title: `Send to ${audience.recipients} customers on WhatsApp?`,
      text: `Only customers who gave permission are included. Estimated Meta cost about US$${Number(audience.costUsd).toFixed(2)}.`,
      yes: "Send now",
    });
    if (!ok) return;
    setBusy(true);
    const { data, error } = await api.POST("/api/offers/send", { body: await form() });
    setBusy(false);
    if (!data) return fail("Not sent", error);
    toast("Offer sent", `${data.sent} WhatsApp message(s).`, "success");
    router.push(`/people/offers?code=${data.code}`);
  };

  return (
    <>
      <ChannelNotice options={options} channel="WHATSAPP" />
      <RuleNote text="Offers go only to customers who gave permission. Every message includes “Reply STOP to unsubscribe”." />
      <div className="g2 grid">
        <Card>
          <CardContent className="stack" style={{ gap: 12 }}>
            <Field label="Offer name" htmlFor="waName" required error={errors.name}>
              <Input
                id="waName"
                maxLength={120}
                placeholder="e.g. Christmas gift offer"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </Field>
            <Field
              label="Template"
              htmlFor="waTpl"
              required
              error={errors.template}
              hint="Marketing templates cost more than utility ones and depend on the customer's country."
            >
              <Select id="waTpl" value={template} onChange={(e) => setTemplate(e.target.value)}>
                <Options items={options.templates.map((t) => ({ value: t.key, label: t.label }))} />
              </Select>
            </Field>
            <Field label="Your text" htmlFor="waText" required error={errors.body}>
              <Textarea id="waText" rows={3} maxLength={600} value={text} onChange={(e) => setText(e.target.value)} />
            </Field>
            <Field label="Picture" htmlFor="waImg">
              <Select id="waImg" value={picture} onChange={(e) => setPicture(e.target.value)}>
                <Options
                  items={[...OFFER_PICTURES.map((g) => ({ value: g, label: g })), { value: "", label: "None" }]}
                />
              </Select>
            </Field>
            <AudiencePicker
              options={options}
              channel="WHATSAPP"
              template={template}
              countries={countries}
              gems={gems}
              setCountries={setCountries}
              setGems={setGems}
              onAudience={setAudience}
            />
          </CardContent>
          <CardFooter>
            <Button variant="outline" disabled={busy} onClick={() => void save()}>
              Save draft
            </Button>
            <Button variant="outline" disabled={busy || !options.whatsappReady} onClick={() => void test()}>
              Send test to my phone
            </Button>
            <Button variant="success" disabled={busy || !options.whatsappReady} onClick={() => void send()}>
              Send WhatsApp offer
            </Button>
          </CardFooter>
        </Card>
        <Card>
          <CardHeader level={3} title="Phone preview" />
          <CardContent>
            <div
              style={{
                maxWidth: 320,
                margin: "0 auto",
                background: "#e5ddd5",
                borderRadius: 24,
                padding: 14,
                border: "8px solid #1f2937",
              }}
            >
              <div
                className="row"
                style={{
                  background: "#075e54",
                  color: "#fff",
                  margin: "-14px -14px 10px",
                  padding: "10px 14px",
                  borderRadius: "14px 14px 0 0",
                }}
              >
                <b style={{ fontSize: 13 }}>{options.shopName}</b>
                <span style={{ fontSize: 10, marginLeft: "auto" }}>✓ Business</span>
              </div>
              <div
                style={{
                  background: "#fff",
                  borderRadius: 8,
                  padding: 6,
                  fontSize: 13,
                  color: "#0f172a",
                  boxShadow: "0 1px 1px rgba(0,0,0,.1)",
                }}
              >
                {picture && (
                  <div
                    style={{
                      background: "radial-gradient(circle,#fff,#e8eefa)",
                      borderRadius: 6,
                      display: "grid",
                      placeItems: "center",
                      height: 130,
                    }}
                  >
                    <GemPicture gem={picture} size={100} box={110} />
                  </div>
                )}
                <p style={{ margin: "6px 4px", whiteSpace: "pre-wrap" }}>{message}</p>
                <div style={{ textAlign: "right", fontSize: 10, color: "#667" }}>{time(new Date())} ✓✓</div>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </>
  );
}
