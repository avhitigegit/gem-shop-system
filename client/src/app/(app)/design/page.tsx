import type { Metadata } from "next";
import { DesignKit } from "./design-kit";

export const metadata: Metadata = { title: "Design kit" };

export default function DesignPage() {
  return <DesignKit />;
}
