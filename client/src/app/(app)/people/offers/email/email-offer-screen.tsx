"use client";

/* Send an offer by email – prototype j-customers-promotions/email-campaign.html (Guide 46; BRD J3, J2). Owner only.
   Picture and text, who receives it, preview as the customer sees it. Sent through Amazon SES from the shop's domain with an
   unsubscribe link; only to customers who gave permission (rule 8). ?code=OF-… opens a draft. */
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import { useConfirm } from "@/components/ui/dialog";
import { Field, Input, Textarea } from "@/components/ui/form";
import { GemPicture } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage, fieldErrors } from "@/lib/api/client";
import { gemPng, OFFER_PICTURES, photoDataUrl, type OfferAudience, type OfferOptions } from "@/lib/offers";
import { useLoad } from "@/lib/use-load";
import { AudiencePicker, ChannelNotice, RuleNote } from "../offer-parts";

const BODY =
  "Dear {name},\n\nThank you for visiting our shop. We have just received a small collection of rare Ceylon sapphires and padparadschas.\n\nReply to this email or WhatsApp us to reserve a piece – we ship worldwide with full lab certificates.\n\nWarm regards";

export function EmailOfferScreen() {
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
        title="Send an offer by email"
        sub="Pictures and text, choose who receives it, and send. Sent through Amazon SES from the shop's domain, with an unsubscribe link."
        actions={
          <Button variant="outline" asChild>
            <Link href="/people/offers">Offer results</Link>
          </Button>
        }
      />
      {error ? (
        <p className="muted">This offer was not found.</p>
      ) : options && draft !== undefined ? (
        <EmailForm key={draft?.code ?? "new"} options={options} draft={draft} />
      ) : (
        <p className="muted">Loading…</p>
      )}
    </>
  );
}

type Draft = {
  code: string;
  name: string;
  subject?: string;
  body: string;
  picture?: string;
  photo?: string;
  countries: string[];
  gems: string[];
} | null;

function EmailForm({ options, draft }: { options: OfferOptions; draft: Draft }) {
  const toast = useToast();
  const confirm = useConfirm();
  const router = useRouter();
  const [code, setCode] = useState(draft?.code);
  const [name, setName] = useState(draft?.name ?? "");
  const [subject, setSubject] = useState(draft?.subject ?? "");
  const [body, setBody] = useState(draft?.body ?? BODY);
  const [picture, setPicture] = useState(draft ? (draft.picture ?? "") : "Padparadscha");
  const [upload, setUpload] = useState<string | null>(draft?.picture === "upload" ? (draft.photo ?? null) : null);
  const [countries, setCountries] = useState<string[]>(draft?.countries ?? []);
  const [gems, setGems] = useState<string[]>(draft?.gems ?? []);
  const [audience, setAudience] = useState<OfferAudience | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const first = audience?.firstName ?? "Anna";

  const form = async () => {
    const photo = picture === "upload" ? (upload ?? undefined) : picture ? await gemPng(picture) : undefined;
    return { code, channel: "EMAIL", name, subject, body, picture: picture || undefined, photo, countries, gems };
  };

  const check = () => {
    const e: Record<string, string> = {};
    if (!name.trim()) e.name = "Enter a name for the offer.";
    if (!subject.trim()) e.subject = "Enter the email subject.";
    if (!body.trim()) e.body = "Enter the text.";
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
    router.replace(`/people/offers/email?code=${data.code}`);
    toast("Draft saved", `${data.code} · ${data.name}`, "success");
  };

  const test = async () => {
    if (!check()) return;
    setBusy(true);
    const { data, error } = await api.POST("/api/offers/test", { body: await form() });
    setBusy(false);
    if (!data) return fail("Test not sent", error);
    toast("Test email sent", `To ${data.to}${data.testMode ? " (test mail server)" : ""}.`, "success");
  };

  const send = async () => {
    if (!check()) return;
    if (!audience?.recipients)
      return toast("No recipients", "No customers with permission match these filters.", "error");
    const ok = await confirm({
      title: `Send to ${audience.recipients} customers?`,
      text: "Only customers who agreed to receive offers are included. Each email has an unsubscribe link.",
      yes: "Send now",
    });
    if (!ok) return;
    setBusy(true);
    const { data, error } = await api.POST("/api/offers/send", { body: await form() });
    setBusy(false);
    if (!data) return fail("Not sent", error);
    toast("Offer sent", `${data.sent} email(s) sent via Amazon SES.`, "success");
    router.push(`/people/offers?code=${data.code}`);
  };

  const choose = async (file: File | undefined) => {
    if (!file) return;
    try {
      setUpload(await photoDataUrl(file));
      setPicture("upload");
    } catch (e) {
      toast("Photo not used", (e as Error).message, "error");
    }
  };

  return (
    <>
      <ChannelNotice options={options} channel="EMAIL" />
      <RuleNote text="Offers go only to customers who gave permission. Each message has an “unsubscribe” option." />
      <div className="g2 grid">
        <Card>
          <CardHeader level={3} title="1 · Message" />
          <CardContent className="stack" style={{ gap: 12 }}>
            <Field label="Offer name" htmlFor="ofName" required error={errors.name}>
              <Input
                id="ofName"
                maxLength={120}
                placeholder="e.g. New padparadscha arrivals"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </Field>
            <Field label="Email subject" htmlFor="ofSubject" required error={errors.subject}>
              <Input
                id="ofSubject"
                maxLength={200}
                placeholder="Rare gems just arrived"
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
              />
            </Field>
            <Field label="Picture" error={errors.photo}>
              <div className="row" style={{ gap: 6, flexWrap: "wrap" }}>
                {OFFER_PICTURES.map((g) => (
                  <button
                    key={g}
                    type="button"
                    className="btn btn-outline"
                    title={g}
                    aria-pressed={picture === g}
                    style={{
                      height: 58,
                      width: 58,
                      padding: 0,
                      borderColor: picture === g ? "var(--sapphire)" : undefined,
                    }}
                    onClick={() => setPicture(g)}
                  >
                    <GemPicture gem={g} size={44} box={52} />
                  </button>
                ))}
                <Button variant={picture === "" ? "sapphire" : "outline"} size="sm" onClick={() => setPicture("")}>
                  No picture
                </Button>
              </div>
              <label className="btn btn-outline btn-sm mt" style={{ width: "fit-content" }}>
                ⇪ or upload your own photo
                <input
                  type="file"
                  accept="image/png,image/jpeg"
                  hidden
                  onChange={(e) => void choose(e.target.files?.[0])}
                />
              </label>
            </Field>
            <Field
              label="Text"
              htmlFor="ofBody"
              required
              error={errors.body}
              hint="{name} is replaced with the customer's first name"
            >
              <Textarea id="ofBody" rows={8} maxLength={5000} value={body} onChange={(e) => setBody(e.target.value)} />
            </Field>
          </CardContent>
          <div style={{ borderTop: "1px solid var(--border)" }}>
            <CardHeader level={3} title="2 · Who receives it" />
          </div>
          <CardContent>
            <AudiencePicker
              options={options}
              channel="EMAIL"
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
            <Button variant="outline" disabled={busy || !options.emailOn} onClick={() => void test()}>
              Send test to me
            </Button>
            <Button variant="sapphire" disabled={busy || !options.emailReady} onClick={() => void send()}>
              Send offer
            </Button>
          </CardFooter>
        </Card>
        <Card>
          <CardHeader level={3} title="Preview">
            <span className="muted" style={{ fontSize: 12 }}>
              as the customer sees it
            </span>
          </CardHeader>
          <CardContent style={{ background: "#eef1f6" }}>
            <div
              style={{
                background: "#fff",
                maxWidth: 520,
                margin: "0 auto",
                borderRadius: 8,
                overflow: "hidden",
                boxShadow: "var(--shadow)",
                color: "#0f172a",
              }}
            >
              <div style={{ background: "var(--navy)", color: "#fff", padding: "14px 18px", fontWeight: 700 }}>
                {options.shopName}
              </div>
              {picture && (
                <div
                  style={{
                    height: 180,
                    display: "grid",
                    placeItems: "center",
                    background: "radial-gradient(circle,#fff,#e8eefa)",
                  }}
                >
                  {picture === "upload" && upload ? (
                    // eslint-disable-next-line @next/next/no-img-element -- the owner's own photo, shown before saving
                    <img src={upload} alt="Offer photo" style={{ maxHeight: 180, maxWidth: "100%" }} />
                  ) : (
                    <GemPicture gem={picture} size={140} box={160} />
                  )}
                </div>
              )}
              <div style={{ padding: "16px 18px" }}>
                <b style={{ fontSize: 16 }}>{subject || "Subject"}</b>
                <p style={{ whiteSpace: "pre-wrap", fontSize: 13, marginTop: 8 }}>{body.replace(/\{name\}/g, first)}</p>
                {options.website && (
                  <span className="btn btn-gold" style={{ pointerEvents: "none" }}>
                    View our gems
                  </span>
                )}
              </div>
              <div style={{ padding: "10px 18px", background: "#f8fafc", fontSize: 11, color: "#64748b" }}>
                {options.shopAddress}
                {options.shopAddress && <br />}
                You receive this because you agreed at our shop. <u>Unsubscribe</u>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </>
  );
}
