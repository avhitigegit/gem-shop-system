"use client";

/* Requests waiting for the owner – the bell in the top bar and the count on "Approvals" in the menu.
   One shared request for every part of the page that shows it; asked again every 10 seconds while the tab is open,
   straight away when the tab comes back to the front, and when the branch in the top bar changes. */
import { useEffect, useSyncExternalStore } from "react";
import { api } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";

export type ApprovalView = components["schemas"]["ApprovalView"];
export type Waiting = components["schemas"]["Waiting"];

const POLL_MS = 10_000;
const EMPTY: Waiting = { count: 0, latest: [] };

let state: Waiting = EMPTY;
let branch: string | null = null;
let users = 0;
let timer: ReturnType<typeof setInterval> | null = null;
let seq = 0;
const listeners = new Set<() => void>();

async function load() {
  if (typeof document !== "undefined" && document.visibilityState === "hidden") return;
  const mine = ++seq;
  const { data } = await api.GET("/api/approvals/waiting");
  if (data && mine === seq) {
    state = data;
    listeners.forEach((l) => l());
  }
}

const onVisible = () => document.visibilityState === "visible" && void load();

function start() {
  if (timer) return;
  timer = setInterval(() => void load(), POLL_MS);
  document.addEventListener("visibilitychange", onVisible);
}

function stop() {
  if (timer) clearInterval(timer);
  timer = null;
  document.removeEventListener("visibilitychange", onVisible);
}

/** Ask again now, e.g. right after the owner approved or rejected something. */
export const refreshWaiting = () => void load();

/** Waiting requests the signed-in person may see, for the branch picked in the top bar. */
export function useApprovalsWaiting(forBranch: string): Waiting {
  useEffect(() => {
    users++;
    start();
    if (branch !== forBranch) {
      branch = forBranch;
      void load();
    }
    return () => {
      users--;
      if (users === 0) {
        stop();
        branch = null;
        state = EMPTY; // the next person to sign in on this computer must not see these
      }
    };
  }, [forBranch]);
  return useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    () => state,
    () => EMPTY,
  );
}
