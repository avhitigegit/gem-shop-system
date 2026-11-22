import type { Metadata } from "next";
import { CorrectionsScreen } from "./corrections-screen";

export const metadata: Metadata = { title: "Fix a stock mistake" };

export default function Page() {
  return <CorrectionsScreen />;
}
