import type { Metadata } from "next";
import { StartDayScreen } from "./start-day-screen";

export const metadata: Metadata = { title: "Start the day" };

export default function Page() {
  return <StartDayScreen />;
}
