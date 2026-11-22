"use client";

/* Suppliers – prototype b-purchasing/suppliers.html (BRD B1). Where every gem came from; purchases are paid in cash.
   The owner adds and edits; the accountant may look. */
import Link from "next/link";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button, buttonClass } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { DataTable, ExportButtons, type Column } from "@/components/ui/data-table";
import { Modal } from "@/components/ui/dialog";
import { Field, FormGrid, Input, Options, Select } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage, fieldErrors } from "@/lib/api/client";
import { inCurrency } from "@/lib/format";
import { ID_TYPES, type IdType, type SupplierRow } from "@/lib/purchasing";
import { OWNER } from "@/lib/roles";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function SuppliersScreen() {
  const { role, currency, rates } = useSession();
  const owner = role === OWNER;
  const { data, reload } = useLoad(() => api.GET("/api/suppliers"), []);
  const [editing, setEditing] = useState<SupplierRow | "new" | null>(null);

  const columns: Column<SupplierRow>[] = [
    {
      key: "name",
      header: "Supplier",
      cell: (s) => (
        <>
          <Link href={`/stock/suppliers/${s.code}`}>
            <b>{s.name}</b>
          </Link>
          <br />
          <small className="muted">
            {s.code}
            {s.contact ? ` · ${s.contact}` : ""}
          </small>
        </>
      ),
    },
    { key: "phone", header: "Phone", cell: (s) => s.phone },
    { key: "address", header: "Address", cell: (s) => s.address },
    {
      key: "id",
      header: "NIC / Business reg.",
      cell: (s) => (
        <>
          <Badge tone="outline">{s.idType}</Badge> <span className="mono">{s.idNo}</span>
        </>
      ),
    },
    { key: "purchases", header: "Purchases", num: true, cell: (s) => s.purchases },
    { key: "total", header: "Total bought", num: true, cell: (s) => inCurrency(s.totalBought, currency, rates) },
    ...(owner
      ? [
          {
            key: "edit",
            header: "",
            cell: (s: SupplierRow) => (
              <Button variant="ghost" size="sm" onClick={() => setEditing(s)}>
                Edit
              </Button>
            ),
          },
        ]
      : []),
  ];

  return (
    <>
      <PageHead
        title="Suppliers"
        sub="Where every gem came from. Purchases are paid in cash."
        actions={
          <>
            {owner ? (
              <>
                <Button variant="sapphire" onClick={() => setEditing("new")}>
                  + Add supplier
                </Button>
                <Link className={buttonClass("outline")} href="/stock/purchases/new">
                  Buy gems
                </Link>
              </>
            ) : null}
            <ExportButtons tableId="suppliers" name="Suppliers" />
          </>
        }
      />
      <Card>
        {data ? (
          <DataTable
            id="suppliers"
            columns={columns}
            rows={data}
            rowKey={(s) => s.code}
            stackOnPhone
            empty="No supplier yet. Add the people and companies you buy gems from."
          />
        ) : (
          <p className="muted card-content">Loading…</p>
        )}
      </Card>
      <SupplierFormModal
        key={editing === "new" ? "new" : (editing?.code ?? "none")}
        supplier={editing}
        onClose={() => setEditing(null)}
        onSaved={() => {
          setEditing(null);
          reload();
        }}
      />
    </>
  );
}

type Form = {
  name: string;
  contact: string;
  phone: string;
  address: string;
  idType: IdType;
  idNo: string;
  notes: string;
};

/** Add / edit a supplier. Used on Suppliers and on One supplier. */
export function SupplierFormModal({
  supplier,
  onClose,
  onSaved,
}: {
  supplier: SupplierRow | "new" | null;
  onClose: () => void;
  onSaved: (s: SupplierRow) => void;
}) {
  const toast = useToast();
  const s = supplier && supplier !== "new" ? supplier : null;
  const [f, setF] = useState<Form>(() => ({
    name: s?.name ?? "",
    contact: s?.contact ?? "",
    phone: s?.phone ?? "",
    address: s?.address ?? "",
    idType: s?.idType ?? "NIC",
    idNo: s?.idNo ?? "",
    notes: s?.notes ?? "",
  }));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const set = <K extends keyof Form>(k: K, v: Form[K]) => {
    setF((o) => ({ ...o, [k]: v }));
    setErrors((o) => ({ ...o, [k]: "" }));
  };

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    const missing: Record<string, string> = {};
    for (const k of ["name", "phone", "address", "idNo"] as const) if (!f[k].trim()) missing[k] = "Required";
    if (Object.keys(missing).length) return setErrors(missing);
    const body = {
      name: f.name.trim(),
      contact: f.contact.trim() || undefined,
      phone: f.phone.trim(),
      address: f.address.trim(),
      idType: f.idType,
      idNo: f.idNo.trim(),
      notes: f.notes.trim() || undefined,
    };
    setBusy(true);
    const { data, error } = s
      ? await api.PUT("/api/suppliers/{code}", { params: { path: { code: s.code } }, body })
      : await api.POST("/api/suppliers", { body });
    setBusy(false);
    if (!data) {
      setErrors(fieldErrors(error));
      return toast("Not saved", errorMessage(error), "error");
    }
    toast("Saved", data.name, "success");
    onSaved(data);
  };

  return (
    <Modal
      open={!!supplier}
      onOpenChange={(o) => !o && onClose()}
      title={s ? `Edit ${s.name}` : "Add supplier"}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="sapphire" type="submit" form="supplier-form" disabled={busy}>
            {busy ? "Saving…" : "Save"}
          </Button>
        </>
      }
    >
      <form id="supplier-form" noValidate onSubmit={save}>
        <FormGrid>
          <Field label="Supplier name" htmlFor="sname" required full error={errors.name}>
            <Input id="sname" maxLength={120} value={f.name} onChange={(e) => set("name", e.target.value)} />
          </Field>
          <Field label="Contact person" htmlFor="contact" error={errors.contact}>
            <Input id="contact" maxLength={120} value={f.contact} onChange={(e) => set("contact", e.target.value)} />
          </Field>
          <Field label="Phone" htmlFor="phone" required error={errors.phone}>
            <Input
              id="phone"
              type="tel"
              maxLength={40}
              value={f.phone}
              onChange={(e) => set("phone", e.target.value)}
            />
          </Field>
          <Field label="Address" htmlFor="address" required full error={errors.address}>
            <Input id="address" maxLength={300} value={f.address} onChange={(e) => set("address", e.target.value)} />
          </Field>
          <Field label="ID type" htmlFor="idType" required error={errors.idType}>
            <Select id="idType" value={f.idType} onChange={(e) => set("idType", e.target.value as IdType)}>
              <Options items={ID_TYPES} />
            </Select>
          </Field>
          <Field label="NIC / BR number" htmlFor="idNo" required error={errors.idNo}>
            <Input
              id="idNo"
              className="mono"
              maxLength={30}
              value={f.idNo}
              onChange={(e) => set("idNo", e.target.value)}
            />
          </Field>
          <Field label="Notes" htmlFor="notes" full error={errors.notes}>
            <Input id="notes" maxLength={500} value={f.notes} onChange={(e) => set("notes", e.target.value)} />
          </Field>
        </FormGrid>
      </form>
    </Modal>
  );
}
