/* Suppliers, cash purchases and returns to suppliers (P2-03). */
import type { components } from "@/lib/api/schema";

export type SupplierRow = components["schemas"]["SupplierRow"];
export type SupplierForm = components["schemas"]["SupplierForm"];
export type SupplierView = components["schemas"]["SupplierView"];
export type PurchaseRow = components["schemas"]["PurchaseRow"];
export type PurchaseView = components["schemas"]["PurchaseView"];
export type PurchaseGem = components["schemas"]["PurchaseGem"];
export type PurchaseLineForm = components["schemas"]["PurchaseLineForm"];
export type ReturnRow = components["schemas"]["ReturnRow"];
export type ReturnableGem = components["schemas"]["ReturnableGem"];
export type RefundMethod = ReturnRow["refundMethod"];
export type IdType = SupplierRow["idType"];

export const ID_TYPES: { value: IdType; label: string }[] = [
  { value: "NIC", label: "NIC (individual)" },
  { value: "BR", label: "Business registration" },
];

export const REFUND_METHODS: { value: RefundMethod; label: string }[] = [
  { value: "CASH", label: "Cash" },
  { value: "REPLACEMENT", label: "Replacement gem" },
  { value: "CREDIT", label: "Credit with supplier" },
];
export const refundLabel = (m: RefundMethod) => REFUND_METHODS.find((x) => x.value === m)?.label ?? m;

export const RETURN_REASONS = [
  "Faulty / damaged",
  "Not as described",
  "Wrong item delivered",
  "Treatment not disclosed",
  "Other",
];

/** Prototype price suggestion when the cost is typed: selling ×1.6, minimum ×1.35, rounded to 1,000. */
export const suggestPrices = (cost: number) => ({
  price: Math.round((cost * 1.6) / 1000) * 1000,
  min: Math.round((cost * 1.35) / 1000) * 1000,
});
