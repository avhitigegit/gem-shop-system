/* Formats – the same as the prototype (prototype/assets/js/app.js → fmt).
   Money: "LKR 1,234.00" · dates: dd/mm/yyyy · times: 24 h · always Sri Lanka time. */

export const CURRENCIES = ["LKR", "USD", "EUR"] as const;
export type Currency = (typeof CURRENCIES)[number];
/** LKR for 1 USD / 1 EUR. */
export type Rates = { USD: number; EUR: number };

export const SHOP_TIME_ZONE = "Asia/Colombo";
type Amount = number | string | null | undefined;

const toNumber = (n: Amount) => (n == null || n === "" ? 0 : Number(n));
const round2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100;

/** 1234.5 → "1,234.50" */
export function num(n: Amount, decimals = 2) {
  return toNumber(n).toLocaleString("en-US", { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
}

/** 1234.5 → "LKR 1,234.50"; (12.5, "USD") → "USD 12.50" */
export const money = (n: Amount, currency: Currency = "LKR") => `${currency} ${num(n)}`;
export const lkr = (n: Amount) => money(n, "LKR");

/** An LKR amount in the currency picked in the top bar, at the day's rate. */
export function inCurrency(amountLkr: Amount, currency: Currency, rates: Rates) {
  if (currency === "LKR") return lkr(amountLkr);
  return money(round2(toNumber(amountLkr) / rates[currency]), currency);
}

/** The same amount in all three currencies: "LKR 450,000.00 · USD 1,500.00 · EUR 1,384.62" */
export const allCurrencies = (amountLkr: Amount, rates: Rates) =>
  CURRENCIES.map((c) => inCurrency(amountLkr, c, rates)).join(" · ");

const parts = (d: Date) => {
  const p = new Intl.DateTimeFormat("en-GB", {
    timeZone: SHOP_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(d);
  const get = (t: string) => p.find((x) => x.type === t)?.value ?? "";
  return { y: get("year"), m: get("month"), d: get("day"), h: get("hour"), min: get("minute") };
};

/** "2026-11-22" or an ISO time → "22/11/2026". A plain date is never moved by time zones. */
export function date(value: string | Date | null | undefined) {
  if (!value) return "—";
  if (typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [y, m, d] = value.split("-");
    return `${d}/${m}/${y}`;
  }
  const p = parts(new Date(value));
  return `${p.d}/${p.m}/${p.y}`;
}

/** "14:05" */
export function time(value: string | Date) {
  const p = parts(new Date(value));
  return `${p.h}:${p.min}`;
}

/** "22/11/2026 14:05" */
export const dateTime = (value: string | Date | null | undefined) => (value ? `${date(value)} ${time(value)}` : "—");

/** "just now", "5 min ago", "3 h ago", "2 days ago" */
export function ago(value: string | Date, now: Date = new Date()) {
  const m = Math.round((now.getTime() - new Date(value).getTime()) / 60000);
  if (m < 1) return "just now";
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} h ago`;
  const d = Math.round(h / 24);
  return `${d} day${d > 1 ? "s" : ""} ago`;
}

/** 1.5 → "1.50 ct" */
export const carat = (n: Amount) => `${num(n, 2)} ct`;

const MONTHS = [
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
/** "2026-11" → "November 2026" */
export function month(key: string) {
  const [y, m] = key.split("-");
  return `${MONTHS[Number(m) - 1]} ${y}`;
}

/** 3 → "3%" */
export const pct = (n: Amount) => `${num(n, 0)}%`;

/** Today in the shop as "yyyy-mm-dd". */
export function todayInShop(now: Date = new Date()) {
  const p = parts(now);
  return `${p.y}-${p.m}-${p.d}`;
}

/** Hour of the day in the shop (0–23). */
export const hourInShop = (now: Date = new Date()) => Number(parts(now).h);

/** "Ruwan Perera" → "RP" */
export const initials = (name: string) =>
  String(name || "?")
    .split(" ")
    .map((w) => w[0])
    .slice(0, 2)
    .join("")
    .toUpperCase();
