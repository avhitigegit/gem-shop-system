"use client";

/* Add / edit a gem (owner) – prototype c-products/product-form.html (Guide 22, 23). The code is made by the system.
   Sapphires suggest a lab certificate, other gems the shop certificate. Minimum price between cost and selling price. */
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button, buttonClass } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Alert, Check, Field, FormGrid, Input, InputGroup, Options, Select, Textarea } from "@/components/ui/form";
import { GemPicture } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { UploadBox } from "@/components/ui/upload-box";
import { api, errorMessage, fieldErrors } from "@/lib/api/client";
import { ACCEPT, discardFile, uploadFile } from "@/lib/files";
import { lkr } from "@/lib/format";
import { CERT_OPTIONS, suggestedCert, useLists, type CertType, type GemView } from "@/lib/gems";
import { ALL_BRANCHES, branchLabel, useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

type Photo = { id: string; url: string; isNew: boolean };

type Form = {
  name: string;
  productType: string;
  gemType: string;
  carat: string;
  colour: string;
  shape: string;
  treatment: string;
  description: string;
  certType: CertType | "";
  certLab: string;
  certNo: string;
  certDate: string;
  cost: string;
  price: string;
  min: string;
  branch: string;
  notReady: boolean;
  supplier: string;
};

const REQUIRED: (keyof Form)[] = [
  "name",
  "productType",
  "gemType",
  "carat",
  "colour",
  "shape",
  "treatment",
  "certType",
  "cost",
  "price",
  "min",
  "branch",
];

export function GemFormScreen({ code }: { code?: string }) {
  const { data: gem } = useLoad(
    async () => (code ? api.GET("/api/gems/{code}", { params: { path: { code } } }) : { data: null }),
    [code],
  );
  if (code && !gem) return <p className="muted">Loading…</p>;
  return <GemFormInner key={gem?.code ?? "new"} gem={gem ?? null} />;
}

function GemFormInner({ gem }: { gem: GemView | null }) {
  const router = useRouter();
  const toast = useToast();
  const lists = useLists();
  const { branch, branches } = useSession();
  const open = branches.filter((b) => b.status !== "INACTIVE");
  const { data: suppliers } = useLoad(() => api.GET("/api/suppliers"), []);
  const bought = !!gem?.purchaseNo;
  const { data: next } = useLoad(async () => (gem ? { data: null } : api.GET("/api/gems/next-code")), [gem]);
  const code = gem?.code ?? next?.code ?? "GEM-…";

  const [f, setF] = useState<Form>(() => ({
    name: gem?.name ?? "",
    productType: gem?.productType ?? "",
    gemType: gem?.gemType ?? "",
    carat: gem ? String(gem.carat) : "",
    colour: gem?.colour ?? "",
    shape: gem?.shape ?? "",
    treatment: gem?.treatment ?? "",
    description: gem?.description ?? "",
    certType: gem?.certType ?? "",
    certLab: gem?.certLab ?? "",
    certNo: gem?.certType === "LAB" ? (gem.certNo ?? "") : "",
    certDate: gem?.certDate ?? "",
    cost: gem?.cost != null ? String(gem.cost) : "",
    price: gem ? String(gem.price) : "",
    min: gem?.min != null ? String(gem.min) : "",
    branch: gem?.branch ?? (branch !== ALL_BRANCHES ? branch : (open.find((b) => b.status === "ACTIVE")?.code ?? "")),
    notReady: false,
    supplier: gem?.supplierCode ?? "",
  }));
  const [photos, setPhotos] = useState<Photo[]>(() =>
    (gem?.photos ?? []).map((p) => ({ id: p.id, url: p.thumbUrl ?? p.url, isNew: false })),
  );
  const [scan, setScan] = useState<{ id: string; name: string } | null>(() =>
    gem?.certScan ? { id: gem.certScan.id, name: gem.certScan.name } : null,
  );
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  // A new gem starts as "Heated" (the prototype default), or the first treatment in the list.
  const treatments = lists.get("TREATMENT");
  const treatment = f.treatment || (gem ? "" : treatments.includes("Heated") ? "Heated" : (treatments[0] ?? ""));

  const set = <K extends keyof Form>(k: K, v: Form[K]) => {
    setF((o) => ({ ...o, [k]: v }));
    setErrors((o) => ({ ...o, [k]: "" }));
  };

  const suggestion = f.gemType ? suggestedCert(f.gemType) : null;
  const chooseGem = (v: string) => {
    set("gemType", v);
    // a new gem follows the suggestion; an edited gem keeps its certificate (with a "use suggestion" link)
    if (v && (!gem || !f.certType)) set("certType", suggestedCert(v));
  };

  const onPhotos = async (files: File[]) => {
    for (const file of files) {
      if (photos.length >= 10) return toast("Up to 10 photos", "Remove a photo first.", "warning");
      const { data, error } = await uploadFile(file, "PRODUCT_PHOTO");
      if (!data) return toast("Photo not uploaded", errorMessage(error), "error");
      setPhotos((o) => [...o, { id: data.id, url: data.thumbUrl ?? data.url, isNew: true }]);
    }
  };
  const removePhoto = (p: Photo) => {
    setPhotos((o) => o.filter((x) => x.id !== p.id));
    if (p.isNew) void discardFile(p.id);
  };
  const onScan = async (files: File[]) => {
    const { data, error } = await uploadFile(files[0], "CERTIFICATE");
    if (!data) return toast("Scan not uploaded", errorMessage(error), "error");
    setScan({ id: data.id, name: data.name });
  };

  const cost = Number(f.cost) || 0;
  const price = Number(f.price) || 0;
  const min = Number(f.min) || 0;
  const minWrong = f.min !== "" && f.price !== "" && f.cost !== "" && (min > price || min < cost);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const need: (keyof Form)[] = f.certType === "LAB" ? [...REQUIRED, "certLab", "certNo", "certDate"] : REQUIRED;
    const missing: Record<string, string> = {};
    for (const k of need) if (!String(k === "treatment" ? treatment : f[k]).trim()) missing[k] = "Required";
    if (Object.keys(missing).length) {
      setErrors(missing);
      return toast("Please fill in the marked fields", undefined, "error");
    }
    if (minWrong) {
      setErrors({ min: "Must be between cost and selling price." });
      return toast(
        "Check the minimum price",
        "Minimum must be at or above cost and not above the selling price.",
        "error",
      );
    }
    const lab = f.certType === "LAB";
    const body = {
      name: f.name.trim(),
      productType: f.productType,
      gemType: f.gemType,
      carat: Number(f.carat),
      colour: f.colour,
      shape: f.shape,
      treatment,
      description: f.description.trim() || undefined,
      certType: f.certType as CertType,
      certLab: lab ? f.certLab : undefined,
      certNo: lab ? f.certNo.trim() : undefined,
      certDate: lab ? f.certDate : undefined,
      certScan: lab ? scan?.id : undefined,
      photos: photos.map((p) => p.id),
      cost,
      price,
      min,
      branch: f.branch,
      notReady: gem ? undefined : f.notReady,
      supplier: bought ? undefined : f.supplier,
    };
    setBusy(true);
    const { data, error } = gem
      ? await api.PUT("/api/gems/{code}", { params: { path: { code: gem.code } }, body })
      : await api.POST("/api/gems", { body });
    setBusy(false);
    if (!data) {
      setErrors(fieldErrors(error));
      return toast("Not saved", errorMessage(error), "error");
    }
    toast(gem ? "Saved" : "Gem saved", gem ? `${data.code} updated` : `${data.code} – print its tag next`, "success");
    router.push(`/stock/gems/${data.code}`);
  };

  return (
    <>
      <PageHead
        title={gem ? `Edit ${gem.code}` : "Add a gem"}
        sub="The gem code is made automatically. Required fields are marked *."
      />
      {!gem ? (
        <Alert tone="info" className="mb">
          Use this page only for gems <b>not bought from a supplier</b> (for example gems the shop already had). Bought
          a gem? Use{" "}
          <Link href="/stock/purchases/new">
            <b>Buy gems from a supplier</b>
          </Link>{" "}
          – it adds the gems to stock for you.
        </Alert>
      ) : null}
      <form noValidate className="g-2-1 grid" onSubmit={submit}>
        <div className="stack">
          <Card>
            <CardHeader
              title="Gem details"
              actions={
                <Badge tone="navy" className="mono">
                  {code}
                </Badge>
              }
            />
            <CardContent>
              <FormGrid>
                <Field label="Gem code" hint="Made automatically · the QR / barcode tag is printed after saving">
                  <Input className="mono" value={code} readOnly />
                </Field>
                <Field label="Gem name" htmlFor="name" required error={errors.name}>
                  <Input
                    id="name"
                    value={f.name}
                    maxLength={120}
                    placeholder="e.g. Blue Sapphire Ring"
                    onChange={(e) => set("name", e.target.value)}
                  />
                </Field>
                <Field label="Product type" htmlFor="productType" required error={errors.productType}>
                  <Select id="productType" value={f.productType} onChange={(e) => set("productType", e.target.value)}>
                    <Options items={lists.get("PRODUCT_TYPE")} placeholder="Select…" />
                  </Select>
                </Field>
                <Field label="Gem type" htmlFor="gemType" required error={errors.gemType}>
                  <Select id="gemType" value={f.gemType} onChange={(e) => chooseGem(e.target.value)}>
                    <Options items={lists.get("GEM_TYPE")} placeholder="Select…" />
                  </Select>
                </Field>
                <Field label="Carat weight" htmlFor="carat" required error={errors.carat} hint="1 carat = 0.2 grams">
                  <InputGroup addon="ct">
                    <Input
                      id="carat"
                      type="number"
                      min="0.01"
                      step="0.01"
                      inputMode="decimal"
                      value={f.carat}
                      onChange={(e) => set("carat", e.target.value)}
                    />
                  </InputGroup>
                </Field>
                <Field label="Colour" htmlFor="colour" required error={errors.colour}>
                  <Select id="colour" value={f.colour} onChange={(e) => set("colour", e.target.value)}>
                    <Options items={lists.get("COLOUR")} placeholder="Select…" />
                  </Select>
                </Field>
                <Field label="Shape / cut" htmlFor="shape" required error={errors.shape}>
                  <Select id="shape" value={f.shape} onChange={(e) => set("shape", e.target.value)}>
                    <Options items={lists.get("SHAPE")} placeholder="Select…" />
                  </Select>
                </Field>
                <Field
                  label="Treatment"
                  htmlFor="treatment"
                  required
                  error={errors.treatment}
                  hint="Natural / heated / untreated – shown on the invoice"
                >
                  <Select id="treatment" value={treatment} onChange={(e) => set("treatment", e.target.value)}>
                    <Options items={treatments} placeholder="Select…" />
                  </Select>
                </Field>
                <Field label="Description" htmlFor="description" full error={errors.description}>
                  <Textarea
                    id="description"
                    maxLength={1000}
                    value={f.description}
                    placeholder="Clarity, origin, setting, metal…"
                    onChange={(e) => set("description", e.target.value)}
                  />
                </Field>
              </FormGrid>
            </CardContent>
          </Card>

          <Card>
            <CardHeader title="Certificate">
              <p>Sapphires normally have a lab certificate; other gems the shop certificate. You can change it.</p>
            </CardHeader>
            <CardContent>
              <FormGrid>
                <Field
                  label="Certificate type"
                  htmlFor="certType"
                  required
                  error={errors.certType}
                  hint={
                    suggestion ? (
                      <>
                        Suggested for {f.gemType}:{" "}
                        <b>{suggestion === "LAB" ? "Lab certificate" : "Shop certificate"}</b>
                        {f.certType && f.certType !== suggestion ? (
                          <>
                            {" – "}
                            <a
                              href="#"
                              onClick={(e) => {
                                e.preventDefault();
                                set("certType", suggestion);
                              }}
                            >
                              use suggestion
                            </a>
                          </>
                        ) : null}
                      </>
                    ) : undefined
                  }
                >
                  <Select
                    id="certType"
                    value={f.certType}
                    onChange={(e) => set("certType", e.target.value as CertType | "")}
                  >
                    <Options items={CERT_OPTIONS} placeholder="Select…" />
                  </Select>
                </Field>
                <div />
                {f.certType === "LAB" ? (
                  <>
                    <Field
                      label="Lab name"
                      htmlFor="certLab"
                      required
                      error={errors.certLab}
                      hint={
                        <>
                          Edit the list in <Link href="/settings/lists">Settings → Drop-down lists</Link>
                        </>
                      }
                    >
                      <Select id="certLab" value={f.certLab} onChange={(e) => set("certLab", e.target.value)}>
                        <Options items={lists.get("LAB")} placeholder="Select lab…" />
                      </Select>
                    </Field>
                    <Field label="Certificate number" htmlFor="certNo" required error={errors.certNo}>
                      <Input
                        id="certNo"
                        className="mono"
                        maxLength={60}
                        value={f.certNo}
                        onChange={(e) => set("certNo", e.target.value)}
                      />
                    </Field>
                    <Field label="Certificate date" htmlFor="certDate" required error={errors.certDate}>
                      <Input
                        id="certDate"
                        type="date"
                        value={f.certDate}
                        onChange={(e) => set("certDate", e.target.value)}
                      />
                    </Field>
                    <Field label="Scanned copy">
                      <UploadBox accept={ACCEPT.CERTIFICATE} done={scan?.name} onFiles={(fl) => void onScan(fl)}>
                        ⇪ Upload certificate scan (PDF / photo)
                      </UploadBox>
                      {scan ? (
                        <Button type="button" variant="ghost" size="sm" onClick={() => setScan(null)}>
                          ✕ Remove scan
                        </Button>
                      ) : null}
                    </Field>
                  </>
                ) : null}
                {f.certType === "SHOP" ? (
                  <div className="full">
                    <Alert tone="info">
                      The shop certificate is printed from the system with the photo, gem details, code and shop
                      details.
                    </Alert>
                  </div>
                ) : null}
              </FormGrid>
            </CardContent>
          </Card>
        </div>

        <div className="stack">
          <Card>
            <CardHeader title="Photos" />
            <CardContent>
              <div className="gem-pic" style={{ height: 170, width: "100%" }}>
                <GemPicture
                  gem={f.gemType || "Other"}
                  type={f.productType || "Loose gem"}
                  photoUrl={photos[0]?.url}
                  size={130}
                  box={170}
                />
              </div>
              {photos.length ? (
                <div className="row mt-s">
                  {photos.map((p, i) => (
                    <div key={p.id} className="thumb-x">
                      {/* eslint-disable-next-line @next/next/no-img-element -- photo from the file store */}
                      <img
                        src={p.url}
                        loading="lazy"
                        alt={`Photo ${i + 1}`}
                        width={56}
                        height={56}
                        style={{ objectFit: "cover", borderRadius: 8 }}
                      />
                      <button type="button" aria-label={`Remove photo ${i + 1}`} onClick={() => removePhoto(p)}>
                        ✕
                      </button>
                    </div>
                  ))}
                </div>
              ) : null}
              <div className="mt-s">
                <UploadBox
                  accept={ACCEPT.PRODUCT_PHOTO}
                  multiple
                  capture="environment"
                  onFiles={(fl) => void onPhotos(fl)}
                >
                  ⇪ Add photos (several per gem)
                  <br />
                  <small>
                    {photos.length ? `${photos.length} photo(s)` : "Shown on the sale screen and the invoice"}
                  </small>
                </UploadBox>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader title="Prices">
              <p>Set by the owner only. Cost is hidden from staff.</p>
            </CardHeader>
            <CardContent className="stack" style={{ gap: 12 }}>
              <Field label="Cost price (LKR)" htmlFor="cost" required error={errors.cost}>
                <Input
                  id="cost"
                  type="number"
                  min="0"
                  inputMode="decimal"
                  value={f.cost}
                  onChange={(e) => set("cost", e.target.value)}
                />
              </Field>
              <Field label="Selling price (LKR)" htmlFor="price" required error={errors.price}>
                <Input
                  id="price"
                  type="number"
                  min="1"
                  inputMode="decimal"
                  value={f.price}
                  onChange={(e) => set("price", e.target.value)}
                />
              </Field>
              <Field
                label="Minimum price (LKR)"
                htmlFor="min"
                required
                error={errors.min || (minWrong ? "Must be between cost and selling price." : undefined)}
                hint="Lowest price staff may sell at without your approval"
              >
                <Input
                  id="min"
                  type="number"
                  min="1"
                  inputMode="decimal"
                  value={f.min}
                  onChange={(e) => set("min", e.target.value)}
                />
              </Field>
              {price && f.cost !== "" ? (
                <div className="muted" style={{ fontSize: 12 }}>
                  Margin at selling price: <b>{lkr(price - cost)}</b>
                  {f.min !== "" ? (
                    <>
                      {" "}
                      · at minimum: <b>{lkr(min - cost)}</b>
                    </>
                  ) : null}{" "}
                  (before commission)
                </div>
              ) : null}
            </CardContent>
          </Card>

          <Card>
            <CardHeader title="Stock" />
            <CardContent className="stack" style={{ gap: 12 }}>
              <Field label="Branch" htmlFor="branch" required error={errors.branch}>
                <Select id="branch" value={f.branch} onChange={(e) => set("branch", e.target.value)}>
                  <Options items={open.map((b) => ({ value: b.code, label: branchLabel(b) }))} placeholder="Select…" />
                </Select>
              </Field>
              <Field
                label="Supplier"
                htmlFor="supplier"
                error={errors.supplier}
                hint={
                  bought
                    ? `Bought in ${gem?.purchaseNo} – linked automatically.`
                    : "Gems bought in a purchase are linked automatically."
                }
              >
                <Select
                  id="supplier"
                  value={f.supplier}
                  disabled={bought}
                  onChange={(e) => set("supplier", e.target.value)}
                >
                  <Options
                    items={(suppliers ?? []).map((s) => ({ value: s.code, label: s.name }))}
                    placeholder="Not linked"
                  />
                </Select>
              </Field>
              {!gem ? (
                <Check checked={f.notReady} onChange={(v) => set("notReady", v)}>
                  Not ready to sell yet <small className="muted">(e.g. needs cutting or polishing)</small>
                </Check>
              ) : null}
            </CardContent>
          </Card>

          <div className="row">
            <Link className={buttonClass("outline")} href={gem ? `/stock/gems/${gem.code}` : "/stock/gems"}>
              Cancel
            </Link>
            <Button variant="sapphire" size="lg" type="submit" disabled={busy} style={{ flex: 1 }}>
              {busy ? "Saving…" : gem ? "Save changes" : "Save gem"}
            </Button>
          </div>
        </div>
      </form>
    </>
  );
}
