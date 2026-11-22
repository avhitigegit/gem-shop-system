"use client";

/* Shop certificate – prototype c-products/shop-certificate.html (Guide 30, BRD C10, rule 6). For gems without a lab
   certificate: photo, gem details, code, shop details and a QR code to check it. Print it, or download the PDF. */
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { QrCode } from "@/components/ui/code-picture";
import { Alert, Options, Select } from "@/components/ui/form";
import { GemPicture } from "@/components/ui/gem-picture";
import { useToast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api/client";
import { downloadFile } from "@/lib/files";
import { carat, date } from "@/lib/format";
import { grams } from "@/lib/gems";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";

export function CertificateScreen() {
  const router = useRouter();
  const toast = useToast();
  const params = useSearchParams();
  const { branch } = useSession();
  const asked = params.get("gem");
  // gems with a shop certificate, newest first – for the picker
  const { data: list } = useLoad(
    () => api.GET("/api/gems", { params: { query: { cert: "SHOP", status: "all", size: 200 } } }),
    [branch],
  );
  const code = asked ?? list?.items.find((g) => g.status !== "SOLD")?.code ?? list?.items[0]?.code ?? null;
  const { data: c, error } = useLoad(
    async () =>
      code ? api.GET("/api/gems/{code}/shop-certificate", { params: { path: { code } } }) : { data: undefined },
    [code],
  );
  const [busy, setBusy] = useState(false);

  if (list && !code)
    return (
      <Card>
        <div className="empty">
          <b>No gem for a shop certificate.</b> Add a gem with “Shop certificate” chosen, then print it here.{" "}
          <Link href="/stock/gems">Go to All gems</Link>
        </div>
      </Card>
    );
  if (error && !c)
    return (
      <Card>
        <div className="empty">{errorMessage(error)}</div>
      </Card>
    );

  const download = async () => {
    if (!c) return;
    setBusy(true);
    const ok = await downloadFile(
      `/api/gems/${encodeURIComponent(c.code)}/shop-certificate/pdf?download=true`,
      `Shop certificate ${c.code}.pdf`,
    );
    setBusy(false);
    if (!ok) toast("Not downloaded", "Please try again.", "error");
  };

  const options = list?.items.map((g) => ({ value: g.code, label: `${g.code} · ${g.name}` })) ?? [];
  if (c && !options.some((o) => o.value === c.code)) options.unshift({ value: c.code, label: `${c.code} · ${c.name}` });

  return (
    <>
      <div className="doc-toolbar no-print">
        <Link className="btn btn-outline" href={c ? `/stock/gems/${c.code}` : "/stock/gems"}>
          ← {c ? c.code : "All gems"}
        </Link>
        <div style={{ flex: 1 }} />
        <Select
          aria-label="Gem"
          value={c?.code ?? ""}
          style={{ width: "auto", maxWidth: "100%" }}
          onChange={(e) => router.replace(`/stock/shop-certificate?gem=${e.target.value}`)}
        >
          <Options items={options} />
        </Select>
        <Button variant="outline" disabled={!c || busy} onClick={() => void download()}>
          ⇩ Download PDF
        </Button>
        <Button variant="sapphire" disabled={!c} onClick={() => window.print()}>
          🖨 Print
        </Button>
      </div>
      {!c ? (
        <p className="muted">Loading…</p>
      ) : (
        <>
          {c.certType !== "SHOP" ? (
            <Alert tone="warning" className="mb no-print">
              {c.code} has a {c.certType === "LAB" ? "lab certificate" : "no certificate"} setting. Shop certificates
              are normally for gems that are not sapphires (rule 6).
            </Alert>
          ) : null}
          <div className="doc cert-doc">
            <div className="center">
              {/* eslint-disable-next-line @next/next/no-img-element -- the shop logo */}
              <img
                src={c.logoUrl ?? "/logo.svg"}
                width={60}
                height={60}
                alt=""
                style={{ objectFit: "contain", display: "block", margin: "0 auto" }}
              />
              <h1 style={{ color: "var(--navy)", marginTop: 6, letterSpacing: ".06em" }}>{c.shopName.toUpperCase()}</h1>
              <div style={{ fontSize: 12, color: "#555" }}>
                {[c.shopAddress, c.shopPhone].filter(Boolean).join(" · ")}
              </div>
              <h2 style={{ color: "var(--gold)", margin: "16px 0 4px", letterSpacing: ".2em", fontSize: 18 }}>
                GEMSTONE CERTIFICATE
              </h2>
              <div className="mono" style={{ fontSize: 12 }}>
                No. {c.certNo} · Gem {c.code}
              </div>
            </div>
            <div className="cert-body mt">
              <div style={{ border: "1px solid #e5e7eb", borderRadius: 12, flexShrink: 0 }}>
                <GemPicture gem={c.gemType} type={c.productType} photoUrl={c.photoUrl} size={170} box={200} />
              </div>
              <table style={{ fontSize: 13 }}>
                <tbody>
                  {(
                    [
                      ["Gem type", c.gemType],
                      ["Product", `${c.productType} – ${c.name}`],
                      ["Weight", `${carat(c.carat)} (${grams(Number(c.carat))} g)`],
                      ["Colour", c.colour],
                      ["Shape / cut", c.shape],
                      ["Treatment", c.treatment],
                      ...(c.description ? [["Description", c.description]] : []),
                    ] as [string, string][]
                  ).map(([k, v]) => (
                    <tr key={k}>
                      <td style={{ color: "#555", width: 130 }}>{k}</td>
                      <td>
                        <b>{v}</b>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="mt" style={{ fontSize: 12 }}>
              We certify that the gemstone described above is a genuine natural {c.gemType.toLowerCase()}, examined by
              our gemmologist. Treatment status is disclosed as above.
            </p>
            <div className="row between mt" style={{ alignItems: "flex-end" }}>
              <div style={{ fontSize: 11, color: "#555" }}>
                Issued {date(c.issuedOn)}
                {c.licenceNo ? (
                  <>
                    <br />
                    NGJA dealer licence {c.licenceNo}
                  </>
                ) : null}
                {c.licenceExpiry ? (
                  <>
                    <br />
                    Valid until {date(c.licenceExpiry)}
                  </>
                ) : null}
              </div>
              <QrCode text={c.qrText} size={70} />
              <div className="center" style={{ fontSize: 11 }}>
                <div style={{ borderTop: "1px solid #333", width: 180, paddingTop: 4 }}>
                  {c.signedBy}
                  <br />
                  Owner &amp; gemmologist
                </div>
              </div>
            </div>
            <div className="center muted mt" style={{ fontSize: 11 }}>
              Scan the QR code{c.shopPhone ? ` or call ${c.shopPhone}` : ""} to check this certificate. This shop
              certificate is not a laboratory report.
            </div>
          </div>
        </>
      )}
    </>
  );
}
