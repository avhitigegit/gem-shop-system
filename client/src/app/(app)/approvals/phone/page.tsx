import type { Metadata } from "next";
import { PhoneScreen } from "./phone-screen";

export const metadata: Metadata = { title: "Phone view" };

export default function Page() {
  return <PhoneScreen />;
}
