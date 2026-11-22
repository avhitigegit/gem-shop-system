/* =====================================================================
   Gem Shop prototype — layout: sidebar + top bar + "where am I" bar,
   injected into every page. The navigation is defined ONCE here (SECTIONS).

   Navigation model (v3 – "Section home → task → back"):
   - 8 big menu items. Each one opens a SECTION HOME page (hub/*.html) that
     shows: things that need attention, big "What do you want to do?" tiles,
     "Look up" cards with live counts, and the rarely used screens.
   - Each task / list opens as its OWN page (no tab strip). A slim bar at the
     top says where you are ("💎 Sell › Invoices") with a back link.
   - The sidebar opens the current section and lists its main pages.
   - Work that happens in order (the cash day, new stock, month end) shows a
     step bar (FLOWS) so staff can see what is done and what comes next.
   - BRD codes, "Business rule" boxes and technical notes are hidden unless
     the reviewer turns on "Show BRD notes" in the user menu.
   Pages declare their allowed roles with <body data-roles="owner salesperson">.
   ===================================================================== */
(function () {
  'use strict';
  const O = 'owner', SP = 'salesperson', ST = 'staff', AC = 'accountant';
  const ALL = [O, SP, ST, AC];

  /* ---------- BRD review mode (hidden by default) ---------- */
  const body = document.body;
  const brdOn = !!P.brd || body.dataset.brd === 'always';
  body.classList.toggle('show-brd', brdOn);
  if (!brdOn) {
    // Strip review references such as "(F1)", "(BRD G1, G2)", "(rule 3)", "(tech stack §7)" from what the client sees.
    const ID = '(?:[A-N]\\d{1,2}|NFR-\\d)';
    const RX = [
      new RegExp('\\s*\\((?:BRD\\s*)?' + ID + '(?:\\s*(?:,|/|&|\\+|and|·)?\\s*' + ID + ')*\\)', 'g'),
      new RegExp('\\((?:BRD\\s*)?' + ID + '(?:\\s*,\\s*' + ID + ')*:\\s*', 'g'),
      /\s*\((?:business\s+)?rules?\s+\d+(?:\s*(?:,|&|and)\s*\d+)*\)/gi,
      /\s*\((?:see\s+)?tech stack[^)]*\)/gi,
      /\s*\(BRD[^)]*\)/g,
      /\s*·\s*BRD [^·<]*$/g
    ];
    const clean = s => { let o = s; RX.forEach((r, i) => { o = o.replace(r, i === 1 ? '(' : ''); }); return o; };
    const fixNode = n => {
      if (n.nodeType === 3) { const v = n.nodeValue; if (/[A-N]\d|NFR|rule|tech stack|BRD/i.test(v)) { const c = clean(v); if (c !== v) n.nodeValue = c; } return; }
      if (n.nodeType !== 1 || n.tagName === 'SCRIPT' || n.tagName === 'STYLE') return;
      ['placeholder', 'title'].forEach(a => { const v = n.getAttribute && n.getAttribute(a); if (v) { const c = clean(v); if (c !== v) n.setAttribute(a, c); } });
      const w = document.createTreeWalker(n, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT);
      let x; while ((x = w.nextNode())) { if (x.nodeType === 3) fixNode(x); else ['placeholder', 'title'].forEach(a => { const v = x.getAttribute(a); if (v) { const c = clean(v); if (c !== v) x.setAttribute(a, c); } }); }
    };
    const start = () => {
      fixNode(body);
      new MutationObserver(ms => ms.forEach(m => { if (m.type === 'characterData') fixNode(m.target); else m.addedNodes.forEach(fixNode); }))
        .observe(body, { childList: true, subtree: true, characterData: true });
    };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
    window.cleanBrd = clean;
  }

  /* ---------- navigation: 8 sections ----------
     Each section has groups. A group is { name, show: 'tiles'|'cards'|'links', side: true|false, items }.
       tiles = big "do this" buttons, cards = "look up" lists with a live count, links = less often.
       side  = listed in the sidebar under the section (main pages only).
     An item is [page, label, icon, short description, roles].
     hidden: detail / print pages → [page, parent page, label] (parent is used for the back link). */
  const SECTIONS = [
    { key: 'home', icon: '🏠', title: 'Home', roles: ALL, home: true, groups: [],
      hidden: [['dashboard/index.html', null, 'Home'], ['n-accountant/dashboard.html', null, 'Home'], ['index.html', null, 'All screens']] },

    { key: 'sell', icon: '💎', title: 'Sell', roles: [O, SP, AC], hub: 'hub/sell.html',
      intro: 'Sell gems, keep a gem for a customer or give a quote (both from New sale), and handle gems that come back.',
      groups: [
        { name: 'What do you want to do?', show: 'tiles', side: true, items: [
          ['f-pos/pos.html', 'New sale', '💎', 'Scan the tag, agree the price, take payment', [O, SP], { side: false }],
          ['e-returns/new-return.html', 'Customer brings a gem back', '↩️', 'Refund or exchange – the owner approves', [O, SP]]
        ] },
        { name: 'Look up', show: 'cards', side: true, items: [
          ['g-payments/invoices.html', 'Invoices', '🧾', 'Print or send again, cancel', [O, SP, AC]],
          ['f-pos/reservations.html', 'Reserved & quotes', '📌', 'Gems kept for customers', [O, SP]],
          ['e-returns/returns.html', 'Returns', '↩️', 'All refunds and exchanges', [O, SP]],
          ['e-returns/credit-note.html', 'Credit notes', '📄', 'Money given back', [O, SP, AC]]
        ] }
      ],
      hidden: [['g-payments/invoice.html', 'g-payments/invoices.html', 'Invoice'], ['f-pos/quotation-print.html', 'f-pos/reservations.html', 'Quotation'], ['g-payments/payment.html', 'f-pos/pos.html', 'Payment (practice)']] },

    { key: 'stock', icon: '📦', title: 'Gems & stock', roles: [O, SP, ST, AC], hub: 'hub/stock.html',
      intro: 'Buy gems, add them with a photo, print tags and keep the stock right.',
      groups: [
        { name: 'What do you want to do?', show: 'tiles', side: true, items: [
          ['b-purchasing/new-purchase.html', 'Buy gems from a supplier', '🛒', 'Cash purchase with bill photo', [O]],
          ['c-products/product-form.html', 'Add a gem (not bought)', '➕', 'Only for gems not from a supplier purchase', [O]],
          ['c-products/tag-print.html', 'Print tags', '🏷', 'QR / barcode labels', [O, ST]],
          ['d-stock/stock-check.html', 'Stock check', '📋', 'Scan every tag on the shelf', [O, ST]],
          ['c-products/shop-certificate.html', 'Print shop certificate', '📜', 'For gems without a lab certificate', [O, SP]]
        ] },
        { name: 'Look up', show: 'cards', side: true, items: [
          ['c-products/products.html', 'All gems', '🔍', 'Search or scan a tag, every branch', [O, SP, ST]],
          ['b-purchasing/suppliers.html', 'Suppliers', '🤝', 'Who we buy from', [O, AC]],
          ['b-purchasing/purchase-history.html', 'Purchases', '🧾', 'Everything bought', [O, AC]]
        ] },
        { name: 'Other tasks', show: 'cards', items: [
          ['d-stock/transfers.html', 'Send / receive between branches', '🚚', 'Move gems to the other branch', [O, ST]],
          ['d-stock/stock-correction.html', 'Fix a stock mistake', '✎', 'Owner approves', [O, ST]],
          ['b-purchasing/supplier-returns.html', 'Return a gem to the supplier', '↩️', 'Wrong or faulty gem', [O]]
        ] }
      ],
      hidden: [['c-products/product-detail.html', 'c-products/products.html', 'Gem details'], ['b-purchasing/supplier-detail.html', 'b-purchasing/suppliers.html', 'Supplier'], ['d-stock/product-history.html', 'c-products/products.html', 'Gem history']] },

    { key: 'people', icon: '👥', title: 'Customers & guides', roles: [O, SP, AC], hub: 'hub/people.html',
      intro: 'Customers, the guides and drivers who bring them, and their commission.',
      groups: [
        { name: 'What do you want to do?', show: 'tiles', side: true, items: [
          ['h-commissions/commissions-payable.html', 'Pay guides & drivers', '🚐', 'Pay what they earned', [O, AC]],
          ['j-customers-promotions/email-campaign.html', 'Send an offer by email', '✉️', 'Only to customers who agreed', [O]],
          ['j-customers-promotions/whatsapp-campaign.html', 'Send an offer on WhatsApp', '💬', 'Only to customers who agreed', [O]]
        ] },
        { name: 'Look up', show: 'cards', side: true, items: [
          ['j-customers-promotions/customers.html', 'Customers', '👥', 'Contacts and past purchases', [O, SP]],
          ['h-commissions/guides.html', 'Guides & drivers', '🚐', 'Who brings customers', [O, SP, AC]],
          ['h-commissions/commission-breakdown.html', 'Commission per sale', '🧮', 'How each amount was worked out', [O, AC]],
          ['j-customers-promotions/campaign-report.html', 'Offer results', '📈', 'Opened, clicked, sold', [O]]
        ] },
        { name: 'Other tasks', show: 'cards', items: [
          ['j-customers-promotions/message-settings.html', 'Email & WhatsApp settings', '⚙️', 'Turn messages on or off', [O]]
        ] }
      ],
      hidden: [['j-customers-promotions/customer-profile.html', 'j-customers-promotions/customers.html', 'Customer'], ['h-commissions/guide-profile.html', 'h-commissions/guides.html', 'Guide / driver'], ['h-commissions/commission-slip.html', 'h-commissions/commissions-payable.html', 'Commission slip']] },

    { key: 'money', icon: '💰', title: 'Cash & expenses', roles: [O, SP, AC], hub: 'hub/money.html',
      intro: 'The cash drawer from morning to evening, and the shop’s bills.',
      groups: [
        { name: 'What do you want to do?', show: 'tiles', side: true, items: [
          ['i-daily-closing/opening-cash.html', 'Start the day', '☀️', 'Count the cash in the drawer', [O, SP]],
          ['i-daily-closing/drawer-expenses.html', 'Pay out cash from the drawer', '💵', 'Small expenses paid in cash', [O, SP]],
          ['i-daily-closing/day-end.html', 'Close the day', '🌙', 'Count the cash, send to owner', [O, SP]],
          ['m-expenses/add-expense.html', 'Add an expense', '🧾', 'Bank or card bills, with photo', [O, AC]]
        ] },
        { name: 'Look up', show: 'cards', side: true, items: [
          ['m-expenses/expenses.html', 'Expenses', '📒', 'All shop expenses', [O, AC]],
          ['m-expenses/recurring.html', 'Monthly bills', '🔁', 'Rent, phone, salaries', [O, AC]]
        ] }
      ], hidden: [] },

    { key: 'reports', icon: '📊', title: 'Reports', roles: [O, AC], hub: 'hub/reports.html',
      intro: 'See how the shop is doing, and the monthly papers for the accountant.',
      groups: [
        { name: 'How is the shop doing?', show: 'cards', side: true, items: [
          ['l-reports/sales.html', 'Sales', '💎', 'By day, gem, staff, country', [O, AC]],
          ['l-reports/profit.html', 'Profit', '📈', 'Sale price minus cost', [O, AC]],
          ['l-reports/daily-closing.html', 'Daily cash', '🌙', 'Every day’s closing', [O, AC]],
          ['m-expenses/expense-report.html', 'Expenses', '🧾', 'By type and month', [O, AC]]
        ] },
        { name: 'Month end (with the accountant)', show: 'cards', side: true, items: [
          ['n-accountant/profit-loss.html', 'Profit & Loss', '📑', 'Income minus all costs', [O, AC]],
          ['n-accountant/tax-pack.html', 'Tax pack', '📦', 'Download everything for tax', [O, AC]],
          ['n-accountant/month-closing.html', 'Close the month', '🔒', 'Lock it so nothing changes', [O, AC]]
        ] },
        { name: 'More reports', show: 'cards', items: [
          ['l-reports/stock.html', 'Stock & value', '📦', 'Value, photos, slow-moving gems', [O]],
          ['l-reports/commissions.html', 'Commissions', '🚐', 'By guide and by staff', [O, AC]],
          ['l-reports/purchases.html', 'Purchases by supplier', '🛒', 'Bought and sold per supplier', [O, AC]]
        ] }
      ], hidden: [['d-stock/stock-value.html', 'l-reports/stock.html', 'Value by gem type']] },

    { key: 'approvals', icon: '✅', title: 'Approvals', roles: ALL, count: true,
      groups: [
        { name: 'Approvals', show: 'cards', side: true, items: [
          ['k-owner-control/approvals.html', 'Waiting for approval', '✅', '', ALL],
          ['k-owner-control/alerts.html', 'Alerts', '🔔', '', [O]],
          ['k-owner-control/owner-mobile.html', 'Phone view', '📱', '', [O]],
          ['k-owner-control/activity-log.html', 'Activity log', '🕘', '', [O]]
        ] }
      ], hidden: [] },

    { key: 'settings', icon: '⚙️', title: 'Settings', roles: [O], hub: 'hub/settings.html',
      intro: 'Set up the shop once. Change these only when something changes.',
      groups: [
        { name: 'Shop set-up', show: 'cards', side: true, items: [
          ['a-setup/shop-vat.html', 'Shop details', '🏪', 'Name, address, gem licence', [O]],
          ['a-setup/users.html', 'Staff logins', '🔑', 'Who can log in and what they see', [O]],
          ['a-setup/currencies.html', 'Exchange rates', '💱', 'USD and EUR rate of the day', [O]],
          ['a-setup/commission-rules.html', 'Commission', '🚐', 'Guide % and staff levels', [O]],
          ['a-setup/branches.html', 'Branches', '🏬', 'Ella now, Branch 2 from January 2027', [O]],
          ['a-setup/lists.html', 'Drop-down lists', '📃', 'Gem types, colours, labs, expense types…', [O]]
        ] }
      ], hidden: [] }
  ];

  /* ---------- flows: work that happens in order ---------- */
  const FLOWS = {
    day: {
      title: 'Today’s cash',
      steps: [
        { page: 'i-daily-closing/opening-cash.html', label: 'Start the day', status: () => { const o = S.openings[todayYmd() + '|' + activeBranch()]; return o ? { done: true, note: 'Done ' + fmt.time(o.at) } : { note: 'Count the drawer' }; } },
        { page: 'i-daily-closing/drawer-expenses.html', label: 'Cash paid out', status: () => { const n = S.expenses.filter(e => e.branch === activeBranch() && ymd(e.date) === todayYmd() && e.method === 'Cash (drawer)').length; return { done: false, optional: true, note: n ? n + ' today' : 'Only if needed' }; } },
        { page: 'i-daily-closing/day-end.html', label: 'Close the day', status: () => { const c = S.closings.find(x => x.date === todayYmd() && x.branch === activeBranch()); return c ? { done: true, note: 'Closed ' + fmt.time(c.at) } : { note: 'At closing time' }; } }
      ]
    },
    newstock: {
      title: 'New gems coming in',
      steps: [
        { page: 'b-purchasing/new-purchase.html', label: 'Buy from supplier', status: () => ({ note: 'Gems go into stock' }) },
        { page: 'c-products/tag-print.html', label: 'Print the tags', status: () => ({ note: 'Stick on the box' }) },
        { page: 'c-products/products.html', label: 'Ready to sell', status: () => ({ note: 'Shows in All gems' }) }
      ]
    },
    monthend: {
      title: 'Month end',
      steps: [
        { page: 'm-expenses/expenses.html', label: 'Check expenses', bar: false, status: () => ({ note: 'Bills & photos' }) },
        { page: 'n-accountant/profit-loss.html', label: 'Profit & Loss', status: () => ({ note: 'The month result' }) },
        { page: 'n-accountant/tax-pack.html', label: 'Tax pack', status: () => ({ note: 'Download' }) },
        { page: 'n-accountant/month-closing.html', label: 'Close the month', status: () => { const lm = new Date(); lm.setDate(1); lm.setMonth(lm.getMonth() - 1); const mk = lm.getFullYear() + '-' + String(lm.getMonth() + 1).padStart(2, '0'); const m = S.months[mk]; return m && m.status === 'closed' ? { done: true, note: fmt.month(mk) + ' locked' } : { note: fmt.month(mk) + ' still open' }; } }
      ]
    }
  };
  /* Render a flow step bar. here = current page (highlighted). */
  function flowBar(key, here, opts) {
    const f = FLOWS[key]; if (!f) return '';
    opts = opts || {};
    const steps = f.steps.filter(s => { const r = pageRoles(s.page); return !r || r.includes(P.role); });
    if (!steps.length) return '';
    const firstOpen = steps.findIndex(s => { const st = s.status(); return !st.done && !st.optional; });
    return `<nav class="flowbar no-print ${opts.compact ? 'compact' : ''}" aria-label="${esc(f.title)}"><span class="fb-title">${esc(opts.title || f.title)}</span><ol>` + steps.map((s, i) => {
      const st = s.status();
      const cls = [st.done ? 'done' : '', s.page === here ? 'here' : '', !st.done && i === firstOpen && s.page !== here ? 'next' : ''].join(' ');
      return `<li class="${cls}"><a href="${url(s.page)}"><span class="fb-n">${st.done ? '✓' : i + 1}</span><span class="fb-t"><b>${esc(s.label)}</b><small>${esc(st.note || '')}</small></span></a></li>`;
    }).join('') + '</ol></nav>';
  }
  const FLOW_OF = {}; Object.keys(FLOWS).forEach(k => FLOWS[k].steps.forEach(s => { if (!FLOW_OF[s.page] && s.bar !== false) FLOW_OF[s.page] = k; }));
  FLOW_OF['c-products/products.html'] = null; // a list page – no flow bar there

  /* ---------- helpers ---------- */
  const strip = p => (p || '').split('?')[0];
  const allItems = sec => sec.groups.flatMap(g => g.items.map(it => ({ it, g })));
  function pageRoles(page) { for (const s of SECTIONS) for (const { it } of allItems(s)) if (strip(it[0]) === page) return it[4]; return null; }
  const visible = items => items.filter(it => it[4].includes(P.role));
  const homeHref = () => P.role === AC ? 'n-accountant/dashboard.html' : 'dashboard/index.html';
  const firstPage = sec => { for (const g of sec.groups) { const v = visible(g.items); if (v.length) return v[0][0]; } return null; };
  const secHref = sec => sec.home ? homeHref() : (sec.hub && sec.groups.some(g => visible(g.items).length) ? sec.hub : firstPage(sec));
  window.SECTIONS = SECTIONS;
  window.Flows = { bar: flowBar, FLOWS };
  window.NAV = SECTIONS.map(s => ({ tag: '', title: s.title, items: allItems(s).map(x => x.it) }));
  window.NavInfo = { visible, secHref, allItems, strip };

  if (body.dataset.layout === 'none') { body.classList.add('role-' + P.role); return; }
  const here = (body.dataset.page || '').replace(/^\//, '');
  body.classList.add('role-' + P.role);
  const curSec = SECTIONS.find(s => s.hub === here) || SECTIONS.find(s => allItems(s).some(x => strip(x.it[0]) === here) || s.hidden.some(h => h[0] === here)) || SECTIONS[0];
  const onHub = curSec.hub === here;
  const intent = new URLSearchParams(location.search).get('intent');
  const hereItem = (allItems(curSec).find(x => x.it[0] === here + (intent ? '?intent=' + intent : '')) || allItems(curSec).find(x => strip(x.it[0]) === here) || {}).it;
  const hereHidden = curSec.hidden.find(h => h[0] === here);
  window.CUR_SECTION = curSec;

  /* ---------- sidebar ---------- */
  const subList = (sec, open) => {
    const seen = new Set();
    const list = [];
    sec.groups.forEach(g => { const v = visible(g.items).filter(it => !seen.has(it[0]) && !(it[5] && it[5].side === false)); v.forEach(it => seen.add(it[0])); if (v.length && list.length) list.push(null); list.push(...v); });
    let activeDone = false;
    const activeKey = hereItem ? hereItem[0] : (hereHidden && hereHidden[1]);
    const lis = list.map(it => {
      if (!it) return '<span class="nav-sub-sep"></span>';
      const act = sec === curSec && !activeDone && activeKey && it[0] === activeKey; if (act) activeDone = true;
      return `<a href="${url(it[0])}" class="nav-sub-i ${act ? 'active' : ''}"><span>${it[2]}</span>${esc(it[1])}</a>`;
    }).join('');
    return lis ? `<div class="nav-sub ${sec === curSec ? 'cur' : ''} ${open ? '' : 'closed'}" data-sec="${sec.key}">${lis}</div>` : '';
  };
  /* which sections are opened/closed with the ⌄ arrow — remembered while the browser tab is open */
  const NAV_KEY = 'gemshop.nav.v1';
  let navMem = {}; try { navMem = JSON.parse(sessionStorage.getItem(NAV_KEY)) || {}; } catch (e) {}
  const isOpen = s => (s.key in (navMem.open || {})) ? navMem.open[s.key] : s === curSec;
  const saveNav = () => { try { sessionStorage.setItem(NAV_KEY, JSON.stringify(navMem)); } catch (e) {} };
  const navHTML = SECTIONS.filter(s => s.roles.includes(P.role) && secHref(s)).map(s => {
    const sub = s.home ? '' : subList(s, isOpen(s));
    const chev = sub ? `<button type="button" class="nav-chev ${isOpen(s) ? 'open' : ''}" data-sec="${s.key}" title="Show / hide pages" aria-label="Show or hide ${esc(s.title)} pages"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M6 9l6 6 6-6"/></svg></button>` : '';
    return `<a href="${url(secHref(s))}" class="nav-big ${s === curSec ? 'active' : ''} ${s === curSec && onHub ? 'on-hub' : ''}"><span class="ni">${s.icon}</span><span class="nt">${esc(s.title)}</span>${s.count ? '<span class="nav-count hidden" id="navCount"></span>' : ''}${chev}</a>` + sub;
  }).join('');
  const sidebar = document.createElement('aside');
  sidebar.className = 'sidebar no-print';
  sidebar.innerHTML = `<div class="brand"><img src="${url('assets/img/logo.svg')}" alt=""><div><b>${esc(S.shop.name)}</b><span>GEM SHOP</span></div></div>
    ${isRole(O, SP) && here !== 'f-pos/pos.html' ? `<div style="padding:14px 14px 4px"><a class="btn btn-gold btn-block btn-lg" href="${url('f-pos/pos.html')}">+ New sale</a></div>` : ''}
    <nav class="nav-simple">${navHTML}</nav>
    <div class="side-foot">Prototype v12 · ${S.emptySystem ? 'empty system' : 'sample data only'}</div>`;
  /* ⌄ arrow: open / close a section's page list without leaving the page */
  sidebar.addEventListener('click', e => {
    const b = e.target.closest('.nav-chev'); if (!b) return;
    e.preventDefault(); e.stopPropagation();
    const sub = sidebar.querySelector(`.nav-sub[data-sec="${b.dataset.sec}"]`); if (!sub) return;
    const open = sub.classList.toggle('closed') === false;
    b.classList.toggle('open', open);
    navMem.open = Object.assign({}, navMem.open, { [b.dataset.sec]: open }); saveNav();
  });
  /* keep the menu where it was scrolled to when the next page opens (no jump back to the top) */
  const saveScroll = () => { navMem.scroll = sidebar.scrollTop; saveNav(); };
  sidebar.addEventListener('scroll', saveScroll, { passive: true });
  window.addEventListener('pagehide', saveScroll);

  /* ---------- "where am I" bar (replaces the old tab strip) ---------- */
  let wherebar = null;
  const isSecStart = !curSec.hub && hereItem && hereItem[0] === firstPage(curSec);
  if (!curSec.home && !onHub && !isSecStart) {
    const secLink = secHref(curSec);
    const parent = hereHidden && hereHidden[1] ? (allItems(curSec).find(x => x.it[0] === hereHidden[1]) || {}).it : null;
    const curLabel = hereItem ? hereItem[1] : hereHidden ? hereHidden[2] : document.title.split('·')[0].trim();
    const backHref = parent ? parent[0] : secLink;
    const backLabel = parent ? parent[1] : curSec.title;
    wherebar = document.createElement('div');
    wherebar.className = 'wherebar no-print';
    wherebar.innerHTML = `<a class="wb-back" href="${url(backHref)}" title="Back">← <span>${esc(backLabel)}</span></a>
      <span class="wb-path"><a href="${url(secLink)}">${curSec.icon} ${esc(curSec.title)}</a>${parent ? ` <i>›</i> <a href="${url(parent[0])}">${esc(parent[1])}</a>` : ''} <i>›</i> <b>${esc(curLabel)}</b></span>`;
  }
  const flowKey = FLOW_OF[here];
  const flowEl = flowKey ? Object.assign(document.createElement('div'), { innerHTML: flowBar(flowKey, here) }).firstElementChild : null;

  /* ---------- top bar ---------- */
  const u = currentUser();
  const canAll = isRole(O, AC);
  const openBranches = S.branches;
  const branchOpts = (canAll ? [{ v: 'all', l: 'All branches' }] : []).concat(openBranches.map(b => ({ v: b.id, l: b.short || b.name })));
  const curBranch = (!canAll && P.branch === 'all') ? u.branch : P.branch;
  const topbar = document.createElement('header');
  topbar.className = 'topbar no-print';
  topbar.innerHTML = `
    <button class="icon-btn menu-toggle" id="menuBtn" aria-label="Menu"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18M3 12h18M3 18h18"/></svg></button>
    <select id="tbBranch" class="tb-sel" title="Branch" aria-label="Branch">${UI.opts(branchOpts, curBranch)}</select>
    <select id="tbCur" class="tb-sel" title="Show amounts in" aria-label="Currency" style="width:76px">${UI.opts(['LKR', 'USD', 'EUR'], P.currency)}</select>
    <div class="spacer"></div>
    <div class="demo-role" title="Demo only – see the app as another person"><b>DEMO</b><select id="tbRole">${UI.opts(Object.keys(ROLES).map(k => ({ v: k, l: ROLES[k] })), P.role)}</select></div>
    <div class="dropdown" id="bellDd"><button class="icon-btn" id="bellBtn" aria-label="Waiting for approval"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/></svg><span class="count hidden" id="bellCount">0</span></button><div class="dropdown-panel" id="bellPanel"></div></div>
    <div class="dropdown" id="userDd"><div class="user-chip" id="userBtn"><div class="avatar ${P.role === O ? 'gold' : ''}">${initials(u.name)}</div><div class="uc-text" style="line-height:1.2"><b style="font-size:13px">${esc(u.name.split(' ')[0])}</b><br><small>${ROLES[P.role]}</small></div></div>
      <div class="dropdown-panel"><div class="dp-head">${esc(u.name)}<br><small class="muted">${esc(u.email)}</small></div>
        <a class="dp-item" href="${url('auth/forgot-password.html')}">Change password</a>
        <label class="dp-item" style="display:flex;gap:8px;align-items:center"><input type="checkbox" id="brdToggle" ${P.brd ? 'checked' : ''}> Show BRD notes <small class="muted">(for review)</small></label>
        <a class="dp-item" href="${url('index.html')}">All screens (sitemap)</a>
        <div class="dp-sep"></div><div class="dp-label">Prototype only</div>
        <button class="dp-item" id="resetNormal">↺ Normal reset <small class="muted">(back to sample data)</small></button>
        <button class="dp-item dp-danger" id="resetSystem">⌫ Reset system <small class="muted">(empty, keep logins)</small></button>
        <div class="dp-sep"></div>
        <a class="dp-item" href="${url('auth/login.html')}" id="logout">Log out</a>
      </div></div>`;

  /* ---------- assemble ---------- */
  const app = document.createElement('div'); app.className = 'app';
  const main = document.createElement('div'); main.className = 'main';
  const content = document.createElement('main'); content.className = 'content';
  const scrim = document.createElement('div'); scrim.className = 'scrim';
  const pageEl = document.getElementById('page');
  Array.from(body.childNodes).forEach(n => { if (!(n.nodeType === 1 && n.tagName === 'SCRIPT')) content.appendChild(n); });
  if (flowEl) content.insertBefore(flowEl, content.firstChild);
  if (wherebar) content.insertBefore(wherebar, content.firstChild);
  main.appendChild(topbar); main.appendChild(content);
  app.appendChild(sidebar); app.appendChild(main);
  body.insertBefore(scrim, body.firstChild);
  body.insertBefore(app, scrim.nextSibling);
  if (typeof navMem.scroll === 'number') { sidebar.scrollTop = navMem.scroll; requestAnimationFrame(() => { sidebar.scrollTop = navMem.scroll; }); }

  /* ---------- role access for the page ---------- */
  const allowed = (body.dataset.roles || ALL.join(' ')).split(/\s+/);
  window.PAGE_ALLOWED = allowed.includes(P.role);
  if (!window.PAGE_ALLOWED && pageEl) pageEl.innerHTML = UI.accessDenied(body.dataset.denied);

  /* ---------- events ---------- */
  $('#menuBtn').onclick = () => body.classList.toggle('nav-open');
  scrim.onclick = () => body.classList.remove('nav-open');
  $('#tbBranch').onchange = e => { Prefs.set('branch', e.target.value); if (e.target.value === 'b2') toast('Branch 2', 'Opens in January 2027. Its stock will arrive by transfer.'); setTimeout(() => location.reload(), 250); };
  $('#tbCur').onchange = e => { Prefs.set('currency', e.target.value); location.reload(); };
  $('#tbRole').onchange = e => {
    const r = e.target.value; Prefs.set('role', r);
    if (![O, AC].includes(r) && P.branch === 'all') Prefs.set('branch', 'ella');
    location.reload();
  };
  $('#brdToggle').onchange = e => { Prefs.set('brd', e.target.checked); location.reload(); };
  $('#resetNormal').onclick = DemoReset.normal;
  $('#resetSystem').onclick = DemoReset.system;
  $('#logout').onclick = () => { Prefs.set('loggedIn', false); };
  const dd = el => { el.firstElementChild.addEventListener('click', e => { e.stopPropagation(); $$('.dropdown.open').forEach(d => d !== el && d.classList.remove('open')); el.classList.toggle('open'); }); };
  dd($('#bellDd')); dd($('#userDd'));
  document.addEventListener('click', e => { if (!e.target.closest('.dropdown')) $$('.dropdown.open').forEach(d => d.classList.remove('open')); });

  function renderBell() {
    const pend = S.approvals.filter(a => a.status === 'pending');
    const c = $('#bellCount'); c.textContent = pend.length; c.classList.toggle('hidden', !pend.length);
    const nc = $('#navCount'); if (nc) { nc.textContent = pend.length; nc.classList.toggle('hidden', !pend.length); }
    const owner = isRole(O);
    $('#bellPanel').innerHTML = `<div class="dp-head">Waiting for the owner (${pend.length})</div>` +
      (pend.slice(0, 6).map(a => `<a class="dp-item" href="${url('k-owner-control/approvals.html')}#${a.id}"><b style="font-size:12px">${esc(APPROVAL_TYPES[a.type] || a.type)}</b><br><span class="muted" style="font-size:12px">${esc(a.title)}</span><br><small class="muted">${fmt.ago(a.at)} · ${esc(user(a.requestedBy).name)}</small></a>`).join('') || '<div class="dp-item muted">Nothing waiting. 🎉</div>') +
      `<a class="dp-item bold" href="${url('k-owner-control/approvals.html')}">${owner ? 'Open approvals →' : 'See request status →'}</a>`;
  }

  /* header buttons that only repeat a page already in the menu are hidden – one way to each page.
     Main action buttons (blue / green / gold) stay. */
  const menuHrefs = new Set($$('.nav-sub.cur .nav-sub-i', sidebar).map(a => a.href.split(/[?#]/)[0]));
  const dedupe = () => $$('.page-head .actions a[href]').forEach(a => {
    if (/btn-(sapphire|success|gold)/.test(a.className) || a.href.includes('?')) return;
    if (menuHrefs.has(a.href.split(/[?#]/)[0])) a.style.display = 'none';
  });
  dedupe(); new MutationObserver(dedupe).observe(content, { childList: true, subtree: true });

  renderBell();
  setInterval(() => { Store.refresh(); renderBell(); }, 4000);
  onStateChange(renderBell);
  window.renderBell = renderBell;
})();
