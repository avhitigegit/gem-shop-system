import type { Metadata } from "next";
import { TaxPackScreen } from "./tax-pack-screen";

export const metadata: Metadata = { title: "Tax pack" };

export default function Page() {
  return <TaxPackScreen />;
}
