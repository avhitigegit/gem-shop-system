import type { Metadata } from "next";
import { GemFormScreen } from "../../gem-form";

export const metadata: Metadata = { title: "Edit gem" };

export default async function Page(props: PageProps<"/stock/gems/[id]/edit">) {
  const { id } = await props.params;
  return <GemFormScreen code={decodeURIComponent(id)} />;
}
