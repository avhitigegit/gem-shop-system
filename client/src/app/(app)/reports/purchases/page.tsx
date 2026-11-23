import type { Metadata } from "next";
import { PurchaseReportScreen } from "./purchase-report-screen";

export const metadata: Metadata = { title: "Purchases by supplier" };

export default function Page() {
  return <PurchaseReportScreen />;
}
