import type { Metadata } from "next";
import { MonthlyBillsScreen } from "./monthly-bills-screen";

export const metadata: Metadata = { title: "Monthly bills" };

export default function Page() {
  return <MonthlyBillsScreen />;
}
