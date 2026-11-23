import type { Metadata } from "next";
import { Suspense } from "react";
import { EmailOfferScreen } from "./email-offer-screen";

export const metadata: Metadata = { title: "Send an offer by email" };

export default function Page() {
  return (
    <Suspense>
      <EmailOfferScreen />
    </Suspense>
  );
}
