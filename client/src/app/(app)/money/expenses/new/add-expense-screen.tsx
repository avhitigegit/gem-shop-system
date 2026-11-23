"use client";

/* Add an expense – prototype m-expenses/add-expense.html (Guide 50). */
import { PageHead } from "@/components/ui/steps";
import { api } from "@/lib/api/client";
import { todayInShop } from "@/lib/format";
import { useLoad } from "@/lib/use-load";
import { ExpenseForm } from "../expense-form";

export function AddExpenseScreen() {
  const month = todayInShop().slice(0, 7);
  const { data } = useLoad(() => api.GET("/api/expenses", { params: { query: { month } } }), [month]);
  return (
    <>
      <PageHead title="Add an expense" sub="Record every shop cost with its bill photo – bank transfer or card." />
      {data ? (
        <ExpenseForm types={data.expenseTypes} limit={Number(data.largeExpenseLimit)} />
      ) : (
        <p className="muted">Loading…</p>
      )}
    </>
  );
}
