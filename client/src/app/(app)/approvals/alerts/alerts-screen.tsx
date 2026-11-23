"use client";

/* Alerts – prototype k-owner-control/alerts.html (Guide 55; BRD K2). Every sale, below-minimum request, return,
   cancellation, stock change and day closing, newest first. Filter by kind (drop-down) and unread only; mark one or all
   as read. Below: push alerts to the owner's phone and which kinds come (P4-02). */
import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Filters } from "@/components/ui/data-table";
import { Check, Field, Options, Select } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { ago, dateTime } from "@/lib/format";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";
import { PushPanel } from "./push-panel";
import { ALERT_ICON } from "@/lib/alerts";

type AlertView = components["schemas"]["AlertView"];

const GROUPS: { value: string; label: string }[] = [
  { value: "sale", label: "Sales" },
  { value: "below_min", label: "Below-minimum" },
  { value: "return", label: "Returns" },
  { value: "cancel", label: "Cancellations" },
  { value: "stock", label: "Stock changes" },
  { value: "closing", label: "Day closing" },
  { value: "other", label: "Other" },
];

export function AlertsScreen() {
  const toast = useToast();
  const { branch, branchName } = useSession();
  const [group, setGroup] = useState("");
  const [unread, setUnread] = useState(false);
  const { data, reload, setData } = useLoad(
    () => api.GET("/api/alerts", { params: { query: { group: group || undefined, unread, size: 80 } } }),
    [group, unread, branch],
  );

  const markRead = async (a: AlertView) => {
    const { data: d } = await api.POST("/api/alerts/{id}/read", { params: { path: { id: a.id } } });
    if (d && data) setData({ ...data, items: data.items.map((x) => (x.id === a.id ? d : x)) });
  };

  const readAll = async () => {
    const { data: d, error } = await api.POST("/api/alerts/read-all");
    if (!d) return toast("Not saved", errorMessage(error), "error");
    toast("All alerts marked as read", undefined, "success");
    reload();
  };

  return (
    <div className="phone-frame" style={{ maxWidth: 760 }}>
      <PageHead
        title="Alerts"
        sub="Instant alerts for every sale, below-minimum request, return, cancellation and stock change."
        actions={
          <Button variant="outline" id="readAll" onClick={() => void readAll()}>
            Mark all read
          </Button>
        }
      />
      <Filters className="mb">
        <Field label="Show" htmlFor="aGroup">
          <Select id="aGroup" value={group} onChange={(e) => setGroup(e.target.value)}>
            <Options placeholder="All alerts" items={GROUPS} />
          </Select>
        </Field>
        <Field label=" ">
          <Check checked={unread} onChange={setUnread}>
            Unread only
          </Check>
        </Field>
      </Filters>
      <Card>
        <ul className="list" id="alertList" style={{ padding: "0 16px" }}>
          {!data ? (
            <li className="muted" style={{ padding: 20 }}>
              Loading…
            </li>
          ) : data.items.length ? (
            data.items.map((a) => (
              <li
                key={a.id}
                data-alert={a.id}
                className={a.read ? undefined : "unread"}
                style={
                  a.read
                    ? { alignItems: "flex-start" }
                    : {
                        alignItems: "flex-start",
                        background: "linear-gradient(90deg,var(--sapphire-soft),transparent)",
                        margin: "0 -16px",
                        padding: "12px 16px",
                      }
                }
              >
                <span style={{ fontSize: 18 }} aria-hidden>
                  {ALERT_ICON[a.type] ?? "🔔"}
                </span>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 13 }}>
                    {a.link ? (
                      <Link href={a.link} onClick={() => !a.read && void markRead(a)}>
                        {a.text}
                      </Link>
                    ) : (
                      a.text
                    )}
                  </div>
                  <small className="muted">
                    {dateTime(a.at)} · {ago(a.at)} · {branchName(a.branch)}
                  </small>
                </div>
                {a.read ? null : (
                  <Button variant="ghost" size="sm" data-read={a.id} onClick={() => void markRead(a)}>
                    Mark read
                  </Button>
                )}
              </li>
            ))
          ) : (
            <li className="muted" style={{ padding: 20 }}>
              No alerts
            </li>
          )}
        </ul>
      </Card>
      <PushPanel />
    </div>
  );
}
