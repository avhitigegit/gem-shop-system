import type { Metadata } from "next";
import { ReportsHome } from "./reports-home";

export const metadata: Metadata = { title: "Reports" };

/** Section home (prototype hub/reports.html). */
export default function Page() {
  return <ReportsHome />;
}
