import type { Metadata } from "next";
import { ProfitLossScreen } from "./profit-loss-screen";

export const metadata: Metadata = { title: "Profit & Loss" };

export default function Page() {
  return <ProfitLossScreen />;
}
