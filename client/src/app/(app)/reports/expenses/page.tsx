import type { Metadata } from "next";
import { ExpenseReportScreen } from "./expense-report-screen";

export const metadata: Metadata = { title: "Expense report" };

export default function Page() {
  return <ExpenseReportScreen />;
}
