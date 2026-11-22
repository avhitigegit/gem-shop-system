"use client";

/* Scan box – prototype .scan-box. A USB scanner "types" the tag code very fast and presses Enter,
   so the box stays focused and sends the code on Enter. Typing a code by hand works the same way.
   Suggestions (e.g. matching gems) can be shown under the box with the `suggestions` slot. */
import { useEffect, useRef, useState, type ReactNode } from "react";

export function ScanBox({
  onScan,
  onChange,
  placeholder = "Scan a tag (USB scanner or phone camera)…",
  autoFocus = true,
  keepFocus,
  suggestions,
  id = "scan",
}: {
  /** Called with the code when Enter is pressed (scanners press Enter at the end). */
  onScan: (code: string) => void;
  /** Called while typing, for search suggestions. */
  onChange?: (text: string) => void;
  placeholder?: string;
  autoFocus?: boolean;
  /** Put the cursor back in the box after each scan (stock check, new sale). */
  keepFocus?: boolean;
  suggestions?: ReactNode;
  id?: string;
}) {
  const [value, setValue] = useState("");
  const ref = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (autoFocus) ref.current?.focus();
  }, [autoFocus]);

  return (
    <div className="scan-box">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#64748b" strokeWidth="2" aria-hidden="true">
        <path d="M3 7V5a2 2 0 0 1 2-2h2M17 3h2a2 2 0 0 1 2 2v2M21 17v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2M7 8v8M11 8v8M15 8v8" />
      </svg>
      <input
        ref={ref}
        id={id}
        className="input input-lg"
        placeholder={placeholder}
        aria-label="Scan or type a tag code"
        autoComplete="off"
        spellCheck={false}
        value={value}
        onChange={(e) => {
          setValue(e.target.value);
          onChange?.(e.target.value);
        }}
        onKeyDown={(e) => {
          if (e.key !== "Enter") return;
          e.preventDefault();
          const code = value.trim().toUpperCase();
          if (!code) return;
          onScan(code);
          setValue("");
          onChange?.("");
          if (keepFocus) ref.current?.focus();
        }}
      />
      {suggestions}
    </div>
  );
}
