"use client";

/* Settings home – prototype hub/settings.html: licence and exchange-rate reminders, counts on the cards. */
import { SectionHome, type Attention } from "@/components/shell/section-home";
import { api } from "@/lib/api/client";
import { date, num } from "@/lib/format";
import { useLoad } from "@/lib/use-load";

export function SettingsHome() {
  const { data: s } = useLoad(() => api.GET("/api/settings/summary"), []);
  const attention: Attention[] = [];
  const counts: Record<string, string | { text: string; tone?: "warning" }> = {};
  if (s) {
    const days = s.licenceDaysLeft;
    if (days != null && days <= s.licenceRemindDays)
      attention.push({
        tone: "warning",
        icon: "📜",
        text: (
          <>
            <b>Gem dealer licence {days < 0 ? `expired ${-days} days ago` : `expires in ${days} days`}</b> (
            {date(s.licenceExpiry)}).
          </>
        ),
        href: "/settings/shop",
        button: "Update",
      });
    if (!s.ratesUpdatedToday)
      attention.push({
        tone: "info",
        icon: "💱",
        text: <b>Exchange rates were not updated today.</b>,
        href: "/settings/rates",
        button: "Update rates",
      });
    counts["/settings/rates"] = s.usd != null && s.eur != null ? `USD ${num(s.usd)} · EUR ${num(s.eur)}` : "Not set";
    counts["/settings/users"] = `${s.staffLogins} logins`;
    counts["/settings/shop"] =
      days != null && days <= s.licenceRemindDays
        ? { text: days < 0 ? "Licence expired" : `Licence: ${days} days left`, tone: "warning" }
        : "Licence OK";
    counts["/settings/branches"] = `${s.branches} branches`;
    counts["/settings/commission"] = `Guides ${Number(s.defaultGuidePct)}%`;
    counts["/settings/lists"] = `${s.listValues} values`;
  }
  return <SectionHome sectionKey="settings" attention={attention} counts={counts} />;
}
