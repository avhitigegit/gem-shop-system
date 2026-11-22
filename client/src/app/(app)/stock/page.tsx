import type { Metadata } from "next";
import { StockHome } from "./stock-home";

export const metadata: Metadata = { title: "Gems & stock" };

/** Section home (prototype hub/stock.html). */
export default function Page() {
  return <StockHome />;
}
