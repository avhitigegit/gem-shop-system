"use client";

/* Activity log – prototype k-owner-control/activity-log.html (Guide 56; BRD K4). Who did what and when, newest first.
   Search, person, from / to (the branch is the one at the top). Read only on purpose – no edit or delete. */
import { useState } from "react";
import { Card, CardFooter, CardContent } from "@/components/ui/card";
import { DataTable, ExportButtons, Filters } from "@/components/ui/data-table";
import { Alert, Field, Input, Options, Select } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { downloadFile } from "@/lib/files";
import { dateTime, initials, todayInShop } from "@/lib/format";
import { ROLE_LABEL } from "@/lib/roles";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

type LogLine = components["schemas"]["LogLine"];

const daysAgo = (n: number) => {
  const d = new Date(todayInShop() + "T00:00:00Z");
  d.setUTCDate(d.getUTCDate() - n);
  return d.toISOString().slice(0, 10);
};

export function ActivityScreen() {
  const toast = useToast();
  const { branch, branchName } = useSession();
  const [q, setQ] = useState("");
  const [user, setUser] = useState("");
  const [from, setFrom] = useState(() => daysAgo(30));
  const [to, setTo] = useState(() => todayInShop());
  const { data: staff } = useLoad(() => api.GET("/api/settings/staff"), []);
  const query = { q: q.trim() || undefined, user: user || undefined, from: from || undefined, to: to || undefined };
  const { data } = useLoad(
    () => api.GET("/api/activity-log", { params: { query: { ...query, size: 300 } } }),
    [q, user, from, to, branch],
  );

  const download = async (format: "pdf" | "xlsx") => {
    const p = new URLSearchParams({ format });
    for (const [k, v] of Object.entries(query)) if (v) p.set(k, v);
    const ok = await downloadFile(`/api/activity-log/export?${p}`, `Activity log.${format}`);
    if (!ok) toast("Not downloaded", "Please try again.", "error");
  };

  return (
    <>
      <PageHead
        title="Activity log"
        sub="A full record of who did what and when."
        actions={
          <ExportButtons name="Activity log" onPdf={() => void download("pdf")} onExcel={() => void download("xlsx")} />
        }
      />
      <Alert icon="🔒" className="mb">
        <b>This log cannot be edited or deleted.</b> Every action is recorded automatically by the system. There are no
        edit or delete buttons on purpose.
      </Alert>
      <Card>
        <CardContent>
          <Filters>
            <Field label="Search" htmlFor="lq" className="grow">
              <Input id="lq" placeholder="Action or detail…" value={q} onChange={(e) => setQ(e.target.value)} />
            </Field>
            <Field label="User" htmlFor="lu">
              <Select id="lu" value={user} onChange={(e) => setUser(e.target.value)}>
                <Options
                  placeholder="All users"
                  items={[
                    ...(staff ?? []).map((u) => ({ value: String(u.id), label: `${u.name} (${ROLE_LABEL[u.role]})` })),
                    { value: "system", label: "System (automatic)" },
                  ]}
                />
              </Select>
            </Field>
            <Field label="From" htmlFor="lf">
              <Input id="lf" type="date" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)} />
            </Field>
            <Field label="To" htmlFor="lt">
              <Input id="lt" type="date" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} />
            </Field>
          </Filters>
        </CardContent>
        {!data ? (
          <p className="muted" style={{ padding: 16 }}>
            Loading…
          </p>
        ) : (
          <DataTable<LogLine>
            id="logT"
            rows={data.items}
            rowKey={(l, i) => `${l.at}-${i}`}
            stackOnPhone
            empty="Nothing found."
            columns={[
              { key: "w", header: "When", cell: (l) => <span className="nowrap">{dateTime(l.at)}</span> },
              {
                key: "u",
                header: "Who",
                cell: (l) => (
                  <div className="row" style={{ gap: 6, flexWrap: "nowrap" }}>
                    <div className="avatar" style={{ width: 24, height: 24, fontSize: 10 }}>
                      {l.system ? "⚙" : initials(l.userName)}
                    </div>
                    {l.userName}
                  </div>
                ),
              },
              { key: "a", header: "Action", cell: (l) => <b>{l.action}</b> },
              { key: "d", header: "Detail", cell: (l) => l.detail },
              { key: "b", header: "Branch", cell: (l) => branchName(l.branch) },
            ]}
          />
        )}
        <CardFooter>
          <span className="muted" id="logCount">
            {data
              ? `${data.total} entries${data.total > data.items.length ? ` (newest ${data.items.length} shown)` : ""}`
              : ""}
          </span>
        </CardFooter>
      </Card>
    </>
  );
}
