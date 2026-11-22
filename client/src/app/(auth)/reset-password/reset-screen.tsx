"use client";

/* Choose a new password – prototype auth/forgot-password.html step 3, opened from the emailed link
   (/reset-password?token=…). The link works once; afterwards all devices are signed out. */
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { Alert, Field, Input } from "@/components/ui/form";
import { useToast } from "@/components/ui/toast";
import { api, errorCode, errorMessage, fieldErrors } from "@/lib/api/client";
import { passwordOk, PASSWORD_HINT } from "@/lib/password";
import { AuthBox } from "../auth-parts";

export function ResetScreen() {
  const token = useSearchParams().get("token") ?? "";
  const toast = useToast();
  const [pw, setPw] = useState("");
  const [pw2, setPw2] = useState("");
  const [errors, setErrors] = useState<{ pw?: string; pw2?: string }>({});
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [badLink, setBadLink] = useState(!token);

  if (done)
    return (
      <AuthBox>
        <div className="center">
          <div style={{ fontSize: 42 }}>✅</div>
          <h2 style={{ fontSize: 20 }}>Password changed</h2>
          <p className="muted">You can sign in with your new password. Other devices were signed out.</p>
          <Link className="btn btn-sapphire btn-block mt" href="/login">
            Sign in
          </Link>
        </div>
      </AuthBox>
    );

  if (badLink)
    return (
      <AuthBox>
        <h2 style={{ fontSize: 20 }}>Link not valid</h2>
        <Alert tone="warning" className="mt">
          This reset link has expired or was already used. A link works for 30 minutes and only once.
        </Alert>
        <Link className="btn btn-sapphire btn-block mt" href="/forgot-password">
          Send a new link
        </Link>
      </AuthBox>
    );

  const save = async () => {
    const e: typeof errors = {};
    if (!pw) e.pw = "This field is required.";
    if (!pw2) e.pw2 = "This field is required.";
    if (Object.keys(e).length) {
      setErrors(e);
      toast("Please complete the required fields", "Fields marked * are required.", "error");
      return;
    }
    if (!passwordOk(pw)) {
      setErrors({ pw: PASSWORD_HINT });
      toast("Password too weak", "", "error");
      return;
    }
    if (pw !== pw2) {
      setErrors({ pw2: "Passwords do not match." });
      toast("Passwords do not match", "", "error");
      return;
    }
    setBusy(true);
    const { error } = await api.POST("/api/auth/reset-password", { body: { token, newPassword: pw } });
    setBusy(false);
    if (!error) return setDone(true);
    if (errorCode(error) === "RESET_LINK_INVALID") return setBadLink(true);
    setErrors({ pw: fieldErrors(error).newPassword });
    toast("Password not changed", errorMessage(error), "error");
  };

  return (
    <AuthBox>
      <h2 style={{ fontSize: 20 }}>Choose a new password</h2>
      <form
        noValidate
        className="stack mt"
        style={{ gap: 12 }}
        onSubmit={(e) => {
          e.preventDefault();
          void save();
        }}
      >
        <Field label="New password" htmlFor="rp-pw" required hint={PASSWORD_HINT} error={errors.pw}>
          <Input
            id="rp-pw"
            type="password"
            autoComplete="new-password"
            value={pw}
            onChange={(e) => (setPw(e.target.value), setErrors((x) => ({ ...x, pw: undefined })))}
          />
        </Field>
        <Field label="Confirm new password" htmlFor="rp-pw2" required error={errors.pw2}>
          <Input
            id="rp-pw2"
            type="password"
            autoComplete="new-password"
            value={pw2}
            onChange={(e) => (setPw2(e.target.value), setErrors((x) => ({ ...x, pw2: undefined })))}
          />
        </Field>
        <button className="btn btn-sapphire btn-block btn-lg" type="submit" disabled={busy}>
          Save new password
        </button>
      </form>
    </AuthBox>
  );
}
