"use client";

/* Shared parts of the customer screens (prototype j-customers-promotions/customers.html, customer-profile.html):
   the birthday cell and drop-downs, the permission badge and the Add / Edit customer form. */
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import { Check, Field, FormGrid, Input, Options, Select } from "@/components/ui/form";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage, fieldErrors } from "@/lib/api/client";
import { birthdayShort, birthdayValue, type CustomerRow } from "@/lib/customers";
import { date } from "@/lib/format";
import { COUNTRIES, MONTHS, pad2 } from "@/lib/sales";

/** "17 Apr" with "🎂 Today" or "in 3 days" when it is close (prototype Birthday.cell). */
export function BirthdayCell({ c }: { c: Pick<CustomerRow, "birthday" | "birthdayIn"> }) {
  if (!c.birthday) return <span className="muted">—</span>;
  const d = c.birthdayIn;
  return (
    <>
      {birthdayShort(c.birthday)}
      {d === 0 ? (
        <>
          {" "}
          <Badge tone="gold">🎂 Today</Badge>
        </>
      ) : d != null && d <= 7 ? (
        <>
          {" "}
          <Badge tone="outline">
            in {d} day{d > 1 ? "s" : ""}
          </Badge>
        </>
      ) : null}
    </>
  );
}

export function PromotionBadge({ c }: { c: CustomerRow }) {
  if (c.unsubscribed) return <Badge tone="danger">Unsubscribed</Badge>;
  if (c.consent) return <Badge tone="success">✓ Consent {date(c.consentAt)}</Badge>;
  return <Badge tone="default">No consent</Badge>;
}

/** Birthday: day and month drop-downs (no year). */
export function BirthdayFields({
  day,
  month,
  onChange,
  error,
  idPrefix,
}: {
  day: string;
  month: string;
  onChange: (day: string, month: string) => void;
  error?: string;
  idPrefix: string;
}) {
  return (
    <Field
      label={
        <>
          Birthday <small className="muted">(optional)</small>
        </>
      }
      htmlFor={`${idPrefix}Day`}
      error={error}
      hint="Day and month only – for the automatic birthday wish."
    >
      <div className="row" style={{ gap: 6, flexWrap: "nowrap" }}>
        <Select
          id={`${idPrefix}Day`}
          aria-label="Birthday day"
          style={{ width: 84 }}
          value={day}
          onChange={(e) => onChange(e.target.value, month)}
        >
          <Options
            items={Array.from({ length: 31 }, (_, i) => ({ value: pad2(i + 1), label: String(i + 1) }))}
            placeholder="Day"
          />
        </Select>
        <Select
          id={`${idPrefix}Month`}
          aria-label="Birthday month"
          style={{ flex: 1 }}
          value={month}
          onChange={(e) => onChange(day, e.target.value)}
        >
          <Options items={MONTHS.map((m, i) => ({ value: pad2(i + 1), label: m }))} placeholder="Month" />
        </Select>
      </div>
    </Field>
  );
}

type Editing = {
  code: string;
  name: string;
  country: string;
  email?: string;
  whatsapp?: string;
  birthday?: string;
  /** "•••• 000" for the owner, or true when a passport is saved but hidden. */
  passport?: string | boolean;
};

/** Add customer (with birthday and permission), or Edit one (permission has its own switch on the customer's page). */
export function CustomerModal({
  customer,
  countries,
  onClose,
  onSaved,
}: {
  customer: Editing | null;
  countries: string[];
  onClose: () => void;
  onSaved: (c: CustomerRow) => void;
}) {
  const toast = useToast();
  const [m, d] = (customer?.birthday ?? "-").split("-");
  const [f, setF] = useState({
    name: customer?.name ?? "",
    country: customer?.country ?? "",
    email: customer?.email ?? "",
    whatsapp: customer?.whatsapp ?? "",
    passport: "",
    day: d ?? "",
    month: m ?? "",
    consent: false,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof f, v: string | boolean, err = k) => {
    setF((x) => ({ ...x, [k]: v }));
    setErrors((e) => ({ ...e, [`customer.${err}`]: "" }));
  };
  const countryList = [...new Set([...COUNTRIES.filter((c) => c !== "Other"), ...countries, f.country].filter(Boolean))]
    .sort()
    .concat("Other");

  const save = async () => {
    const e: Record<string, string> = {};
    if (!f.name.trim()) e["customer.name"] = "Required";
    if (!f.country) e["customer.country"] = "Required";
    if (f.email.trim() && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(f.email.trim()))
      e["customer.email"] = "e.g. anna@example.com";
    const birthday = birthdayValue(f.day, f.month);
    if (birthday === null) e["customer.birthday"] = "Choose both the day and the month, or leave both empty";
    setErrors(e);
    if (Object.values(e).some(Boolean)) return;
    setBusy(true);
    const body = {
      name: f.name.trim(),
      country: f.country,
      email: f.email.trim() || undefined,
      whatsapp: f.whatsapp.trim() || undefined,
      birthday: birthday || undefined,
      passport: f.passport.trim() || undefined,
      consent: customer ? undefined : f.consent,
    };
    const { data, error } = customer
      ? await api.PUT("/api/customers/{code}", { params: { path: { code: customer.code } }, body })
      : await api.POST("/api/customers", { body });
    setBusy(false);
    if (!data) {
      setErrors(fieldErrors(error));
      return toast(customer ? "Not saved" : "Customer not added", errorMessage(error), "error");
    }
    toast(customer ? "Saved" : "Customer added", `${data.name} · ${data.code}`, "success");
    onSaved(data);
  };

  const saved = customer?.passport;
  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title={customer ? `Edit ${customer.name}` : "Add customer"}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="sapphire" id="saveCustomer" disabled={busy} onClick={() => void save()}>
            {busy ? "Saving…" : "Save"}
          </Button>
        </>
      }
    >
      <FormGrid>
        <Field label="Name" htmlFor="cuName" required error={errors["customer.name"]} full>
          <Input id="cuName" maxLength={120} value={f.name} onChange={(e) => set("name", e.target.value)} />
        </Field>
        <Field label="Country" htmlFor="cuCountry" required error={errors["customer.country"]}>
          <Select id="cuCountry" value={f.country} onChange={(e) => set("country", e.target.value)}>
            <Options items={countryList.map((x) => ({ value: x, label: x }))} placeholder="Select…" />
          </Select>
        </Field>
        <Field
          label="Passport no."
          htmlFor="cuPassport"
          error={errors["customer.passport"]}
          hint={
            saved
              ? `${typeof saved === "string" ? saved : "A passport is saved"} – leave empty to keep it`
              : "Stored encrypted"
          }
        >
          <Input
            id="cuPassport"
            maxLength={20}
            autoComplete="off"
            value={f.passport}
            placeholder={saved ? "type a new one to change" : "stored encrypted"}
            onChange={(e) => set("passport", e.target.value)}
          />
        </Field>
        <Field label="Email" htmlFor="cuEmail" error={errors["customer.email"]}>
          <Input
            id="cuEmail"
            type="email"
            maxLength={160}
            value={f.email}
            onChange={(e) => set("email", e.target.value)}
          />
        </Field>
        <Field label="WhatsApp" htmlFor="cuWa" error={errors["customer.whatsapp"]}>
          <Input
            id="cuWa"
            type="tel"
            maxLength={40}
            placeholder="+49 …"
            value={f.whatsapp}
            onChange={(e) => set("whatsapp", e.target.value)}
          />
        </Field>
        <BirthdayFields
          idPrefix="cu"
          day={f.day}
          month={f.month}
          error={errors["customer.birthday"]}
          onChange={(day, month) => {
            setF((x) => ({ ...x, day, month }));
            setErrors((er) => ({ ...er, "customer.birthday": "" }));
          }}
        />
        {customer ? null : (
          <div className="full">
            <Check checked={f.consent} onChange={(v) => set("consent", v)}>
              Customer agrees to receive offers and birthday wishes by email / WhatsApp. They can unsubscribe at any
              time.
            </Check>
          </div>
        )}
      </FormGrid>
    </Modal>
  );
}
