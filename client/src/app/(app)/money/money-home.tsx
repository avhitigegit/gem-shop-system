"use client";

/* Cash & expenses home – prototype hub/money.html (hub.js money()): the day not started, closing time, large expenses
   waiting for the owner, and days this week that did not match the expected cash. */
import { SectionHome, type Attention } from "@/components/shell/section-home";
import type { Count } from "@/components/ui/card";
import { api } from "@/lib/api/client";
import { hourInShop } from "@/lib/format";
import { ACCOUNTANT, OWNER, SALESPERSON } from "@/lib/roles";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function MoneyHome() {
  const { role, branch } = useSession();
  const { data } = useLoad(() => api.GET("/api/cash/summary"), [branch]);
  const attention: Attention[] = [];
  const counts: Record<string, Count> = {};
  const drawer = role === OWNER || role === SALESPERSON;
  if (data) {
    if (drawer && data.branch) {
      if (!data.opened && !data.closed)
        attention.push({
          tone: "info",
          icon: "☀️",
          text: <>The day is not started. Count the cash in the drawer first.</>,
          href: "/money/start-day",
          button: "Start the day",
        });
      else if (!data.closed && hourInShop() >= 17)
        attention.push({
          tone: "warning",
          icon: "🌙",
          text: <>Closing time – count the cash and close the day.</>,
          href: "/money/close-day",
          button: "Close the day",
        });
      counts["/money/start-day"] = data.opened ? "started ✓" : { text: "not started", tone: "warning" };
      counts["/money/close-day"] = data.closed ? "closed ✓" : null;
    }
    if ((role === OWNER || role === ACCOUNTANT) && data.expensesWaiting > 0)
      attention.push({
        tone: "warning",
        icon: "🧾",
        text: (
          <>
            <b>{data.expensesWaiting} large expense(s)</b> waiting for the owner.
          </>
        ),
        href: "/approvals",
        button: "Approvals",
      });
    if ((role === OWNER || role === ACCOUNTANT) && data.daysNotMatching > 0)
      attention.push({
        tone: "warning",
        icon: "⚖️",
        text: (
          <>
            <b>{data.daysNotMatching} day(s)</b> this week did not match the expected cash.
          </>
        ),
        href: "/reports/daily-cash",
        button: "See closings",
      });
  }
  return <SectionHome sectionKey="money" attention={attention} counts={counts} />;
}
