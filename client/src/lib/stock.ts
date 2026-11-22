/* Stock: transfers, stock check, value, corrections (P2-04). */
import type { components } from "@/lib/api/schema";

export type StockGem = components["schemas"]["StockGem"];
export type TransferView = components["schemas"]["TransferView"];
export type CheckView = components["schemas"]["CheckView"];
export type ScanView = components["schemas"]["ScanView"];
export type StockValue = components["schemas"]["StockValue"];
export type ValueRow = components["schemas"]["ValueRow"];
export type CorrectionView = components["schemas"]["CorrectionView"];
export type CorrectionKind = CorrectionView["kind"];

export const CORRECTION_KINDS: { value: CorrectionKind; label: string }[] = [
  { value: "STATUS", label: "Status (lost / damaged / found)" },
  { value: "BRANCH", label: "Branch (wrong place)" },
  { value: "CARAT", label: "Carat weight (data error)" },
];

export const CORRECTION_STATUS: { value: string; label: string }[] = [
  { value: "WRITTEN_OFF", label: "Written off (lost / damaged)" },
  { value: "IN_STOCK", label: "In stock (found)" },
];

/** Badge tone and label for a correction's state. */
export const correctionState = (s: CorrectionView["status"]) =>
  s === "APPROVED"
    ? { tone: "success" as const, label: "Approved" }
    : s === "REJECTED"
      ? { tone: "danger" as const, label: "Rejected" }
      : { tone: "warning" as const, label: "Waiting for owner" };

/** Timeline colour per history action (prototype product-history.html). */
export const historyTone = (action: string) =>
  ({
    Added: "gold",
    Sold: "green",
    Returned: "red",
    "Stock correction": "red",
    "Returned to supplier": "red",
    Received: "green",
    Reserved: "gold",
    "On hold": "gold",
    "Correction requested": "gold",
  })[action] ?? "";
