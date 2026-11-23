import type { Metadata } from "next";
import { ReturnsScreen } from "./returns-screen";

export const metadata: Metadata = { title: "Returns & exchanges" };

export default function Page() {
  return <ReturnsScreen />;
}
