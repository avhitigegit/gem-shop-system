"use client";

/* Sign in – prototype auth/login.html. Each person has their own login and role.
   Wrong password 5 times locks the login for 15 minutes.
   Owner with two-step sign-in on (P6-01): after the right password the screen asks for the 6-digit code from the
   authenticator app (or a backup code) and sends everything again. "Keep me signed in" is for the person's own device;
   on shared shop computers 15 minutes without use signs out.
   The "Demo: sign in as…" buttons only show on your own computer / dev (the backend says if they exist). */
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { GemSvg } from "@/components/ui/gem-picture";
import { Alert, Check, Field, Input } from "@/components/ui/form";
import { useToast } from "@/components/ui/toast";
import { api, errorCode, errorMessage } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import type { SessionData } from "@/lib/auth";
import { initials } from "@/lib/format";
import { emailOk } from "@/lib/password";
import { ROLE_LABEL } from "@/lib/roles";
import { homeHref, useAuth } from "@/lib/session";
import { cn } from "@/lib/utils";
import { AuthSide, safeNext } from "../auth-parts";

type DemoUser = components["schemas"]["DemoUser"];
const GEMS = ["Blue Sapphire", "Padparadscha", "Ruby", "Moonstone", "Cat's Eye"];

export function LoginScreen() {
  const auth = useAuth();
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));
  const toast = useToast();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(true);
  const [errors, setErrors] = useState<{ email?: string; password?: string; code?: string }>({});
  const [codeStep, setCodeStep] = useState(false);
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [shopName, setShopName] = useState<string | null>(null);
  const [setupNeeded, setSetupNeeded] = useState(false);
  const [demo, setDemo] = useState<DemoUser[]>([]);

  useEffect(() => {
    api.GET("/api/setup/status").then(({ data }) => {
      if (!data) return;
      setShopName(data.shopName ?? null);
      setSetupNeeded(data.needed);
    });
    api.GET("/api/auth/demo-users").then(({ data }) => data && setDemo(data));
  }, []);

  // Already signed in (e.g. the back button) → straight into the app. Not after signing in here: go() moves on itself.
  const signingIn = useRef(false);
  useEffect(() => {
    if (auth.status === "signedIn" && !signingIn.current) router.replace(next ?? "/");
  }, [auth.status, next, router]);

  const go = (s: SessionData) => {
    signingIn.current = true;
    auth.signIn(s);
    const u = s.me.user;
    toast("Welcome, " + u.name.split(" ")[0], ROLE_LABEL[u.role], "success");
    router.replace(next ?? homeHref(u.role));
  };

  const failed = (error: unknown) => {
    const code = errorCode(error);
    if (code === "TWO_STEP_CODE_NEEDED") {
      setCodeStep(true);
      setTimeout(() => document.getElementById("otp")?.focus(), 0);
    } else if (code === "TWO_STEP_WRONG_CODE") {
      setErrors((x) => ({ ...x, code: "That code is not right." }));
      toast("Wrong code", errorMessage(error), "error");
    } else if (code === "BAD_LOGIN") toast("Wrong email or password", "", "error");
    else if (code === "ACCOUNT_DISABLED") toast("Account disabled", "Ask the owner to activate it.", "error");
    else if (code === "ACCOUNT_LOCKED") toast("Login locked", errorMessage(error), "error");
    else toast("Could not sign in", errorMessage(error), "error");
  };

  const submit = async () => {
    const e: typeof errors = {};
    if (!email.trim() || !emailOk(email)) e.email = "This field is required.";
    if (!password) e.password = "This field is required.";
    if (codeStep && !code.trim()) e.code = "Enter the 6-digit code.";
    setErrors(e);
    if (Object.keys(e).length) {
      toast("Please complete the required fields", "Fields marked * are required.", "error");
      (document.getElementById(e.email ? "em" : e.password ? "pw" : "otp") as HTMLInputElement | null)?.focus();
      return;
    }
    setBusy(true);
    const { data, error } = await api.POST("/api/auth/login", {
      body: { email: email.trim(), password, remember, ...(codeStep ? { code: code.trim() } : {}) },
    });
    if (data) go(data);
    else failed(error);
    setBusy(false);
  };

  const demoLogin = async (u: DemoUser) => {
    setBusy(true);
    const { data, error } = await api.POST("/api/auth/demo-login", { body: { email: u.email } });
    if (data) go(data);
    else failed(error);
    setBusy(false);
  };

  return (
    <div className="auth-wrap">
      <AuthSide
        title={
          <div>
            <b style={{ color: "#fff" }}>{shopName ?? "Gem Shop System"}</b>
            <br />
            <small className="gold">SALES &amp; INVENTORY</small>
          </div>
        }
      >
        <h1>
          Every gem tracked.
          <br />
          <span className="gold">Full control from anywhere.</span>
        </h1>
        <p className="mt" style={{ maxWidth: 420 }}>
          Buying, stock for two branches, selling to tourists, invoices, commissions and promotions – in one simple
          system.
        </p>
        <div className="row mt">
          {GEMS.map((g) => (
            <GemSvg key={g} gem={g} type="Loose gem" size={54} />
          ))}
        </div>
      </AuthSide>
      <div className="auth-main">
        <div className="auth-card">
          <div className="card">
            <div className="card-content" style={{ padding: 28 }}>
              <h2 style={{ fontSize: 22 }}>Sign in</h2>
              <p className="muted">Each person has their own login and role.</p>
              {auth.notice && (
                <Alert tone="warning" className="mt">
                  {auth.notice}
                </Alert>
              )}
              <form
                noValidate
                className="stack mt"
                style={{ gap: 12 }}
                onSubmit={(e) => {
                  e.preventDefault();
                  void submit();
                }}
              >
                <Field label="Email" htmlFor="em" required error={errors.email}>
                  <Input
                    id="em"
                    type="email"
                    name="email"
                    autoComplete="username"
                    placeholder="you@ceylongemhouse.lk"
                    value={email}
                    onChange={(e) => (
                      setEmail(e.target.value),
                      setErrors((x) => ({ ...x, email: undefined })),
                      setCodeStep(false),
                      setCode("")
                    )}
                  />
                </Field>
                <div className={cn("field", errors.password && "invalid")}>
                  <div className="row between">
                    <label className="req" htmlFor="pw">
                      Password
                    </label>
                    <Link href="/forgot-password" style={{ fontSize: 12 }}>
                      Forgot password?
                    </Link>
                  </div>
                  <Input
                    id="pw"
                    type="password"
                    name="password"
                    autoComplete="current-password"
                    placeholder="••••••••"
                    value={password}
                    onChange={(e) => (setPassword(e.target.value), setErrors((x) => ({ ...x, password: undefined })))}
                  />
                  {errors.password && (
                    <span className="err" role="alert">
                      {errors.password}
                    </span>
                  )}
                </div>
                {codeStep && (
                  <Field
                    label="Code from your phone"
                    htmlFor="otp"
                    required
                    hint="Open the authenticator app and type the 6-digit code. Lost your phone? Type a backup code."
                    error={errors.code}
                  >
                    <Input
                      id="otp"
                      name="one-time-code"
                      autoComplete="one-time-code"
                      inputMode="numeric"
                      maxLength={20}
                      placeholder="123 456"
                      value={code}
                      onChange={(e) => (setCode(e.target.value), setErrors((x) => ({ ...x, code: undefined })))}
                    />
                  </Field>
                )}
                <Check checked={remember} onChange={setRemember}>
                  Keep me signed in on this device <small className="muted">(not on shared shop computers)</small>
                </Check>
                <button className="btn btn-lg btn-sapphire btn-block" type="submit" disabled={busy}>
                  Sign in
                </button>
              </form>
              {demo.length > 0 && (
                <>
                  <div className="sep" />
                  <div className="label mb-s">Demo: sign in as…</div>
                  <div className="g2 grid" style={{ gap: 8 }}>
                    {demo.map((u) => (
                      <button
                        key={u.email}
                        type="button"
                        className="btn btn-outline"
                        style={{ height: "auto", padding: 8, justifyContent: "flex-start", textAlign: "left" }}
                        disabled={busy}
                        onClick={() => void demoLogin(u)}
                      >
                        <div
                          className={cn("avatar", u.role === "OWNER" && "gold")}
                          style={{ width: 28, height: 28, fontSize: 11 }}
                        >
                          {initials(u.name)}
                        </div>
                        <span style={{ lineHeight: 1.2 }}>
                          <b style={{ fontSize: 12 }}>{u.name}</b>
                          <br />
                          <small className="muted">{ROLE_LABEL[u.role]}</small>
                        </span>
                      </button>
                    ))}
                  </div>
                </>
              )}
              {setupNeeded && (
                <p className="muted center mt" style={{ fontSize: 12 }}>
                  New shop? <Link href="/setup">Set up the owner account</Link>
                </p>
              )}
            </div>
          </div>
          <p className="muted center mt-s" style={{ fontSize: 11 }}>
            🔒 Secure login · encrypted connection
          </p>
        </div>
      </div>
    </div>
  );
}
