"use client";

/* Forgot password – prototype auth/forgot-password.html (steps 1 and 2).
   The reset link is sent from the shop's email (SMTP); it works for 30 minutes and only once.
   The answer is the same whether the email has a login or not. Step 3 is the /reset-password page. */
import { useState } from "react";
import { Field, Input } from "@/components/ui/form";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import { emailOk } from "@/lib/password";
import { AuthBox } from "../auth-parts";

export function ForgotScreen() {
  const toast = useToast();
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string>();
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const send = async (to: string) => {
    setBusy(true);
    const { error: e } = await api.POST("/api/auth/forgot-password", { body: { email: to } });
    setBusy(false);
    if (e) {
      toast("Email not sent", errorMessage(e), "error");
      return false;
    }
    return true;
  };

  if (sentTo)
    return (
      <AuthBox>
        <div className="center">
          <div style={{ fontSize: 42 }}>✉️</div>
          <h2 style={{ fontSize: 20 }}>Check your email</h2>
          <p className="muted">
            If <b>{sentTo}</b> has an account, a reset link was sent. The link works for 30 minutes and only once.
          </p>
        </div>
        <button
          className="btn btn-outline btn-block mt"
          disabled={busy}
          onClick={async () => (await send(sentTo)) && toast("Email sent again", sentTo, "success")}
        >
          Resend email
        </button>
      </AuthBox>
    );

  return (
    <AuthBox>
      <h2 style={{ fontSize: 20 }}>Forgot your password?</h2>
      <p className="muted">Enter your login email. We&apos;ll send a reset link from the shop&apos;s email.</p>
      <form
        noValidate
        className="stack mt"
        style={{ gap: 12 }}
        onSubmit={async (e) => {
          e.preventDefault();
          if (!email.trim() || !emailOk(email)) {
            setError("This field is required.");
            toast("Please complete the required fields", "Fields marked * are required.", "error");
            return;
          }
          if (await send(email.trim())) setSentTo(email.trim());
        }}
      >
        <Field label="Email" htmlFor="fp-em" required error={error}>
          <Input
            id="fp-em"
            type="email"
            autoComplete="username"
            placeholder="you@ceylongemhouse.lk"
            value={email}
            onChange={(e) => (setEmail(e.target.value), setError(undefined))}
          />
        </Field>
        <button className="btn btn-sapphire btn-block btn-lg" type="submit" disabled={busy}>
          Send reset link
        </button>
      </form>
    </AuthBox>
  );
}
