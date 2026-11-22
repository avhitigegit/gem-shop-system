import type { Metadata } from "next";
import { BranchesScreen } from "./branches-screen";

export const metadata: Metadata = { title: "Branches" };

export default function Page() {
  return <BranchesScreen />;
}
