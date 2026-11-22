"use client";

/* Section home – prototype hub.js: "needs attention" list, big "What do you want to do?" tiles,
   "Look up" cards with live counts, and a side list (latest invoices, newest gems).
   Each heading that builds a section passes its attention items, counts and side list. */
import Link from "next/link";
import type { ReactNode } from "react";
import { type Count, HomeTile, HubCard } from "@/components/ui/card";
import { SECTIONS, visibleItems, type NavGroup } from "@/lib/nav";
import { ALL_BRANCHES, useSession } from "@/lib/session";
import { cn } from "@/lib/utils";

export type Attention = {
  tone: "warning" | "success" | "info";
  icon: string;
  text: ReactNode;
  href?: string;
  button?: string;
};

export function SectionHome({
  sectionKey,
  attention = [],
  counts = {},
  side,
}: {
  sectionKey: string;
  attention?: Attention[];
  counts?: Record<string, Count>;
  side?: ReactNode;
}) {
  const { role, branch, branchName } = useSession();
  const sec = SECTIONS.find((s) => s.key === sectionKey);
  if (!sec) return null;
  const onPage = new Set(sec.groups.flatMap((g) => visibleItems(g.items, role).map((i) => i.href)));

  const group = (g: NavGroup) => {
    const items = visibleItems(g.items, role);
    if (!items.length) return null;
    if (g.show === "tiles")
      return (
        <div key={g.name}>
          <h2 className="hub-h">{g.name}</h2>
          <div className="home-tiles hub-tiles">
            {items.map((it, i) => (
              <HomeTile key={it.href} href={it.href} icon={it.icon} title={it.label} sub={it.desc} primary={i === 0} />
            ))}
          </div>
        </div>
      );
    if (g.show === "cards")
      return (
        <div key={g.name}>
          <h2 className="hub-h">{g.name}</h2>
          <div className="hub-cards">
            {items.map((it) => (
              <HubCard
                key={it.href}
                href={it.href}
                icon={it.icon}
                title={it.label}
                sub={it.desc}
                count={counts[it.href]}
              />
            ))}
          </div>
        </div>
      );
    return (
      <details key={g.name} className="hub-more">
        <summary>
          {g.name} <small className="muted">({items.length})</small>
        </summary>
        <div className="hub-links">
          {items.map((it) => (
            <Link key={it.href} href={it.href}>
              <span>{it.icon}</span>
              {it.label}
              {it.desc ? <small className="muted"> – {it.desc}</small> : null}
            </Link>
          ))}
        </div>
      </details>
    );
  };

  return (
    <>
      <div className="hub-head">
        <span className="hh-i">{sec.icon}</span>
        <div>
          <h1>{sec.title}</h1>
          <p className="muted">
            {sec.intro}
            {branch !== ALL_BRANCHES ? " · " + branchName(branch) : ""}
          </p>
        </div>
      </div>
      {attention.length ? (
        <div className="att-list mb">
          {attention.map((a, i) => (
            <div key={i} className={cn("att", a.tone)}>
              <span className="att-i">{a.icon}</span>
              <span className="att-t">{a.text}</span>
              {a.href && !onPage.has(a.href) ? (
                <Link className={cn("btn btn-sm", a.tone === "success" ? "btn-success" : "btn-outline")} href={a.href}>
                  {a.button ?? "Open"}
                </Link>
              ) : null}
            </div>
          ))}
        </div>
      ) : (
        <div className="att-ok mb">✓ Nothing needs your attention here right now.</div>
      )}
      <div className={side ? "hub-grid" : undefined}>
        <div>{sec.groups.map(group)}</div>
        {side ? <div className="hub-side">{side}</div> : null}
      </div>
    </>
  );
}
