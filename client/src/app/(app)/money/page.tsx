import type { Metadata } from "next";
import { SectionHome } from "@/components/shell/section-home";

export const metadata: Metadata = { title: "Cash & expenses" };

/** Section home (prototype hub/money.html). Attention items, counts and the side list come with the section's heading. */
export default function Page() {
  return <SectionHome sectionKey="money" />;
}
