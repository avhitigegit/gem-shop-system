/* Guides & drivers and commission (P3-05) – types from the API and small helpers. */
import type { components } from "@/lib/api/schema";

export type GuideCard = components["schemas"]["GuideCard"];
export type GuideForm = components["schemas"]["GuideForm"];
export type CommissionRow = components["schemas"]["CommissionRow"];
export type PayableShow = "OWED" | "PAID" | "REVERSED" | "ALL";
export type PayMethod = components["schemas"]["PayForm"]["method"];

export const VEHICLES = ["Tuk-tuk", "Van", "Taxi", "Car", "Other"] as const;

/** 🛺 / 🚐 / 🚕 before the vehicle (prototype guides.html). */
export const vehicleIcon = (v: string | null | undefined) =>
  ({ "Tuk-tuk": "🛺", Van: "🚐", Taxi: "🚕" })[v ?? ""] ?? "";

export const PAY_METHODS: { value: PayMethod; label: string; hint?: string }[] = [
  { value: "DRAWER", label: "Cash (drawer)", hint: "Included in today’s cash closing" },
  { value: "OWNER_CASH", label: "Cash (owner)" },
  { value: "BANK", label: "Bank transfer" },
  { value: "SALARY", label: "With salary" },
];

export const SHOW_LABEL: Record<PayableShow, string> = {
  OWED: "Not yet paid",
  PAID: "Paid",
  REVERSED: "Taken back",
  ALL: "All",
};

/** Status pill key for a line (STATUS in badge.tsx: unpaid / paid / reversed). */
export const lineStatus = (r: Pick<CommissionRow, "status">) => r.status.toLowerCase();

export const payeeLabel = (p: CommissionRow["payee"]) => (p === "GUIDE" ? "Guide/driver" : "Staff");
