"use client";

/* Send / receive between branches – prototype d-stock/transfers.html (BRD D3, rule 9). Sent gems are "On the way"
   until the receiving branch ticks or scans every one and confirms; only then do they count there. */
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { DataTable, type Column } from "@/components/ui/data-table";
import { Modal } from "@/components/ui/dialog";
import { Alert, Check, Field, FormGrid, Input, Options, Select } from "@/components/ui/form";
import { GemSvg } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import { carat, dateTime, inCurrency } from "@/lib/format";
import { OWNER } from "@/lib/roles";
import { ALL_BRANCHES, branchLabel, useSession } from "@/lib/session";
import type { StockGem, TransferView } from "@/lib/stock";
import { useLoad } from "@/lib/use-load";

export function TransfersScreen() {
  const toast = useToast();
  const { role, branch, branchName, currency, rates } = useSession();
  const owner = role === OWNER;
  const { data, reload } = useLoad(() => api.GET("/api/transfers"), [branch]);
  const [ticks, setTicks] = useState<Record<string, string[]>>({});
  const [sending, setSending] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);

  const ticked = (t: TransferView) => ticks[t.number] ?? [];
  const tick = (t: TransferView, code: string, on: boolean) =>
    setTicks((o) => {
      const have = o[t.number] ?? [];
      return { ...o, [t.number]: on ? [...new Set([...have, code])] : have.filter((c) => c !== code) };
    });
  const scan = (t: TransferView, text: string) => {
    const code = text.trim().toUpperCase().split("|")[0];
    const g = t.gems.find((x) => x.code === code || x.code.endsWith(code.padStart(5, "0")));
    if (!g) return toast("Not in this transfer", code, "error");
    tick(t, g.code, true);
    toast(`✓ ${g.code}`, "ticked");
  };

  const receive = async (t: TransferView) => {
    const have = ticked(t);
    if (have.length !== t.gems.length)
      return toast(
        "Tick every gem first",
        `${have.length} of ${t.gems.length} confirmed. A missing gem must be reported as a stock correction.`,
        "error",
      );
    setBusy(t.number);
    const { data: r, error } = await api.POST("/api/transfers/{number}/receive", {
      params: { path: { number: t.number } },
      body: { gems: have },
    });
    setBusy(null);
    if (!r) return toast("Not received", errorMessage(error), "error");
    toast("Transfer received", `${r.number} – the gems now count at ${branchName(r.to)}`, "success");
    reload();
  };

  const columns = (t: TransferView): Column<StockGem>[] => [
    {
      key: "gem",
      header: "Gem",
      cell: (g) => (
        <span className="row" style={{ flexWrap: "nowrap", gap: 8 }}>
          <GemSvg gem={g.gemType} type={g.productType} size={28} />
          <span>
            <b>{g.name}</b>
            <br />
            <small className="mono muted">{g.code}</small>
          </span>
        </span>
      ),
    },
    { key: "details", header: "Details", cell: (g) => `${carat(g.carat)} · ${g.colour}` },
    ...(owner
      ? [{ key: "value", header: "Value", num: true, cell: (g: StockGem) => inCurrency(g.price ?? 0, currency, rates) }]
      : []),
    {
      key: "state",
      header: t.status === "IN_TRANSIT" && t.canReceive ? "Scan to confirm" : "Status",
      cell: (g) =>
        t.status === "RECEIVED" ? (
          <Badge tone="success">Received</Badge>
        ) : t.canReceive ? (
          <Check checked={ticked(t).includes(g.code)} onChange={(v) => tick(t, g.code, v)}>
            received
          </Check>
        ) : (
          <Badge tone="warning">On the way</Badge>
        ),
    },
  ];

  return (
    <>
      <PageHead
        title="Send / receive between branches"
        sub="Send gems from one branch to the other; the receiving branch confirms each gem."
        actions={
          <Button variant="sapphire" onClick={() => setSending(true)}>
            + Send gems
          </Button>
        }
      />
      <div className="stack">
        {!data ? <p className="muted">Loading…</p> : null}
        {data && !data.length ? (
          <Card>
            <div className="empty">No transfers yet.</div>
          </Card>
        ) : null}
        {data?.map((t) => (
          <Card key={t.number}>
            <CardHeader
              title={
                <>
                  <span className="mono">{t.number}</span> · {branchName(t.from)} → {branchName(t.to)}
                </>
              }
              level={3}
              actions={
                t.status === "IN_TRANSIT" ? (
                  <Badge tone="warning">On the way</Badge>
                ) : (
                  <Badge tone="success">Received</Badge>
                )
              }
            >
              <p>
                Sent {dateTime(t.sentAt)} by {t.sentBy}
                {t.receivedAt ? ` · received ${dateTime(t.receivedAt)} by ${t.receivedBy}` : ""}
                {t.notes ? ` · ${t.notes}` : ""}
              </p>
            </CardHeader>
            <CardContent>
              <div className="stepper">
                <span className="on">1 · Sent</span>
                <span className="on">2 · On the way</span>
                <span className={t.status === "RECEIVED" ? "on" : ""}>3 · Received &amp; confirmed</span>
              </div>
              <DataTable columns={columns(t)} rows={t.gems} rowKey={(g) => g.code} stackOnPhone />
              {t.status === "IN_TRANSIT" && t.canReceive ? (
                <div className="row mt">
                  <Input
                    style={{ maxWidth: 260 }}
                    placeholder="Scan tag to tick…"
                    aria-label={`Scan a tag of ${t.number}`}
                    onKeyDown={(e) => {
                      if (e.key !== "Enter") return;
                      e.preventDefault();
                      scan(t, e.currentTarget.value);
                      e.currentTarget.value = "";
                    }}
                  />
                  <Button variant="success" disabled={busy === t.number} onClick={() => void receive(t)}>
                    Confirm receipt at {branchName(t.to)} ({ticked(t).length}/{t.gems.length})
                  </Button>
                </div>
              ) : t.status === "IN_TRANSIT" ? (
                <p className="muted mt-s" style={{ fontSize: 12 }}>
                  {branchName(t.to)} confirms the gems when they arrive.
                </p>
              ) : null}
            </CardContent>
          </Card>
        ))}
        <Alert tone="info">
          Each branch has its own stock. Gems only count at the receiving branch after it confirms them.
        </Alert>
      </div>
      {sending ? (
        <SendModal
          start={branch !== ALL_BRANCHES ? branch : ""}
          onClose={() => setSending(false)}
          onSent={(t) => {
            setSending(false);
            toast("Transfer sent", `${t.number} – the receiving branch must confirm.`, "success");
            reload();
          }}
        />
      ) : null}
    </>
  );
}

function SendModal({
  start,
  onClose,
  onSent,
}: {
  start: string;
  onClose: () => void;
  onSent: (t: TransferView) => void;
}) {
  const toast = useToast();
  const { branches, user } = useSession();
  const open = branches.filter((b) => b.status !== "INACTIVE");
  const fixed = user.branch;
  const first = fixed ?? (start || (open.find((b) => b.status === "ACTIVE")?.code ?? ""));
  const [from, setFrom] = useState(first);
  const [to, setTo] = useState(() => open.find((b) => b.code !== first)?.code ?? "");
  const [notes, setNotes] = useState("");
  const [q, setQ] = useState("");
  const [chosen, setChosen] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const { data: gems } = useLoad(
    async () =>
      from ? api.GET("/api/transfers/gems", { params: { query: { branch: from } } }) : { data: [] as StockGem[] },
    [from],
  );
  const shown = (gems ?? []).filter((g) => !q || `${g.code} ${g.name}`.toLowerCase().includes(q.toLowerCase()));

  const send = async () => {
    if (!from || !to) return toast("Choose both branches", undefined, "error");
    if (!chosen.length) return toast("Choose at least one gem", undefined, "error");
    setBusy(true);
    const { data, error } = await api.POST("/api/transfers", {
      body: { from, to, gems: chosen, notes: notes.trim() || undefined },
    });
    setBusy(false);
    if (!data) return toast("Not sent", errorMessage(error), "error");
    onSent(data);
  };

  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title="Send gems to the other branch"
      size="lg"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="sapphire" disabled={busy} onClick={() => void send()}>
            {busy ? "Sending…" : `Send transfer (${chosen.length})`}
          </Button>
        </>
      }
    >
      <FormGrid className="mb">
        <Field label="From" htmlFor="from">
          <Select
            id="from"
            value={from}
            disabled={!!fixed}
            onChange={(e) => {
              setFrom(e.target.value);
              setChosen([]);
              if (e.target.value === to) setTo(open.find((b) => b.code !== e.target.value)?.code ?? "");
            }}
          >
            <Options items={open.map((b) => ({ value: b.code, label: branchLabel(b) }))} placeholder="Select…" />
          </Select>
        </Field>
        <Field label="To" htmlFor="to">
          <Select id="to" value={to} onChange={(e) => setTo(e.target.value)}>
            <Options
              items={open.filter((b) => b.code !== from).map((b) => ({ value: b.code, label: branchLabel(b) }))}
              placeholder="Select…"
            />
          </Select>
        </Field>
        <Field label="Note" htmlFor="tnote" full>
          <Input
            id="tnote"
            maxLength={300}
            placeholder="e.g. Display stock for opening"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />
        </Field>
      </FormGrid>
      <div className="row between mb-s">
        <b>Choose gems (only available stock can be sent)</b>
        <Input
          style={{ maxWidth: 200 }}
          placeholder="Search…"
          aria-label="Search gems"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
      </div>
      <div className="pick-list">
        {!from ? <p className="muted">Choose the branch to send from.</p> : null}
        {from && gems && !shown.length ? <p className="muted">No gem to send.</p> : null}
        {shown.map((g) => (
          <Check
            key={g.code}
            checked={chosen.includes(g.code)}
            onChange={(v) => setChosen((o) => (v ? [...o, g.code] : o.filter((c) => c !== g.code)))}
          >
            <span className="row" style={{ flexWrap: "nowrap", gap: 8 }}>
              <GemSvg gem={g.gemType} type={g.productType} size={26} />
              <span>
                <b className="mono">{g.code}</b> {g.name} · {carat(g.carat)}
              </span>
            </span>
          </Check>
        ))}
      </div>
    </Modal>
  );
}
