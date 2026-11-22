/* Sign-in for the API calls.
   - The short login token (10 minutes) lives only in memory: it is sent as "Authorization: Bearer …".
   - The long token is an http-only cookie the browser sends to /api/auth/refresh by itself
     (scripts cannot read it). It renews the login token while the person works.
   - Every call also says which branch the person looks at (header X-Branch); the backend still
     checks the role and branch itself.
   - A call that comes back "not signed in" renews the login once and is sent again. */
import type { components } from "@/lib/api/schema";

export type SessionData = components["schemas"]["Session"];

const BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";
/** Sign-in calls answer 401 for a wrong password – never renew-and-retry those. */
const NO_RETRY = /^\/api\/(auth\/(login|refresh|logout|demo-login|forgot-password|reset-password)|setup)/;

let current: SessionData | null = null;
let branchHeader = "all";
type Listener = (s: SessionData | null) => void;
const listeners = new Set<Listener>();

/** Called when the login is renewed or ended (also by API calls in the background). */
export function onSessionChange(l: Listener) {
  listeners.add(l);
  return () => void listeners.delete(l);
}

export function setSession(s: SessionData | null) {
  current = s;
  listeners.forEach((l) => l(s));
}

export const currentSession = () => current;

export function setBranchHeader(code: string) {
  branchHeader = code;
}

const wait = (ms: number) => new Promise((r) => setTimeout(r, ms));

async function callRefresh(): Promise<SessionData | null> {
  try {
    const res = await fetch(BASE + "/api/auth/refresh", { method: "POST", credentials: "include" });
    return res.ok ? ((await res.json()) as SessionData) : null;
  } catch {
    return null;
  }
}

let inflight: Promise<SessionData | null> | null = null;

/** Renews the login with the cookie. Only one tab renews at a time; a second try covers
    "another tab renewed a moment ago". Returns null when the person must sign in again. */
export function refreshSession(): Promise<SessionData | null> {
  inflight ??= (async () => {
    const run = async () => (await callRefresh()) ?? (await wait(400), await callRefresh());
    try {
      const locks = typeof navigator !== "undefined" ? navigator.locks : undefined;
      return locks ? await locks.request("gemshop-refresh", run) : await run();
    } finally {
      inflight = null;
    }
  })();
  return inflight;
}

function withAuth(req: Request): Request {
  const headers = new Headers(req.headers);
  if (current) headers.set("Authorization", "Bearer " + current.accessToken);
  headers.set("X-Branch", branchHeader);
  return new Request(req, { headers, credentials: "include" });
}

/** fetch for the API client: adds the login token and branch, renews once on "not signed in". */
export async function authFetch(req: Request): Promise<Response> {
  const again = req.clone();
  const res = await fetch(withAuth(req));
  if (res.status !== 401 || !current || NO_RETRY.test(new URL(req.url, "http://x").pathname)) return res;
  const s = await refreshSession();
  setSession(s);
  return s ? fetch(withAuth(again)) : res;
}
