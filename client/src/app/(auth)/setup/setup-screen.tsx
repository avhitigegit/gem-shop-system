"use client";

/* Owner first-time setup – prototype auth/signup.html: three short steps (owner, shop & licence, branch & rates).
   Only works while the system has no logins; afterwards this page sends people to sign in.
   The owner is signed in straight away. Staff, the second branch and the lists are added later in Settings. */
import Link from "next/link";
import { useRouter } from "next/navigation";
import { type ComponentProps, useEffect, useState } from "react";
import { Modal } from "@/components/ui/dialog";
import { Alert, Check, Field, FormGrid, Input } from "@/components/ui/form";
import { useToast } from "@/components/ui/toast";
import { api, errorCode, errorMessage, fieldErrors } from "@/lib/api/client";
import type { SessionData } from "@/lib/auth";
import { emailOk, passwordOk, PASSWORD_HINT } from "@/lib/password";
import { useAuth } from "@/lib/session";
import { cn } from "@/lib/utils";
import { AuthSide } from "../auth-parts";

type Form = {
  oname: string;
  email: string;
  phone: string;
  pw: string;
  pw2: string;
  shop: string;
  address: string;
  ngja: string;
  ngjaExp: string;
  branch: string;
  usd: string;
  eur: string;
  terms: boolean;
};
type Key = keyof Form;

const STEPS: Key[][] = [
  ["oname", "email", "phone", "pw", "pw2"],
  ["shop", "address", "ngja", "ngjaExp"],
  ["branch", "usd", "eur", "terms"],
];
/** Backend field → form box, to mark the right box red. */
const SERVER_FIELDS: Record<string, Key> = {
  "owner.name": "oname",
  "owner.email": "email",
  "owner.phone": "phone",
  "owner.password": "pw",
  "shop.name": "shop",
  "shop.address": "address",
  "shop.ngjaLicenceNo": "ngja",
  "shop.ngjaLicenceExpiry": "ngjaExp",
  "branch.name": "branch",
  "rates.usd": "usd",
  "rates.eur": "eur",
  terms: "terms",
};
const stepOf = (k: Key) => STEPS.findIndex((s) => s.includes(k)) + 1;

export function SetupScreen() {
  const router = useRouter();
  const auth = useAuth();
  const toast = useToast();
  const [step, setStep] = useState(1);
  const [f, setF] = useState<Form>({
    oname: "",
    email: "",
    phone: "",
    pw: "",
    pw2: "",
    shop: "",
    address: "Ella, Sri Lanka",
    ngja: "",
    ngjaExp: "",
    branch: "Ella",
    usd: "300",
    eur: "325",
    terms: false,
  });
  const [errors, setErrors] = useState<Partial<Record<Key, string>>>({});
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState<SessionData | null>(null);

  // Already set up → sign in instead.
  useEffect(() => {
    api.GET("/api/setup/status").then(({ data }) => data && !data.needed && router.replace("/login"));
  }, [router]);

  const set = <K extends Key>(k: K, v: Form[K]) => {
    setF((x) => ({ ...x, [k]: v }));
    setErrors((x) => ({ ...x, [k]: undefined }));
  };

  const checkStep = () => {
    const e: Partial<Record<Key, string>> = {};
    for (const k of STEPS[step - 1]) {
      const v = f[k];
      if (v === false || (typeof v === "string" && !v.trim())) e[k] = "This field is required.";
    }
    if (!e.email && step === 1 && !emailOk(f.email)) e.email = "Enter a valid email.";
    if (step === 3) {
      for (const k of ["usd", "eur"] as const) if (!e[k] && !(Number(f[k]) > 0)) e[k] = "Must be more than 0.";
    }
    if (Object.keys(e).length) {
      setErrors(e);
      toast("Please complete the required fields", "Fields marked * are required.", "error");
      return false;
    }
    if (step === 1) {
      if (!passwordOk(f.pw)) {
        setErrors({ pw: PASSWORD_HINT });
        toast("Password too weak", PASSWORD_HINT + ".", "error");
        return false;
      }
      if (f.pw !== f.pw2) {
        setErrors({ pw2: "Passwords do not match." });
        toast("Passwords do not match", "", "error");
        return false;
      }
    }
    return true;
  };

  const create = async () => {
    setBusy(true);
    const { data, error } = await api.POST("/api/setup", {
      body: {
        owner: { name: f.oname.trim(), email: f.email.trim(), phone: f.phone.trim(), password: f.pw },
        shop: {
          name: f.shop.trim(),
          address: f.address.trim(),
          ngjaLicenceNo: f.ngja.trim(),
          ngjaLicenceExpiry: f.ngjaExp,
        },
        branch: { name: f.branch.trim() },
        rates: { usd: Number(f.usd), eur: Number(f.eur) },
        terms: f.terms,
      },
    });
    setBusy(false);
    if (data) {
      auth.signIn(data);
      setDone(data);
      return;
    }
    if (errorCode(error) === "SETUP_DONE") {
      toast("Already set up", errorMessage(error), "error");
      router.replace("/login");
      return;
    }
    const e: Partial<Record<Key, string>> = {};
    for (const [field, msg] of Object.entries(fieldErrors(error)))
      if (SERVER_FIELDS[field]) e[SERVER_FIELDS[field]] = msg;
    setErrors(e);
    const first = Object.keys(e)[0] as Key | undefined;
    if (first) setStep(stepOf(first));
    toast("Owner account not created", errorMessage(error), "error");
  };

  const text = (k: Key, label: string, props: Omit<ComponentProps<"input">, "size"> = {}, hint?: string) => (
    <Field label={label} htmlFor={"su-" + k} required hint={hint} error={errors[k]}>
      <Input
        id={"su-" + k}
        name={k}
        value={f[k] as string}
        onChange={(e) => set(k, e.target.value)}
        {...props}
        className={cn(props.className)}
      />
    </Field>
  );

  return (
    <div className="auth-wrap">
      <AuthSide title={<b style={{ color: "#fff" }}>Gem Shop System</b>}>
        <h1>
          Set up your shop
          <br />
          <span className="gold">in three short steps.</span>
        </h1>
        <p className="mt">
          The owner account controls everything: products, prices, approvals, staff and reports. Staff accounts are
          added afterwards.
        </p>
      </AuthSide>
      <div className="auth-main">
        <div className="auth-card" style={{ maxWidth: 480 }}>
          <div className="card">
            <div className="card-content" style={{ padding: 28 }}>
              <h2 style={{ fontSize: 22 }}>Owner account setup</h2>
              <div className="stepper mt">
                {["1 · Owner", "2 · Shop & licence", "3 · Branch & rates"].map((s, i) => (
                  <span key={s} className={i < step ? "on" : undefined}>
                    {s}
                  </span>
                ))}
              </div>
              <form
                noValidate
                onSubmit={(e) => {
                  e.preventDefault();
                  if (!checkStep()) return;
                  if (step < 3) setStep(step + 1);
                  else void create();
                }}
              >
                {step === 1 && (
                  <div className="stack" style={{ gap: 12 }}>
                    {text("oname", "Full name", { placeholder: "e.g. Ruwan Perera", autoComplete: "name" })}
                    {text("email", "Email (login)", { type: "email", autoComplete: "username" })}
                    {text("phone", "Mobile (for owner alerts)", { placeholder: "+94 77 …", autoComplete: "tel" })}
                    {text(
                      "pw",
                      "Password",
                      { type: "password", autoComplete: "new-password" },
                      "At least 8 characters with a number",
                    )}
                    {text("pw2", "Confirm password", { type: "password", autoComplete: "new-password" })}
                  </div>
                )}
                {step === 2 && (
                  <div className="stack" style={{ gap: 12 }}>
                    {text("shop", "Shop name", { autoComplete: "organization" })}
                    {text("address", "Address", { autoComplete: "street-address" })}
                    {text("ngja", "NGJA licence no.", { className: "mono" })}
                    {text("ngjaExp", "NGJA licence expiry", { type: "date" })}
                  </div>
                )}
                {step === 3 && (
                  <div className="stack" style={{ gap: 12 }}>
                    {text("branch", "First branch")}
                    <FormGrid>
                      {text("usd", "1 USD = LKR", { type: "number", min: 0, step: "0.01", inputMode: "decimal" })}
                      {text("eur", "1 EUR = LKR", { type: "number", min: 0, step: "0.01", inputMode: "decimal" })}
                    </FormGrid>
                    <Alert tone="info">
                      <span style={{ fontSize: 12 }}>
                        Currencies are LKR, USD and EUR only. You can add the second branch, staff and lists later in
                        Settings.
                      </span>
                    </Alert>
                    <div className={cn("field", errors.terms && "invalid")}>
                      <Check checked={f.terms} onChange={(v) => set("terms", v)}>
                        I agree that customer data is kept private and used only with permission (PDPA No. 9 of 2022)
                      </Check>
                      {errors.terms && (
                        <span className="err" role="alert">
                          {errors.terms}
                        </span>
                      )}
                    </div>
                  </div>
                )}
                <div className="row between mt">
                  <button
                    type="button"
                    className="btn btn-outline"
                    style={{ visibility: step > 1 ? "visible" : "hidden" }}
                    onClick={() => setStep(step - 1)}
                  >
                    Back
                  </button>
                  <button type="submit" className="btn btn-sapphire" disabled={busy}>
                    {step === 3 ? "Create owner account" : "Next"}
                  </button>
                </div>
              </form>
              <p className="muted center mt" style={{ fontSize: 12 }}>
                Already set up? <Link href="/login">Sign in</Link>
              </p>
            </div>
          </div>
        </div>
      </div>
      <Modal
        open={done != null}
        onOpenChange={(o) => !o && router.replace("/")}
        title="Owner account created ✓"
        footer={
          <button className="btn btn-sapphire" onClick={() => router.replace("/")}>
            Go to dashboard
          </button>
        }
      >
        <dl className="dl">
          <dt>Owner</dt>
          <dd>{f.oname}</dd>
          <dt>Shop</dt>
          <dd>{f.shop}</dd>
          <dt>Branch</dt>
          <dd>{f.branch}</dd>
          <dt>Rates</dt>
          <dd>
            USD {f.usd} · EUR {f.eur}
          </dd>
        </dl>
        <Alert tone="info" className="mt">
          A confirmation email was sent to {f.email.trim().toLowerCase()}.
        </Alert>
      </Modal>
    </div>
  );
}
