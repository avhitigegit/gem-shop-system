import type { Metadata } from "next";
import { SlipScreen } from "./slip-screen";

export async function generateMetadata(props: PageProps<"/people/commissions/slip/[id]">): Promise<Metadata> {
  const { id } = await props.params;
  return { title: `Commission slip ${decodeURIComponent(id)}` };
}

export default async function Page(props: PageProps<"/people/commissions/slip/[id]">) {
  const { id } = await props.params;
  return <SlipScreen id={decodeURIComponent(id)} />;
}
