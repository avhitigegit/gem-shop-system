import type { Metadata } from "next";
import { Suspense } from "react";
import { BreakdownScreen } from "./breakdown-screen";

export const metadata: Metadata = { title: "Commission per sale" };

export default function Page() {
  return (
    <Suspense>
      <BreakdownScreen />
    </Suspense>
  );
}
