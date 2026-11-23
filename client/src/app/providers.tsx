"use client";

import { useEffect, type ReactNode } from "react";
import { DialogProvider } from "@/components/ui/dialog";
import { ToastProvider } from "@/components/ui/toast";
import { registerServiceWorker } from "@/lib/push";
import { SessionProvider } from "@/lib/session";

export function Providers({ children }: { children: ReactNode }) {
  // The app's service worker: shows push alerts and opens the right screen on tap (P4-02).
  useEffect(() => {
    void registerServiceWorker();
  }, []);
  return (
    <SessionProvider>
      <ToastProvider>
        <DialogProvider>{children}</DialogProvider>
      </ToastProvider>
    </SessionProvider>
  );
}
