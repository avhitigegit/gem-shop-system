/* New sale (P3-01): types, the unfinished bill kept in the browser, countries and months. */
import { todayInShop } from "@/lib/format";
import type { components } from "@/lib/api/schema";

export type GemCard = components["schemas"]["GemCard"];
export type PosGem = components["schemas"]["PosGem"];
export type BillView = components["schemas"]["BillView"];
export type CustomerPick = components["schemas"]["CustomerPick"];
export type GuidePick = components["schemas"]["GuidePick"];
export type HeldView = components["schemas"]["HeldView"];
export type SaleView = components["schemas"]["SaleView"];
export type SaleRow = components["schemas"]["SaleRow"];
export type PaymentForm = components["schemas"]["PaymentForm"];
export type InvoiceRow = components["schemas"]["InvoiceRow"];
export type InvoicePage = components["schemas"]["InvoicePage"];
export type SendResult = components["schemas"]["SendResult"];
export type QuoteView = components["schemas"]["QuoteView"];
export type QuoteKind = QuoteView["kind"];
export type ReturnSale = components["schemas"]["ReturnSale"];
export type ReturnView = components["schemas"]["ReturnView"];
export type CreditNoteView = components["schemas"]["CreditNoteView"];
export const kindLabel = (k: ReturnView["kind"]) => (k === "EXCHANGE" ? "Exchange" : "Refund");

/** "2026-11-26" in the shop, n days from today. */
export const inDays = (n: number) => todayInShop(new Date(Date.now() + n * 86_400_000));

/** "Card" · "Cash USD" for one payment line. */
export const payLabel = (p: { method: string; currency: string }) =>
  p.method === "CARD" ? "Card" : p.method === "CREDIT" ? "Credit note" : `Cash ${p.currency}`;

export type NewCustomer = {
  name: string;
  country: string;
  email: string;
  whatsapp: string;
  day: string;
  month: string;
  consent: boolean;
};

/** The bill being made. Kept in the browser so a refresh does not lose it (prototype gemshop.pos.draft). */
export type Draft = {
  items: { gem: GemCard; agreed: number }[];
  custMode: "new" | "existing";
  customer: string;
  newCust: NewCustomer;
  guide: string;
  held: string | null;
  /** RS-… / QT-… when selling a reservation or quotation. */
  quote: string | null;
  /** CN-… exchange credit note paying for this bill (P3-04). */
  credit: string | null;
};

const KEY = "gemshop.pos.draft";

export const blankDraft = (): Draft => ({
  items: [],
  custMode: "new",
  customer: "",
  newCust: { name: "", country: "", email: "", whatsapp: "", day: "", month: "", consent: false },
  guide: "",
  held: null,
  quote: null,
  credit: null,
});

export function loadDraft(): Draft {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return blankDraft();
    const d = JSON.parse(raw) as Partial<Draft>;
    return { ...blankDraft(), ...d, newCust: { ...blankDraft().newCust, ...(d.newCust ?? {}) } };
  } catch {
    return blankDraft();
  }
}

export function saveDraft(d: Draft) {
  try {
    localStorage.setItem(KEY, JSON.stringify(d));
  } catch {
    /* private window: the bill is only lost on refresh */
  }
}

/** The customer part of the sale / hold form. */
export function customerForm(d: Draft) {
  if (d.custMode === "existing") return { code: d.customer };
  const c = d.newCust;
  return {
    name: c.name.trim(),
    country: c.country,
    email: c.email.trim() || undefined,
    whatsapp: c.whatsapp.trim() || undefined,
    birthday: c.day && c.month ? `${c.month}-${c.day}` : undefined,
    consent: c.consent,
  };
}

/** Problems with the customer before sending, as field errors ("customer.name" …). */
export function customerErrors(d: Draft): Record<string, string> {
  const e: Record<string, string> = {};
  if (d.custMode === "existing") {
    if (!d.customer) e["customer.code"] = "Select the customer";
    return e;
  }
  const c = d.newCust;
  if (!c.name.trim()) e["customer.name"] = "Required";
  if (!c.country) e["customer.country"] = "Required";
  if (c.email.trim() && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(c.email.trim()))
    e["customer.email"] = "e.g. anna@example.com";
  if (!!c.day !== !!c.month) e["customer.birthday"] = "Choose both the day and the month, or neither";
  else if (c.day && c.month) {
    const max = [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][Number(c.month) - 1];
    if (Number(c.day) > max) e["customer.birthday"] = "That day is not in the month";
  }
  return e;
}

export const COUNTRIES = [
  "Germany",
  "United Kingdom",
  "France",
  "USA",
  "Australia",
  "Netherlands",
  "India",
  "Italy",
  "Spain",
  "Switzerland",
  "Russia",
  "China",
  "Japan",
  "Sri Lanka",
  "Other",
];

export const MONTHS = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

export const pad2 = (n: number) => String(n).padStart(2, "0");
