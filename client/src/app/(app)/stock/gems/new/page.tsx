import type { Metadata } from "next";
import { GemFormScreen } from "../gem-form";

export const metadata: Metadata = { title: "Add a gem" };

export default function Page() {
  return <GemFormScreen />;
}
