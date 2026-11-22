import type { Metadata } from "next";
import { GemsScreen } from "./gems-screen";

export const metadata: Metadata = { title: "All gems" };

export default function Page() {
  return <GemsScreen />;
}
