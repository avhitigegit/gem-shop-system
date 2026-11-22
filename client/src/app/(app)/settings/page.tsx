import type { Metadata } from "next";
import { SettingsHome } from "./settings-home";

export const metadata: Metadata = { title: "Settings" };

/** Section home (prototype hub/settings.html). */
export default function Page() {
  return <SettingsHome />;
}
