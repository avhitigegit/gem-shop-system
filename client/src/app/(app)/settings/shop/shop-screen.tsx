"use client";

/* Shop details – prototype a-setup/shop-vat.html (Guide 5). Printed on every invoice and certificate.
   No VAT anywhere; the card bank fee is a shop cost (the customer pays the normal price). */
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import { Alert, Check, Field, FormGrid, Input, Textarea } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { UploadBox } from "@/components/ui/upload-box";
import { errorMessage, fieldErrors } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { api } from "@/lib/api/client";
import { ACCEPT, uploadFile } from "@/lib/files";
import { date } from "@/lib/format";
import { reloadSession, useLoad } from "@/lib/use-load";

type ShopView = components["schemas"]["ShopView"];
type Form = Record<
  | "name"
  | "tagline"
  | "address"
  | "phone"
  | "email"
  | "website"
  | "ngjaLicenceNo"
  | "ngjaLicenceExpiry"
  | "ngjaRemindDays"
  | "largeExpenseLimit"
  | "returnPolicyDays"
  | "cardFeePct",
  string
> & { printLicenceOnInvoice: boolean };

const REQUIRED: (keyof Form)[] = [
  "name",
  "address",
  "phone",
  "email",
  "ngjaLicenceNo",
  "ngjaLicenceExpiry",
  "largeExpenseLimit",
  "cardFeePct",
];

const toForm = (s: ShopView): Form => ({
  name: s.name,
  tagline: s.tagline ?? "",
  address: s.address ?? "",
  phone: s.phone ?? "",
  email: s.email ?? "",
  website: s.website ?? "",
  ngjaLicenceNo: s.ngjaLicenceNo ?? "",
  ngjaLicenceExpiry: s.ngjaLicenceExpiry ?? "",
  ngjaRemindDays: String(s.ngjaRemindDays),
  printLicenceOnInvoice: s.printLicenceOnInvoice,
  largeExpenseLimit: String(Number(s.largeExpenseLimit)),
  returnPolicyDays: String(s.returnPolicyDays),
  cardFeePct: String(Number(s.cardFeePct)),
});

export function ShopScreen() {
  const { data, setData } = useLoad(() => api.GET("/api/settings/shop"), []);
  if (!data) return <PageHead title="Shop & licence details" sub="Loading…" />;
  return <ShopForm key={data.logoFileId ?? "default"} shop={data} onSaved={setData} />;
}

function ShopForm({ shop, onSaved }: { shop: ShopView; onSaved: (s: ShopView) => void }) {
  const toast = useToast();
  const [f, setF] = useState<Form>(() => toForm(shop));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [logo, setLogo] = useState<{ id: string; url: string } | null>(null);
  const [removeLogo, setRemoveLogo] = useState(false);
  const [busy, setBusy] = useState(false);

  const set = (k: keyof Form) => (e: { target: { value: string } }) => {
    setF((o) => ({ ...o, [k]: e.target.value }));
    setErrors((o) => ({ ...o, [k]: "" }));
  };

  const onLogo = async (files: File[]) => {
    const { data, error } = await uploadFile(files[0], "LOGO");
    if (!data) return toast("Logo not uploaded", errorMessage(error), "error");
    setLogo({ id: data.id, url: data.url });
    setRemoveLogo(false);
  };

  const save = async (e: FormEvent) => {
    e.preventDefault();
    const missing: Record<string, string> = {};
    for (const k of REQUIRED) if (!String(f[k]).trim()) missing[k] = "Required.";
    if (Object.keys(missing).length) {
      setErrors(missing);
      return toast("Please fill in the marked fields", undefined, "error");
    }
    setBusy(true);
    const { data, error } = await api.PUT("/api/settings/shop", {
      body: {
        name: f.name,
        tagline: f.tagline || undefined,
        address: f.address,
        phone: f.phone,
        email: f.email,
        website: f.website || undefined,
        ngjaLicenceNo: f.ngjaLicenceNo,
        ngjaLicenceExpiry: f.ngjaLicenceExpiry,
        ngjaRemindDays: Number(f.ngjaRemindDays || 60),
        printLicenceOnInvoice: f.printLicenceOnInvoice,
        largeExpenseLimit: Number(f.largeExpenseLimit),
        cardFeePct: Number(f.cardFeePct),
        returnPolicyDays: Number(f.returnPolicyDays || 0),
        logoFileId: logo?.id,
        removeLogo,
      },
    });
    setBusy(false);
    if (!data) {
      setErrors(fieldErrors(error));
      return toast("Not saved", errorMessage(error), "error");
    }
    toast("Settings saved", "", "success");
    onSaved(data);
    void reloadSession(); // shop name in the top bar
  };

  const days = shop.licenceDaysLeft;
  const logoUrl = removeLogo ? null : (logo?.url ?? shop.logoUrl ?? null);
  return (
    <>
      <PageHead
        title="Shop & licence details"
        sub="Printed on every invoice and certificate. Can be changed at any time."
      />
      {days != null && days <= shop.ngjaRemindDays ? (
        <Alert tone={days < 0 ? "danger" : "warning"} className="mb">
          <b>
            📜 NGJA dealer licence {days < 0 ? `EXPIRED ${-days} days ago` : `expires in ${days} days`} (
            {date(shop.ngjaLicenceExpiry)}).
          </b>{" "}
          Renew with the National Gem &amp; Jewellery Authority and update the expiry date below.
        </Alert>
      ) : null}
      <form noValidate className="g2 grid" onSubmit={save}>
        <Card>
          <CardHeader title="Shop details" level={3} />
          <CardContent className="stack" style={{ gap: 12 }}>
            <Field label="Shop name" htmlFor="sname" required error={errors.name}>
              <Input id="sname" value={f.name} onChange={set("name")} />
            </Field>
            <Field label="Tagline" htmlFor="tagline" error={errors.tagline}>
              <Input id="tagline" value={f.tagline} onChange={set("tagline")} />
            </Field>
            <Field label="Address" htmlFor="address" required error={errors.address}>
              <Textarea id="address" rows={2} value={f.address} onChange={set("address")} />
            </Field>
            <FormGrid>
              <Field label="Phone" htmlFor="phone" required error={errors.phone}>
                <Input id="phone" value={f.phone} onChange={set("phone")} />
              </Field>
              <Field label="Email" htmlFor="email" required error={errors.email}>
                <Input id="email" type="email" value={f.email} onChange={set("email")} />
              </Field>
            </FormGrid>
            <Field label="Website" htmlFor="website" error={errors.website}>
              <Input id="website" value={f.website} onChange={set("website")} />
            </Field>
            <Field label="Logo" hint="PNG with a see-through background looks best on invoices.">
              <UploadBox accept={ACCEPT.LOGO} onFiles={(fl) => void onLogo(fl)}>
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={logoUrl ?? "/logo.svg"}
                  alt="Shop logo"
                  style={{ height: 46, margin: "0 auto 6px", display: "block" }}
                />
                ⇪ Change logo
              </UploadBox>
              {logoUrl ? (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    setLogo(null);
                    setRemoveLogo(true);
                  }}
                >
                  Use the standard logo
                </Button>
              ) : null}
            </Field>
          </CardContent>
        </Card>
        <div className="stack">
          <Card>
            <CardHeader title="NGJA gem dealer licence" level={3} />
            <CardContent className="stack" style={{ gap: 12 }}>
              <FormGrid>
                <Field label="Licence number" htmlFor="ngjaNo" required error={errors.ngjaLicenceNo}>
                  <Input id="ngjaNo" className="mono" value={f.ngjaLicenceNo} onChange={set("ngjaLicenceNo")} />
                </Field>
                <Field label="Expiry date" htmlFor="ngjaExpiry" required error={errors.ngjaLicenceExpiry}>
                  <Input id="ngjaExpiry" type="date" value={f.ngjaLicenceExpiry} onChange={set("ngjaLicenceExpiry")} />
                </Field>
              </FormGrid>
              <FormGrid>
                <Field label="Remind me before expiry (days)" htmlFor="remind" error={errors.ngjaRemindDays}>
                  <Input
                    id="remind"
                    type="number"
                    min={1}
                    max={365}
                    value={f.ngjaRemindDays}
                    onChange={set("ngjaRemindDays")}
                  />
                </Field>
                <div style={{ alignSelf: "end" }}>
                  <Check
                    checked={f.printLicenceOnInvoice}
                    onChange={(v) => setF((o) => ({ ...o, printLicenceOnInvoice: v }))}
                  >
                    Print licence on invoices &amp; certificates
                  </Check>
                </div>
              </FormGrid>
            </CardContent>
          </Card>
          <Card>
            <CardHeader title="Control limits" level={3} />
            <CardContent>
              <FormGrid>
                <Field
                  label="Expenses needing owner approval above (LKR)"
                  htmlFor="limit"
                  required
                  error={errors.largeExpenseLimit}
                >
                  <Input
                    id="limit"
                    type="number"
                    min={0}
                    value={f.largeExpenseLimit}
                    onChange={set("largeExpenseLimit")}
                  />
                </Field>
                <Field label="Return policy (days)" htmlFor="ret" error={errors.returnPolicyDays}>
                  <Input
                    id="ret"
                    type="number"
                    min={0}
                    max={365}
                    value={f.returnPolicyDays}
                    onChange={set("returnPolicyDays")}
                  />
                </Field>
                <Field
                  label="Card bank fee (%) – paid by the shop"
                  htmlFor="cardFee"
                  required
                  full
                  error={errors.cardFeePct}
                  hint="The part of every card payment the bank keeps. The customer pays the normal price; this fee is a shop cost (shown in Profit & Loss)."
                >
                  <Input
                    id="cardFee"
                    type="number"
                    min={0}
                    max={10}
                    step={0.1}
                    value={f.cardFeePct}
                    onChange={set("cardFeePct")}
                  />
                </Field>
              </FormGrid>
            </CardContent>
            <CardFooter>
              <Button variant="sapphire" type="submit" disabled={busy}>
                {busy ? "Saving…" : "Save settings"}
              </Button>
            </CardFooter>
          </Card>
        </div>
      </form>
    </>
  );
}
