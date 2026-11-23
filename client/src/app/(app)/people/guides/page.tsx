import type { Metadata } from "next";
import { GuidesScreen } from "./guides-screen";

export const metadata: Metadata = { title: "Guides & drivers" };

export default function Page() {
  return <GuidesScreen />;
}
