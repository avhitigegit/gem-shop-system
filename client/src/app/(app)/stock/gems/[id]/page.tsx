import type { Metadata } from "next";
import { GemScreen } from "./gem-screen";

export const metadata: Metadata = { title: "Gem details" };

export default async function Page(props: PageProps<"/stock/gems/[id]">) {
  const { id } = await props.params;
  return <GemScreen code={decodeURIComponent(id)} />;
}
