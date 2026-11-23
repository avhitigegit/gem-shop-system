import type { Metadata } from "next";
import { PayOutScreen } from "./pay-out-screen";

export const metadata: Metadata = { title: "Pay out cash from the drawer" };

export default function Page() {
  return <PayOutScreen />;
}
