"use client";

/* Who is using the app, which branch and currency they look at.
   UNTIL LOGIN (P1-02): the user is a PREVIEW user picked in the top bar, from the sample data
   (the same 4 people as the prototype). P1-02 replaces PREVIEW_* with the logged-in user from the backend,
   and P1-06 replaces the shop name and exchange rates with the real settings. */
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import type { Currency, Rates } from "@/lib/format";
import { type Role, seesAllBranches } from "@/lib/roles";

export type SessionUser = { id: string; name: string; email: string; role: Role; branch: string | null };
export type BranchInfo = {
  code: string;
  name: string;
  shortName: string | null;
  status: "ACTIVE" | "OPENING" | "INACTIVE";
};
export type ShopInfo = { name: string };

export const PREVIEW_USERS: SessionUser[] = [
  { id: "u1", name: "Ruwan Perera", email: "owner@ceylongemhouse.lk", role: "OWNER", branch: null },
  { id: "u2", name: "Nimal Silva", email: "nimal@ceylongemhouse.lk", role: "SALESPERSON", branch: "ella" },
  { id: "u3", name: "Kasun Bandara", email: "kasun@ceylongemhouse.lk", role: "STAFF", branch: "ella" },
  { id: "u4", name: "Dilani Fernando", email: "dilani@fernandoassociates.lk", role: "ACCOUNTANT", branch: null },
];
export const PREVIEW_BRANCHES: BranchInfo[] = [
  { code: "ella", name: "Ella", shortName: null, status: "ACTIVE" },
  { code: "b2", name: "Branch 2 (opening January 2027)", shortName: "Branch 2", status: "OPENING" },
];
const PREVIEW_SHOP: ShopInfo = { name: "Ceylon Gem House" };
const PREVIEW_RATES: Rates = { USD: 300, EUR: 325 };

export const ALL_BRANCHES = "all";
export const branchLabel = (b: BranchInfo) => b.shortName || b.name;

type Prefs = { role: Role; branch: string; currency: Currency };
const PREFS_KEY = "gemshop.prefs.v1";
const DEFAULT_PREFS: Prefs = { role: "OWNER", branch: "ella", currency: "LKR" };

function readPrefs(): Prefs {
  try {
    const p = JSON.parse(localStorage.getItem(PREFS_KEY) || "{}");
    return { ...DEFAULT_PREFS, ...p };
  } catch {
    return DEFAULT_PREFS;
  }
}

export type Session = {
  /** false until the browser has read the saved choices (avoids showing the wrong menu first). */
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
  /** Preview only (until login): see the app as another role. */
  preview: boolean;
  setPreviewRole: (r: Role) => void;
};

const SessionContext = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [prefs, setPrefs] = useState<Prefs>(DEFAULT_PREFS);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    // Saved choices live in this browser only; read them once after the first render.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setPrefs(readPrefs());
    setReady(true);
  }, []);

  const save = useCallback((p: Partial<Prefs>) => {
    setPrefs((old) => {
      const next = { ...old, ...p };
      try {
        localStorage.setItem(PREFS_KEY, JSON.stringify(next));
      } catch {
        /* private window – the choice lasts until the page is closed */
      }
      return next;
    });
  }, []);

  const value = useMemo<Session>(() => {
    const user = PREVIEW_USERS.find((u) => u.role === prefs.role) ?? PREVIEW_USERS[0];
    // Salesperson and Shop Staff always see their own branch (BRD rule 9; the backend checks it too).
    const branch = seesAllBranches(user.role) ? prefs.branch : (user.branch ?? PREVIEW_BRANCHES[0].code);
    const branchName = (code: string) => {
      if (code === ALL_BRANCHES) return "All branches";
      const b = PREVIEW_BRANCHES.find((x) => x.code === code);
      return b ? branchLabel(b) : code;
    };
    return {
      ready,
      user,
      role: user.role,
      shop: PREVIEW_SHOP,
      branches: PREVIEW_BRANCHES,
      branch,
      setBranch: (code) => save({ branch: code }),
      branchName,
      currency: prefs.currency,
      setCurrency: (currency) => save({ currency }),
      rates: PREVIEW_RATES,
      preview: true,
      setPreviewRole: (role) =>
        save(
          seesAllBranches(role) ? { role } : { role, branch: prefs.branch === ALL_BRANCHES ? "ella" : prefs.branch },
        ),
    };
  }, [prefs, ready, save]);

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const s = useContext(SessionContext);
  if (!s) throw new Error("useSession must be used inside <SessionProvider>");
  return s;
}
