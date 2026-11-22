"use client";

import { useCallback, useEffect, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { api, errorMessage, type HealthResponse } from "@/lib/api/client";

type State = { kind: "loading" } | { kind: "ok"; data: HealthResponse } | { kind: "error"; message: string };

function StatusBadge({ value }: { value?: string }) {
  const up = value === "UP";
  return (
    <Badge variant={up ? "default" : "destructive"} data-testid="status-badge">
      {up ? "✓ " : "✕ "}
      {value ?? "DOWN"}
    </Badge>
  );
}

export function HealthCheck() {
  const [state, setState] = useState<State>({ kind: "loading" });

  const check = useCallback(async () => {
    setState({ kind: "loading" });
    try {
      const { data, error } = await api.GET("/api/health");
      setState(data ? { kind: "ok", data } : { kind: "error", message: errorMessage(error) });
    } catch (e) {
      setState({ kind: "error", message: errorMessage(e) });
    }
  }, []);

  useEffect(() => {
    // Runs once when the page opens; the button below runs it again.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void check();
  }, [check]);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Backend API</CardTitle>
        <CardDescription>GET /api/health</CardDescription>
      </CardHeader>
      <CardContent>
        {state.kind === "loading" && <p className="text-sm text-muted-foreground">Checking…</p>}

        {state.kind === "error" && (
          <div role="alert" className="rounded-md border border-destructive/40 bg-destructive/5 p-3 text-sm">
            <b className="block">The backend did not answer</b>
            {state.message}
          </div>
        )}

        {state.kind === "ok" && (
          <dl className="grid grid-cols-[140px_1fr] gap-y-2 text-sm">
            <dt className="text-muted-foreground">API</dt>
            <dd>
              <StatusBadge value={state.data.status} />
            </dd>
            <dt className="text-muted-foreground">Database</dt>
            <dd>
              <StatusBadge value={state.data.database} />
            </dd>
            <dt className="text-muted-foreground">Environment</dt>
            <dd>{state.data.environment}</dd>
            <dt className="text-muted-foreground">Version</dt>
            <dd>{state.data.version}</dd>
            <dt className="text-muted-foreground">Checked at</dt>
            <dd>{state.data.time ? new Date(state.data.time).toLocaleString("en-GB") : "—"}</dd>
          </dl>
        )}
      </CardContent>
      <CardFooter>
        <Button variant="outline" onClick={() => void check()} disabled={state.kind === "loading"}>
          Check again
        </Button>
      </CardFooter>
    </Card>
  );
}
