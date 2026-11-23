"use client";

/* Customers & guides home – prototype hub/people.html (hub.js people()): guides / drivers waiting for commission
   (owner, accountant), the number of guides, and what is still to pay. Customer counts come with P4-01. */
import { SectionHome, type Attention } from "@/components/shell/section-home";
import type { Count } from "@/components/ui/card";
import { api } from "@/lib/api/client";
import { lkr } from "@/lib/format";
import { ACCOUNTANT, OWNER } from "@/lib/roles";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function PeopleHome() {
  const { role, branch } = useSession();
  const money = role === OWNER || role === ACCOUNTANT;
  const { data: guides } = useLoad(() => api.GET("/api/guides"), [branch]);
  const { data: pay } = useLoad(
    () => (money ? api.GET("/api/commissions") : Promise.resolve({ data: undefined, error: undefined })),
    [branch, money],
  );
  const attention: Attention[] = [];
  const counts: Record<string, Count> = {};
  if (guides) counts["/people/guides"] = `${guides.guides.length} guides`;
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
