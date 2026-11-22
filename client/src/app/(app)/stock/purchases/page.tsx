import type { Metadata } from "next";
import { PurchasesScreen } from "./purchases-screen";

export const metadata: Metadata = { title: "Purchases" };

export default function Page() {
  return <PurchasesScreen />;
}
