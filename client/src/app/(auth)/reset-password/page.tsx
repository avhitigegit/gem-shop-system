import type { Metadata } from "next";
import { Suspense } from "react";
import { ResetScreen } from "./reset-screen";

export const metadata: Metadata = { title: "Reset password" };

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={<div className="auth-main" style={{ minHeight: "100vh" }} aria-busy="true" />}>
      <ResetScreen />
    </Suspense>
  );
}
