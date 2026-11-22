import type { Metadata } from "next";
import { ShopScreen } from "./shop-screen";

export const metadata: Metadata = { title: "Shop details" };

export default function Page() {
  return <ShopScreen />;
}
