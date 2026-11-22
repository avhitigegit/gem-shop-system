import { Suspense } from "react";
import { AppShell } from "@/components/shell/app-shell";

/** Every screen inside the app frame (menu, top bar, back bar). Login and print pages stay outside. */
export default function AppLayout({ children }: LayoutProps<"/">) {
  return (
    <Suspense fallback={<div className="app" aria-busy="true" />}>
      <AppShell>{children}</AppShell>
    </Suspense>
  );
}
