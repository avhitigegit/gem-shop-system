"use client";

/* Stock check – prototype d-stock/stock-check.html (BRD D4). Scan every tag in the branch: each gem turns green; the
   page lists what is missing, in the wrong place or unknown. Finish saves the result and tells the owner; missing
   gems are then reported as stock corrections. */
import Link from "next/link";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button, buttonClass } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { useConfirm } from "@/components/ui/dialog";
import { Field, Options, Select } from "@/components/ui/form";
import { GemSvg } from "@/components/ui/gem-picture";
import { ScanBox } from "@/components/ui/scan-box";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import { date } from "@/lib/format";
import { ALL_BRANCHES, branchLabel, useSession } from "@/lib/session";
import type { CheckView, ScanView } from "@/lib/stock";
import { useLoad } from "@/lib/use-load";

export function CheckScreen() {
  const { user, branch, branches, branchName } = useSession();
  const open = branches.filter((b) => b.status !== "INACTIVE");
  const [picked, setPicked] = useState(
    () => user.branch ?? (branch !== ALL_BRANCHES ? branch : (open.find((b) => b.status === "ACTIVE")?.code ?? "")),
  );
  return (
    <>
      <PageHead
        title={`Stock check – ${picked ? branchName(picked) : "choose a branch"}`}
        sub="Scan every tag in the branch. The system lists any gem that is missing or in the wrong place."
      />
      {!user.branch ? (
        <Card className="mb">
          <CardContent>
            <Field label="Branch to check" htmlFor="checkBranch">
              <Select
                id="checkBranch"
                value={picked}
                style={{ maxWidth: 260 }}
                onChange={(e) => setPicked(e.target.value)}
              >
                <Options items={open.map((b) => ({ value: b.code, label: branchLabel(b) }))} placeholder="Select…" />
              </Select>
            </Field>
          </CardContent>
        </Card>
      ) : null}
      {picked ? <Check key={picked} branch={picked} /> : null}
    </>
  );
}

function Check({ branch }: { branch: string }) {
  const toast = useToast();
  const confirm = useConfirm();
  const { data: start } = useLoad(
    () => api.GET("/api/stock-checks/start", { params: { query: { branch } } }),
    [branch],
  );
  const { data: previous, reload } = useLoad(() => api.GET("/api/stock-checks"), [branch]);
  const [found, setFound] = useState<string[]>([]);
  const [wrong, setWrong] = useState<ScanView[]>([]);
  const [unknown, setUnknown] = useState<string[]>([]);
  const [finished, setFinished] = useState<CheckView | null>(null);
  const [busy, setBusy] = useState(false);

  const expected = start?.expected ?? [];
  const missing = expected.filter((g) => !found.includes(g.code));
  const pct = expected.length ? (found.length / expected.length) * 100 : 0;

  const scan = async (code: string) => {
    if (finished) return toast("This check is finished", "Open the page again to start a new check.", "warning");
    const { data, error } = await api.GET("/api/stock-checks/scan", { params: { query: { branch, code } } });
    if (!data) return toast("Scan not checked", errorMessage(error), "error");
    if (data.result === "FOUND") {
      if (found.includes(data.code)) return toast("Already scanned", data.code, "warning");
      setFound((o) => [...o, data.code]);
    } else if (data.result === "WRONG_PLACE") {
      if (!wrong.some((w) => w.code === data.code)) setWrong((o) => [...o, data]);
      toast("Wrong place", `${data.code}: ${data.note}`, "warning");
    } else {
      if (!unknown.includes(data.code)) setUnknown((o) => [...o, data.code]);
      toast("Unknown tag", data.code, "error");
    }
  };

  const finish = async () => {
    const ok = await confirm({
      title: "Finish stock check?",
      text: `${found.length} of ${expected.length} found. ${missing.length} missing, ${wrong.length} in the wrong place. The owner receives the result.`,
      yes: "Finish",
    });
    if (!ok) return;
    setBusy(true);
    const { data, error } = await api.POST("/api/stock-checks", {
      body: { branch, scanned: [...found, ...wrong.map((w) => w.code), ...unknown] },
    });
    setBusy(false);
    if (!data) return toast("Not saved", errorMessage(error), "error");
    setFinished(data);
    reload();
    toast(
      "Stock check saved",
      data.missing.length ? `${data.number} – report missing gems as stock corrections` : `${data.number} – all found`,
      data.missing.length ? "warning" : "success",
    );
  };

  return (
    <div className="g-2-1 grid">
      <div className="stack">
        <Card>
          <CardContent>
            <ScanBox onScan={(c) => void scan(c)} keepFocus />
            <div className="row between mt-s">
              <span className="muted" style={{ fontSize: 12 }}>
                A USB scanner types the code and presses Enter. You can also type the code.
              </span>
              <Button variant="sapphire" disabled={busy || !!finished || !start} onClick={() => void finish()}>
                {finished ? `Saved as ${finished.number}` : "Finish check"}
              </Button>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader
            title="Progress"
            level={3}
            actions={
              <b>
                {found.length} / {expected.length}
              </b>
            }
          />
          <CardContent>
            <div className="bar">
              <i style={{ width: `${pct}%` }} />
            </div>
            {!start ? <p className="muted mt">Loading…</p> : null}
            {start && !expected.length ? <p className="muted mt">No gems on the shelf at this branch.</p> : null}
            <div className="check-grid mt">
              {expected.map((g) => {
                const ok = found.includes(g.code);
                return (
                  <div key={g.code} className={ok ? "check-tile ok" : "check-tile"}>
                    <GemSvg gem={g.gemType} type={g.productType} size={36} />
                    <div className="mono" style={{ fontSize: 10 }}>
                      {g.code}
                    </div>
                    {ok ? <Badge tone="success">✓</Badge> : <Badge>…</Badge>}
                  </div>
                );
              })}
            </div>
          </CardContent>
        </Card>
      </div>
      <div className="stack">
        <Card>
          <CardHeader title="Missing" level={3} />
          <CardContent>
            {missing.length ? (
              <ul className="list">
                {missing.map((g) => (
                  <li key={g.code}>
                    <GemSvg gem={g.gemType} type={g.productType} size={26} />
                    <span style={{ flex: 1, fontSize: 12 }}>
                      <b className="mono">{g.code}</b> {g.name}
                    </span>
                    {finished ? (
                      <Link className={buttonClass("outline", "sm")} href={`/stock/corrections?gem=${g.code}`}>
                        Report
                      </Link>
                    ) : null}
                  </li>
                ))}
              </ul>
            ) : start ? (
              <span className="text-success">Nothing missing ✓</span>
            ) : null}
          </CardContent>
        </Card>
        <Card>
          <CardHeader title="Wrong place / unknown" level={3} />
          <CardContent>
            {wrong.length || unknown.length ? (
              <ul className="list">
                {wrong.map((w) => (
                  <li key={w.code} style={{ fontSize: 12 }}>
                    <Badge tone="warning" className="mono">
                      {w.code}
                    </Badge>
                    <span>{w.note}</span>
                  </li>
                ))}
                {unknown.map((u) => (
                  <li key={u} style={{ fontSize: 12 }}>
                    <Badge tone="danger" className="mono">
                      {u}?
                    </Badge>
                    <span>Not a gem tag</span>
                  </li>
                ))}
              </ul>
            ) : (
              <span className="muted">None</span>
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader title="Previous checks" level={3} />
          <CardContent>
            {previous?.length ? (
              <ul className="list">
                {previous.map((c) => (
                  <li key={c.number} style={{ fontSize: 12 }}>
                    <div style={{ flex: 1 }}>
                      <b>{c.number}</b> · {date(c.checkedAt)} · {c.checkedBy}
                      <br />
                      {c.found}/{c.expected} found · missing: {c.missing.join(", ") || "none"}
                      {c.wrong.length ? ` · wrong place: ${c.wrong.join(", ")}` : ""}
                      {c.notes ? (
                        <>
                          <br />
                          <span className="muted">{c.notes}</span>
                        </>
                      ) : null}
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <span className="muted">No check yet.</span>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
