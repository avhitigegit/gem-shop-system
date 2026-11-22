import type { Metadata } from "next";
import { SetupScreen } from "./setup-screen";

export const metadata: Metadata = { title: "Owner setup" };

export default function SetupPage() {
  return <SetupScreen />;
}
