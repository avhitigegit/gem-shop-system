import type { ComponentProps, ReactNode } from "react";
import Link from "next/link";
import { cn } from "@/lib/utils";

/* Cards, number tiles and section tiles – prototype .card, .kpi, .home-tile, .hub-card. */

export function Card({ className, ...props }: ComponentProps<"div">) {
  return <div className={cn("card", className)} {...props} />;
}

/** Card title row, with optional buttons on the right. */
export function CardHeader({
  title,
  actions,
  level = 2,
  className,
  children,
}: {
  title?: ReactNode;
  actions?: ReactNode;
  level?: 2 | 3;
  className?: string;
  children?: ReactNode;
}) {
  const H = level === 3 ? "h3" : "h2";
  return (
    <div className={cn("card-header", className)}>
      {title != null && <H>{title}</H>}
      {children}
      {actions != null && <div className="row">{actions}</div>}
    </div>
  );
}

export function CardContent({ className, ...props }: ComponentProps<"div">) {
  return <div className={cn("card-content", className)} {...props} />;
}

export function CardFooter({ className, ...props }: ComponentProps<"div">) {
  return <div className={cn("card-footer", className)} {...props} />;
}

/** Number tile: label, big value, small line under it. accent = gold line on top. */
export function Kpi({
  label,
  value,
  sub,
  accent,
  className,
}: {
  label: ReactNode;
  value: ReactNode;
  sub?: ReactNode;
  accent?: boolean;
  className?: string;
}) {
  return (
    <div className={cn("card kpi", accent && "accent", className)}>
      <div className="k-label">{label}</div>
      <div className="k-value">{value}</div>
      {sub != null && <div className="k-sub">{sub}</div>}
    </div>
  );
}

/** Home-page number card (prototype home: "Today's sales", "Cash in drawer"). */
export function BigNumber({ label, value, sub }: { label: ReactNode; value: ReactNode; sub?: ReactNode }) {
  return (
    <div className="card">
      <div className="card-content">
        <div className="muted" style={{ fontSize: 13 }}>
          {label}
        </div>
        <div className="big-num">{value}</div>
        {sub != null && <small className="muted">{sub}</small>}
      </div>
    </div>
  );
}

/** Big "do this" tile (home and section homes). The first one is navy. */
export function HomeTile({
  href,
  icon,
  title,
  sub,
  primary,
  badge,
}: {
  href: string;
  icon: ReactNode;
  title: ReactNode;
  sub?: ReactNode;
  primary?: boolean;
  badge?: ReactNode;
}) {
  return (
    <Link className={cn("home-tile", primary && "primary")} href={href}>
      {badge ? <span className="ht-badge">{badge}</span> : null}
      <span className="ht-i">{icon}</span>
      <b>{title}</b>
      {sub ? <small>{sub}</small> : null}
    </Link>
  );
}

export type Count = { text: string; tone?: "warning" } | string | null | undefined;

/** "Look up" card with a live count on the right. */
export function HubCard({
  href,
  icon,
  title,
  sub,
  count,
}: {
  href: string;
  icon: ReactNode;
  title: ReactNode;
  sub?: ReactNode;
  count?: Count;
}) {
  const c = typeof count === "string" ? { text: count } : count;
  return (
    <Link className="hub-card" href={href}>
      <span className="hc-i">{icon}</span>
      <span className="hc-t">
        <b>{title}</b>
        {sub ? <small>{sub}</small> : null}
      </span>
      {c?.text ? <span className={cn("hc-count", c.tone)}>{c.text}</span> : null}
      <span className="hc-go">›</span>
    </Link>
  );
}
