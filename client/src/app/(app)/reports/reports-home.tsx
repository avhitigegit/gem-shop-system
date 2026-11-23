"use client";

/* Reports home – prototype hub/reports.html (hub.js reports()): the report cards, with today's sales on the Sales
   card, and "last month is not closed yet" for the owner and the accountant. */
import { SectionHome, type Attention } from "@/components/shell/section-home";
import type { Count } from "@/components/ui/card";
import { api } from "@/lib/api/client";
import { inCurrency, month } from "@/lib/format";
import { ACCOUNTANT, OWNER } from "@/lib/roles";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function ReportsHome() {
  const { branch, currency, rates, role } = useSession();
  const monthEnd = role === OWNER || role === ACCOUNTANT;
  const { data } = useLoad(() => api.GET("/api/reports/summary"), [branch]);
  const { data: home } = useLoad(
    () => (monthEnd ? api.GET("/api/accountant/home") : Promise.resolve({ data: undefined, error: undefined })),
    [branch, monthEnd],
  );
  const counts: Record<string, Count> = {};
  const attention: Attention[] = [];
  if (data) counts["/reports/sales"] = `${inCurrency(data.salesToday, currency, rates)} today`;
  if (home) {
    const last = home.toClose;
    counts["/reports/close-month"] = last.closed
      ? `${month(last.month)} closed ✓`
      : { text: "last month open", tone: "warning" };
    if (!last.closed && !last.reopenWaiting)
      attention.push({
        tone: "warning",
        icon: "🔒",
        text: (
          <>
            <b>{month(last.month)}</b> is not closed yet.
          </>
        ),
        href: "/reports/close-month",
        button: "Close the month",
      });
  }
  return <SectionHome sectionKey="reports" attention={attention} counts={counts} />;
}
