import type { Metadata } from "next";
import { TagsScreen } from "./tags-screen";

export const metadata: Metadata = { title: "Print tags" };

export default function Page() {
  return <TagsScreen />;
}
