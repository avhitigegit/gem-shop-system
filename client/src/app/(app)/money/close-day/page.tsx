import type { Metadata } from "next";
import { CloseDayScreen } from "./close-day-screen";

export const metadata: Metadata = { title: "Close the day" };

export default function Page() {
  return <CloseDayScreen />;
}
