import type { Metadata } from "next";
import { DailyCashReportScreen } from "./daily-cash-report-screen";

export const metadata: Metadata = { title: "Daily closing report" };

export default function Page() {
  return <DailyCashReportScreen />;
}
