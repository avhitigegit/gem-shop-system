import type { Metadata } from "next";
import { PeopleHome } from "./people-home";

export const metadata: Metadata = { title: "Customers & guides" };

/** Section home (prototype hub/people.html). */
export default function Page() {
  return <PeopleHome />;
}
