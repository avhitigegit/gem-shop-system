import type { Metadata } from "next";
import { CommissionScreen } from "./commission-screen";

export const metadata: Metadata = { title: "Commission rules" };

export default function Page() {
  return <CommissionScreen />;
}
