import type { Metadata } from "next";
import { TransfersScreen } from "./transfers-screen";

export const metadata: Metadata = { title: "Send / receive between branches" };

export default function Page() {
  return <TransfersScreen />;
}
