"use client";

/* Reports (P5-02) – prototype l-reports/*. Types from the API, the default periods and the PDF / Excel download.
   The branch at the top filters every report (All branches = the whole shop). */
import { useToast } from "@/components/ui/toast";
import type { components } from "@/lib/api/schema";
import { downloadFile } from "@/lib/files";
import { todayInShop } from "@/lib/format";

type S = components["schemas"];
export type SalesReport = S["SalesReport"];
export type SalesRow = S["SalesRow"];
export type SalesBy = SalesReport["by"];
export type ProfitRow = S["ProfitRow"];
export type CashReportRow = S["CashReportRow"];
export type StockReportRow = S["StockReportRow"];
export type CommissionPersonRow = S["CommissionPersonRow"];
export type SupplierReportRow = S["SupplierReportRow"];

export const SALES_BY: { value: SalesBy; label: string }[] = [
  { value: "DAY", label: "Day" },
  { value: "MONTH", label: "Month" },
  { value: "BRANCH", label: "Branch" },
  { value: "GEM", label: "Gem type" },
  { value: "STAFF", label: "Salesperson" },
  { value: "GUIDE", label: "Guide / driver" },
  { value: "PAY", label: "Payment method" },
  { value: "COUNTRY", label: "Customer country" },
];

/** "YYYY-MM-DD", n days before today in Sri Lanka. */
export function daysAgo(n: number) {
  const d = new Date(todayInShop() + "T00:00:00Z");
  d.setUTCDate(d.getUTCDate() - n);
  return d.toISOString().slice(0, 10);
}

/** Query string without empty values. */
export function query(params: Record<string, string | number | boolean | undefined | null>) {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(params))
    if (v !== undefined && v !== null && v !== "" && v !== false) p.set(k, String(v));
  return p;
}

/** Export PDF / Excel of a report: the backend makes the file with the same filters. */
export function useReportDownload(report: string, name: string, params: Parameters<typeof query>[0]) {
  const toast = useToast();
  return async (format: "pdf" | "xlsx") => {
    const p = query({ ...params, format });
    const ok = await downloadFile(`/api/reports/${report}/export?${p}`, `${name}.${format}`);
    if (ok) toast(format === "pdf" ? "PDF ready" : "Excel export ready", `${name} downloaded.`, "success");
    else toast("Not downloaded", "Please try again.", "error");
  };
}
