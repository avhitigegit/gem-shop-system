import type { Metadata } from "next";
import { Suspense } from "react";
import { OffersScreen } from "./offers-screen";

export const metadata: Metadata = { title: "Offer results" };

export default function Page() {
  return (
    <Suspense>
      <OffersScreen />
    </Suspense>
  );
}
