"use client";

/* Send an invoice or a quotation – the prototype's send window (BRD G4): email with the PDF (shop email) and / or WhatsApp. WhatsApp runs in
   test mode until the shop's WhatsApp Business account is connected (P6-03): the message is only written to the log. */
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Modal } from "@/components/ui/dialog";
import { Check, Field, Input } from "@/components/ui/form";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage, fieldErrors } from "@/lib/api/client";
import { useSession } from "@/lib/session";

type Doc = {
  /** "invoice" for /api/sales/…/send, "quotation" / "reservation" for /api/quotes/…/send */
  label: "invoice" | "quotation" | "reservation";
  number: string;
  branch: string;
  customer: { name: string; email?: string | null; whatsapp?: string | null };
};

export function SendModal({ doc, onClose, onSent }: { doc: Doc; onClose: () => void; onSent: () => void }) {
  const toast = useToast();
  const { shop, branchName } = useSession();
  const [email, setEmail] = useState(true);
  const [whatsapp, setWhatsapp] = useState(!!doc.customer.whatsapp);
  const [emailTo, setEmailTo] = useState(doc.customer.email ?? "");
  const [waTo, setWaTo] = useState(doc.customer.whatsapp ?? "");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const first = doc.customer.name.trim().split(/\s+/)[0] || "customer";

  const send = async () => {
    const e: Record<string, string> = {};
    if (!email && !whatsapp) return toast("Choose email or WhatsApp", undefined, "error");
    if (email && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(emailTo.trim())) e.emailTo = "Enter a valid email address";
    if (whatsapp && !/^\+?[0-9 ()-]{7,20}$/.test(waTo.trim()))
      e.whatsappTo = "Number with the country code, e.g. +49 151 …";
    setErrors(e);
    if (Object.keys(e).length) return;
    setBusy(true);
    const body = { email, emailTo: emailTo.trim() || undefined, whatsapp, whatsappTo: waTo.trim() || undefined };
    const path = { number: doc.number };
    const { data, error } =
      doc.label === "invoice"
        ? await api.POST("/api/sales/{number}/send", { params: { path }, body })
        : await api.POST("/api/quotes/{number}/send", { params: { path }, body });
    setBusy(false);
    if (!data) {
      setErrors(fieldErrors(error));
      return toast("Not sent", errorMessage(error), "error");
    }
    toast(
      `${title(doc.label)} ${doc.number} sent`,
      data.message + (data.whatsappTestMode ? " WhatsApp is in test mode – no real message yet." : ""),
      "success",
    );
    onSent();
  };

  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title={`Send ${doc.label} ${doc.number}`}
      desc="Send to the customer by email and / or WhatsApp"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="sapphire" id="sendGo" disabled={busy} onClick={() => void send()}>
            {busy ? "Sending…" : "Send"}
          </Button>
        </>
      }
    >
      <div className="stack" style={{ gap: 12 }}>
        <Check checked={email} onChange={setEmail}>
          <b>Email</b> (PDF attached, from the shop email)
        </Check>
        <Field label="Email address" htmlFor="sdEmail" error={errors.emailTo}>
          <Input
            id="sdEmail"
            type="email"
            maxLength={160}
            disabled={!email}
            value={emailTo}
            onChange={(e) => setEmailTo(e.target.value)}
          />
        </Field>
        <Check checked={whatsapp} onChange={setWhatsapp}>
          <b>WhatsApp</b> (PDF) <Badge tone="warning">Test mode</Badge>
        </Check>
        <Field
          label="WhatsApp number"
          htmlFor="sdWa"
          error={errors.whatsappTo}
          hint="WhatsApp Business is connected near the end of the project. Until then the message is only recorded."
        >
          <Input
            id="sdWa"
            type="tel"
            maxLength={40}
            disabled={!whatsapp}
            value={waTo}
            onChange={(e) => setWaTo(e.target.value)}
          />
        </Field>
        <Card style={{ background: "var(--muted)" }}>
          <CardContent style={{ fontSize: 12 }}>
            <b>Message preview</b>
            <br />
            Dear {first}, thank you for visiting {shop.name}, {branchName(doc.branch)}. Please find your {doc.label}{" "}
            {doc.number} attached as a PDF. Safe travels!
          </CardContent>
        </Card>
      </div>
    </Modal>
  );
}

const title = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);
