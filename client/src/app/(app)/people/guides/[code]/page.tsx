import type { Metadata } from "next";
import { GuideScreen } from "./guide-screen";

export async function generateMetadata(props: PageProps<"/people/guides/[code]">): Promise<Metadata> {
  const { code } = await props.params;
  return { title: decodeURIComponent(code) };
}

export default async function Page(props: PageProps<"/people/guides/[code]">) {
  const { code } = await props.params;
  return <GuideScreen code={decodeURIComponent(code)} />;
}
