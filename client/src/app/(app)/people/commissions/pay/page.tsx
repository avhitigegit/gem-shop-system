import type { Metadata } from "next";
import { Suspense } from "react";
import { PayScreen } from "./pay-screen";

export const metadata: Metadata = { title: "Pay guides & drivers" };

export default function Page() {
  return (
    <Suspense>
      <PayScreen />
    </Suspense>
  );
}
