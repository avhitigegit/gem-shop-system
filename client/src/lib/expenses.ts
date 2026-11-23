/* Shop expenses (P5-01, BRD M1–M7): bank / card expenses with a bill photo, monthly bills, the expense report.
   Cash paid from the drawer is recorded on Daily cash → Pay out cash from the drawer. */
import type { components } from "@/lib/api/schema";

export type ExpenseRow = components["schemas"]["ExpenseRow"];
export type ExpenseList = components["schemas"]["ExpenseList"];
export type ExpenseDetail = components["schemas"]["ExpenseDetail"];
export type MonthlyBillRow = components["schemas"]["MonthlyBillRow"];
export type ExpenseReport = components["schemas"]["ExpenseReport"];

export const METHOD_LABEL: Record<string, string> = {
  DRAWER: "Cash (drawer)",
  OWNER_CASH: "Cash (owner)",
  BANK: "Bank transfer",
  CARD: "Card",
};

/** Methods on Add an expense and Monthly bills (the drawer is Pay out cash). */
export const PAID_BY = [
  { value: "BANK", label: "Bank transfer" },
  { value: "CARD", label: "Card" },
];

export const methodLabel = (m: string) => METHOD_LABEL[m] ?? m;

export const EXPENSE_STATUS = [
  { value: "APPROVED", label: "Approved" },
  { value: "PENDING", label: "Waiting for owner" },
  { value: "REJECTED", label: "Rejected" },
];
