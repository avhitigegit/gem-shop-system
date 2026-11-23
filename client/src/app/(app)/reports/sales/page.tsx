import type { Metadata } from "next";
import { SalesReportScreen } from "./sales-report-screen";

export const metadata: Metadata = { title: "Sales report" };

export default function Page() {
  return <SalesReportScreen />;
}
