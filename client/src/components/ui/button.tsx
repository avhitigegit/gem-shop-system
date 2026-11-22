import type { ComponentProps } from "react";
import { Slot } from "radix-ui";
import { cn } from "@/lib/utils";

/* Buttons – prototype .btn. Navy is the normal button; sapphire = main action; gold = New sale. */
export type ButtonVariant = "default" | "sapphire" | "gold" | "outline" | "ghost" | "destructive" | "success";

export type ButtonProps = ComponentProps<"button"> & {
  variant?: ButtonVariant;
  size?: "sm" | "lg";
  block?: boolean;
  /** Style a link (or any child) as a button. */
  asChild?: boolean;
};

export function buttonClass(
  variant: ButtonVariant = "default",
  size?: "sm" | "lg",
  block?: boolean,
  className?: string,
) {
  return cn("btn", variant !== "default" && `btn-${variant}`, size && `btn-${size}`, block && "btn-block", className);
}

export function Button({ variant = "default", size, block, asChild, className, type, ...props }: ButtonProps) {
  if (asChild) return <Slot.Root className={buttonClass(variant, size, block, className)} {...props} />;
  return <button type={type ?? "button"} className={buttonClass(variant, size, block, className)} {...props} />;
}
