"use client";

/* Customers – prototype j-customers-promotions/customers.html (Guide 42, 43; BRD J1, J2, J8, F5). Most buyers are tourists.
   Contact details are private (PDPA No. 9 of 2022); offers and birthday wishes only to customers who gave permission.
   Owner and salesperson; Export for the owner. Purchases and spent count the branch at the top. */
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, Kpi } from "@/components/ui/card";
import { DataTable, ExportButtons, Filters, type Column } from "@/components/ui/data-table";
import { Field, Input, Options, Select } from "@/components/ui/form";
import { PageHead } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api/client";
import { birthdayShort, birthdayThisWeek, type CustomerRow } from "@/lib/customers";
import { downloadFile } from "@/lib/files";
import { inCurrency } from "@/lib/format";
import { OWNER } from "@/lib/roles";
import { useSession } from "@/lib/session";
import { useLoad } from "@/lib/use-load";
import { BirthdayCell, CustomerModal, PromotionBadge } from "./customer-parts";

export function CustomersScreen() {
  const router = useRouter();
  const toast = useToast();
  const { role, branch, currency, rates } = useSession();
  const owner = role === OWNER;
  const { data, reload } = useLoad(() => api.GET("/api/customers"), [branch]);
  const [q, setQ] = useState("");
  const [country, setCountry] = useState("");
  const [consent, setConsent] = useState("");
  const [adding, setAdding] = useState(false);
  const cur = (v: number | string) => inCurrency(v, currency, rates);

  const all = useMemo(() => data?.customers ?? [], [data]);
  const countries = useMemo(() => [...new Set(all.map((c) => c.country))].sort(), [all]);
  const week = useMemo(
    () => all.filter(birthdayThisWeek).sort((a, b) => (a.birthdayIn ?? 0) - (b.birthdayIn ?? 0)),
    [all],
  );
  const rows = useMemo(() => {
    const s = q.trim().toLowerCase();
    return all.filter(
      (c) =>
        (!country || c.country === country) &&
        (consent === "" ||
          (consent === "yes" && c.canMessage) ||
          (consent === "no" && !c.canMessage) ||
          (consent === "week" && birthdayThisWeek(c))) &&
        (!s || `${c.name} ${c.email ?? ""} ${c.whatsapp ?? ""} ${c.code}`.toLowerCase().includes(s)),
    );
  }, [all, q, country, consent]);

  const download = async (format: "pdf" | "xlsx") => {
    const ok = await downloadFile(`/api/customers/export?format=${format}`, `Customers.${format}`);
    if (!ok) toast("Not downloaded", "Please try again.", "error");
  };

  const columns: Column<CustomerRow>[] = [
    {
      key: "cust",
      header: "Customer",
      cell: (c) => (
        <>
          <Link href={`/people/customers/${c.code}`}>
            <b>{c.name}</b>
          </Link>
          <br />
          <small className="muted">{c.code}</small>
        </>
      ),
    },
    { key: "country", header: "Country", cell: (c) => c.country },
    { key: "email", header: "Email", cell: (c) => c.email ?? "—" },
    { key: "wa", header: "WhatsApp", cell: (c) => c.whatsapp ?? "—" },
    { key: "bday", header: "Birthday", cell: (c) => <BirthdayCell c={c} /> },
    { key: "buys", header: "Purchases", num: true, cell: (c) => c.purchases },
    { key: "spent", header: "Spent", num: true, cell: (c) => cur(c.spent) },
    { key: "promo", header: "Promotions", cell: (c) => <PromotionBadge c={c} /> },
  ];

  return (
    <>
      <PageHead
        title="Customers"
        sub="Most buyers are tourists. Contact details are private (PDPA No. 9 of 2022) and offers go only to customers who gave permission."
        actions={
          <>
            <Button variant="sapphire" id="addCustomer" onClick={() => setAdding(true)}>
              + Add customer
            </Button>
            {owner ? (
              <ExportButtons
                name="Customers"
                onPdf={() => void download("pdf")}
                onExcel={() => void download("xlsx")}
              />
            ) : null}
          </>
        }
      />
      {!data ? (
        <p className="muted">Loading…</p>
      ) : (
        <>
          <div className="g4 mb grid">
            <Kpi label="Customers" value={all.length} sub={`${countries.length} countries`} />
            <Kpi
              label="Permission for offers"
              value={all.filter((c) => c.canMessage).length}
              sub="can get offers and birthday wishes"
            />
            <Kpi label="Repeat buyers" value={all.filter((c) => c.purchases > 1).length} sub="bought more than once" />
            <Kpi
              label="🎂 Birthdays this week"
              value={week.length}
              sub={
                week.length
                  ? week
                      .slice(0, 3)
                      .map((c) => `${c.name.split(" ")[0]} ${birthdayShort(c.birthday)}`)
                      .join(" · ") + (week.length > 3 ? " …" : "")
                  : "wish sent automatically"
              }
            />
          </div>
          <Card>
            <CardContent>
              <Filters>
                <Field label="Search" htmlFor="cuQ" className="grow">
                  <Input id="cuQ" placeholder="Name, email, phone…" value={q} onChange={(e) => setQ(e.target.value)} />
                </Field>
                <Field label="Country" htmlFor="cuCo">
                  <Select id="cuCo" value={country} onChange={(e) => setCountry(e.target.value)}>
                    <Options items={countries.map((c) => ({ value: c, label: c }))} placeholder="All countries" />
                  </Select>
                </Field>
                <Field label="Show" htmlFor="cuCs">
                  <Select id="cuCs" value={consent} onChange={(e) => setConsent(e.target.value)}>
                    <Options
                      items={[
                        { value: "yes", label: "Permission given" },
                        { value: "no", label: "No permission / unsubscribed" },
                        { value: "week", label: "🎂 Birthdays this week" },
                      ]}
                      placeholder="All customers"
                    />
                  </Select>
                </Field>
              </Filters>
            </CardContent>
            <DataTable
              id="cuT"
              columns={columns}
              rows={rows}
              rowKey={(c) => c.code}
              stackOnPhone
              empty={all.length ? "No customer matches." : "No customers yet. They are added during a sale or here."}
            />
          </Card>
        </>
      )}
      {adding ? (
        <CustomerModal
          customer={null}
          countries={countries}
          onClose={() => setAdding(false)}
          onSaved={(c) => {
            setAdding(false);
            reload();
            router.push(`/people/customers/${c.code}`);
          }}
        />
      ) : null}
    </>
  );
}
