import type { Metadata } from "next";
import { ValueScreen } from "./value-screen";

export const metadata: Metadata = { title: "Value by gem type" };

export default function Page() {
  return <ValueScreen />;
}
