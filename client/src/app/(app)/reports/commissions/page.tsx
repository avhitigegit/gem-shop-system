import type { Metadata } from "next";
import { CommissionReportScreen } from "./commission-report-screen";

export const metadata: Metadata = { title: "Commission report" };

export default function Page() {
  return <CommissionReportScreen />;
}
