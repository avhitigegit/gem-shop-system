import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/* Status pills – prototype .badge and the STATUS list (prototype/assets/js/app.js). */
export type BadgeTone = "default" | "navy" | "blue" | "gold" | "success" | "warning" | "danger" | "outline";

export function Badge({
  tone = "default",
  className,
  children,
}: {
  tone?: BadgeTone;
  className?: string;
  children: ReactNode;
}) {
  return <span className={cn("badge", `badge-${tone}`, className)}>{children}</span>;
}

export const STATUS: Record<string, [string, BadgeTone]> = {
  in_stock: ["In stock", "success"],
  reserved: ["Reserved", "gold"],
  sold: ["Sold", "navy"],
  returned: ["Returned", "blue"],
  in_transit: ["On the way", "warning"],
  written_off: ["Written off", "danger"],
  returned_supplier: ["Returned to supplier", "default"],
  on_hold: ["On hold (approval)", "warning"],
  not_ready: ["Not ready to sell", "default"],
  pending: ["Pending", "warning"],
  approved: ["Approved", "success"],
  rejected: ["Rejected", "danger"],
  completed: ["Completed", "success"],
  cancelled: ["Cancelled", "danger"],
  paid: ["Paid", "success"],
  unpaid: ["Not yet paid", "warning"],
  reversed: ["Reversed", "danger"],
  active: ["Active", "blue"],
  expired: ["Expired", "default"],
  converted: ["Converted to sale", "success"],
  sent: ["Sent", "success"],
  draft: ["Draft", "default"],
  received: ["Received", "success"],
  opening: ["Opening Jan 2027", "gold"],
  closed: ["Closed (locked)", "navy"],
  open: ["Open", "blue"],
};

/** A status from the backend (any case, e.g. IN_STOCK or in_stock) as a coloured pill. */
export function StatusPill({ status, label }: { status: string; label?: string }) {
  const s = STATUS[status.toLowerCase()] ?? [status, "default" as BadgeTone];
  return <Badge tone={s[1]}>{label ?? s[0]}</Badge>;
}

export function CertBadge({ type }: { type: "lab" | "shop" | "none" | null | undefined }) {
  if (type === "lab") return <Badge tone="blue">Lab certificate</Badge>;
  if (type === "shop") return <Badge tone="gold">Shop certificate</Badge>;
  return <Badge>No certificate</Badge>;
}
