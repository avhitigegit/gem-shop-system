"use client";

/* The app frame – the same as the prototype (prototype/assets/js/layout.js):
   navy side menu (8 sections, gold + New sale, the open section's pages), top bar (branch, currency,
   bell, user menu), the "where am I" back bar, the step bar, and the role check for the page. */
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { AccessDenied } from "@/components/shell/access-denied";
import { ChangePasswordDialog } from "@/components/shell/change-password";
import { FlowBar } from "@/components/ui/steps";
import { useToast } from "@/components/ui/toast";
import { useApprovalsWaiting } from "@/lib/approvals";
import { ago, CURRENCIES, type Currency, initials } from "@/lib/format";
import {
  allItems,
  firstPage,
  flowOf,
  menuSections,
  pageInfo,
  type Section,
  sectionHref,
  visibleItems,
  type NavItem,
} from "@/lib/nav";
import { OWNER, ROLE_LABEL, type Role, SALESPERSON, seesAllBranches } from "@/lib/roles";
import { ALL_BRANCHES, branchLabel, useAuth, useSession } from "@/lib/session";
import { cn } from "@/lib/utils";

const NAV_KEY = "gemshop.nav.v1";

/** Signed in → the frame; not signed in → the login page (and back to this page afterwards). */
export function AppShell({ children }: { children: ReactNode }) {
  const path = usePathname() || "/";
  const { status } = useAuth();
  const router = useRouter();
  useEffect(() => {
    if (status === "signedOut") router.replace(path === "/" ? "/login" : `/login?next=${encodeURIComponent(path)}`);
  }, [status, path, router]);
  if (status !== "signedIn") return <div className="app" aria-busy="true" />;
  return <Frame path={path}>{children}</Frame>;
}

function Frame({ path, children }: { path: string; children: ReactNode }) {
  const session = useSession();
  const [navOpen, setNavOpen] = useState(false);

  useEffect(() => {
    document.body.classList.toggle("nav-open", navOpen);
  }, [navOpen]);
  // a new page closes the phone menu
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => setNavOpen(false), [path]);

  const info = pageInfo(path);
  const role = session.role;
  const allowed = info.section.roles.includes(role) && (!info.roles || info.roles.includes(role));
  const flow = allowed ? flowOf(path) : null;

  return (
    <>
      <div className="scrim" onClick={() => setNavOpen(false)} aria-hidden="true" />
      <div className="app">
        <Sidebar path={path} />
        <div className="main">
          <Topbar onMenu={() => setNavOpen((o) => !o)} />
          <main className="content" id="page">
            {allowed && <WhereBar path={path} />}
            {flow && <FlowBar flow={flow} here={path} role={role} />}
            {allowed ? children : <AccessDenied />}
          </main>
        </div>
      </div>
    </>
  );
}

/* ---------------------------------------------------------------- side menu */

function Sidebar({ path }: { path: string }) {
  const { role, shop } = useSession();
  const info = pageInfo(path);
  const cur = info.section;
  const [open, setOpen] = useState<Record<string, boolean>>({});
  useEffect(() => {
    try {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setOpen(JSON.parse(sessionStorage.getItem(NAV_KEY) || "{}").open || {});
    } catch {
      /* ignore */
    }
  }, []);
  const isOpen = (s: Section) => (s.key in open ? open[s.key] : s === cur);
  const toggle = (s: Section) => {
    const next = { ...open, [s.key]: !isOpen(s) };
    setOpen(next);
    try {
      sessionStorage.setItem(NAV_KEY, JSON.stringify({ open: next }));
    } catch {
      /* ignore */
    }
  };
  const activeHref = info.item?.href ?? info.hidden?.parent ?? null;

  return (
    <aside className="sidebar no-print" aria-label="Menu">
      <div className="brand">
        {/* eslint-disable-next-line @next/next/no-img-element -- small SVG logo */}
        <img src="/logo.svg" alt="" />
        <div>
          <b>{shop.name}</b>
          <span>GEM SHOP</span>
        </div>
      </div>
      {(role === OWNER || role === SALESPERSON) && path !== "/sell/new" && (
        <div style={{ padding: "14px 14px 4px" }}>
          <Link className="btn btn-gold btn-block btn-lg" href="/sell/new">
            + New sale
          </Link>
        </div>
      )}
      <nav className="nav-simple">
        {menuSections(role).map((s) => {
          const sub = s.home ? [] : subList(s, role);
          const here = s === cur;
          return (
            <div key={s.key}>
              <Link
                href={sectionHref(s, role)!}
                className={cn("nav-big", here && "active", here && info.onHub && "on-hub")}
                aria-current={here && (info.onHub || (s.home && path === "/")) ? "page" : undefined}
              >
                <span className="ni">{s.icon}</span>
                <span className="nt">{s.title}</span>
                {s.count ? <ApprovalCount /> : null}
                {sub.length > 0 && (
                  <button
                    type="button"
                    className={cn("nav-chev", isOpen(s) && "open")}
                    title="Show / hide pages"
                    aria-label={`Show or hide ${s.title} pages`}
                    aria-expanded={isOpen(s)}
                    onClick={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      toggle(s);
                    }}
                  >
                    <svg
                      viewBox="0 0 24 24"
                      width="14"
                      height="14"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2.5"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    >
                      <path d="M6 9l6 6 6-6" />
                    </svg>
                  </button>
                )}
              </Link>
              {sub.length > 0 && (
                <div className={cn("nav-sub", here && "cur", !isOpen(s) && "closed")} data-sec={s.key}>
                  {sub.map((it, i) =>
                    it === null ? (
                      <span key={"sep" + i} className="nav-sub-sep" />
                    ) : (
                      <Link
                        key={it.href}
                        href={it.href}
                        className={cn("nav-sub-i", here && it.href === activeHref && "active")}
                        aria-current={here && it.href === activeHref ? "page" : undefined}
                      >
                        <span>{it.icon}</span>
                        {it.label}
                      </Link>
                    ),
                  )}
                </div>
              )}
            </div>
          );
        })}
      </nav>
      <div className="side-foot">Gem Shop System</div>
    </aside>
  );
}

/** The section's main pages for the side menu, with a line between groups (null). */
function subList(s: Section, role: Role): (NavItem | null)[] {
  const seen = new Set<string>();
  const list: (NavItem | null)[] = [];
  for (const g of s.groups) {
    const v = visibleItems(g.items, role).filter((it) => !seen.has(it.href) && it.side !== false);
    v.forEach((it) => seen.add(it.href));
    if (v.length && list.length) list.push(null);
    list.push(...v);
  }
  return list;
}

/** Red count on "Approvals" in the menu: requests waiting for the owner. */
function ApprovalCount() {
  const { branch } = useSession();
  const { count } = useApprovalsWaiting(branch);
  return count ? <span className="nav-count">{count}</span> : null;
}

/* ---------------------------------------------------------------- "where am I" bar */

function WhereBar({ path }: { path: string }) {
  const { role } = useSession();
  const info = pageInfo(path);
  const s = info.section;
  if (s.home || info.onHub) return null;
  // the first page of a section without a section home (Approvals) has no back bar
  if (!s.hub && info.item && info.item.href === firstPage(s, role)) return null;
  const secLink = sectionHref(s, role) ?? "/";
  const parent = info.hidden?.parent ? allItems(s).find((i) => i.href === info.hidden!.parent) : undefined;
  const label = info.item?.label ?? info.hidden?.label ?? "";
  const back = parent ?? { href: secLink, label: s.title };
  return (
    <div className="wherebar no-print">
      <Link className="wb-back" href={back.href} title="Back">
        ← <span>{back.label}</span>
      </Link>
      <span className="wb-path">
        <Link href={secLink}>
          {s.icon} {s.title}
        </Link>
        {parent && (
          <>
            {" "}
            <i>›</i> <Link href={parent.href}>{parent.label}</Link>
          </>
        )}{" "}
        <i>›</i> <b>{label}</b>
      </span>
    </div>
  );
}

/* ---------------------------------------------------------------- top bar */

function Topbar({ onMenu }: { onMenu: () => void }) {
  const s = useSession();
  const { signOut } = useAuth();
  const router = useRouter();
  const toast = useToast();
  const [changePw, setChangePw] = useState(false);
  const canAll = seesAllBranches(s.role);
  const options = [
    ...(canAll ? [{ v: ALL_BRANCHES, l: "All branches" }] : []),
    ...s.branches.filter((b) => canAll || b.code === s.user.branch).map((b) => ({ v: b.code, l: branchLabel(b) })),
  ];
  const waiting = useApprovalsWaiting(s.branch);
  const owner = s.role === OWNER;

  return (
    <header className="topbar no-print">
      <button className="icon-btn menu-toggle" aria-label="Menu" onClick={onMenu}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M3 6h18M3 12h18M3 18h18" />
        </svg>
      </button>
      <select
        className="tb-sel"
        title="Branch"
        aria-label="Branch"
        value={s.branch}
        disabled={options.length < 2}
        onChange={(e) => {
          const code = e.target.value;
          s.setBranch(code);
          const b = s.branches.find((x) => x.code === code);
          if (b?.status === "OPENING")
            toast(branchLabel(b), "Opens in January 2027. Its stock will arrive by transfer.");
        }}
      >
        {options.map((o) => (
          <option key={o.v} value={o.v}>
            {o.l}
          </option>
        ))}
      </select>
      <select
        className="tb-sel"
        title="Show amounts in"
        aria-label="Currency"
        style={{ width: 76 }}
        value={s.currency}
        onChange={(e) => s.setCurrency(e.target.value as Currency)}
      >
        {CURRENCIES.map((c) => (
          <option key={c}>{c}</option>
        ))}
      </select>
      <div className="spacer" />
      <Dropdown
        label="Waiting for approval"
        button={
          <span className="icon-btn">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
              <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
            </svg>
            {waiting.count > 0 && <span className="count">{waiting.count}</span>}
          </span>
        }
      >
        <div className="dp-head">Waiting for the owner ({waiting.count})</div>
        {waiting.latest.length ? (
          waiting.latest.map((a) => (
            <Link key={a.number} className="dp-item" href={`/approvals#${a.number}`}>
              <b style={{ fontSize: 12 }}>{a.typeLabel}</b>
              <br />
              <span className="muted" style={{ fontSize: 12 }}>
                {a.title}
              </span>
              <br />
              <small className="muted">
                {ago(a.requestedAt)} · {a.requestedBy.name}
              </small>
            </Link>
          ))
        ) : (
          <div className="dp-item muted">Nothing waiting. 🎉</div>
        )}
        <Link className="dp-item bold" href="/approvals">
          {owner ? "Open approvals →" : "See request status →"}
        </Link>
      </Dropdown>
      <Dropdown
        label="User menu"
        button={
          <span className="user-chip">
            <span className={cn("avatar", owner && "gold")}>{initials(s.user.name)}</span>
            <span className="uc-text" style={{ lineHeight: 1.2, textAlign: "left" }}>
              <b style={{ fontSize: 13 }}>{s.user.name.split(" ")[0]}</b>
              <br />
              <small>{ROLE_LABEL[s.role]}</small>
            </span>
          </span>
        }
      >
        <div className="dp-head">
          {s.user.name}
          <br />
          <small className="muted">{s.user.email}</small>
        </div>
        <button className="dp-item" onClick={() => setChangePw(true)}>
          Change password
        </button>
        <Link className="dp-item" href="/design">
          Design kit <small className="muted">(how the screens look)</small>
        </Link>
        <div className="dp-sep" />
        <button
          className="dp-item"
          onClick={async () => {
            await signOut();
            toast("Signed out", "See you next time.");
            router.replace("/login");
          }}
        >
          Log out
        </button>
      </Dropdown>
      <ChangePasswordDialog open={changePw} onOpenChange={setChangePw} />
    </header>
  );
}

/** Top bar drop-down (prototype .dropdown): opens on click, closes on a click outside or Esc. */
function Dropdown({ label, button, children }: { label: string; button: ReactNode; children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const path = usePathname();
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => setOpen(false), [path]);
  useEffect(() => {
    if (!open) return;
    const click = (e: MouseEvent) => !ref.current?.contains(e.target as Node) && setOpen(false);
    const key = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", click);
    document.addEventListener("keydown", key);
    return () => {
      document.removeEventListener("mousedown", click);
      document.removeEventListener("keydown", key);
    };
  }, [open]);
  return (
    <div className={cn("dropdown", open && "open")} ref={ref}>
      <button
        type="button"
        className="scrim-btn"
        aria-label={label}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        {button}
      </button>
      {open && (
        <div
          className="dropdown-panel"
          role="menu"
          onClick={(e) => (e.target as HTMLElement).closest(".dp-item") && setOpen(false)}
        >
          {children}
        </div>
      )}
    </div>
  );
}
