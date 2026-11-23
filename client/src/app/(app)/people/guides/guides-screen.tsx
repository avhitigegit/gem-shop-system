"use client";

/* Guides & drivers – prototype h-commissions/guides.html (Guide 37; BRD H1, A6, F4). Non-staff who bring tourists; each
   earns their own % of every sale. The owner adds and edits them (new guides start with the default %); the salesperson
   sees them without amounts. */
import Link from "next/link";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/data-table";
import { Modal } from "@/components/ui/dialog";
import { Field, FormGrid, Input, Options, Select } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage, fieldErrors } from "@/lib/api/client";
import { VEHICLES, vehicleIcon, type GuideCard } from "@/lib/commissions";
import { inCurrency, initials, num } from "@/lib/format";
import { OWNER } from "@/lib/roles";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function GuidesScreen() {
  const { role, branch, currency, rates } = useSession();
  const owner = role === OWNER;
  const { data, reload } = useLoad(() => api.GET("/api/guides"), [branch]);
  const [editing, setEditing] = useState<GuideCard | "new" | null>(null);
  const cur = (v: number | string) => inCurrency(v, currency, rates);

  return (
    <>
      <PageHead
        title="Guides & drivers"
        sub="Non-staff who bring tourists. Each earns a percentage of the selling price on every sale."
        actions={
          owner ? (
            <Button variant="sapphire" id="addGuide" onClick={() => setEditing("new")}>
              + Add guide / driver
            </Button>
          ) : null
        }
      />
      {!data ? (
        <p className="muted">Loading…</p>
      ) : !data.guides.length ? (
        <EmptyState
          title="No guides or drivers yet"
          text="Add the guides and drivers who bring customers to the shop."
        />
      ) : (
        <div
          className="prod-grid"
          id="guideCards"
          style={{ gridTemplateColumns: "repeat(auto-fill,minmax(260px,1fr))" }}
        >
          {data.guides.map((g) => (
            <Card key={g.code} data-guide={g.code}>
              <CardContent>
                <div className="row" style={{ flexWrap: "nowrap" }}>
                  <div className="avatar gold" style={{ width: 44, height: 44 }}>
                    {initials(g.name)}
                  </div>
                  <div style={{ flex: 1 }}>
                    <Link href={`/people/guides/${g.code}`}>
                      <b>{g.name}</b>
                    </Link>
                    <br />
                    <Badge tone="outline">
                      {vehicleIcon(g.vehicle)} {g.vehicle}
                    </Badge>{" "}
                    <span className="mono muted" style={{ fontSize: 11 }}>
                      {g.vehicleNo}
                    </span>
                  </div>
                  <div className="right">
                    <div style={{ fontSize: 22, fontWeight: 800, color: "var(--sapphire)" }}>{num(g.pct, 0)}%</div>
                  </div>
                </div>
                <dl className="dl mt" style={{ gridTemplateColumns: "1fr auto", fontSize: 12 }}>
                  <dt>Phone</dt>
                  <dd className="right">{g.phone}</dd>
                  <dt>Sales brought</dt>
                  <dd className="right">
                    {g.sales} · {cur(g.salesValue)}
                  </dd>
                  {g.earned != null ? (
                    <>
                      <dt>Commission earned</dt>
                      <dd className="right">{cur(g.earned)}</dd>
                      <dt>Not yet paid</dt>
                      <dd className={Number(g.unpaid) > 0 ? "right bold text-warning" : "right"}>
                        {cur(g.unpaid ?? 0)}
                      </dd>
                    </>
                  ) : null}
                </dl>
              </CardContent>
              {owner ? (
                <CardFooter>
                  <Button size="sm" variant="ghost" data-edit={g.code} onClick={() => setEditing(g)}>
                    Edit
                  </Button>
                </CardFooter>
              ) : null}
            </Card>
          ))}
        </div>
      )}
      {editing && data ? (
        <GuideModal
          guide={editing === "new" ? null : editing}
          defaultPct={Number(data.defaultPct)}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            reload();
          }}
        />
      ) : null}
    </>
  );
}

/** Add / edit a guide or driver (owner). */
export function GuideModal({
  guide,
  defaultPct,
  onClose,
  onSaved,
}: {
  guide: GuideCard | null;
  defaultPct: number;
  onClose: () => void;
  onSaved: (g: GuideCard) => void;
}) {
  const toast = useToast();
  const [f, setF] = useState({
    name: guide?.name ?? "",
    phone: guide?.phone ?? "",
    vehicle: guide?.vehicle ?? "Tuk-tuk",
    vehicleNo: guide?.vehicleNo ?? "",
    pct: String(guide ? Number(guide.pct) : defaultPct),
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof f, v: string) => {
    setF((x) => ({ ...x, [k]: v }));
    setErrors((e) => ({ ...e, [k]: "" }));
  };

  const save = async () => {
    const e: Record<string, string> = {};
    if (f.name.trim().length < 2) e.name = "Enter the name.";
    if (f.phone.trim().length < 5) e.phone = "Enter the phone number.";
    if (!f.vehicleNo.trim()) e.vehicleNo = "Enter the vehicle number.";
    const p = Number(f.pct);
    if (f.pct === "" || !(p >= 0 && p <= 50)) e.pct = "0 to 50.";
    setErrors(e);
    if (Object.values(e).some(Boolean)) return;
    setBusy(true);
    const body = {
      name: f.name.trim(),
      phone: f.phone.trim(),
      vehicle: f.vehicle,
      vehicleNo: f.vehicleNo.trim(),
      pct: p,
    };
    const { data, error } = guide
      ? await api.PUT("/api/guides/{code}", { params: { path: { code: guide.code } }, body })
      : await api.POST("/api/guides", { body });
    setBusy(false);
    if (!data) {
      setErrors(fieldErrors(error));
      return toast("Not saved", errorMessage(error), "error");
    }
    toast("Saved", `${data.name} · ${num(data.pct, 0)}%`, "success");
    onSaved(data);
  };

  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title={guide ? `Edit ${guide.name}` : "Add guide / driver"}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="sapphire" id="saveGuide" disabled={busy} onClick={() => void save()}>
            {busy ? "Saving…" : "Save"}
          </Button>
        </>
      }
    >
      <FormGrid>
        <Field label="Name" htmlFor="gName" required error={errors.name} full>
          <Input id="gName" value={f.name} onChange={(e) => set("name", e.target.value)} />
        </Field>
        <Field label="Phone" htmlFor="gPhone" required error={errors.phone}>
          <Input id="gPhone" value={f.phone} onChange={(e) => set("phone", e.target.value)} />
        </Field>
        <Field label="Vehicle" htmlFor="gVehicle" required>
          <Select id="gVehicle" value={f.vehicle} onChange={(e) => set("vehicle", e.target.value)}>
            <Options items={[...VEHICLES]} />
          </Select>
        </Field>
        <Field label="Vehicle number" htmlFor="gVehNo" required error={errors.vehicleNo}>
          <Input id="gVehNo" value={f.vehicleNo} onChange={(e) => set("vehicleNo", e.target.value)} />
        </Field>
        <Field
          label="Commission %"
          htmlFor="gPct"
          required
          error={errors.pct}
          hint={`Default ${num(defaultPct, 0)}%. A new % applies to new sales.`}
        >
          <Input
            id="gPct"
            type="number"
            min={0}
            max={50}
            step={0.5}
            value={f.pct}
            onChange={(e) => set("pct", e.target.value)}
          />
        </Field>
      </FormGrid>
    </Modal>
  );
}
