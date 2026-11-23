/* Daily cash (P3-06): the drawer from morning to evening – start the day, pay out cash, close the day.
   Expected cash = opening + cash sales (LKR after change given) − cash paid out of the drawer. */
import type { components } from "@/lib/api/schema";
import type { Rates } from "@/lib/format";

export type CashDay = components["schemas"]["CashDay"];
export type DrawerAmounts = components["schemas"]["DrawerAmounts"];
export type ClosingView = components["schemas"]["ClosingView"];

export const DRAWER = ["lkr", "usd", "eur"] as const;
export type DrawerKey = (typeof DRAWER)[number];
export const DRAWER_LABEL: Record<DrawerKey, string> = { lkr: "LKR", usd: "USD", eur: "EUR" };

/** A difference smaller than this (in LKR) counts as balanced; from this on a note is needed. */
export const TOLERANCE = 0.5;

/** LKR value of an amount in a drawer currency at today's rates. */
export const toLkr = (key: DrawerKey, v: number, rates: Rates) =>
  key === "lkr" ? v : key === "usd" ? v * rates.USD : v * rates.EUR;

/** Cash in the drawer, all in LKR. */
export const drawerLkr = (a: Record<DrawerKey, number>, rates: Rates) =>
  DRAWER.reduce((t, k) => t + toLkr(k, a[k], rates), 0);

/** "Choose the branch at the top" – the owner on All branches with more than one trading branch. */
export const NEED_BRANCH = "CHOOSE_BRANCH";
