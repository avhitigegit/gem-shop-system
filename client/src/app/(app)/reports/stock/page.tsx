import type { Metadata } from "next";
import { StockReportScreen } from "./stock-report-screen";

export const metadata: Metadata = { title: "Stock report" };

export default function Page() {
  return <StockReportScreen />;
}
