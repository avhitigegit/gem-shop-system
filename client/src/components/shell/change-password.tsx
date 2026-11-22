"use client";

/* Change password – from the user menu. The new password needs at least 8 characters with a number.
   Other devices are signed out; this one stays signed in. */
import { useState } from "react";
import { Modal } from "@/components/ui/dialog";
import { Field, Input } from "@/components/ui/form";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage, fieldErrors } from "@/lib/api/client";
import { passwordOk, PASSWORD_HINT } from "@/lib/password";

type Errors = Partial<Record<"current" | "pw" | "pw2", string>>;

export function ChangePasswordDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const toast = useToast();
  const [current, setCurrent] = useState("");
  const [pw, setPw] = useState("");
  const [pw2, setPw2] = useState("");
  const [errors, setErrors] = useState<Errors>({});
  const [busy, setBusy] = useState(false);

  const close = (o: boolean) => {
    if (!o) {
      setCurrent("");
      setPw("");
      setPw2("");
      setErrors({});
    }
    onOpenChange(o);
  };

  const save = async () => {
    const e: Errors = {};
    if (!current) e.current = "This field is required.";
    if (!pw) e.pw = "This field is required.";
    if (!pw2) e.pw2 = "This field is required.";
    if (Object.keys(e).length) {
      setErrors(e);
      toast("Please complete the required fields", "Fields marked * are required.", "error");
      return;
    }
    if (!passwordOk(pw)) {
      setErrors({ pw: PASSWORD_HINT });
      toast("Password too weak", PASSWORD_HINT, "error");
      return;
    }
    if (pw !== pw2) {
      setErrors({ pw2: "Passwords do not match." });
      toast("Passwords do not match", "", "error");
      return;
    }
    setBusy(true);
    const { error } = await api.POST("/api/auth/change-password", {
      body: { currentPassword: current, newPassword: pw },
    });
    setBusy(false);
    if (error) {
      const f = fieldErrors(error);
      setErrors({ current: f.currentPassword, pw: f.newPassword });
      toast("Password not changed", errorMessage(error), "error");
      return;
    }
    toast("Password changed", "Other devices were signed out.", "success");
    close(false);
  };

  return (
    <Modal
      open={open}
      onOpenChange={close}
      title="Change password"
      desc="Other devices where you are signed in will be signed out."
      size="sm"
      footer={
        <>
          <button type="button" className="btn btn-outline" onClick={() => close(false)}>
            Cancel
          </button>
          <button type="submit" form="change-pw" className="btn btn-sapphire" disabled={busy}>
            Save new password
          </button>
        </>
      }
    >
      <form
        id="change-pw"
        noValidate
        className="stack"
        style={{ gap: 12 }}
        onSubmit={(e) => {
          e.preventDefault();
          void save();
        }}
      >
        <Field label="Current password" htmlFor="cp-cur" required error={errors.current}>
          <Input
            id="cp-cur"
            type="password"
            autoComplete="current-password"
            value={current}
            onChange={(e) => (setCurrent(e.target.value), setErrors((x) => ({ ...x, current: undefined })))}
          />
        </Field>
        <Field label="New password" htmlFor="cp-new" required hint={PASSWORD_HINT} error={errors.pw}>
          <Input
            id="cp-new"
            type="password"
            autoComplete="new-password"
            value={pw}
            onChange={(e) => (setPw(e.target.value), setErrors((x) => ({ ...x, pw: undefined })))}
          />
        </Field>
        <Field label="Confirm new password" htmlFor="cp-new2" required error={errors.pw2}>
          <Input
            id="cp-new2"
            type="password"
            autoComplete="new-password"
            value={pw2}
            onChange={(e) => (setPw2(e.target.value), setErrors((x) => ({ ...x, pw2: undefined })))}
          />
        </Field>
      </form>
    </Modal>
  );
}
