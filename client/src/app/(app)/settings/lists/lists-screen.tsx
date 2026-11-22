"use client";

/* Drop-down lists – prototype a-setup/lists.html (Guide 11–12). Lists the owner can edit; changes show in every form
   straight away. A value in use cannot be removed – rename it instead (the records using it change too). */
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { DataTable } from "@/components/ui/data-table";
import { Modal, useConfirm } from "@/components/ui/dialog";
import { Field, Input } from "@/components/ui/form";
import { GemSvg } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage, fieldErrors } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { useLoad } from "@/lib/use-load";
import { cn } from "@/lib/utils";

type ListView = components["schemas"]["ListView"];
type ListItem = components["schemas"]["ListItem"];
type ListName = ListView["name"];

const HINT: Partial<Record<ListName, string>> = {
  GEM_TYPE: "New gems that contain “Sapphire” are automatically suggested a lab certificate.",
  PRODUCT_TYPE: "BRD: Loose gem, Ring, Pendant, Earrings, Bracelet, Necklace, Other.",
};

/** "Gem types" → "gem type"; "Shapes / cuts" → "shape". */
const one = (label: string) => label.toLowerCase().replace(/ \/.*/, "").replace(/s$/, "");

export function ListsScreen() {
  const toast = useToast();
  const confirm = useConfirm();
  const { data, setData } = useLoad(() => api.GET("/api/settings/lists"), []);
  const [tab, setTab] = useState<ListName>("GEM_TYPE");
  const [value, setValue] = useState("");
  const [error, setError] = useState("");
  const [renaming, setRenaming] = useState<ListItem | null>(null);

  const list = data?.find((l) => l.name === tab);

  const add = async (e: FormEvent) => {
    e.preventDefault();
    if (!value.trim()) return setError("Required.");
    const { data: saved, error: err } = await api.POST("/api/settings/lists/{list}", {
      params: { path: { list: tab } },
      body: { value },
    });
    if (!saved) {
      setError(fieldErrors(err).value ?? errorMessage(err));
      return toast("Not added", errorMessage(err), "error");
    }
    setData(saved);
    toast("Added", value.trim(), "success");
    setValue("");
  };

  const up = async (it: ListItem) => {
    const { data: saved, error: err } = await api.POST("/api/settings/lists/values/{id}/up", {
      params: { path: { id: it.id } },
    });
    if (saved) setData(saved);
    else toast("Not moved", errorMessage(err), "error");
  };

  const remove = async (it: ListItem) => {
    if (it.usedBy > 0)
      return toast("Cannot remove", `${it.value} is used by ${it.usedBy} ${it.unit}. Rename it instead.`, "error");
    const ok = await confirm({
      title: `Remove “${it.value}”?`,
      text: "It will no longer appear in drop-downs.",
      yes: "Remove",
      danger: true,
    });
    if (!ok) return;
    const { data: saved, error: err } = await api.DELETE("/api/settings/lists/values/{id}", {
      params: { path: { id: it.id } },
    });
    if (!saved) return toast("Cannot remove", errorMessage(err), "error");
    setData(saved);
    toast("Removed", it.value, "success");
  };

  return (
    <>
      <PageHead title="Drop-down lists" sub="Lists you can edit. Changes appear in all forms straight away." />
      {!data || !list ? (
        <p className="muted">Loading…</p>
      ) : (
        <>
          <div className="tabs mb" role="tablist">
            {data.map((l) => (
              <button
                key={l.name}
                type="button"
                role="tab"
                aria-selected={l.name === tab}
                className={cn(l.name === tab && "active")}
                onClick={() => {
                  setTab(l.name);
                  setError("");
                }}
              >
                {l.label} ({l.items.length})
              </button>
            ))}
          </div>
          <div className="g-2-1 grid">
            <Card>
              <CardHeader level={3} title={list.label} />
              <DataTable<ListItem>
                columns={[
                  { key: "n", header: "#", cell: (_, i) => i + 1 },
                  {
                    key: "v",
                    header: "Value",
                    cell: (it) =>
                      tab === "GEM_TYPE" ? (
                        <span className="row" style={{ gap: 6 }}>
                          <GemSvg gem={it.value} type="Loose gem" size={22} />
                          {it.value}
                        </span>
                      ) : (
                        it.value
                      ),
                  },
                  { key: "u", header: "Used by", num: true, cell: (it) => `${it.usedBy} ${it.unit}` },
                  {
                    key: "a",
                    header: "",
                    cell: (it, i) => (
                      <span className="row" style={{ gap: 2, flexWrap: "nowrap" }}>
                        <Button
                          size="sm"
                          variant="ghost"
                          disabled={i === 0}
                          aria-label={`Move ${it.value} up`}
                          onClick={() => void up(it)}
                        >
                          ↑
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => setRenaming(it)}>
                          Rename
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          style={{ color: "var(--danger)" }}
                          onClick={() => void remove(it)}
                        >
                          Remove
                        </Button>
                      </span>
                    ),
                  },
                ]}
                rows={list.items}
                rowKey={(it) => String(it.id)}
              />
            </Card>
            <Card style={{ alignSelf: "start" }}>
              <CardHeader level={3} title="Add value" />
              <form className="card-content stack" noValidate style={{ gap: 10 }} onSubmit={add}>
                <Field label={`New ${one(list.label)}`} htmlFor="newValue" required error={error}>
                  <Input
                    id="newValue"
                    value={value}
                    maxLength={80}
                    onChange={(e) => {
                      setValue(e.target.value);
                      setError("");
                    }}
                  />
                </Field>
                <Button variant="sapphire" type="submit">
                  Add
                </Button>
                {HINT[tab] ? (
                  <p className="muted" style={{ fontSize: 12 }}>
                    {HINT[tab]}
                  </p>
                ) : null}
              </form>
            </Card>
          </div>
        </>
      )}
      {renaming ? (
        <RenameDialog
          item={renaming}
          unit={tab === "EXPENSE_TYPE" ? "Expenses" : "Gems"}
          onClose={() => setRenaming(null)}
          onSaved={(lists) => {
            setRenaming(null);
            setData(lists);
            toast("Renamed", "", "success");
          }}
        />
      ) : null}
    </>
  );
}

function RenameDialog({
  item,
  unit,
  onClose,
  onSaved,
}: {
  item: ListItem;
  unit: string;
  onClose: () => void;
  onSaved: (l: ListView[]) => void;
}) {
  const [v, setV] = useState(item.value);
  const [error, setError] = useState("");
  const save = async () => {
    if (!v.trim()) return setError("Required.");
    const { data, error: err } = await api.PUT("/api/settings/lists/values/{id}", {
      params: { path: { id: item.id } },
      body: { value: v },
    });
    if (!data) return setError(fieldErrors(err).value ?? errorMessage(err));
    onSaved(data);
  };
  return (
    <Modal
      open
      size="sm"
      onOpenChange={(o) => !o && onClose()}
      title="Rename"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="sapphire" onClick={() => void save()}>
            Save
          </Button>
        </>
      }
    >
      <Field label="Value" htmlFor="nv" required error={error}>
        <Input id="nv" value={v} maxLength={80} onChange={(e) => setV(e.target.value)} />
      </Field>
      <p className="muted mt-s" style={{ fontSize: 12 }}>
        {unit} using “{item.value}” are updated too.
      </p>
    </Modal>
  );
}
