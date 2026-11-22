"use client";

import type { ReactNode } from "react";
import { DialogProvider } from "@/components/ui/dialog";
import { ToastProvider } from "@/components/ui/toast";
import { SessionProvider } from "@/lib/session";

export function Providers({ children }: { children: ReactNode }) {
  return (
    <SessionProvider>
      <ToastProvider>
        <DialogProvider>{children}</DialogProvider>
      </ToastProvider>
    </SessionProvider>
  );
}
