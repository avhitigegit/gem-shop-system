import type { Metadata } from "next";
import { CustomersScreen } from "./customers-screen";

export const metadata: Metadata = { title: "Customers" };

export default function Page() {
  return <CustomersScreen />;
}
