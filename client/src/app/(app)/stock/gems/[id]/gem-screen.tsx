"use client";

/* One gem – prototype c-products/product-detail.html (Guide 20). Photos, details, certificate, prices (cost, minimum
   and margin for the owner only) and "✓ Mark ready to sell" for a gem that was not ready. */
import Link from "next/link";
import { useState } from "react";
import { Badge, CertBadge, StatusPill } from "@/components/ui/badge";
import { Button, buttonClass } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Modal } from "@/components/ui/dialog";
import { Alert } from "@/components/ui/form";
import { GemPicture, GemSvg } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import { carat, date, inCurrency, lkr, money, num } from "@/lib/format";
import { certKey, grams } from "@/lib/gems";
import { OWNER, SALESPERSON, STAFF } from "@/lib/roles";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

/** Without photos the gem is drawn from three sides, as in the prototype. */
const ANGLES: [string | null, string][] = [
  ["Loose gem", "Top view"],
  [null, "Product"],
  ["Loose gem", "Side view"],
];

export function GemScreen({ code }: { code: string }) {
  const toast = useToast();
  const { role, rates } = useSession();
  const owner = role === OWNER;
  const {
    data: g,
    error,
    setData,
  } = useLoad(() => api.GET("/api/gems/{code}", { params: { path: { code } } }), [code]);
  const [sel, setSel] = useState(0);
  const [scanOpen, setScanOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  if (error && !g)
    return (
      <Card>
        <div className="empty">
          {errorMessage(error)} <Link href="/stock/gems">Back to All gems</Link>
        </div>
      </Card>
    );
  if (!g) return <p className="muted">Loading…</p>;

  const markReady = async () => {
    setBusy(true);
    const { data, error: e } = await api.POST("/api/gems/{code}/ready", { params: { path: { code: g.code } } });
    setBusy(false);
    if (!data) return toast("Not changed", errorMessage(e), "error");
    setData(data);
    toast("Ready to sell", `${data.code} can now be sold. Print its tag if needed.`, "success");
  };

  const photos = g.photos;
  const picked = photos.length ? Math.min(sel, photos.length - 1) : Math.min(sel, ANGLES.length - 1);
  const scanIsImage = g.certScan?.contentType.startsWith("image/");

  return (
    <>
      <PageHead
        title={g.name}
        sub={
          <>
            <span className="mono">{g.code}</span> · {g.gemType} · {g.productType} · <StatusPill status={g.status} />
          </>
        }
        actions={
          <>
            {owner || role === STAFF ? (
              <>
                <Link className={buttonClass("outline")} href={`/stock/gems/${g.code}/history`}>
                  History
                </Link>
                <Link className={buttonClass("outline")} href={`/stock/tags?gems=${g.code}`}>
                  Print tag
                </Link>
              </>
            ) : null}
            {g.certType === "SHOP" && (owner || role === SALESPERSON) ? (
              <Link className={buttonClass("outline")} href={`/stock/shop-certificate?gem=${g.code}`}>
                Shop certificate
              </Link>
            ) : null}
            {g.status === "NOT_READY" && (owner || role === STAFF) ? (
              <Button variant="success" disabled={busy} onClick={() => void markReady()}>
                ✓ Mark ready to sell
              </Button>
            ) : null}
            {owner ? (
              <Link className={buttonClass("sapphire")} href={`/stock/gems/${g.code}/edit`}>
                Edit
              </Link>
            ) : null}
            {g.canSell && (owner || role === SALESPERSON) ? (
              <Link className={buttonClass("success")} href={`/sell/new?gem=${g.code}`}>
                Sell
              </Link>
            ) : null}
          </>
        }
      />
      {g.status === "SOLD" ? (
        <Alert tone="info" className="mb">
          This gem was sold on {date(g.soldAt)}
          {g.saleNo ? (
            <>
              {" "}
              (invoice <span className="mono">{g.saleNo}</span>)
            </>
          ) : null}
          . It cannot be sold again unless it is returned.
        </Alert>
      ) : null}
      {g.status === "NOT_READY" ? (
        <Alert tone="warning" className="mb">
          <b>Not ready to sell yet</b> (e.g. needs cutting or polishing). It cannot be sold until it is marked ready.
        </Alert>
      ) : null}

      <div className="g-1-2 grid">
        <div className="stack">
          <Card>
            <CardContent>
              <div className="gem-pic" style={{ height: 260, width: "100%" }}>
                {photos.length ? (
                  // eslint-disable-next-line @next/next/no-img-element -- photo from the file store
                  <img
                    src={photos[picked].url}
                    alt={`${g.name} photo ${picked + 1}`}
                    style={{ maxHeight: 260, maxWidth: "100%", objectFit: "contain" }}
                  />
                ) : (
                  <GemSvg gem={g.gemType} type={ANGLES[picked][0] ?? g.productType} size={210} />
                )}
              </div>
              <div className="row thumbs mt-s" style={{ justifyContent: "center" }}>
                {photos.length > 1
                  ? photos.map((p, i) => (
                      <button
                        key={p.id}
                        type="button"
                        className={i === picked ? "btn btn-outline active" : "btn btn-outline"}
                        aria-label={`Photo ${i + 1}`}
                        onClick={() => setSel(i)}
                      >
                        <GemPicture gem={g.gemType} photoUrl={p.thumbUrl ?? p.url} size={62} box={62} />
                      </button>
                    ))
                  : !photos.length
                    ? ANGLES.map((a, i) => (
                        <button
                          key={a[1]}
                          type="button"
                          className={i === picked ? "btn btn-outline active" : "btn btn-outline"}
                          aria-label={a[1]}
                          onClick={() => setSel(i)}
                        >
                          <GemSvg gem={g.gemType} type={a[0] ?? g.productType} size={50} />
                        </button>
                      ))
                    : null}
              </div>
              <p className="muted center mt-s" style={{ fontSize: 12 }}>
                {photos.length
                  ? `${photos.length} photo(s) · photos are made smaller (about 300 KB) with thumbnails`
                  : `No photo yet – drawing (${ANGLES[picked][1]})${owner ? " · add photos with Edit" : ""}`}
              </p>
            </CardContent>
          </Card>
        </div>

        <div className="stack">
          <Card>
            <CardHeader title="Gem details" />
            <CardContent>
              <dl className="dl">
                <dt>Product type</dt>
                <dd>{g.productType}</dd>
                <dt>Gem type</dt>
                <dd>{g.gemType}</dd>
                <dt>Carat weight</dt>
                <dd>
                  {carat(g.carat)} <small className="muted">({grams(Number(g.carat))} g)</small>
                </dd>
                <dt>Colour</dt>
                <dd>{g.colour}</dd>
                <dt>Shape / cut</dt>
                <dd>{g.shape}</dd>
                <dt>Treatment</dt>
                <dd>{g.treatment}</dd>
                <dt>Description</dt>
                <dd>{g.description || "—"}</dd>
                <dt>Branch</dt>
                <dd>{g.branchName}</dd>
                <dt>Status</dt>
                <dd>
                  <StatusPill status={g.status} />
                </dd>
                {owner ? (
                  <>
                    <dt>Bought</dt>
                    <dd>{g.purchaseNo ? `${g.purchaseNo}${g.supplierCode ? " · " + g.supplierCode : ""}` : "—"}</dd>
                  </>
                ) : null}
                <dt>Added</dt>
                <dd>
                  {date(g.addedAt)} by {g.addedBy}
                </dd>
              </dl>
            </CardContent>
          </Card>

          <Card>
            <CardHeader title="Certificate" />
            <CardContent>
              <dl className="dl">
                <dt>Type</dt>
                <dd>
                  <CertBadge type={certKey(g.certType)} />
                </dd>
                {g.certType === "LAB" ? (
                  <>
                    <dt>Lab</dt>
                    <dd>{g.certLab}</dd>
                    <dt>Certificate no.</dt>
                    <dd className="mono">{g.certNo}</dd>
                    <dt>Date</dt>
                    <dd>{date(g.certDate)}</dd>
                    <dt>Scanned copy</dt>
                    <dd>
                      {g.certScan ? (
                        scanIsImage ? (
                          <a
                            href="#"
                            onClick={(e) => {
                              e.preventDefault();
                              setScanOpen(true);
                            }}
                          >
                            📄 {g.certScan.name}
                          </a>
                        ) : (
                          <a href={g.certScan.url} target="_blank" rel="noreferrer">
                            📄 {g.certScan.name}
                          </a>
                        )
                      ) : (
                        "—"
                      )}
                    </dd>
                  </>
                ) : null}
                {g.certType === "SHOP" ? (
                  <>
                    <dt>Shop cert. no.</dt>
                    <dd className="mono">{g.certNo}</dd>
                  </>
                ) : null}
              </dl>
            </CardContent>
          </Card>

          <Card>
            <CardHeader title="Prices" />
            <CardContent>
              <dl className="dl">
                <dt>Selling price</dt>
                <dd>
                  <b style={{ fontSize: 18 }}>{lkr(g.price)}</b>
                  <br />
                  <small className="muted">
                    {inCurrency(g.price, "USD", rates)} · {inCurrency(g.price, "EUR", rates)}
                  </small>
                </dd>
                {owner ? (
                  <>
                    <dt>Minimum price</dt>
                    <dd>{lkr(g.min ?? 0)}</dd>
                    <dt>Cost price</dt>
                    <dd>
                      {lkr(g.cost ?? 0)} <Badge tone="gold">owner only</Badge>
                    </dd>
                    <dt>Margin</dt>
                    <dd>{money(Number(g.price) - Number(g.cost ?? 0))}</dd>
                  </>
                ) : null}
              </dl>
              {!owner ? (
                <div className="muted mt-s" style={{ fontSize: 12 }}>
                  Cost and minimum prices are visible to the owner only.
                </div>
              ) : null}
            </CardContent>
          </Card>
        </div>
      </div>

      {g.certScan && scanIsImage ? (
        <Modal
          open={scanOpen}
          onOpenChange={setScanOpen}
          title="Certificate scan"
          desc={`${g.certLab ?? ""} · ${g.certNo ?? ""} · ${date(g.certDate)} · ${num(g.carat)} ct`}
          size="lg"
        >
          {/* eslint-disable-next-line @next/next/no-img-element -- scan from the file store */}
          <img src={g.certScan.url} alt="Certificate scan" style={{ maxWidth: "100%" }} />
        </Modal>
      ) : null}
    </>
  );
}
