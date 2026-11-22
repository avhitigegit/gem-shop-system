/* Gems (P2-01): types from the API, status and certificate labels, the certificate suggestion and the drop-down lists. */
import { api } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { useLoad } from "@/lib/use-load";

export type GemCard = components["schemas"]["GemCard"];
export type GemView = components["schemas"]["GemView"];
export type GemForm = components["schemas"]["GemForm"];
export type GemStatus = GemCard["status"];
export type CertType = GemCard["certType"];
export type ListName = components["schemas"]["ListValues"]["name"];

/** Status filter on All gems – "stock" = still in the shop (prototype "Available stock"). */
export const STATUS_FILTER: { value: string; label: string }[] = [
  { value: "stock", label: "Available stock" },
  { value: "all", label: "All statuses" },
  { value: "IN_STOCK", label: "In stock" },
  { value: "RESERVED", label: "Reserved" },
  { value: "SOLD", label: "Sold" },
  { value: "RETURNED", label: "Returned" },
  { value: "IN_TRANSIT", label: "On the way" },
  { value: "ON_HOLD", label: "On hold" },
  { value: "NOT_READY", label: "Not ready to sell" },
];

export const CERT_OPTIONS: { value: CertType; label: string }[] = [
  { value: "LAB", label: "Lab certificate" },
  { value: "SHOP", label: "Shop certificate" },
  { value: "NONE", label: "No certificate" },
];

export const certKey = (t: CertType | null | undefined) => (t ? (t.toLowerCase() as "lab" | "shop" | "none") : null);

/** Rule 6 / C9: sapphires (and padparadscha) normally have a lab certificate; other gems the shop certificate. */
export const suggestedCert = (gemType: string): CertType => (/sapphire|padparadscha/i.test(gemType) ? "LAB" : "SHOP");

/** 1 carat = 0.2 grams. */
export const grams = (carat: number) => (carat * 0.2).toFixed(3);

/** The drop-down lists (Settings → Drop-down lists) as name → values. */
export function useLists() {
  const { data } = useLoad(() => api.GET("/api/lists"), []);
  const get = (name: ListName) => data?.find((l) => l.name === name)?.values ?? [];
  return { ready: data !== undefined, get };
}
