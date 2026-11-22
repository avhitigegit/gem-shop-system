import type { Metadata } from "next";
import { SectionHome } from "@/components/shell/section-home";

export const metadata: Metadata = { title: "Sell" };

/** Section home (prototype hub/sell.html). Attention items, counts and the side list come with the section's heading. */
export default function Page() {
  return <SectionHome sectionKey="sell" />;
}
