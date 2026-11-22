import type { Metadata } from "next";
import { HomeScreen } from "../home-screen";

export const metadata: Metadata = { title: "Accountant home" };

/** The Accountant opens here after sign-in (prototype n-accountant/dashboard.html).
    Its numbers – month profit, tax pack, month closing – come with P5-03; until then the section tiles. */
export default function AccountantHomePage() {
  return <HomeScreen />;
}
