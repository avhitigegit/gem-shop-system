import type { Metadata } from "next";
import { InvoiceScreen } from "./invoice-screen";

export async function generateMetadata(props: PageProps<"/sell/invoices/[number]">): Promise<Metadata> {
  const { number } = await props.params;
  return { title: `Invoice ${decodeURIComponent(number)}` };
}

export default async function Page(props: PageProps<"/sell/invoices/[number]">) {
  const { number } = await props.params;
  return <InvoiceScreen number={decodeURIComponent(number)} />;
}
