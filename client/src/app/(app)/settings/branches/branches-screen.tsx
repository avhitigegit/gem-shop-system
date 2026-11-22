"use client";

/* Branches – prototype a-setup/branches.html (Guide 10). Ella now, Branch 2 from January 2027. Opening a branch is a
   setting change: add it, add its staff, move stock by transfer. Branches are never deleted, only closed. */
import { useState } from "react";
import { Badge, type BadgeTone } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter } from "@/components/ui/card";
import { Modal, useConfirm } from "@/components/ui/dialog";
import { Alert, Field, FormGrid, Input } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage, fieldErrors } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { date, todayInShop } from "@/lib/format";
import { ROLE_LABEL } from "@/lib/roles";
import { reloadSession, useLoad } from "@/lib/use-load";

type BranchView = components["schemas"]["BranchView"];
type Status = BranchView["status"];

const STATUS: Record<Status, [string, BadgeTone]> = {
  ACTIVE: ["Active", "success"],
  OPENING: ["Opening", "gold"],
  INACTIVE: ["Closed", "default"],
};

export function BranchesScreen() {
  const toast = useToast();
  const confirm = useConfirm();
  const { data, reload } = useLoad(() => api.GET("/api/settings/branches"), []);
  const [editing, setEditing] = useState<BranchView | "new" | null>(null);

  const setStatus = async (b: BranchView, status: Status) => {
    const text: Record<Status, [string, string, string]> = {
      ACTIVE: [
        `Open ${b.name}?`,
        "Staff can then be linked to this branch and sales can start. The opening date becomes today if it is later.",
        "Mark as open",
      ],
      INACTIVE: [
        `Close ${b.name}?`,
        "It disappears from the branch picker. Its sales and history stay. Move or switch off its staff logins first.",
        "Close branch",
      ],
      OPENING: [`Set ${b.name} back to opening?`, "", "Set to opening"],
    };
    const [title, t, yes] = text[status];
    if (!(await confirm({ title, text: t, yes, danger: status === "INACTIVE" }))) return;
    const { data: saved, error } = await api.POST("/api/settings/branches/{code}/status", {
      params: { path: { code: b.code } },
      body: { status },
    });
    if (!saved) return toast("Not changed", errorMessage(error), "error");
    toast(
      status === "ACTIVE" ? "Branch is now active" : status === "INACTIVE" ? "Branch closed" : "Saved",
      b.name,
      "success",
    );
    void reload();
    void reloadSession(); // branch picker in the top bar
  };

  return (
    <>
      <PageHead
        title="Branches"
        sub="Each branch has its own stock, sales and cash. The owner sees all branches together."
        actions={
          <Button variant="sapphire" onClick={() => setEditing("new")}>
            + Add branch
          </Button>
        }
      />
      {!data ? (
        <p className="muted">Loading…</p>
      ) : (
        <div className="g2 grid">
          {data.map((b) => (
            <Card key={b.code}>
              <div className="card-header">
                <div>
                  <h2>{b.name}</h2>
                  <p>
                    {b.address ?? "—"}
                    {b.phone ? ` · ${b.phone}` : ""}
                  </p>
                </div>
                <Badge tone={STATUS[b.status][1]}>{STATUS[b.status][0]}</Badge>
              </div>
              <CardContent>
                <dl className="dl">
                  <dt>{b.status === "OPENING" ? "Opens" : "Opened"}</dt>
                  <dd>{date(b.openedOn)}</dd>
                  <dt>Staff</dt>
                  <dd>
                    {b.staff.filter((s) => s.active).length
                      ? b.staff
                          .filter((s) => s.active)
                          .map((s) => `${s.name} (${ROLE_LABEL[s.role]})`)
                          .join(", ")
                      : "None yet – add logins when the branch opens"}
                  </dd>
                </dl>
                {b.status === "OPENING" ? (
                  <Alert tone="info" className="mt">
                    Opening a branch is a setting change: add the branch and its users. No new server and no extra cost.
                    Stock arrives by branch transfer.
                  </Alert>
                ) : null}
              </CardContent>
              <CardFooter>
                <Button variant="outline" size="sm" onClick={() => setEditing(b)}>
                  Edit
                </Button>
                {b.status !== "ACTIVE" ? (
                  <Button variant="success" size="sm" onClick={() => void setStatus(b, "ACTIVE")}>
                    {b.status === "INACTIVE" ? "Open again" : "Mark as open"}
                  </Button>
                ) : null}
                {b.status !== "INACTIVE" ? (
                  <Button variant="ghost" size="sm" onClick={() => void setStatus(b, "INACTIVE")}>
                    Close branch
                  </Button>
                ) : null}
              </CardFooter>
            </Card>
          ))}
        </div>
      )}
      {editing ? (
        <BranchDialog
          branch={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(b, isNew) => {
            setEditing(null);
            toast(isNew ? "Branch added" : "Saved", b.name, "success");
            void reload();
            void reloadSession();
          }}
        />
      ) : null}
    </>
  );
}

function BranchDialog({
  branch,
  onClose,
  onSaved,
}: {
  branch: BranchView | null;
  onClose: () => void;
  onSaved: (b: BranchView, isNew: boolean) => void;
}) {
  const toast = useToast();
  const [f, setF] = useState({
    name: branch?.name ?? "",
    shortName: branch?.shortName ?? "",
    address: branch?.address ?? "",
    phone: branch?.phone ?? "",
    openedOn: branch?.openedOn ?? todayInShop(),
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });

  const save = async () => {
    const e: Record<string, string> = {};
    if (!f.name.trim()) e.name = "Required.";
    if (!f.address.trim()) e.address = "Required.";
    if (!f.openedOn) e.openedOn = "Required.";
    setErrors(e);
    if (Object.keys(e).length) return;
    setBusy(true);
    const body = {
      name: f.name,
      shortName: f.shortName || undefined,
      address: f.address,
      phone: f.phone || undefined,
      openedOn: f.openedOn,
    };
    const r = branch
      ? await api.PUT("/api/settings/branches/{code}", { params: { path: { code: branch.code } }, body })
      : await api.POST("/api/settings/branches", { body });
    setBusy(false);
    if (!r.data) {
      setErrors(fieldErrors(r.error));
      return toast("Not saved", errorMessage(r.error), "error");
    }
    onSaved(r.data, !branch);
  };

  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title={branch ? "Edit branch" : "Add branch"}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="sapphire" disabled={busy} onClick={() => void save()}>
            {busy ? "Saving…" : "Save"}
          </Button>
        </>
      }
    >
      <FormGrid>
        <Field label="Name" htmlFor="bname" required full error={errors.name}>
          <Input id="bname" value={f.name} onChange={set("name")} />
        </Field>
        <Field label="Short name" htmlFor="bshort" hint="Shown in the branch picker, e.g. “Branch 2”." full>
          <Input id="bshort" value={f.shortName} onChange={set("shortName")} />
        </Field>
        <Field label="Address" htmlFor="baddr" required full error={errors.address}>
          <Input id="baddr" value={f.address} onChange={set("address")} />
        </Field>
        <Field label="Phone" htmlFor="bphone" error={errors.phone}>
          <Input id="bphone" value={f.phone} onChange={set("phone")} />
        </Field>
        <Field label="Opening date" htmlFor="bopened" required error={errors.openedOn}>
          <Input id="bopened" type="date" value={f.openedOn} onChange={set("openedOn")} />
        </Field>
        {!branch ? (
          <Alert tone="info" className="full">
            A branch with a later opening date shows as “Opening” until you mark it as open.
          </Alert>
        ) : null}
      </FormGrid>
    </Modal>
  );
}
