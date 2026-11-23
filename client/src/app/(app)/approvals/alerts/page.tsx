import type { Metadata } from "next";
import { AlertsScreen } from "./alerts-screen";

export const metadata: Metadata = { title: "Alerts" };

export default function Page() {
  return <AlertsScreen />;
}
