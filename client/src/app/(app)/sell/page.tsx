import type { Metadata } from "next";
import { SellHome } from "./sell-home";

export const metadata: Metadata = { title: "Sell" };

/** Section home (prototype hub/sell.html). */
export default function Page() {
  return <SellHome />;
}
