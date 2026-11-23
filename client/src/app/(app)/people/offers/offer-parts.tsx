"use client";

/* Shared parts of the two offer screens: who receives it (countries and gems bought before, ticked = only those) and the
   count of customers with permission (rule 8), asked from the server whenever the choice changes. */
import Link from "next/link";
import { useEffect, useState } from "react";
import { Alert, Check, Field } from "@/components/ui/form";
import { api } from "@/lib/api/client";
import type { OfferAudience, OfferOptions } from "@/lib/offers";

export function Ticks({
  label,
  items,
  value,
  onChange,
}: {
  label: string;
  items: string[];
  value: string[];
  onChange: (v: string[]) => void;
}) {
  const flip = (x: string, on: boolean) => onChange(on ? [...value, x] : value.filter((v) => v !== x));
  return (
    <Field label={label}>
      <div className="row" style={{ gap: "6px 14px", flexWrap: "wrap" }}>
        {items.map((x) => (
          <Check key={x} checked={value.includes(x)} onChange={(on) => flip(x, on)}>
            {x}
          </Check>
        ))}
      </div>
    </Field>
  );
}

/** Countries + gems and the live count. Calls onAudience with the server's answer. */
export function AudiencePicker({
  options,
  channel,
  template,
  countries,
  gems,
  setCountries,
  setGems,
  onAudience,
}: {
  options: OfferOptions;
  channel: "EMAIL" | "WHATSAPP";
  template?: string;
  countries: string[];
  gems: string[];
  setCountries: (v: string[]) => void;
  setGems: (v: string[]) => void;
  onAudience: (a: OfferAudience | null) => void;
}) {
  const [a, setA] = useState<OfferAudience | null>(null);
  const key = JSON.stringify([channel, template, countries, gems]);
  useEffect(() => {
    let live = true;
    const t = setTimeout(async () => {
      const { data } = await api.POST("/api/offers/audience", { body: { channel, template, countries, gems } });
      if (live) {
        setA(data ?? null);
        onAudience(data ?? null);
      }
    }, 250);
    return () => {
      live = false;
      clearTimeout(t);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- key holds every input
  }, [key]);

  const email = channel === "EMAIL";
  return (
    <div className="stack" style={{ gap: 10 }}>
      <Ticks
        label="Countries (none ticked = all)"
        items={options.countries}
        value={countries}
        onChange={setCountries}
      />
      <Ticks label="Gem bought before (none ticked = any)" items={options.gemTypes} value={gems} onChange={setGems} />
      {a && (
        <Alert tone={a.recipients ? "info" : "warning"}>
          <b>{a.recipients} recipient(s)</b> will receive this {email ? "email" : "message"}.
          <br />
          <small>
            {a.matching} customers match · {a.excluded} left out (no permission, unsubscribed or no{" "}
            {email ? "email" : "number"}).
          </small>
          <br />
          <small>
            Estimated cost: about US${Number(a.costUsd).toFixed(email ? 4 : 2)}{" "}
            {email ? "via Amazon SES" : "paid by the shop to Meta directly. Tip: use email for large offers."}
          </small>
        </Alert>
      )}
    </div>
  );
}

/** Red / yellow notice when the channel cannot send (the owner can still prepare the offer). */
export function ChannelNotice({ options, channel }: { options: OfferOptions; channel: "EMAIL" | "WHATSAPP" }) {
  if (channel === "EMAIL" && !options.emailReady)
    return (
      <Alert tone="danger" className="mb">
        {options.emailOn ? "Amazon SES for offers is off" : "Email messages are switched off"} in{" "}
        <Link href="/people/message-settings">Email &amp; WhatsApp settings</Link>. You can prepare a draft but not
        send.
      </Alert>
    );
  if (channel === "WHATSAPP" && !options.whatsappReady)
    return (
      <Alert tone="warning" className="mb">
        <b>WhatsApp Business (Premium) is off or not verified.</b> Turn it on and verify the connection in{" "}
        <Link href="/people/message-settings">Email &amp; WhatsApp settings</Link> to send. You can still prepare the
        message.
      </Alert>
    );
  if ((channel === "EMAIL" && options.emailTestMode) || (channel === "WHATSAPP" && options.whatsappTestMode))
    return (
      <Alert tone="info" className="mb">
        <b>Test mode.</b> Until the shop&apos;s real {channel === "EMAIL" ? "Amazon SES" : "WhatsApp Business"} account
        is connected, offers are only recorded – nothing reaches customers yet.
      </Alert>
    );
  return null;
}

export function RuleNote({ text }: { text: string }) {
  return (
    <Alert tone="info" className="mb">
      <b>Rule 8:</b> {text}
    </Alert>
  );
}
