"use client";

/* "Cancel…" on the Invoices list and "Request cancellation" on an invoice (BRD G6, rules 4 and 7): the person gives a
   reason, the owner approves or rejects. The invoice is never deleted – once approved it stays on record as CANCELLED. */
import { useReason } from "@/components/ui/dialog";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import { refreshWaiting } from "@/lib/approvals";

export function useCancelInvoice() {
  const askReason = useReason();
  const toast = useToast();
  return async (number: string) => {
    const reason = await askReason({
      title: `Request cancellation of ${number}`,
      desc: "The owner must approve. The invoice stays on record marked CANCELLED; the gems go back in stock and the commissions are reversed after the owner approves.",
      yes: "Send to the owner",
      placeholder: "e.g. Wrong customer selected – invoice to be re-issued",
    });
    if (!reason) return false;
    const { data, error } = await api.POST("/api/sales/{number}/cancel", {
      params: { path: { number } },
      body: { reason },
    });
    if (!data) {
      toast("Not sent", errorMessage(error), "error");
      return false;
    }
    refreshWaiting();
    toast("Cancellation sent for owner approval", `${number} · ${data.cancel?.number ?? ""}`, "warning");
    return true;
  };
}
