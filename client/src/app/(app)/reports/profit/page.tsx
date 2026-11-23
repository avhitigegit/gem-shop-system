import type { Metadata } from "next";
import { ProfitReportScreen } from "./profit-report-screen";

export const metadata: Metadata = { title: "Profit report" };

export default function Page() {
  return <ProfitReportScreen />;
}
