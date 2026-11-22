import type { ComponentProps, ReactNode } from "react";
import { cn } from "@/lib/utils";

/* Forms – prototype .field: label (red * when required), the input, a hint, and an error message.
   An error makes the box red; typing in it is the page's job to clear. */

export function Field({
  label,
  htmlFor,
  required,
  hint,
  error,
  full,
  className,
  children,
}: {
  label?: ReactNode;
  htmlFor?: string;
  required?: boolean;
  hint?: ReactNode;
  /** Error message; shown under the box and turns it red. */
  error?: ReactNode;
  /** Takes the full width in a FormGrid. */
  full?: boolean;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div className={cn("field", error ? "invalid" : null, full && "full", className)}>
      {label != null && (
        <label htmlFor={htmlFor} className={required ? "req" : undefined}>
          {label}
        </label>
      )}
      {children}
      {hint != null && <span className="hint">{hint}</span>}
      {error ? (
        <span className="err" role="alert">
          {error}
        </span>
      ) : null}
    </div>
  );
}

export function Input({ className, size, ...props }: Omit<ComponentProps<"input">, "size"> & { size?: "lg" }) {
  return <input className={cn("input", size === "lg" && "input-lg", className)} {...props} />;
}

export function Select({ className, children, ...props }: ComponentProps<"select">) {
  return (
    <select className={className} {...props}>
      {children}
    </select>
  );
}

/** Options from a list: strings, or { value, label }. */
export function Options({
  items,
  placeholder,
}: {
  items: (string | { value: string; label: string })[];
  placeholder?: string;
}) {
  return (
    <>
      {placeholder != null && <option value="">{placeholder}</option>}
      {items.map((o) => {
        const v = typeof o === "string" ? o : o.value;
        const l = typeof o === "string" ? o : o.label;
        return (
          <option key={v} value={v}>
            {l}
          </option>
        );
      })}
    </>
  );
}

export function Textarea({ className, ...props }: ComponentProps<"textarea">) {
  return <textarea className={className} {...props} />;
}

/** Box with a fixed word in front, e.g. "LKR" | 450,000. */
export function InputGroup({ addon, children }: { addon: ReactNode; children: ReactNode }) {
  return (
    <div className="input-group">
      <span className="addon">{addon}</span>
      {children}
    </div>
  );
}

/** On / off switch. */
export function Switch({
  checked,
  onChange,
  label,
  disabled,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  label?: string;
  disabled?: boolean;
}) {
  return (
    <label className="switch" aria-label={label}>
      <input type="checkbox" checked={checked} disabled={disabled} onChange={(e) => onChange(e.target.checked)} />
      <span />
    </label>
  );
}

/** Tick box with its text. */
export function Check({
  checked,
  onChange,
  children,
  disabled,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  children: ReactNode;
  disabled?: boolean;
}) {
  return (
    <label className="check">
      <input type="checkbox" checked={checked} disabled={disabled} onChange={(e) => onChange(e.target.checked)} />
      <span>{children}</span>
    </label>
  );
}

/** Two columns on a computer, one on a phone. */
export function FormGrid({ className, ...props }: ComponentProps<"div">) {
  return <div className={cn("form-grid", className)} {...props} />;
}

/** Notice box: info (blue), warning (yellow), danger (red), success (green). */
export function Alert({
  tone = "info",
  icon,
  className,
  children,
}: {
  tone?: "info" | "warning" | "danger" | "success";
  icon?: ReactNode;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div className={cn("alert", tone, className)} role={tone === "danger" ? "alert" : undefined}>
      {icon != null && <span>{icon}</span>}
      <div>{children}</div>
    </div>
  );
}

/** Checks required values; returns an error message per field (empty = all good). */
export function requiredErrors<T extends Record<string, unknown>>(
  values: T,
  required: (keyof T)[],
  message = "This field is required.",
) {
  const errors: Partial<Record<keyof T, string>> = {};
  for (const k of required) {
    const v = values[k];
    if (v == null || (typeof v === "string" && !v.trim()) || v === false) errors[k] = message;
  }
  return errors;
}
