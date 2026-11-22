import type { Metadata } from "next";
import { SupplierScreen } from "./supplier-screen";

export const metadata: Metadata = { title: "Supplier" };

export default async function Page(props: PageProps<"/stock/suppliers/[id]">) {
  const { id } = await props.params;
  return <SupplierScreen code={decodeURIComponent(id)} />;
}
