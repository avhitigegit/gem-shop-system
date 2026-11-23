"use client";

/* Month end with the accountant (P5-03) – prototype n-accountant/*. Types from the API and the close / re-open actions.
   Closing locks everything dated in the month (rule 11); the owner re-opens it with a reason, the accountant asks the
   owner (approval). */
import { useConfirm, useReason } from "@/components/ui/dialog";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import { refreshWaiting } from "@/lib/approvals";
import { month } from "@/lib/format";
import { OWNER } from "@/lib/roles";
import { useSession } from "@/lib/session";

import type { components } from "@/lib/api/schema";

type S = components["schemas"];
export type PlMonth = S["PlMonth"];
export type PlExpenseType = S["PlExpenseType"];
export type PlBranch = S["PlBranch"];
export type MonthRow = S["MonthRow"];

/** The checks shown before closing a month (they warn, they do not stop the closing). */
export function monthChecks(m: MonthRow): { ok: boolean; text: string }[] {
  return [
    {
      ok: m.billsMissing === 0,
      text: m.billsMissing ? `${m.billsMissing} expense(s) without a bill photo` : "All expenses have bill photos",
    },
    {
      ok: m.expensesWaiting === 0,
      text: m.expensesWaiting
        ? `${m.expensesWaiting} expense(s) waiting for the owner`
        : "No expenses waiting for approval",
    },
    {
      ok: m.cancellationsWaiting === 0,
      text: m.cancellationsWaiting
        ? `${m.cancellationsWaiting} invoice cancellation(s) waiting`
        : "No invoice cancellations waiting",
    },
    { ok: m.ended, text: m.ended ? "Month has ended" : "Month has not ended yet" },
  ];
}

/** Close / re-open a month. Returns true when something changed (reload the page data). */
export function useMonthActions() {
  const { role } = useSession();
  const confirm = useConfirm();
  const askReason = useReason();
  const toast = useToast();

  const close = async (m: MonthRow) => {
    const ok = await confirm({
      title: `Close ${month(m.month)}?`,
      text: "Nothing dated in this month can be added or changed afterwards without the owner's approval. Make sure the month has been checked.",
      yes: "Close month",
    });
    if (!ok) return false;
    const { data, error } = await api.POST("/api/months/{month}/close", { params: { path: { month: m.month } } });
    if (!data) {
      toast("Not closed", errorMessage(error), "error");
      return false;
    }
    toast(`${month(m.month)} closed`, "The month is now locked.", "success");
    return true;
  };

  const reopen = async (m: MonthRow) => {
    const owner = role === OWNER;
    const reason = await askReason(
      owner
        ? {
            title: `Re-open ${month(m.month)}`,
            desc: "The owner can re-open a closed month. The reason is kept in the activity log.",
            yes: "Re-open",
            placeholder: "e.g. Supplier invoice arrived late",
          }
        : {
            title: `Ask to re-open ${month(m.month)}`,
            desc: "The owner must approve before anything in a closed month can change.",
            yes: "Send to the owner",
            placeholder: "e.g. Electricity bill for the month was missing",
          },
    );
    if (!reason) return false;
    const { data, error } = await api.POST("/api/months/{month}/reopen", {
      params: { path: { month: m.month } },
      body: { reason },
    });
    if (!data) {
      toast("Not done", errorMessage(error), "error");
      return false;
    }
    if (owner) toast("Month re-opened", month(m.month), "warning");
    else {
      refreshWaiting();
      toast("Sent for owner approval", `${month(m.month)} · ${data.reopenWaiting ?? ""}`, "warning");
    }
    return true;
  };

  return { close, reopen, isOwner: role === OWNER };
}
