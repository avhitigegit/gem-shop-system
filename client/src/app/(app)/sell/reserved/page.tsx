import type { Metadata } from "next";
import { ReservedScreen } from "./reserved-screen";

export const metadata: Metadata = { title: "Reserved & quotes" };

export default function Page() {
  return <ReservedScreen />;
}
