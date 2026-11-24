"use client";

/* Two-step sign-in – owner only, from the user menu (P6-01; Security & Data Agreement step 4).
   Set up: scan the QR code with an authenticator app (Google / Microsoft Authenticator), type the first code,
   then write down the 8 backup codes (shown once). Switch off: needs the password. */
import { useEffect, useState } from "react";
import { Modal } from "@/components/ui/dialog";
import { Alert, Field, Input } from "@/components/ui/form";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { date } from "@/lib/format";

type Status = components["schemas"]["TwoStepStatus"];
type Setup = components["schemas"]["TwoStepSetup"];
type Step = "status" | "scan" | "codes";

export function TwoStepDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const toast = useToast();
  const [status, setStatus] = useState<Status | null>(null);
  const [step, setStep] = useState<Step>("status");
  const [setup, setSetup] = useState<Setup | null>(null);
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [backup, setBackup] = useState<string[]>([]);
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    let live = true;
    void api.GET("/api/auth/two-step").then(({ data }) => live && data && setStatus(data));
    return () => {
      live = false;
    };
  }, [open]);

  const close = (o: boolean) => {
    if (!o) {
      setStep("status");
      setSetup(null);
      setCode("");
      setPassword("");
      setBackup([]);
      setError(undefined);
      setStatus(null);
    }
    onOpenChange(o);
  };

  const start = async () => {
    setBusy(true);
    const { data, error: e } = await api.POST("/api/auth/two-step/start");
    setBusy(false);
    if (!data) return toast("Could not start", errorMessage(e), "error");
    setSetup(data);
    setStep("scan");
    setTimeout(() => document.getElementById("ts-code")?.focus(), 0);
  };

  const confirm = async () => {
    if (!/^\d{6}$/.test(code.replace(/\s/g, ""))) {
      setError("Type the 6 digits shown in the app.");
      return;
    }
    setBusy(true);
    const { data, error: e } = await api.POST("/api/auth/two-step/confirm", {
      body: { code: code.replace(/\s/g, "") },
    });
    setBusy(false);
    if (!data) {
      setError("That code is not right.");
      return toast("Wrong code", errorMessage(e), "error");
    }
    setBackup(data.codes);
    setStep("codes");
    toast("Two-step sign-in is on", "Your phone's code is now needed to sign in.", "success");
  };

  const off = async () => {
    if (!password) {
      setError("This field is required.");
      return;
    }
    setBusy(true);
    const { error: e } = await api.POST("/api/auth/two-step/off", { body: { password } });
    setBusy(false);
    if (e) {
      setError("The password is not right.");
      return toast("Not switched off", errorMessage(e), "error");
    }
    toast("Two-step sign-in is off", "", "success");
    close(false);
  };

  const copy = () => {
    void navigator.clipboard?.writeText(backup.join("\n")).then(() => toast("Backup codes copied", "", "success"));
  };

  const footer =
    step === "scan" ? (
      <>
        <button type="button" className="btn btn-outline" onClick={() => close(false)}>
          Cancel
        </button>
        <button type="submit" form="ts-form" className="btn btn-sapphire" disabled={busy}>
          Turn on
        </button>
      </>
    ) : step === "codes" ? (
      <button type="button" className="btn btn-sapphire" onClick={() => close(false)}>
        I have saved the codes
      </button>
    ) : status?.on ? (
      <>
        <button type="button" className="btn btn-outline" onClick={() => close(false)}>
          Close
        </button>
        <button type="submit" form="ts-form" className="btn btn-destructive" disabled={busy}>
          Switch off
        </button>
      </>
    ) : (
      <>
        <button type="button" className="btn btn-outline" onClick={() => close(false)}>
          Not now
        </button>
        <button type="button" className="btn btn-sapphire" disabled={busy || !status} onClick={() => void start()}>
          Set up
        </button>
      </>
    );

  return (
    <Modal
      open={open}
      onOpenChange={close}
      title="Two-step sign-in"
      desc="A code from your phone is needed after the password – a stolen password alone cannot open the shop."
      size="sm"
      footer={footer}
    >
      <form
        id="ts-form"
        noValidate
        className="stack"
        style={{ gap: 12 }}
        onSubmit={(e) => {
          e.preventDefault();
          void (step === "scan" ? confirm() : off());
        }}
      >
        {step === "status" && !status && <p className="muted">Loading…</p>}

        {step === "status" && status && !status.on && (
          <>
            <Alert tone="info">
              You need an authenticator app on your phone, e.g. <b>Google Authenticator</b> or{" "}
              <b>Microsoft Authenticator</b> (free).
            </Alert>
            <p className="muted" style={{ fontSize: 13 }}>
              Only the owner&apos;s login uses two-step sign-in. Staff logins stay as they are.
            </p>
          </>
        )}

        {step === "status" && status?.on && (
          <>
            <Alert tone="success">
              <b>On</b>
              {status.since && <> since {date(status.since)}</>} · {status.backupCodesLeft} backup codes left
            </Alert>
            {status.backupCodesLeft <= 2 && (
              <Alert tone="warning">Few backup codes left. Switch off and on again to get 8 new ones.</Alert>
            )}
            <Field label="Your password (to switch off)" htmlFor="ts-pw" required error={error}>
              <Input
                id="ts-pw"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => (setPassword(e.target.value), setError(undefined))}
              />
            </Field>
          </>
        )}

        {step === "scan" && setup && (
          <>
            <p style={{ fontSize: 13 }}>
              <b>1.</b> In the app tap <b>+</b> and scan this code.
            </p>
            {/* eslint-disable-next-line @next/next/no-img-element -- a data URL from the server, not a file */}
            <img
              src={setup.qr}
              alt="QR code for the authenticator app"
              width={180}
              height={180}
              style={{ margin: "0 auto" }}
            />
            <p className="muted center" style={{ fontSize: 12 }}>
              Cannot scan? Type this key:{" "}
              <code style={{ wordBreak: "break-all" }}>{setup.secret.replace(/(.{4})/g, "$1 ")}</code>
            </p>
            <Field label="2. The 6-digit code from the app" htmlFor="ts-code" required error={error}>
              <Input
                id="ts-code"
                autoComplete="one-time-code"
                inputMode="numeric"
                maxLength={7}
                placeholder="123 456"
                value={code}
                onChange={(e) => (setCode(e.target.value), setError(undefined))}
              />
            </Field>
          </>
        )}

        {step === "codes" && (
          <>
            <Alert tone="warning">
              <b>Write these backup codes down</b> and keep them safe (not on the phone). Each one signs you in once if
              the phone is lost. They are shown only now.
            </Alert>
            <div className="g2 grid" style={{ gap: 6, fontFamily: "monospace", fontSize: 15 }}>
              {backup.map((c) => (
                <div
                  key={c}
                  className="center"
                  style={{ padding: 6, border: "1px solid var(--border)", borderRadius: 6 }}
                >
                  {c}
                </div>
              ))}
            </div>
            <div className="row" style={{ gap: 8 }}>
              <button type="button" className="btn btn-outline btn-sm" onClick={copy}>
                Copy
              </button>
              <button type="button" className="btn btn-outline btn-sm" onClick={() => window.print()}>
                Print
              </button>
            </div>
          </>
        )}
      </form>
    </Modal>
  );
}
