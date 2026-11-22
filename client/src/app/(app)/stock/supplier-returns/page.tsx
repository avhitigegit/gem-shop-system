import type { Metadata } from "next";
import { ReturnsScreen } from "./returns-screen";

export const metadata: Metadata = { title: "Return a gem to the supplier" };

export default function Page() {
  return <ReturnsScreen />;
}
