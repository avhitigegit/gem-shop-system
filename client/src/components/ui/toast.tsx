"use client";

/* Toasts – prototype .toast: a small box bottom-right for 3.8 seconds.
   const toast = useToast(); toast("Sale saved", "INV-00041 printed", "success"); */
import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react";

export type ToastType = "info" | "success" | "error" | "warning";
type Item = { id: number; title: string; message?: string; type: ToastType; leaving: boolean };
type ToastFn = (title: string, message?: string, type?: ToastType) => void;

const ToastContext = createContext<ToastFn | null>(null);
export const TOAST_MS = 3800;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<Item[]>([]);
  const next = useRef(1);

  const toast = useCallback<ToastFn>((title, message, type = "info") => {
    const id = next.current++;
    setItems((l) => [...l, { id, title, message, type, leaving: false }]);
    setTimeout(() => setItems((l) => l.map((t) => (t.id === id ? { ...t, leaving: true } : t))), TOAST_MS);
    setTimeout(() => setItems((l) => l.filter((t) => t.id !== id)), TOAST_MS + 300);
  }, []);

  return (
    <ToastContext.Provider value={toast}>
      {children}
      <div className="toaster" aria-live="polite">
        {items.map((t) => (
          <div
            key={t.id}
            className={`toast ${t.type === "info" ? "" : t.type}`}
            role={t.type === "error" ? "alert" : "status"}
            style={{ transition: "opacity .3s", opacity: t.leaving ? 0 : 1 }}
          >
            <b>{t.title}</b>
            {t.message ? <div className="muted">{t.message}</div> : null}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastFn {
  const t = useContext(ToastContext);
  if (!t) throw new Error("useToast must be used inside <ToastProvider>");
  return t;
}
