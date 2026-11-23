import type { Metadata } from "next";
import { CreditNoteScreen } from "./credit-note-screen";

export async function generateMetadata(props: PageProps<"/sell/credit-notes/[number]">): Promise<Metadata> {
  const { number } = await props.params;
  return { title: decodeURIComponent(number) };
}

export default async function Page(props: PageProps<"/sell/credit-notes/[number]">) {
  const { number } = await props.params;
  return <CreditNoteScreen number={decodeURIComponent(number)} />;
}
