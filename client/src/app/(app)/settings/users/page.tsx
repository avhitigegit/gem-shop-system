import type { Metadata } from "next";
import { StaffScreen } from "./staff-screen";

export const metadata: Metadata = { title: "Staff logins" };

export default function Page() {
  return <StaffScreen />;
}
