"use client";

/* Staff logins – prototype a-setup/users.html (Guide 6). Each person has their own login and role, linked to a
   branch. A new login gets an email with a link to choose a password. Logins are switched off, never deleted. */
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardFooter, CardHeader } from "@/components/ui/card";
import { DataTable, type Column } from "@/components/ui/data-table";
import { Modal, useConfirm } from "@/components/ui/dialog";
import { Alert, Check, Field, FormGrid, Input, Options, Select } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage, fieldErrors } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { dateTime, initials } from "@/lib/format";
import { ALL_ROLES, ROLE_LABEL, seesAllBranches, type Role } from "@/lib/roles";
import { ALL_BRANCHES, branchLabel, useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";
import { cn } from "@/lib/utils";

type StaffView = components["schemas"]["StaffView"];

/** What each role can do (BRD §03). 1 = yes, 0.5 = partly / view only, 0 = no. Checked on every API call. */
const MATRIX: [string, number, number, number, number][] = [
  ["Add products, set / change prices", 1, 0, 0, 0],
  ["See cost price, profit & stock value", 1, 0, 0, 0.5],
  ["Sell, take payments, print & send bills", 1, 1, 0, 0],
  ["Select the guide, close the day", 1, 1, 0, 0],
  ["Approve below-minimum, returns, cancellations", 1, 0, 0, 0],
  ["View stock, stock checks, receive products", 1, 0.5, 1, 0],
  ["Record / correct expenses", 1, 0, 0, 1],
  ["Sales, purchases, profit reports", 1, 0, 0, 1],
  ["Download tax pack, close months", 1, 0, 0, 1],
  ["Customers & promotions", 1, 0.5, 0, 0],
  ["Manage staff, commissions, settings", 1, 0, 0, 0],
  ["Delete anything (invoices, log)", 0, 0, 0, 0],
];

const mark = (v: number) =>
  v === 1 ? (
    <span className="bold text-success">✓</span>
  ) : v === 0.5 ? (
    <span className="text-warning" title="partly / view only">
      ◐
    </span>
  ) : (
    <span className="muted">—</span>
  );

export function StaffScreen() {
  const toast = useToast();
  const confirm = useConfirm();
  const { branchName } = useSession();
  const { data, reload } = useLoad(() => api.GET("/api/settings/staff"), []);
  const [editing, setEditing] = useState<StaffView | "new" | null>(null);

  const reset = async (u: StaffView) => {
    const invite = !u.hasPassword;
    const ok = await confirm({
      title: invite ? `Send the invitation again to ${u.name}?` : `Reset ${u.name}'s password?`,
      text: invite
        ? `An email with a link to choose a password is sent to ${u.email}.`
        : `An email with a link to choose a new password is sent to ${u.email}. The old password works until then.`,
      yes: "Send email",
    });
    if (!ok) return;
    const { error } = await api.POST("/api/settings/staff/{id}/reset-password", { params: { path: { id: u.id } } });
    if (error) return toast("Not sent", errorMessage(error), "error");
    toast(invite ? "Invitation sent" : "Password reset email sent", "To " + u.email, "success");
    void reload();
  };

  const unlock = async (u: StaffView) => {
    const { error } = await api.POST("/api/settings/staff/{id}/unlock", { params: { path: { id: u.id } } });
    if (error) return toast("Not unlocked", errorMessage(error), "error");
    toast("Login unlocked", u.name, "success");
    void reload();
  };

  const columns: Column<StaffView>[] = [
    {
      key: "user",
      header: "User",
      cell: (u) => (
        <div className="row" style={{ flexWrap: "nowrap" }}>
          <div className={cn("avatar", u.role === "OWNER" && "gold")}>{initials(u.name)}</div>
          <div>
            <b>{u.name}</b>
            {u.me ? <small className="muted"> (you)</small> : null}
            <br />
            <small className="muted">{u.email}</small>
          </div>
        </div>
      ),
    },
    { key: "role", header: "Role", cell: (u) => <Badge tone="blue">{ROLE_LABEL[u.role]}</Badge> },
    { key: "branch", header: "Branch", cell: (u) => (u.branch ? branchName(u.branch) : "All branches") },
    { key: "phone", header: "Phone", cell: (u) => u.phone ?? "—" },
    { key: "last", header: "Last login", cell: (u) => dateTime(u.lastLoginAt) },
    {
      key: "status",
      header: "Status",
      cell: (u) =>
        !u.active ? (
          <Badge>Switched off</Badge>
        ) : u.locked ? (
          <Badge tone="danger">Locked</Badge>
        ) : !u.hasPassword ? (
          <Badge tone="warning">Invited</Badge>
        ) : (
          <Badge tone="success">Active</Badge>
        ),
    },
    {
      key: "actions",
      header: "",
      cell: (u) => (
        <span className="row" style={{ gap: 4, flexWrap: "nowrap" }}>
          <Button size="sm" variant="outline" onClick={() => setEditing(u)}>
            Edit
          </Button>
          {u.active && u.locked ? (
            <Button size="sm" variant="ghost" onClick={() => void unlock(u)}>
              Unlock
            </Button>
          ) : null}
          {u.active && !u.me ? (
            <Button size="sm" variant="ghost" onClick={() => void reset(u)}>
              {u.hasPassword ? "Reset password" : "Send invitation again"}
            </Button>
          ) : null}
        </span>
      ),
    },
  ];

  return (
    <>
      <PageHead
        title="Staff logins"
        sub="Each person has their own login and role, linked to a branch."
        actions={
          <Button variant="sapphire" onClick={() => setEditing("new")}>
            + Add user
          </Button>
        }
      />
      <Card className="mb">
        {data ? (
          <DataTable columns={columns} rows={data} rowKey={(u) => String(u.id)} stackOnPhone />
        ) : (
          <div className="empty">Loading…</div>
        )}
      </Card>
      <Card>
        <CardHeader level={3}>
          <div>
            <h3>What each role can do</h3>
            <p>Checked on every action by the system, not only hidden on screen.</p>
          </div>
        </CardHeader>
        <DataTable
          columns={[
            { key: "p", header: "Permission", cell: (r) => r[0] },
            ...ALL_ROLES.map((role, i) => ({
              key: role,
              header: ROLE_LABEL[role],
              className: "center",
              cell: (r: (typeof MATRIX)[number]) => mark(r[i + 1] as number),
            })),
          ]}
          rows={MATRIX}
          rowKey={(r) => r[0]}
        />
        <CardFooter style={{ justifyContent: "flex-start" }}>
          <span className="muted" style={{ fontSize: 12 }}>
            Switching a login off signs the person out on every device within a few minutes.
          </span>
        </CardFooter>
      </Card>
      {editing ? (
        <StaffDialog
          user={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(u, isNew) => {
            setEditing(null);
            toast(isNew ? "User invited" : "Saved", u.email, "success");
            void reload();
          }}
        />
      ) : null}
    </>
  );
}

function StaffDialog({
  user,
  onClose,
  onSaved,
}: {
  user: StaffView | null;
  onClose: () => void;
  onSaved: (u: StaffView, isNew: boolean) => void;
}) {
  const { branches } = useSession();
  const toast = useToast();
  const isNew = !user;
  const [f, setF] = useState({
    name: user?.name ?? "",
    email: user?.email ?? "",
    phone: user?.phone ?? "",
    role: (user?.role ?? "SALESPERSON") as Role,
    branch: user?.branch ?? branches[0]?.code ?? ALL_BRANCHES,
    active: user?.active ?? true,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const allBranches = seesAllBranches(f.role);
  const lockRole = !!user && (user.me || user.role === "OWNER");

  const save = async () => {
    const e: Record<string, string> = {};
    if (!f.name.trim()) e.name = "Required.";
    if (!f.email.trim()) e.email = "Required.";
    if (!allBranches && (!f.branch || f.branch === ALL_BRANCHES)) e.branch = "Choose a branch.";
    setErrors(e);
    if (Object.keys(e).length) return;
    setBusy(true);
    const body = {
      name: f.name,
      email: f.email,
      phone: f.phone || undefined,
      role: f.role,
      branch: allBranches ? undefined : f.branch,
      active: f.active,
    };
    const r = user
      ? await api.PUT("/api/settings/staff/{id}", { params: { path: { id: user.id } }, body })
      : await api.POST("/api/settings/staff", { body });
    setBusy(false);
    if (!r.data) {
      setErrors(fieldErrors(r.error));
      return toast("Not saved", errorMessage(r.error), "error");
    }
    onSaved(r.data, isNew);
  };

  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title={isNew ? "Add user" : "Edit " + user.name}
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
        <Field label="Full name" htmlFor="uname" required full error={errors.name}>
          <Input id="uname" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} />
        </Field>
        <Field label="Email (login)" htmlFor="uemail" required error={errors.email}>
          <Input id="uemail" type="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} />
        </Field>
        <Field label="Phone" htmlFor="uphone" error={errors.phone}>
          <Input id="uphone" value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} />
        </Field>
        <Field label="Role" htmlFor="urole" required error={errors.role}>
          <Select
            id="urole"
            value={f.role}
            disabled={lockRole}
            onChange={(e) => setF({ ...f, role: e.target.value as Role })}
          >
            <Options items={ALL_ROLES.map((r) => ({ value: r, label: ROLE_LABEL[r] }))} />
          </Select>
        </Field>
        <Field
          label="Branch"
          htmlFor="ubranch"
          required
          error={errors.branch}
          hint={allBranches ? "Owner and Accountant see all branches." : undefined}
        >
          <Select
            id="ubranch"
            value={allBranches ? ALL_BRANCHES : f.branch}
            disabled={allBranches}
            onChange={(e) => setF({ ...f, branch: e.target.value })}
          >
            <Options
              items={[{ value: ALL_BRANCHES, label: "All branches" }].concat(
                branches.map((b) => ({ value: b.code, label: branchLabel(b) })),
              )}
            />
          </Select>
        </Field>
        <div className="full">
          <Check checked={f.active} disabled={user?.me} onChange={(v) => setF({ ...f, active: v })}>
            Active (can log in)
          </Check>
        </div>
        {isNew ? (
          <Alert tone="info" className="full">
            An invitation email with a link to set a password is sent to the user.
          </Alert>
        ) : null}
        {user?.me ? (
          <Alert tone="info" className="full">
            This is your own login: your role cannot be changed and it cannot be switched off here.
          </Alert>
        ) : null}
      </FormGrid>
    </Modal>
  );
}
