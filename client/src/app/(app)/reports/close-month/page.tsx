import type { Metadata } from "next";
import { CloseMonthScreen } from "./close-month-screen";

export const metadata: Metadata = { title: "Close the month" };

export default function Page() {
  return <CloseMonthScreen />;
}
