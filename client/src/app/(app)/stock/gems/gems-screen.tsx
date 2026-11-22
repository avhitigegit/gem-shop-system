"use client";

/* All gems – prototype c-products/products.html (Guide 16, 20). Photo grid or table, filters, and a search box
   that also takes a scanned tag (Enter opens the gem). Cost and minimum price only for the owner. */
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { CertBadge, StatusPill } from "@/components/ui/badge";
import { Button, buttonClass } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { DataTable, Filters, type Column } from "@/components/ui/data-table";
import { Field, Input, Options, Select } from "@/components/ui/form";
import { GemPicture } from "@/components/ui/gem-picture";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api/client";
import { carat, inCurrency } from "@/lib/format";
import { CERT_OPTIONS, certKey, STATUS_FILTER, useLists, type CertType, type GemCard } from "@/lib/gems";
import { OWNER, STAFF } from "@/lib/roles";
import { useSession } from "@/lib/session";

const PAGE = 48;
const VIEW_KEY = "gemshop.gemview";

type Filter = { q: string; gemType: string; productType: string; status: string; cert: string };

export function GemsScreen() {
  const router = useRouter();
  const toast = useToast();
  const params = useSearchParams();
  const { role, branch, branchName, currency, rates } = useSession();
  const owner = role === OWNER;
  const lists = useLists();
  const [f, setF] = useState<Filter>({
    q: "",
    gemType: "",
    productType: "",
    status: params.get("status") ?? "stock",
    cert: "",
  });
  const [q, setQ] = useState(""); // what the search sends, a moment after typing stops
  const [view, setView] = useState<"grid" | "table">(() => {
    try {
      return localStorage.getItem(VIEW_KEY) === "table" ? "table" : "grid";
    } catch {
      return "grid"; // private window – keep the photo grid
    }
  });
  const [rows, setRows] = useState<GemCard[] | null>(null);
  const [total, setTotal] = useState<{ count: number; price: number; cost: number | null }>({
    count: 0,
    price: 0,
    cost: null,
  });
  const [page, setPage] = useState(0);
  const key = JSON.stringify([q, f.gemType, f.productType, f.status, f.cert, page, branch]);
  const [answered, setAnswered] = useState("");
  const busy = answered !== key;

  useEffect(() => {
    const t = setTimeout(() => setQ(f.q.trim()), 250);
    return () => clearTimeout(t);
  }, [f.q]);

  useEffect(() => {
    let live = true;
    void api
      .GET("/api/gems", {
        params: {
          query: {
            q: q || undefined,
            gemType: f.gemType || undefined,
            productType: f.productType || undefined,
            status: f.status,
            cert: (f.cert || undefined) as CertType | undefined,
            page,
            size: PAGE,
          },
        },
      })
      .then(({ data }) => {
        if (!live || !data) return;
        setRows((old) => (page === 0 || !old ? data.items : [...old, ...data.items]));
        setTotal({
          count: data.total,
          price: Number(data.totalPrice),
          cost: data.totalCost == null ? null : Number(data.totalCost),
        });
      })
      .finally(() => {
        if (live) setAnswered(key);
      });
    return () => {
      live = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- key holds the same values
  }, [key]);

  const set = (patch: Partial<Filter>) => {
    setPage(0);
    setF((old) => ({ ...old, ...patch }));
  };

  /** Enter in the search box (a scanner presses Enter after the code): open that gem. */
  const scan = async (text: string) => {
    const t = text.trim();
    if (!t) return;
    if (/^(gem-?)?\d{1,5}$/i.test(t)) {
      const { data } = await api.GET("/api/gems/{code}", { params: { path: { code: t } } });
      if (data) return router.push(`/stock/gems/${data.code}`);
      return toast("Not found", `No gem with code ${t.toUpperCase()}.`, "warning");
    }
    if (rows?.length === 1) router.push(`/stock/gems/${rows[0].code}`);
  };

  const chooseView = (v: "grid" | "table") => {
    setView(v);
    try {
      localStorage.setItem(VIEW_KEY, v);
    } catch {
      /* not remembered */
    }
  };

  const price = (n: number | string) => inCurrency(n, currency, rates);

  const columns: Column<GemCard>[] = [
    {
      key: "gem",
      header: "Gem",
      cell: (g) => (
        <Link href={`/stock/gems/${g.code}`} className="row" style={{ color: "inherit", flexWrap: "nowrap", gap: 10 }}>
          <GemPicture gem={g.gemType} type={g.productType} photoUrl={g.photo} size={34} box={40} />
          <span>
            <b>{g.name}</b>
            <br />
            <small className="muted mono">{g.code}</small>
          </span>
        </Link>
      ),
    },
    { key: "type", header: "Type", cell: (g) => g.productType },
    { key: "gemType", header: "Gem type", cell: (g) => g.gemType },
    { key: "carat", header: "Carat", num: true, cell: (g) => carat(g.carat) },
    {
      key: "colour",
      header: "Colour / shape",
      cell: (g) => (
        <>
          {g.colour}
          <br />
          <small className="muted">
            {g.shape} · {g.treatment}
          </small>
        </>
      ),
    },
    { key: "cert", header: "Certificate", cell: (g) => <CertBadge type={certKey(g.certType)} /> },
    ...(owner ? [{ key: "cost", header: "Cost", num: true, cell: (g: GemCard) => price(g.cost ?? 0) }] : []),
    { key: "price", header: "Selling", num: true, cell: (g) => <b>{price(g.price)}</b> },
    ...(owner ? [{ key: "min", header: "Minimum", num: true, cell: (g: GemCard) => price(g.min ?? 0) }] : []),
    { key: "branch", header: "Branch", cell: (g) => branchName(g.branch) },
    { key: "status", header: "Status", cell: (g) => <StatusPill status={g.status} /> },
  ];
  const footer = columns.map((c, i) =>
    i === 0
      ? `${total.count} gems`
      : c.key === "cost"
        ? price(total.cost ?? 0)
        : c.key === "price"
          ? price(total.price)
          : "",
  );

  return (
    <>
      <PageHead
        title="All gems"
        sub="Every gem is unique and has its own code, photos and certificate details."
        actions={
          <>
            {owner ? (
              <Link className={buttonClass("sapphire")} href="/stock/gems/new">
                + Add gem
              </Link>
            ) : null}
            {owner || role === STAFF ? (
              <Link className={buttonClass("outline")} href="/stock/tags">
                Print tags
              </Link>
            ) : null}
          </>
        }
      />
      <Card className="mb">
        <CardContent>
          <Filters>
            <Field label="Search or scan" htmlFor="q" className="grow">
              <Input
                id="q"
                autoFocus
                autoComplete="off"
                spellCheck={false}
                placeholder="Code (GEM-00125), name, colour…"
                value={f.q}
                onChange={(e) => set({ q: e.target.value })}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    void scan(f.q);
                  }
                }}
              />
            </Field>
            <Field label="Gem type" htmlFor="gemType">
              <Select id="gemType" value={f.gemType} onChange={(e) => set({ gemType: e.target.value })}>
                <Options items={lists.get("GEM_TYPE")} placeholder="All gem types" />
              </Select>
            </Field>
            <Field label="Product type" htmlFor="productType">
              <Select id="productType" value={f.productType} onChange={(e) => set({ productType: e.target.value })}>
                <Options items={lists.get("PRODUCT_TYPE")} placeholder="All product types" />
              </Select>
            </Field>
            <Field label="Status" htmlFor="status">
              <Select id="status" value={f.status} onChange={(e) => set({ status: e.target.value })}>
                <Options items={STATUS_FILTER} />
              </Select>
            </Field>
            <Field label="Certificate" htmlFor="cert">
              <Select id="cert" value={f.cert} onChange={(e) => set({ cert: e.target.value })}>
                <Options items={CERT_OPTIONS} placeholder="All" />
              </Select>
            </Field>
            <div className="tabs" role="group" aria-label="Show as">
              <button
                type="button"
                className={view === "grid" ? "active" : undefined}
                onClick={() => chooseView("grid")}
              >
                ▦ Photos
              </button>
              <button
                type="button"
                className={view === "table" ? "active" : undefined}
                onClick={() => chooseView("table")}
              >
                ☰ Table
              </button>
            </div>
          </Filters>
        </CardContent>
      </Card>

      {rows === null ? (
        <p className="muted">Loading…</p>
      ) : view === "grid" ? (
        rows.length ? (
          <div className="prod-grid">
            {rows.map((g) => (
              <Link
                key={g.code}
                className="card prod-card"
                href={`/stock/gems/${g.code}`}
                style={{ color: "inherit", textDecoration: "none" }}
              >
                <div className="pic">
                  <StatusPill status={g.status} />
                  <GemPicture gem={g.gemType} type={g.productType} photoUrl={g.photo} size={110} box={130} />
                </div>
                <div className="body">
                  <span className="code">
                    {g.code} · {g.productType}
                  </span>
                  <span className="name">{g.name}</span>
                  <span className="muted" style={{ fontSize: 12 }}>
                    {carat(g.carat)} · {g.colour} · {g.treatment}
                  </span>
                  <div className="mt-s">
                    <CertBadge type={certKey(g.certType)} />
                  </div>
                  <span className="price">{price(g.price)}</span>
                  {owner ? (
                    <span className="muted" style={{ fontSize: 11 }}>
                      Cost {price(g.cost ?? 0)} · Min {price(g.min ?? 0)}
                    </span>
                  ) : null}
                </div>
              </Link>
            ))}
          </div>
        ) : (
          <Card>
            <div className="empty">No gems match.</div>
          </Card>
        )
      ) : (
        <Card>
          <DataTable
            columns={columns}
            rows={rows}
            rowKey={(g) => g.code}
            empty="No gems match."
            footer={footer}
            stackOnPhone
          />
        </Card>
      )}

      {rows && rows.length < total.count ? (
        <div className="center mt">
          <Button variant="outline" disabled={busy} onClick={() => setPage((p) => p + 1)}>
            {busy ? "Loading…" : `Show more (${rows.length} of ${total.count})`}
          </Button>
        </div>
      ) : rows?.length && view === "grid" ? (
        <p className="muted center mt" style={{ fontSize: 12 }}>
          {total.count} gem(s)
        </p>
      ) : null}

      <p className="mt muted" style={{ fontSize: 12 }}>
        Only the owner can add gems, set or change prices and see cost prices.
      </p>
    </>
  );
}
