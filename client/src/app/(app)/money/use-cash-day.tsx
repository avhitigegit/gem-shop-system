"use client";

/* Today's drawer for the branch chosen at the top (GET /api/cash/day), shared by Start the day, Pay out and Close the day.
   The owner on "All branches" with more than one trading branch is asked to choose one – each branch has its own drawer. */
import { Alert } from "@/components/ui/form";
import { api, errorMessage } from "@/lib/api/client";
import { NEED_BRANCH } from "@/lib/cash";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function useCashDay() {
  const { branch } = useSession();
  return useLoad(() => api.GET("/api/cash/day"), [branch]);
}

export function CashDayState({ error }: { error: unknown }) {
  if (!error) return <p className="muted">Loading…</p>;
  const code = error && typeof error === "object" && "code" in error ? error.code : null;
  return (
    <Alert tone={code === NEED_BRANCH ? "info" : "danger"}>
      {code === NEED_BRANCH
        ? "Choose the branch at the top first – each branch has its own cash drawer."
        : errorMessage(error)}
    </Alert>
  );
}
