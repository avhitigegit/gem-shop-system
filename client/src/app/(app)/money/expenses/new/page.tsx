import type { Metadata } from "next";
import { AddExpenseScreen } from "./add-expense-screen";

export const metadata: Metadata = { title: "Add an expense" };

export default function Page() {
  return <AddExpenseScreen />;
}
