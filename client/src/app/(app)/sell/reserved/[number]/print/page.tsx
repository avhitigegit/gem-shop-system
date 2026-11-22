import type { Metadata } from "next";
import { QuotePrintScreen } from "./quote-print-screen";

export async function generateMetadata(props: PageProps<"/sell/reserved/[number]/print">): Promise<Metadata> {
  const { number } = await props.params;
  return { title: decodeURIComponent(number) };
}

export default async function Page(props: PageProps<"/sell/reserved/[number]/print">) {
  const { number } = await props.params;
  return <QuotePrintScreen number={decodeURIComponent(number)} />;
}
