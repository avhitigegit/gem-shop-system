import type { Metadata } from "next";
import { PosScreen } from "./pos-screen";

export const metadata: Metadata = { title: "New sale" };

export default function Page() {
  return <PosScreen />;
}
