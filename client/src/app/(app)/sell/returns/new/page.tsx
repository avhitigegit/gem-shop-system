import type { Metadata } from "next";
import { NewReturnScreen } from "./new-return-screen";

export const metadata: Metadata = { title: "Customer brings a gem back" };

export default function Page() {
  return <NewReturnScreen />;
}
