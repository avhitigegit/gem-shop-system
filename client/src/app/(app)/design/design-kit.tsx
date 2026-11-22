"use client";

/* Design kit – every building block of the screens on one page, to compare with the prototype
   and to copy from when building a screen. */
import { useState } from "react";
import { AccessDenied } from "@/components/shell/access-denied";
import { Badge, CertBadge, STATUS, StatusPill } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { BigNumber, Card, CardContent, CardFooter, CardHeader, HomeTile, HubCard, Kpi } from "@/components/ui/card";
import { type Column, DataTable, EmptyState, ExportButtons, Filters } from "@/components/ui/data-table";
import { Modal, useConfirm, useReason } from "@/components/ui/dialog";
import {
  Alert,
  Check,
  Field,
  FormGrid,
  Input,
  InputGroup,
  Options,
  requiredErrors,
  Select,
  Switch,
  Textarea,
} from "@/components/ui/form";
import { GEM_COLORS, GemPicture, GemSvg } from "@/components/ui/gem-picture";
import { ScanBox } from "@/components/ui/scan-box";
import { FlowBar, PageHead, WizardSteps } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { UploadBox } from "@/components/ui/upload-box";
import { allCurrencies, ago, carat, date, dateTime, inCurrency, lkr, money, month, pct } from "@/lib/format";
import { useSession } from "@/lib/session";

const SWATCHES = [
  "navy",
  "navy-2",
  "sapphire",
  "sapphire-soft",
  "gold",
  "gold-soft",
  "background",
  "muted",
  "border",
  "success",
  "success-soft",
  "warning",
  "warning-soft",
  "danger",
  "danger-soft",
];

type Row = { code: string; name: string; gem: string; type: string; ct: number; price: number; status: string };
const ROWS: Row[] = [
  {
    code: "GEM-00101",
    name: "Royal Blue Sapphire",
    gem: "Blue Sapphire",
    type: "Loose stone",
    ct: 3.12,
    price: 950000,
    status: "in_stock",
  },
  {
    code: "GEM-00102",
    name: "Padparadscha Ring",
    gem: "Padparadscha",
    type: "Ring",
    ct: 1.45,
    price: 620000,
    status: "reserved",
  },
  {
    code: "GEM-00103",
    name: "Star Sapphire Pendant",
    gem: "Star Sapphire",
    type: "Pendant",
    ct: 4.8,
    price: 380000,
    status: "sold",
  },
];

export function DesignKit() {
  const toast = useToast();
  const confirm = useConfirm();
  const askReason = useReason();
  const { currency, rates } = useSession();
  const [modal, setModal] = useState(false);
  const [form, setForm] = useState({ name: "", phone: "", consent: false, note: "", type: "" });
  const [errors, setErrors] = useState<Record<string, string | undefined>>({});
  const [on, setOn] = useState(true);
  const [step, setStep] = useState(1);
  const [scanned, setScanned] = useState<string[]>([]);
  const [search, setSearch] = useState("");

  const columns: Column<Row>[] = [
    {
      key: "gem",
      header: "Gem",
      cell: (r) => (
        <div className="prod-cell">
          <div className="thumb">
            <GemSvg gem={r.gem} type={r.type} size={36} />
          </div>
          <div>
            <b>{r.name}</b>
            <span className="muted mono" style={{ fontSize: 11 }}>
              {r.code}
            </span>
          </div>
        </div>
      ),
    },
    { key: "ct", header: "Carat", num: true, cell: (r) => carat(r.ct) },
    { key: "price", header: "Price", num: true, cell: (r) => inCurrency(r.price, currency, rates) },
    { key: "status", header: "Status", cell: (r) => <StatusPill status={r.status} /> },
  ];
  const rows = ROWS.filter((r) => (r.name + r.code).toLowerCase().includes(search.toLowerCase()));

  const save = () => {
    const e = requiredErrors(form, ["name", "phone", "type"]);
    setErrors(e);
    if (Object.keys(e).length) toast("Please complete the required fields", "Fields marked * are required.", "error");
    else toast("Saved", "The form is complete.", "success");
  };

  return (
    <div className="stack">
      <PageHead
        title="Design kit"
        sub="The building blocks of every screen – copied from the prototype."
        actions={<Button variant="sapphire">Main action</Button>}
      />

      <Card>
        <CardHeader title="Colours" />
        <CardContent>
          <div className="g4 grid">
            {SWATCHES.map((s) => (
              <div key={s}>
                <div className="kit-swatch" style={{ background: `var(--${s})` }} />
                <small className="mono">--{s}</small>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader title="Buttons" />
        <CardContent className="row">
          <Button>Navy</Button>
          <Button variant="sapphire">Sapphire</Button>
          <Button variant="gold">+ New sale</Button>
          <Button variant="outline">Outline</Button>
          <Button variant="ghost">Ghost</Button>
          <Button variant="success">Approve</Button>
          <Button variant="destructive">Reject</Button>
          <Button size="sm" variant="outline">
            Small
          </Button>
          <Button size="lg" variant="sapphire">
            Large
          </Button>
          <Button disabled>Disabled</Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader title="Status pills" />
        <CardContent className="row">
          {Object.keys(STATUS).map((s) => (
            <StatusPill key={s} status={s} />
          ))}
          <CertBadge type="lab" />
          <CertBadge type="shop" />
          <CertBadge type="none" />
          <Badge tone="outline">Outline</Badge>
        </CardContent>
      </Card>

      <div className="g4 grid">
        <Kpi label="Today's sales" value={inCurrency(1570000, currency, rates)} sub="3 sales" accent />
        <Kpi label="Gems in stock" value="74" sub="Ella" />
        <BigNumber label="Cash in drawer" value={lkr(38500)} sub="plus USD 200.00 · EUR 0.00" />
        <BigNumber label="Waiting for you" value="2" sub="Tap Approvals" />
      </div>

      <div className="home-tiles">
        <HomeTile href="/design" icon="💎" title="New sale" sub="Scan a tag and sell" primary />
        <HomeTile href="/design" icon="✅" title="Approvals" sub="2 waiting for you" badge={2} />
        <HomeTile href="/design" icon="🔍" title="Find a gem" sub="74 in stock" />
        <HomeTile href="/design" icon="🧾" title="Add expense" sub="With bill photo" />
      </div>
      <div className="hub-cards">
        <HubCard href="/design" icon="🧾" title="Invoices" sub="Print or send again, cancel" count="3 today" />
        <HubCard
          href="/design"
          icon="↩️"
          title="Returns"
          sub="All refunds and exchanges"
          count={{ text: "1 waiting", tone: "warning" }}
        />
      </div>

      <Card>
        <CardHeader title="Notices" />
        <CardContent className="stack">
          <Alert tone="info" icon="ℹ️">
            <b>Exchange rates</b> were updated today.
          </Alert>
          <Alert tone="warning" icon="📜">
            <b>Gem dealer licence expires in 45 days</b> (06/01/2027).
          </Alert>
          <Alert tone="danger" icon="⛔">
            Price is below the minimum – the owner must approve.
          </Alert>
          <Alert tone="success" icon="✅">
            Day closed and sent to the owner.
          </Alert>
        </CardContent>
      </Card>

      <Card>
        <CardHeader title="Form" />
        <CardContent>
          <FormGrid>
            <Field label="Customer name" htmlFor="k-name" required error={errors.name}>
              <Input
                id="k-name"
                value={form.name}
                onChange={(e) => (
                  setForm({ ...form, name: e.target.value }),
                  setErrors({ ...errors, name: undefined })
                )}
              />
            </Field>
            <Field
              label="Phone"
              htmlFor="k-phone"
              required
              hint="With country code, e.g. +44 7700 900123"
              error={errors.phone}
            >
              <Input
                id="k-phone"
                value={form.phone}
                onChange={(e) => (
                  setForm({ ...form, phone: e.target.value }),
                  setErrors({ ...errors, phone: undefined })
                )}
              />
            </Field>
            <Field label="Gem type" htmlFor="k-type" required error={errors.type}>
              <Select
                id="k-type"
                value={form.type}
                onChange={(e) => (
                  setForm({ ...form, type: e.target.value }),
                  setErrors({ ...errors, type: undefined })
                )}
              >
                <Options items={Object.keys(GEM_COLORS)} placeholder="Choose…" />
              </Select>
            </Field>
            <Field label="Agreed price">
              <InputGroup addon="LKR">
                <Input inputMode="decimal" placeholder="0.00" />
              </InputGroup>
            </Field>
            <Field label="Note" full>
              <Textarea
                value={form.note}
                onChange={(e) => setForm({ ...form, note: e.target.value })}
                placeholder="Optional"
              />
            </Field>
            <div className="full row between">
              <Check checked={form.consent} onChange={(v) => setForm({ ...form, consent: v })}>
                Customer agrees to get offers and birthday wishes
              </Check>
              <span className="row">
                <Switch checked={on} onChange={setOn} label="Messages on" /> Messages {on ? "on" : "off"}
              </span>
            </div>
          </FormGrid>
        </CardContent>
        <CardFooter>
          <Button variant="outline" onClick={() => setErrors({})}>
            Clear errors
          </Button>
          <Button variant="sapphire" onClick={save}>
            Save
          </Button>
        </CardFooter>
      </Card>

      <Card>
        <CardHeader title="Dialogs and toasts" />
        <CardContent className="row">
          <Button variant="outline" onClick={() => setModal(true)}>
            Open dialog
          </Button>
          <Button
            variant="outline"
            onClick={async () =>
              toast(
                (await confirm({
                  title: "Cancel this invoice?",
                  text: "The gems go back into stock.",
                  yes: "Cancel invoice",
                  danger: true,
                }))
                  ? "Invoice cancelled"
                  : "Kept",
                undefined,
                "success",
              )
            }
          >
            Confirm dialog
          </Button>
          <Button
            variant="outline"
            onClick={async () => {
              const r = await askReason({ title: "Price below minimum", desc: "The owner must approve this price." });
              if (r) toast("Sent for owner approval", r, "success");
            }}
          >
            Reason dialog
          </Button>
          <Button variant="ghost" onClick={() => toast("Saved", "Normal message")}>
            Toast
          </Button>
          <Button variant="ghost" onClick={() => toast("Sale completed", "INV-00041", "success")}>
            Success toast
          </Button>
          <Button variant="ghost" onClick={() => toast("Not saved", "The backend did not answer.", "error")}>
            Error toast
          </Button>
          <Button variant="ghost" onClick={() => toast("Branch 2", "Opens in January 2027.", "warning")}>
            Warning toast
          </Button>
        </CardContent>
      </Card>
      <Modal
        open={modal}
        onOpenChange={setModal}
        title="Send invoice"
        desc="By email and / or WhatsApp."
        footer={
          <>
            <Button variant="outline" onClick={() => setModal(false)}>
              Cancel
            </Button>
            <Button variant="sapphire" onClick={() => (setModal(false), toast("Invoice sent", undefined, "success"))}>
              Send
            </Button>
          </>
        }
      >
        <Field label="Email" htmlFor="k-mail">
          <Input id="k-mail" type="email" placeholder="customer@example.com" />
        </Field>
      </Modal>

      <Card>
        <CardHeader title="Gem pictures (no photo)" />
        <CardContent>
          <div className="prod-grid">
            {Object.keys(GEM_COLORS).map((g) => (
              <div key={g} className="card prod-card">
                <div className="pic">
                  <GemSvg gem={g} size={96} />
                </div>
                <div className="body">
                  <span className="name">{g}</span>
                </div>
              </div>
            ))}
          </div>
          <div className="row mt">
            {["Ring", "Pendant", "Earrings", "Bracelet", "Necklace"].map((t) => (
              <GemPicture key={t} gem="Ruby" type={t} size={80} />
            ))}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader title="Formats" />
        <CardContent>
          <dl className="dl">
            <dt>Money</dt>
            <dd>
              {lkr(1234)} · {money(1500, "USD")}
            </dd>
            <dt>In the top-bar currency</dt>
            <dd>{inCurrency(450000, currency, rates)}</dd>
            <dt>All three</dt>
            <dd>{allCurrencies(450000, rates)}</dd>
            <dt>Date</dt>
            <dd>
              {date("2026-11-22")} · {dateTime(new Date())}
            </dd>
            <dt>Ago</dt>
            <dd>{ago("2026-11-22T04:30:00Z", new Date("2026-11-22T07:30:00Z"))}</dd>
            <dt>Carat · % · month</dt>
            <dd>
              {carat(1.5)} · {pct(3)} · {month("2026-11")}
            </dd>
          </dl>
        </CardContent>
      </Card>

      <Card>
        <CardHeader title="Table" actions={<ExportButtons tableId="kit-table" name="Gems" />} />
        <CardContent>
          <Filters className="mb">
            <Field label="Search">
              <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Name or code" />
            </Field>
          </Filters>
          <DataTable
            id="kit-table"
            columns={columns}
            rows={rows}
            rowKey={(r) => r.code}
            empty="No gems match the search."
            stackOnPhone
            footer={[
              "Total",
              "",
              inCurrency(
                rows.reduce((a, r) => a + r.price, 0),
                currency,
                rates,
              ),
              "",
            ]}
          />
        </CardContent>
      </Card>
      <EmptyState
        title="No sales yet"
        text="Sales you make today show here."
        action={<Button variant="sapphire">New sale</Button>}
      />

      <div className="g2 grid">
        <Card>
          <CardHeader title="Photo upload" />
          <CardContent>
            <UploadBox
              onFiles={(f) => toast("Photo ready", `${f[0].name} – ${Math.round(f[0].size / 1024)} KB`, "success")}
              capture="environment"
            >
              ⇪ Take a photo of the bill / upload
            </UploadBox>
          </CardContent>
        </Card>
        <Card>
          <CardHeader title="Scan box (USB scanner)" />
          <CardContent>
            <ScanBox autoFocus={false} keepFocus onScan={(c) => setScanned((l) => [c, ...l].slice(0, 5))} />
            <p className="muted mt-s">Scanned: {scanned.join(", ") || "—"}</p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader title="Step bars" />
        <CardContent className="stack">
          <FlowBar
            flow="day"
            role="OWNER"
            here="/money/pay-out"
            status={{ "/money/start-day": { done: true, note: "Done 08:12" } }}
          />
          <FlowBar flow="newstock" role="OWNER" compact />
          <WizardSteps steps={["Gems", "Customer", "Price", "Payment"]} current={step} onStep={setStep} />
          <div className="wiz-foot">
            <Button variant="outline" disabled={step === 0} onClick={() => setStep(step - 1)}>
              ← Back
            </Button>
            <Button variant="sapphire" disabled={step === 3} onClick={() => setStep(step + 1)}>
              Next →
            </Button>
          </div>
        </CardContent>
      </Card>

      <AccessDenied message="Example of the page a role sees when it may not open a screen." />
    </div>
  );
}
