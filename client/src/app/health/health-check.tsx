"use client";

import { useCallback, useEffect, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import { Alert } from "@/components/ui/form";
import { dateTime } from "@/lib/format";
import { api, errorMessage, type HealthResponse } from "@/lib/api/client";

type State = { kind: "loading" } | { kind: "ok"; data: HealthResponse } | { kind: "error"; message: string };

function StatusBadge({ value }: { value?: string }) {
  const up = value === "UP";
  return (
    <Badge tone={up ? "success" : "danger"}>
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
      <CardHeader title="Backend API" />
      <CardContent>
        {state.kind === "loading" && <p className="muted">Checking…</p>}

        {state.kind === "error" && (
          <Alert tone="danger" icon="✕">
            <b>The backend did not answer</b>
            <br />
            {state.message}
          </Alert>
        )}

        {state.kind === "ok" && (
          <dl className="dl">
            <dt>API</dt>
            <dd>
              <StatusBadge value={state.data.status} />
            </dd>
            <dt>Database</dt>
            <dd>
              <StatusBadge value={state.data.database} />
            </dd>
            <dt>Environment</dt>
            <dd>{state.data.environment}</dd>
            <dt>Version</dt>
            <dd>{state.data.version}</dd>
            <dt>Checked at</dt>
            <dd>{dateTime(state.data.time)}</dd>
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
