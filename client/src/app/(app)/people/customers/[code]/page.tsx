import type { Metadata } from "next";
import { CustomerScreen } from "./customer-screen";

export async function generateMetadata(props: PageProps<"/people/customers/[code]">): Promise<Metadata> {
  const { code } = await props.params;
  return { title: decodeURIComponent(code) };
}

export default async function Page(props: PageProps<"/people/customers/[code]">) {
  const { code } = await props.params;
  return <CustomerScreen code={decodeURIComponent(code)} />;
}
