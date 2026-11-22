"use client";

/* Who is signed in, which branch and currency they look at.
   - On opening the app the login is renewed with the sign-in cookie; no cookie = the login page.
   - Shop name, branches and today's exchange rates come from the backend with the person (GET /api/auth/me).
   - Shared shop computers: 15 minutes without use signs out (not with "Keep me signed in").
     Use in any open tab counts, so a second tab does not sign the person out.
   - Signing out in one tab signs out the other tabs too. */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { api } from "@/lib/api/client";
import { refreshSession, onSessionChange, setBranchHeader, setSession, type SessionData } from "@/lib/auth";
import type { Currency, Rates } from "@/lib/format";
import { type Role, seesAllBranches } from "@/lib/roles";

export type SessionUser = {
  id: number;
  name: string;
  email: string;
  phone: string | null;
  role: Role;
  branch: string | null;
};
export type BranchInfo = {
  code: string;
  name: string;
  shortName: string | null;
  status: "ACTIVE" | "OPENING" | "INACTIVE";
};
export type ShopInfo = { name: string };

export const ALL_BRANCHES = "all";
export const branchLabel = (b: BranchInfo) => b.shortName || b.name;

/** The Accountant opens the Accountant home; everyone else opens Home. */
export const homeHref = (role: Role) => (role === "ACCOUNTANT" ? "/accountant" : "/");

type Prefs = { branch: string; currency: Currency };
const PREFS_KEY = "gemshop.prefs.v1";
const ACTIVE_KEY = "gemshop.active";
const DEFAULT_PREFS: Prefs = { branch: ALL_BRANCHES, currency: "LKR" };
/** How often the timers look at the clock. */
const TICK_MS = 15_000;
/** Renew the login token this long before it ends. */
const RENEW_BEFORE_MS = 90_000;

function readPrefs(): Prefs {
  try {
    const p = JSON.parse(localStorage.getItem(PREFS_KEY) || "{}");
    return {
      ...DEFAULT_PREFS,
      ...(typeof p.branch === "string" ? { branch: p.branch } : {}),
      ...(p.currency ? { currency: p.currency } : {}),
    };
  } catch {
    return DEFAULT_PREFS;
  }
}

function writePrefs(p: Prefs) {
  try {
    localStorage.setItem(PREFS_KEY, JSON.stringify(p));
  } catch {
    /* private window – the choice lasts until the page is closed */
  }
}

/** Last use in any tab of this browser. */
function lastActive(local: number) {
  try {
    return Math.max(local, Number(localStorage.getItem(ACTIVE_KEY)) || 0);
  } catch {
    return local;
  }
}

export type AuthStatus = "loading" | "signedOut" | "signedIn";

export type Auth = {
  status: AuthStatus;
  /** Why the person was signed out (shown on the login page), e.g. after 15 minutes without use. */
  notice: string | null;
  /** After login / first-time setup / reset: start using this sign-in. */
  signIn: (s: SessionData) => void;
  signOut: (notice?: string) => Promise<void>;
};

export type Session = {
  /** Kept for the screens that wait for the browser (always true once signed in). */
  ready: boolean;
  user: SessionUser;
  role: Role;
  shop: ShopInfo;
  branches: BranchInfo[];
  /** The branch the person looks at: a branch code, or "all" (owner / accountant only). */
  branch: string;
  setBranch: (code: string) => void;
  branchName: (code: string) => string;
  currency: Currency;
  setCurrency: (c: Currency) => void;
  rates: Rates;
  /** Sign-out after this many minutes without use; 0 = "Keep me signed in". */
  idleMinutes: number;
};

const AuthContext = createContext<Auth | null>(null);
const SessionContext = createContext<Session | null>(null);

const channel = () => (typeof BroadcastChannel !== "undefined" ? new BroadcastChannel("gemshop-auth") : null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState<SessionData | null>(null);
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [notice, setNotice] = useState<string | null>(null);
  const [prefs, setPrefs] = useState<Prefs>(DEFAULT_PREFS);
  const active = useRef(0);
  const bc = useRef<BroadcastChannel | null>(null);

  // Every renewal (also from API calls in the background) updates the person, shop, branches and rates.
  useEffect(
    () =>
      onSessionChange((s) => {
        setData(s);
        setStatus(s ? "signedIn" : "signedOut");
      }),
    [],
  );

  const markActive = useCallback(() => {
    const now = Date.now();
    if (now - active.current < 5_000) return;
    active.current = now;
    try {
      localStorage.setItem(ACTIVE_KEY, String(now));
    } catch {
      /* ignore */
    }
  }, []);

  const signIn = useCallback(
    (s: SessionData) => {
      // A fresh sign-in starts on the person's own branch (owner and accountant: all branches).
      const next = { ...readPrefs(), branch: s.me.user.branch ?? ALL_BRANCHES };
      writePrefs(next);
      setPrefs(next);
      setNotice(null);
      active.current = 0;
      markActive();
      setSession(s);
      bc.current?.postMessage("in");
    },
    [markActive],
  );

  const signOut = useCallback(async (why?: string) => {
    setNotice(why ?? null);
    setSession(null);
    bc.current?.postMessage("out");
    try {
      await api.POST("/api/auth/logout");
    } catch {
      /* offline – the cookie ends by itself */
    }
  }, []);

  // Opening the app: renew the login with the cookie (if there is one).
  useEffect(() => {
    // Saved choices live in this browser only; read them once after the first render.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setPrefs(readPrefs());
    let cancelled = false;
    refreshSession().then((s) => {
      if (cancelled) return;
      if (s) markActive();
      setSession(s);
    });
    bc.current = channel();
    if (bc.current)
      bc.current.onmessage = (e) => {
        if (e.data === "out") setSession(null);
        if (e.data === "in") refreshSession().then((s) => s && setSession(s));
      };
    return () => {
      cancelled = true;
      bc.current?.close();
      bc.current = null;
    };
  }, [markActive]);

  // While signed in: count use, renew the token in time, sign out after the idle time.
  const idle = data?.idleMinutes ?? 0;
  const expiresAt = data ? Date.parse(data.expiresAt) : 0;
  useEffect(() => {
    if (status !== "signedIn") return;
    const events = ["pointerdown", "keydown", "wheel", "touchstart", "mousemove"] as const;
    events.forEach((e) => window.addEventListener(e, markActive, { passive: true }));
    let renewing = false;
    const tick = async () => {
      const since = Date.now() - lastActive(active.current);
      if (idle > 0 && since > idle * 60_000) {
        await signOut(`You were signed out after ${idle} minutes without use.`);
        return;
      }
      if (!renewing && expiresAt - Date.now() < RENEW_BEFORE_MS) {
        renewing = true;
        const s = await refreshSession();
        renewing = false;
        if (s) setSession(s);
        else await signOut("Your sign-in has ended. Please sign in again.");
      }
    };
    const t = setInterval(tick, TICK_MS);
    const visible = () => document.visibilityState === "visible" && void tick();
    document.addEventListener("visibilitychange", visible);
    return () => {
      clearInterval(t);
      document.removeEventListener("visibilitychange", visible);
      events.forEach((e) => window.removeEventListener(e, markActive));
    };
  }, [status, idle, expiresAt, markActive, signOut]);

  const save = useCallback((p: Partial<Prefs>) => {
    setPrefs((old) => {
      const next = { ...old, ...p };
      writePrefs(next);
      return next;
    });
  }, []);

  const session = useMemo<Session | null>(() => {
    if (!data) return null;
    const me = data.me;
    const user: SessionUser = {
      id: me.user.id,
      name: me.user.name,
      email: me.user.email,
      phone: me.user.phone ?? null,
      role: me.user.role,
      branch: me.user.branch ?? null,
    };
    const branches: BranchInfo[] = me.branches.map((b) => ({ ...b, shortName: b.shortName ?? null }));
    // Salesperson and Shop Staff always see their own branch (BRD rule 9; the backend checks it too).
    const known = prefs.branch === ALL_BRANCHES || branches.some((b) => b.code === prefs.branch);
    const branch = seesAllBranches(user.role)
      ? known
        ? prefs.branch
        : ALL_BRANCHES
      : (user.branch ?? branches[0]?.code ?? ALL_BRANCHES);
    // API calls say which branch the person looks at (set here, before the pages load their lists).
    setBranchHeader(branch);
    const branchName = (code: string) => {
      if (code === ALL_BRANCHES) return "All branches";
      const b = branches.find((x) => x.code === code);
      return b ? branchLabel(b) : code;
    };
    return {
      ready: true,
      user,
      role: user.role,
      shop: me.shop,
      branches,
      branch,
      setBranch: (code) => save({ branch: code }),
      branchName,
      currency: prefs.currency,
      setCurrency: (currency) => save({ currency }),
      rates: { USD: Number(me.rates.usd), EUR: Number(me.rates.eur) },
      idleMinutes: data.idleMinutes,
    };
  }, [data, prefs, save]);

  const auth = useMemo<Auth>(() => ({ status, notice, signIn, signOut }), [status, notice, signIn, signOut]);

  return (
    <AuthContext.Provider value={auth}>
      <SessionContext.Provider value={session}>{children}</SessionContext.Provider>
    </AuthContext.Provider>
  );
}

export function useAuth(): Auth {
  const a = useContext(AuthContext);
  if (!a) throw new Error("useAuth must be used inside <SessionProvider>");
  return a;
}

/** The signed-in person. Only inside the app frame (it shows the login page when nobody is signed in). */
export function useSession(): Session {
  const s = useContext(SessionContext);
  if (!s) throw new Error("useSession is only for signed-in pages (inside the app frame)");
  return s;
}
