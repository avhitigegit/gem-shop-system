"use client";

/* Customers & guides home – prototype hub/people.html (hub.js people()): guides / drivers waiting for commission
   (owner, accountant), the number of customers (birthdays today) and guides, and what is still to pay. */
import { SectionHome, type Attention } from "@/components/shell/section-home";
import type { Count } from "@/components/ui/card";
import { api } from "@/lib/api/client";
import { lkr } from "@/lib/format";
import { ACCOUNTANT, OWNER, SALESPERSON } from "@/lib/roles";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function PeopleHome() {
  const { role, branch } = useSession();
  const money = role === OWNER || role === ACCOUNTANT;
  const sees = role === OWNER || role === SALESPERSON;
  const { data: guides } = useLoad(() => api.GET("/api/guides"), [branch]);
  const { data: customers } = useLoad(
    () => (sees ? api.GET("/api/customers") : Promise.resolve({ data: undefined, error: undefined })),
    [branch, sees],
  );
  const { data: pay } = useLoad(
    () => (money ? api.GET("/api/commissions") : Promise.resolve({ data: undefined, error: undefined })),
    [branch, money],
  );
  const attention: Attention[] = [];
  const counts: Record<string, Count> = {};
  if (guides) counts["/people/guides"] = `${guides.guides.length} guides`;
  if (customers) {
    const today = customers.customers.filter((c) => c.birthdayIn === 0).length;
    counts["/people/customers"] = `${customers.customers.length} customers${today ? ` · 🎂 ${today} today` : ""}`;
  }
  if (pay) {
    const guideOwed = Number(pay.guidesOwed);
    counts["/people/commissions/pay"] =
      guideOwed > 0 ? { text: `${lkr(guideOwed)} to pay`, tone: "warning" } : "all paid";
    if (pay.guidesPeople > 0 && guideOwed > 0)
      attention.push({
        tone: "warning",
        icon: "🚐",
        text: (
          <>
            <b>{pay.guidesPeople} guide(s) / driver(s) are waiting for commission</b> – {lkr(guideOwed)} in total.
          </>
        ),
        href: "/people/commissions/pay",
        button: "Pay now",
      });
  }
  return <SectionHome sectionKey="people" attention={attention} counts={counts} />;
}
