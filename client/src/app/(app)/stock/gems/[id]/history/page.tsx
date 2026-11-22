import type { Metadata } from "next";
import { HistoryScreen } from "./history-screen";

export const metadata: Metadata = { title: "Gem history" };

export default async function Page(props: PageProps<"/stock/gems/[id]/history">) {
  const { id } = await props.params;
  return <HistoryScreen code={decodeURIComponent(id)} />;
}
