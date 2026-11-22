import type { Metadata } from "next";
import { RatesScreen } from "./rates-screen";

export const metadata: Metadata = { title: "Exchange rates" };

export default function Page() {
  return <RatesScreen />;
}
