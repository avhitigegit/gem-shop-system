import type { Metadata } from "next";
import { MoneyHome } from "./money-home";

export const metadata: Metadata = { title: "Cash & expenses" };

/** Section home (prototype hub/money.html). */
export default function Page() {
  return <MoneyHome />;
}
