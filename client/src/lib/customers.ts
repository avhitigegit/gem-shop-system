/* Customers (P4-03, BRD J1, J2, J8): types and the birthday helpers (day + month only, "MM-DD"). */
import type { components } from "@/lib/api/schema";
import { MONTHS } from "@/lib/sales";

export type CustomerRow = components["schemas"]["CustomerRow"];
export type CustomerProfile = components["schemas"]["CustomerProfile"];
export type CustomerDetailsForm = components["schemas"]["CustomerDetailsForm"];

const MAX_DAY = [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

/** "04-17" → "17 Apr" (or "—"). */
export function birthdayShort(b: string | null | undefined) {
  if (!b) return "—";
  const [m, d] = b.split("-");
  return `${Number(d)} ${MONTHS[Number(m) - 1].slice(0, 3)}`;
}

/** "04-17" → "17 April". */
export function birthdayLong(b: string | null | undefined) {
  if (!b) return "—";
  const [m, d] = b.split("-");
  return `${Number(d)} ${MONTHS[Number(m) - 1]}`;
}

/** The two drop-downs → "MM-DD", "" (none) or null (only one chosen, or a day the month does not have). */
export function birthdayValue(day: string, month: string): string | null {
  if (!day && !month) return "";
  if (!day || !month || Number(day) > MAX_DAY[Number(month) - 1]) return null;
  return `${month}-${day}`;
}

/** "Birthdays this week": today and the next 6 days. */
export const birthdayThisWeek = (c: Pick<CustomerRow, "birthdayIn">) => c.birthdayIn != null && c.birthdayIn <= 6;

/** What the permission means right now, for the badge. */
export function promotionState(c: CustomerRow): "unsubscribed" | "consent" | "none" {
  return c.unsubscribed ? "unsubscribed" : c.consent ? "consent" : "none";
}
