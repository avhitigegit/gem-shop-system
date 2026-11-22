"use client";

/* Load data for a screen from the API:
     const { data, error, reload, setData } = useLoad(() => api.GET("/api/settings/shop"), []);
   data is undefined while loading. reload() asks again (e.g. after saving); setData() puts a saved answer in place. */
import { useCallback, useEffect, useRef, useState, type DependencyList } from "react";
import { refreshSession, setSession } from "@/lib/auth";

type Answer<T> = { data?: T; error?: unknown };

export function useLoad<T>(load: () => Promise<Answer<T>>, deps: DependencyList) {
  const [data, setData] = useState<T | undefined>(undefined);
  const [error, setError] = useState<unknown>(null);
  const seq = useRef(0);
  const fn = useRef(load);
  useEffect(() => {
    fn.current = load;
  });

  const reload = useCallback(async () => {
    const mine = ++seq.current;
    try {
      const r = await fn.current();
      if (mine !== seq.current) return;
      if (r.data !== undefined) {
        setData(r.data);
        setError(null);
      } else setError(r.error ?? "Could not load");
    } catch (e) {
      if (mine === seq.current) setError(e);
    }
  }, []);

  useEffect(() => {
    void reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return { data, error, reload, setData };
}

/** After the owner changed the shop name, branches or rates: the top bar and menus show the new values. */
export async function reloadSession() {
  const s = await refreshSession();
  if (s) setSession(s);
}
