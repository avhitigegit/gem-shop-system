import type { Metadata } from "next";
import { MessageSettingsScreen } from "./message-settings-screen";

export const metadata: Metadata = { title: "Email & WhatsApp settings" };

export default function Page() {
  return <MessageSettingsScreen />;
}
