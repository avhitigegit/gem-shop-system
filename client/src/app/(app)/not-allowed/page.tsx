import type { Metadata } from "next";
import { AccessDenied } from "@/components/shell/access-denied";

export const metadata: Metadata = { title: "Not allowed" };

/** Shown when the backend says "not allowed" (403) for a screen, and when a role opens a page it cannot see. */
export default function NotAllowedPage() {
  return <AccessDenied />;
}
