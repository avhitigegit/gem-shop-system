import type { Metadata } from "next";
import { ListsScreen } from "./lists-screen";

export const metadata: Metadata = { title: "Drop-down lists" };

export default function Page() {
  return <ListsScreen />;
}
