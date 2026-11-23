import type { Metadata } from "next";
import { CreditNotesScreen } from "./credit-notes-screen";

export const metadata: Metadata = { title: "Credit notes" };

export default function Page() {
  return <CreditNotesScreen />;
}
