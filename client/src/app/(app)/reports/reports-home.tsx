"use client";

/* Reports home – prototype hub/reports.html (hub.js reports()): the report cards, with today's sales on the Sales
   card. "Last month is not closed yet" comes with Close the month (P5-03). */
import { SectionHome } from "@/components/shell/section-home";
import type { Count } from "@/components/ui/card";
import { api } from "@/lib/api/client";
import { inCurrency } from "@/lib/format";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function ReportsHome() {
  const { branch, currency, rates } = useSession();
  const { data } = useLoad(() => api.GET("/api/reports/summary"), [branch]);
  const counts: Record<string, Count> = {};
  if (data) counts["/reports/sales"] = `${inCurrency(data.salesToday, currency, rates)} today`;
  return <SectionHome sectionKey="reports" counts={counts} />;
}
