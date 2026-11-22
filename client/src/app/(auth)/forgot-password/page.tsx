import type { Metadata } from "next";
import { ForgotScreen } from "./forgot-screen";

export const metadata: Metadata = { title: "Reset password" };

export default function ForgotPasswordPage() {
  return <ForgotScreen />;
}
