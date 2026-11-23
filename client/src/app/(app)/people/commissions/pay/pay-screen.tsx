"use client";

/* Pay guides & drivers – prototype h-commissions/commissions-payable.html (Guide 40; BRD H4, H5, I2). What each person
   is owed, the commission lines (Not yet paid / Paid / Taken back / All), and recording a payment (CP-…) with the slip.
   A "taken back" line (return / cancelled invoice) comes off the person's next payment. */
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { StatusPill } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader, Kpi } from "@/components/ui/card";
import { DataTable, ExportButtons, Filters, type Column } from "@/components/ui/data-table";
import { Modal } from "@/components/ui/dialog";
import { Field, FormGrid, Input, Options, Select } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import {
  lineStatus,
  PAY_METHODS,
  payeeLabel,
  SHOW_LABEL,
  type CommissionRow,
  type PayableShow,
  type PayMethod,
} from "@/lib/commissions";
import { downloadFile } from "@/lib/files";
import { date, inCurrency, initials, lkr, num, todayInShop } from "@/lib/format";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function PayScreen() {
  const toast = useToast();
  const params = useSearchParams();
  const { branch, currency, rates } = useSession();
  const [show, setShow] = useState<PayableShow>("OWED");
  const [person, setPerson] = useState(params.get("person") ?? "");
  const [picked, setPicked] = useState<Set<number>>(new Set());
  const [paying, setPaying] = useState<CommissionRow[] | null>(null);
  const { data, reload } = useLoad(
    () => api.GET("/api/commissions", { params: { query: { show, person: person || undefined } } }),
    [show, person, branch],
  );
  const cur = (v: number | string) => inCurrency(v, currency, rates);
  const rows = data?.lines ?? [];
  const chosen = rows.filter((r) => r.owed && picked.has(r.id));

  const toggle = (id: number) =>
    setPicked((s) => {
      const n = new Set(s);
      if (n.has(id)) n.delete(id);
      else n.add(id);
      return n;
    });

  const payAll = async (key: string) => {
    const { data: d } = await api.GET("/api/commissions", { params: { query: { show: "OWED", person: key } } });
    if (d) setPaying(d.lines);
  };

  const download = async (format: "pdf" | "xlsx") => {
    const q = new URLSearchParams({ format, show, ...(person ? { person } : {}) });
    const ok = await downloadFile(`/api/commissions/export?${q}`, `Commissions payable.${format}`);
    if (!ok) toast("Not downloaded", "Please try again.", "error");
  };

  const columns: Column<CommissionRow>[] = [
    {
      key: "sel",
      header: "",
      label: "Select",
      cell: (c) =>
        c.owed ? (
          <input
            type="checkbox"
            data-sel={c.id}
            aria-label={`Select ${c.sale} ${c.name}`}
            checked={picked.has(c.id)}
            onChange={() => toggle(c.id)}
          />
        ) : null,
    },
    { key: "date", header: "Date", cell: (c) => date(c.earnedAt) },
    {
      key: "inv",
      header: "Invoice",
      cell: (c) => (
        <Link className="mono" href={`/people/commissions?id=${c.sale}`}>
          {c.sale}
        </Link>
      ),
    },
    {
      key: "payee",
      header: "Payee",
      cell: (c) => (
        <>
          {c.name}
          <br />
          <small className="muted">
            {c.payee === "GUIDE" ? `Guide ${c.pct != null ? num(c.pct, 0) + "%" : ""}` : "Staff level"}
          </small>
        </>
      ),
    },
    {
      key: "amt",
      header: "Amount",
      num: true,
      cell: (c) => <b className={Number(c.amount) < 0 ? "text-danger" : undefined}>{cur(c.amount ?? 0)}</b>,
    },
    {
      key: "st",
      header: "Status",
      cell: (c) => (
        <>
          <StatusPill status={lineStatus(c)} />
          {c.paidAt ? (
            <>
              <br />
              <small className="muted">
                {date(c.paidAt)} · {c.method}
                {c.payment ? (
                  <>
                    {" · "}
                    <Link className="mono" href={`/people/commissions/slip/${c.payment}`}>
                      {c.payment}
                    </Link>
                  </>
                ) : null}
              </small>
            </>
          ) : null}
          {c.note ? (
            <>
              <br />
              <small className="muted">{c.note}</small>
            </>
          ) : null}
        </>
      ),
    },
  ];

  return (
    <>
      <PageHead
        title="Commissions payable"
        sub="Record commission payments (paid / not yet paid) and print a commission slip."
        actions={
          <ExportButtons
            name="Commissions payable"
            onPdf={() => void download("pdf")}
            onExcel={() => void download("xlsx")}
          />
        }
      />
      {!data ? (
        <p className="muted">Loading…</p>
      ) : (
        <>
          <div className="g3 mb grid">
            <Kpi accent label="Not yet paid" value={cur(data.owed)} sub={`${data.owedLines} commission lines`} />
            <Kpi label="Guides & drivers owed" value={cur(data.guidesOwed)} sub={`${data.guidesPeople} people`} />
            <Kpi label="Staff owed" value={cur(data.staffOwed)} sub="usually paid with salary" />
          </div>
          <div className="g-1-2 grid">
            <Card>
              <CardHeader title="Owed by person" level={3} />
              <CardContent>
                <ul className="list" id="owedList">
                  {data.byPerson.length ? (
                    data.byPerson.map((p) => (
                      <li key={p.person} data-person={p.person}>
                        <div className={p.payee === "GUIDE" ? "avatar gold" : "avatar"}>{initials(p.name)}</div>
                        <div style={{ flex: 1 }}>
                          <b>{p.name}</b>
                          <br />
                          <small className="muted">
                            {payeeLabel(p.payee)} · {p.lines} sale(s)
                          </small>
                        </div>
                        <div className="right">
                          <b>{cur(p.amount)}</b>
                          <br />
                          {Number(p.amount) > 0 ? (
                            <Button
                              size="sm"
                              variant="success"
                              className="mt-s"
                              data-payall={p.person}
                              onClick={() => void payAll(p.person)}
                            >
                              Pay all
                            </Button>
                          ) : (
                            <small className="muted">Off the next payment</small>
                          )}
                        </div>
                      </li>
                    ))
                  ) : (
                    <li className="muted">Everything is paid ✓</li>
                  )}
                </ul>
              </CardContent>
            </Card>
            <Card>
              <CardContent>
                <Filters>
                  <Field label="Show" htmlFor="pShow">
                    <Select id="pShow" value={show} onChange={(e) => setShow(e.target.value as PayableShow)}>
                      <Options
                        items={(Object.keys(SHOW_LABEL) as PayableShow[]).map((k) => ({
                          value: k,
                          label: SHOW_LABEL[k],
                        }))}
                      />
                    </Select>
                  </Field>
                  <Field label="Person" htmlFor="pp">
                    <Select id="pp" value={person} onChange={(e) => setPerson(e.target.value)}>
                      <Options
                        placeholder="Everyone"
                        items={data.people.map((p) => ({
                          value: p.key,
                          label: `${p.name} (${p.payee === "GUIDE" ? "guide" : "staff"})`,
                        }))}
                      />
                    </Select>
                  </Field>
                </Filters>
              </CardContent>
              <DataTable
                id="cpT"
                columns={columns}
                rows={rows}
                rowKey={(c) => String(c.id)}
                stackOnPhone
                empty="Nothing here."
              />
              <CardFooter>
                <Button
                  variant="success"
                  id="paySel"
                  onClick={() =>
                    chosen.length ? setPaying(chosen) : toast("Select unpaid commissions first", undefined, "error")
                  }
                >
                  Record payment for selected
                </Button>
              </CardFooter>
            </Card>
          </div>
        </>
      )}
      {paying ? (
        <PayModal
          lines={paying}
          onClose={() => setPaying(null)}
          onPaid={() => {
            setPaying(null);
            setPicked(new Set());
            reload();
          }}
        />
      ) : null}
    </>
  );
}

function PayModal({ lines, onClose, onPaid }: { lines: CommissionRow[]; onClose: () => void; onPaid: () => void }) {
  const toast = useToast();
  const router = useRouter();
  const [paidOn, setPaidOn] = useState(() => todayInShop());
  const [method, setMethod] = useState<PayMethod>("DRAWER");
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState<{ number: string; payee: string } | null>(null);
  const total = lines.reduce((a, l) => a + Number(l.amount ?? 0), 0);
  const names = [...new Set(lines.map((l) => l.name))].join(", ");

  const save = async () => {
    setBusy(true);
    const { data, error } = await api.POST("/api/commissions/pay", {
      body: { lines: lines.map((l) => l.id), paidOn, method },
    });
    setBusy(false);
    if (!data) return toast("Not saved", errorMessage(error), "error");
    toast("Payment recorded", `${data.number} · ${lkr(data.amount)}`, "success");
    setDone({ number: data.number, payee: data.payee });
  };

  if (done)
    return (
      <Modal
        open
        onOpenChange={(o) => !o && onPaid()}
        title="Print commission slip?"
        desc={`Print a slip for ${done.payee} to sign.`}
        footer={
          <>
            <Button variant="outline" onClick={onPaid}>
              Not now
            </Button>
            <Button
              variant="sapphire"
              id="printSlip"
              onClick={() => router.push(`/people/commissions/slip/${done.number}`)}
            >
              Print slip
            </Button>
          </>
        }
      />
    );

  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title="Record commission payment"
      desc={`${names} · ${lines.length} line(s)`}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="success" id="savePay" disabled={busy || total <= 0} onClick={() => void save()}>
            {busy ? "Saving…" : "Save payment"}
          </Button>
        </>
      }
    >
      <div className="kpi card">
        <div className="k-label">Amount</div>
        <div className="k-value" id="payAmount">
          {lkr(total)}
        </div>
        {lines.some((l) => Number(l.amount) < 0) ? (
          <div className="k-sub">Includes commission taken back after a return or a cancelled invoice.</div>
        ) : null}
      </div>
      <FormGrid className="mt">
        <Field label="Paid on" htmlFor="pd" required>
          <Input id="pd" type="date" value={paidOn} max={todayInShop()} onChange={(e) => setPaidOn(e.target.value)} />
        </Field>
        <Field label="Method" htmlFor="pm" required hint="“Cash (drawer)” is included in today’s cash closing">
          <Select id="pm" value={method} onChange={(e) => setMethod(e.target.value as PayMethod)}>
            <Options items={PAY_METHODS.map((m) => ({ value: m.value, label: m.label }))} />
          </Select>
        </Field>
      </FormGrid>
      {total <= 0 ? (
        <p className="mt text-danger">Nothing to pay – the commission taken back is more than what is owed.</p>
      ) : null}
    </Modal>
  );
}
