"use client";

/* Print tags – prototype c-products/tag-print.html (Guide 21). Choose gems, label size and whether the price shows;
   the tags open as a PDF the size of the label, ready for the label printer. Scanners read the QR or the barcode. */
import { useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Barcode, QrCode } from "@/components/ui/code-picture";
import { Modal } from "@/components/ui/dialog";
import { Check, Field, Input, Options, Select } from "@/components/ui/form";
import { GemSvg } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { carat, lkr } from "@/lib/format";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

type LabelSize = components["schemas"]["TagPrintForm"]["size"];

const SIZES: { value: LabelSize; label: string }[] = [
  { value: "L60X30", label: "60 × 30 mm label" },
  { value: "L50X25", label: "50 × 25 mm label" },
  { value: "A4_SHEET", label: "A4 sheet (24 labels)" },
];

export function TagsScreen() {
  const toast = useToast();
  const params = useSearchParams();
  const { branch } = useSession();
  const asked = (params.get("gems") ?? "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  // gems in stock, for the "Choose gems" list (and the 6 newest when none were asked for)
  const { data: stock } = useLoad(
    () => api.GET("/api/gems", { params: { query: { status: "stock", size: 200 } } }),
    [branch],
  );
  const [picked, setPicked] = useState<string[] | null>(asked.length ? asked : null);
  const chosen = useMemo(() => picked ?? (stock ? stock.items.slice(0, 6).map((g) => g.code) : []), [picked, stock]);
  const { data: sheet, error } = useLoad(
    async () =>
      chosen.length ? api.GET("/api/gems/tags", { params: { query: { codes: chosen } } }) : { data: undefined },
    [chosen.join(",")],
  );
  const [showPrice, setShowPrice] = useState(true);
  const [size, setSize] = useState<LabelSize>("L60X30");
  const [choosing, setChoosing] = useState(false);
  const [busy, setBusy] = useState(false);
  const tags = chosen.length ? (sheet?.tags ?? []) : [];

  const print = async () => {
    if (!tags.length) return;
    const tab = window.open("", "_blank"); // opened now, so the browser does not block it
    setBusy(true);
    const { data, error: e } = await api.POST("/api/gems/tags/pdf", {
      body: { codes: tags.map((t) => t.code), size, showPrice },
      parseAs: "blob",
    });
    setBusy(false);
    if (!data) {
      tab?.close();
      return toast("Tags not made", errorMessage(e), "error");
    }
    const url = URL.createObjectURL(data as Blob);
    if (tab) tab.location.href = url;
    else window.location.href = url;
    setTimeout(() => URL.revokeObjectURL(url), 120_000);
    toast("Tags ready", `${tags.length} tag(s) opened as a PDF – print them on the label printer.`, "success");
  };

  return (
    <>
      <PageHead
        title="QR / barcode tags"
        sub="Tags print on a label printer from the PDF. Scanners read the Code 39 barcode or the QR code."
        actions={
          <>
            <Button variant="outline" onClick={() => setChoosing(true)}>
              Choose gems ({chosen.length})
            </Button>
            <Button variant="sapphire" disabled={!tags.length || busy} onClick={() => void print()}>
              {busy ? "Making the PDF…" : `🖨 Print ${tags.length} tag(s)`}
            </Button>
          </>
        }
      />
      <Card className="mb no-print">
        <CardContent className="row" style={{ alignItems: "flex-end" }}>
          <Field label="Label size" htmlFor="size">
            <Select id="size" value={size} onChange={(e) => setSize(e.target.value as LabelSize)}>
              <Options items={SIZES} />
            </Select>
          </Field>
          <Check checked={showPrice} onChange={setShowPrice}>
            Show selling price on the tag
          </Check>
          <span className="muted" style={{ fontSize: 12 }}>
            {tags.length} label(s)
          </span>
        </CardContent>
      </Card>
      {error ? (
        <Card>
          <div className="empty">{errorMessage(error)}</div>
        </Card>
      ) : tags.length ? (
        <div className="tag-sheet">
          {tags.map((t) => (
            <div key={t.code} className="tag-label">
              <QrCode text={t.code} size={76} />
              <div style={{ minWidth: 0 }}>
                <b style={{ fontSize: 12, color: "var(--navy)" }}>{sheet?.shopName}</b>
                <div className="mono" style={{ fontSize: 13, fontWeight: 700 }}>
                  {t.code}
                </div>
                <div style={{ whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{t.name}</div>
                <div>
                  {carat(t.carat)} · {t.treatment}
                  {t.certType === "LAB" ? " · Lab cert" : ""}
                </div>
                {showPrice ? <div className="bold">{lkr(t.price).replace(/\.00$/, "")}</div> : null}
              </div>
              <div className="full">
                <Barcode text={t.code} height={30} />
              </div>
            </div>
          ))}
        </div>
      ) : (
        <Card>
          <div className="empty">{stock || picked ? "No gems chosen. Press Choose gems." : "Loading…"}</div>
        </Card>
      )}
      {choosing ? (
        <ChooseGems
          items={stock?.items ?? []}
          chosen={chosen}
          onClose={() => setChoosing(false)}
          onUse={(codes) => {
            setPicked(codes);
            setChoosing(false);
          }}
        />
      ) : null}
    </>
  );
}

type Item = components["schemas"]["GemCard"];

function ChooseGems({
  items,
  chosen,
  onClose,
  onUse,
}: {
  items: Item[];
  chosen: string[];
  onClose: () => void;
  onUse: (codes: string[]) => void;
}) {
  const [sel, setSel] = useState(() => new Set(chosen));
  const [q, setQ] = useState("");
  const shown = items.filter((g) => !q || `${g.code} ${g.name}`.toLowerCase().includes(q.toLowerCase()));
  const toggle = (code: string, on: boolean) =>
    setSel((old) => {
      const n = new Set(old);
      if (on) n.add(code);
      else n.delete(code);
      return n;
    });
  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title="Choose gems to tag"
      desc="Gems in stock in this branch."
      size="lg"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="sapphire" onClick={() => onUse([...sel])}>
            Use selection ({sel.size})
          </Button>
        </>
      }
    >
      <Input placeholder="Find by code or name…" value={q} onChange={(e) => setQ(e.target.value)} />
      <div style={{ maxHeight: "55vh", overflow: "auto", marginTop: 8 }}>
        {shown.map((g) => (
          <label
            key={g.code}
            className="check"
            style={{ padding: "6px 0", borderBottom: "1px solid var(--border)", alignItems: "center" }}
          >
            <input type="checkbox" checked={sel.has(g.code)} onChange={(e) => toggle(g.code, e.target.checked)} />
            <GemSvg gem={g.gemType} type={g.productType} size={28} />
            <span>
              <b className="mono">{g.code}</b> {g.name}
            </span>
          </label>
        ))}
        {!shown.length ? <div className="empty">No gems match.</div> : null}
      </div>
    </Modal>
  );
}
