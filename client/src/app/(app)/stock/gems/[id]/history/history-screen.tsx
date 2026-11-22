"use client";

/* History of one gem – prototype d-stock/product-history.html (BRD D5). Who added, edited, moved or sold the gem and
   when, newest first. History cannot be edited. Scan or type another code to switch gems. */
import Link from "next/link";
import { useRouter } from "next/navigation";
import { CertBadge, StatusPill } from "@/components/ui/badge";
import { buttonClass } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Alert } from "@/components/ui/form";
import { GemPicture } from "@/components/ui/gem-picture";
import { ScanBox } from "@/components/ui/scan-box";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api/client";
import { carat, dateTime } from "@/lib/format";
import { certKey } from "@/lib/gems";
import { useSession } from "@/lib/session";
import { historyTone } from "@/lib/stock";
import { useLoad } from "@/lib/use-load";

export function HistoryScreen({ code }: { code: string }) {
  const router = useRouter();
  const toast = useToast();
  const { branchName } = useSession();
  const { data: g, error } = useLoad(() => api.GET("/api/gems/{code}", { params: { path: { code } } }), [code]);

  const open = async (text: string) => {
    const { data } = await api.GET("/api/gems/{code}", { params: { path: { code: text } } });
    if (!data) return toast("Not found", `No gem with code ${text}.`, "warning");
    router.push(`/stock/gems/${data.code}/history`);
  };

  const events = [...(g?.history ?? [])].sort((a, b) => b.at.localeCompare(a.at));

  return (
    <>
      <PageHead
        title="Gem history"
        sub="Who added, edited, moved or sold the gem, and when. History cannot be edited."
      />
      <Card className="mb">
        <CardContent>
          <ScanBox
            id="historyScan"
            autoFocus={false}
            placeholder="Scan or type another gem code…"
            onScan={(c) => void open(c)}
          />
        </CardContent>
      </Card>
      {error ? <Alert tone="danger">Gem {code} not found.</Alert> : null}
      {!g && !error ? <p className="muted">Loading…</p> : null}
      {g ? (
        <div className="g-1-2 grid">
          <Card>
            <CardContent className="center">
              <div className="gem-pic" style={{ height: 180, width: "100%" }}>
                <GemPicture gem={g.gemType} type={g.productType} photoUrl={g.photos[0]?.url} size={150} box={180} />
              </div>
              <h2 className="mt-s">{g.name}</h2>
              <div className="mono muted">{g.code}</div>
              <div className="mt-s row" style={{ justifyContent: "center" }}>
                <StatusPill status={g.status} /> <CertBadge type={certKey(g.certType)} />
              </div>
              <p className="muted mt-s">
                {carat(g.carat)} · {g.colour} · {branchName(g.branch)}
              </p>
              <Link className={buttonClass("outline", "sm")} href={`/stock/gems/${g.code}`}>
                Gem details
              </Link>
            </CardContent>
          </Card>
          <Card>
            <CardHeader title="Timeline" level={3} actions={<span className="muted">{events.length} events</span>} />
            <CardContent>
              <ul className="timeline">
                {events.map((h, i) => (
                  <li key={i} className={historyTone(h.action)}>
                    <div className="t-when">
                      {dateTime(h.at)} · {h.user}
                    </div>
                    <b>{h.action}</b>
                    <div className="muted" style={{ fontSize: 12 }}>
                      {h.detail}
                    </div>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        </div>
      ) : null}
    </>
  );
}
