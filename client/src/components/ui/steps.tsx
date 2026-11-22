import Link from "next/link";
import type { ReactNode } from "react";
import { FLOWS, pageInfo } from "@/lib/nav";
import type { Role } from "@/lib/roles";
import { cn } from "@/lib/utils";

/* Step bars.
   - FlowBar: work that happens in order across pages (Today's cash, New gems coming in, Month end).
     Done steps get a green ✓, the next step a blue ring, the page you are on is highlighted.
   - WizardSteps: the steps inside one page (new sale, return). */

export type StepStatus = { done?: boolean; note?: string };

export function FlowBar({
  flow,
  here,
  role,
  status = {},
  compact,
}: {
  flow: string;
  here?: string | null;
  role: Role;
  /** Live status per step page, e.g. { "/money/start-day": { done: true, note: "Done 08:12" } }. */
  status?: Record<string, StepStatus>;
  compact?: boolean;
}) {
  const f = FLOWS[flow];
  if (!f) return null;
  const steps = f.steps.filter((s) => {
    const r = pageInfo(s.href).roles;
    return !r || r.includes(role);
  });
  if (!steps.length) return null;
  const st = (href: string) => status[href] ?? {};
  const firstOpen = steps.findIndex((s) => !st(s.href).done && !s.optional);
  return (
    <nav className={cn("flowbar no-print", compact && "compact")} aria-label={f.title}>
      <span className="fb-title">{f.title}</span>
      <ol>
        {steps.map((s, i) => {
          const done = !!st(s.href).done;
          return (
            <li
              key={s.href}
              className={cn(
                done && "done",
                s.href === here && "here",
                !done && i === firstOpen && s.href !== here && "next",
              )}
              aria-current={s.href === here ? "step" : undefined}
            >
              <Link href={s.href}>
                <span className="fb-n">{done ? "✓" : i + 1}</span>
                <span className="fb-t">
                  <b>{s.label}</b>
                  <small>{st(s.href).note ?? s.note}</small>
                </span>
              </Link>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

/** Steps inside one page. Steps before `current` are done (✓) and can be clicked to go back. */
export function WizardSteps({
  steps,
  current,
  onStep,
}: {
  steps: ReactNode[];
  current: number;
  onStep?: (i: number) => void;
}) {
  return (
    <ol className="wiz-steps">
      {steps.map((label, i) => {
        const ok = i < current;
        return (
          <li
            key={i}
            className={cn(i === current && "on", ok && "ok")}
            onClick={ok && onStep ? () => onStep(i) : undefined}
            aria-current={i === current ? "step" : undefined}
          >
            <span className="fb-n">{ok ? "✓" : i + 1}</span>
            <span className="lbl">{label}</span>
          </li>
        );
      })}
    </ol>
  );
}

/** Page title row: title, a line under it, buttons on the right. */
export function PageHead({ title, sub, actions }: { title: ReactNode; sub?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="page-head">
      <div>
        <h1>{title}</h1>
        {sub != null && (
          <p className="muted" style={{ margin: "4px 0 0" }}>
            {sub}
          </p>
        )}
      </div>
      {actions != null && <div className="actions">{actions}</div>}
    </div>
  );
}
