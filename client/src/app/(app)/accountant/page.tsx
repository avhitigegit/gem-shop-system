import type { Metadata } from "next";
import { AccountantHome } from "./accountant-home";

export const metadata: Metadata = { title: "Accountant home" };

/** The Accountant opens here after sign-in (prototype n-accountant/dashboard.html). */
export default function AccountantHomePage() {
  return <AccountantHome />;
}
