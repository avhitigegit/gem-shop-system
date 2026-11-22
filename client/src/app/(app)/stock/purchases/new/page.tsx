import type { Metadata } from "next";
import { BuyScreen } from "./buy-screen";

export const metadata: Metadata = { title: "Buy gems from a supplier" };

export default function Page() {
  return <BuyScreen />;
}
