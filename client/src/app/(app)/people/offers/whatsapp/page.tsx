import type { Metadata } from "next";
import { Suspense } from "react";
import { WhatsappOfferScreen } from "./whatsapp-offer-screen";

export const metadata: Metadata = { title: "Send an offer on WhatsApp" };

export default function Page() {
  return (
    <Suspense>
      <WhatsappOfferScreen />
    </Suspense>
  );
}
