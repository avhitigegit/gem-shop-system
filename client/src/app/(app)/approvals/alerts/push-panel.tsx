"use client";

/* Push alerts to the phone (P4-02; prototype k-owner-control/alerts.html "Push notification settings"). Turn push on for
   this phone / browser, choose which kinds come (sale, approval request, cash difference, day closed) and send a test. */
import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Alert, Switch } from "@/components/ui/form";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import { date } from "@/lib/format";
import { pushState, turnOffPush, turnOnPush, type PushKind, type PushState } from "@/lib/push";
import { useLoad } from "@/lib/use-load";

const STATE_TEXT: Record<PushState, string> = {
  unsupported:
    "This browser cannot show push alerts. Use Chrome on Android, or Safari on iPhone (added to the home screen).",
  "needs-install":
    "On iPhone: tap Share → Add to Home Screen, open Gem Shop from the home screen, then turn push on here (iOS 16.4 or later).",
  blocked: "Notifications are blocked for this app. Allow them in the phone / browser settings, then come back.",
  off: "Push alerts are off on this phone.",
  on: "Push alerts are on for this phone ✓",
};

export function PushPanel() {
  const toast = useToast();
  const [state, setState] = useState<PushState | null>(null);
  const [busy, setBusy] = useState(false);
  const { data, setData, reload } = useLoad(() => api.GET("/api/push/settings"), []);

  const check = useCallback(() => {
    void pushState().then(setState);
  }, []);
  useEffect(check, [check]);

  const on = async () => {
    setBusy(true);
    const err = await turnOnPush();
    setBusy(false);
    if (err) toast("Push not turned on", err, "error");
    else toast("Push alerts on", "This phone will get the alerts you choose below.", "success");
    check();
    reload();
  };

  const off = async () => {
    setBusy(true);
    await turnOffPush();
    setBusy(false);
    toast("Push alerts off on this phone", undefined, "success");
    check();
    reload();
  };

  const toggle = async (kind: PushKind, v: boolean) => {
    const { data: d, error } = await api.PUT("/api/push/settings", { body: { kinds: { [kind]: v } } });
    if (!d) return toast("Not saved", errorMessage(error), "error");
    setData(d);
    toast("Saved", "Push setting updated", "success");
  };

  const test = async () => {
    const { data: d, error } = await api.POST("/api/push/test");
    if (!d) return toast("Not sent", errorMessage(error), "error");
    if (!d.sent) return toast("No phone signed up", "Turn push on first.", "error");
    toast("🔔 Test push sent", `Check your phone (${d.sent} device${d.sent > 1 ? "s" : ""}).`, "success");
  };

  return (
    <Card className="mt" id="pushCard">
      <CardHeader title="Push alerts on your phone" level={3} />
      <CardContent className="stack" style={{ gap: 10 }}>
        {state ? (
          <Alert tone={state === "on" ? "success" : state === "off" ? "info" : "warning"}>
            <span id="pushState">{STATE_TEXT[state]}</span>
          </Alert>
        ) : null}
        {state === "off" ? (
          <Button variant="sapphire" id="pushOn" disabled={busy} onClick={() => void on()}>
            {busy ? "Turning on…" : "Turn on push for this phone"}
          </Button>
        ) : null}
        {data?.kinds.map((k) => (
          <label key={k.kind} className="row between" data-kind={k.kind}>
            <span>{k.label}</span>
            <Switch checked={k.on} label={k.label} onChange={(v) => void toggle(k.kind, v)} />
          </label>
        ))}
        {data?.devices.length ? (
          <small className="muted" id="pushDevices">
            Signed up: {data.devices.map((d) => `${d.device ?? "Phone"} (since ${date(d.addedAt)})`).join(" · ")}
          </small>
        ) : null}
        <div className="row" style={{ gap: 8, flexWrap: "wrap" }}>
          <Button variant="outline" id="testPush" onClick={() => void test()}>
            Send a test push to my phone
          </Button>
          {state === "on" ? (
            <Button variant="ghost" id="pushOff" disabled={busy} onClick={() => void off()}>
              Turn off on this phone
            </Button>
          ) : null}
        </div>
      </CardContent>
    </Card>
  );
}
