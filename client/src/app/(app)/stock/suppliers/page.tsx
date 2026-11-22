import type { Metadata } from "next";
import { SuppliersScreen } from "./suppliers-screen";

export const metadata: Metadata = { title: "Suppliers" };

export default function Page() {
  return <SuppliersScreen />;
}
