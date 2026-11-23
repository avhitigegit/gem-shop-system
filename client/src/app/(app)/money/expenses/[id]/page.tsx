import type { Metadata } from "next";
import { ExpenseScreen } from "./expense-screen";

export async function generateMetadata(props: PageProps<"/money/expenses/[id]">): Promise<Metadata> {
  const { id } = await props.params;
  return { title: `Expense ${decodeURIComponent(id)}` };
}

export default async function Page(props: PageProps<"/money/expenses/[id]">) {
  const { id } = await props.params;
  return <ExpenseScreen id={decodeURIComponent(id)} />;
}
