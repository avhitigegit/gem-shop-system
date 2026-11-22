import type { Metadata } from "next";
import { CheckScreen } from "./check-screen";

export const metadata: Metadata = { title: "Stock check" };

export default function Page() {
  return <CheckScreen />;
}
