"use client";

/* Dialogs – prototype .dialog: title, text, body, buttons bottom-right. Esc, × or a click outside closes it.
   - <Modal> for any dialog
   - const confirm = useConfirm();  if (await confirm({ title, text, yes: "Cancel invoice", danger: true })) …
   - const askReason = useReason(); const reason = await askReason({ title, desc });  (null = cancelled)
     A reason is required (at least 4 letters) – used for price below minimum, returns, cancelling, corrections. */
import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react";
import { Dialog } from "radix-ui";
import { Button } from "@/components/ui/button";
import { Field, Textarea } from "@/components/ui/form";
import { cn } from "@/lib/utils";

export function Modal({
  open,
  onOpenChange,
  title,
  desc,
  size,
  children,
  footer,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  desc?: ReactNode;
  size?: "sm" | "lg" | "xl";
  children?: ReactNode;
  /** Buttons. Leave empty for a dialog without a button row. */
  footer?: ReactNode;
}) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="dialog-overlay">
          <Dialog.Content className={cn("dialog", size)} {...(desc ? {} : { "aria-describedby": undefined })}>
            <div className="dialog-head">
              <div>
                <Dialog.Title asChild>
                  <h2>{title}</h2>
                </Dialog.Title>
                {desc ? (
                  <Dialog.Description asChild>
                    <p>{desc}</p>
                  </Dialog.Description>
                ) : null}
              </div>
              <Dialog.Close className="x-btn" aria-label="Close">
                ×
              </Dialog.Close>
            </div>
            {children != null && <div className="dialog-body">{children}</div>}
            {footer != null && <div className="dialog-foot">{footer}</div>}
          </Dialog.Content>
        </Dialog.Overlay>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

type ConfirmOptions = { title: string; text: ReactNode; yes?: string; danger?: boolean };
type ReasonOptions = {
  title: string;
  desc?: ReactNode;
  yes?: string;
  placeholder?: string;
  extra?: ReactNode;
  rule?: ReactNode;
};
type Open =
  | { kind: "confirm"; o: ConfirmOptions; resolve: (v: boolean) => void }
  | { kind: "reason"; o: ReasonOptions; resolve: (v: string | null) => void }
  | null;

const DialogContext = createContext<{
  confirm: (o: ConfirmOptions) => Promise<boolean>;
  reason: (o: ReasonOptions) => Promise<string | null>;
} | null>(null);

export const MIN_REASON = 4;

export function DialogProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState<Open>(null);
  const [text, setText] = useState("");
  const [error, setError] = useState(false);
  const done = useRef(false);

  const confirm = useCallback(
    (o: ConfirmOptions) =>
      new Promise<boolean>((resolve) => {
        done.current = false;
        setOpen({ kind: "confirm", o, resolve });
      }),
    [],
  );
  const reason = useCallback(
    (o: ReasonOptions) =>
      new Promise<string | null>((resolve) => {
        done.current = false;
        setText("");
        setError(false);
        setOpen({ kind: "reason", o, resolve });
      }),
    [],
  );

  const close = (result: boolean | string | null) => {
    if (!open || done.current) return;
    done.current = true;
    if (open.kind === "confirm") open.resolve(result === true);
    else open.resolve(typeof result === "string" ? result : null);
    setOpen(null);
  };

  const submitReason = () => {
    const v = text.trim();
    if (v.length < MIN_REASON) {
      setError(true);
      return;
    }
    close(v);
  };

  return (
    <DialogContext.Provider value={{ confirm, reason }}>
      {children}
      {open?.kind === "confirm" && (
        <Modal
          open
          onOpenChange={(o) => !o && close(false)}
          title={open.o.title}
          size="sm"
          footer={
            <>
              <Button variant="outline" onClick={() => close(false)}>
                Cancel
              </Button>
              <Button variant={open.o.danger ? "destructive" : "sapphire"} onClick={() => close(true)}>
                {open.o.yes ?? "Confirm"}
              </Button>
            </>
          }
        >
          <p>{open.o.text}</p>
        </Modal>
      )}
      {open?.kind === "reason" && (
        <Modal
          open
          onOpenChange={(o) => !o && close(null)}
          title={open.o.title}
          desc={open.o.desc}
          footer={
            <>
              <Button variant="outline" onClick={() => close(null)}>
                Cancel
              </Button>
              <Button variant="sapphire" onClick={submitReason}>
                {open.o.yes ?? "Send for owner approval"}
              </Button>
            </>
          }
        >
          {open.o.extra}
          <Field label="Reason" htmlFor="rsn" required error={error ? "A reason is required." : undefined}>
            <Textarea
              id="rsn"
              autoFocus
              value={text}
              placeholder={open.o.placeholder ?? "Explain why this is needed…"}
              onChange={(e) => {
                setText(e.target.value);
                setError(false);
              }}
            />
          </Field>
          {open.o.rule ? <div className="mt">{open.o.rule}</div> : null}
        </Modal>
      )}
    </DialogContext.Provider>
  );
}

function useDialogs() {
  const d = useContext(DialogContext);
  if (!d) throw new Error("Dialogs must be used inside <DialogProvider>");
  return d;
}
export const useConfirm = () => useDialogs().confirm;
export const useReason = () => useDialogs().reason;
