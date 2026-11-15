/* =====================================================================
   Gem Shop prototype — shared helpers.
   State (localStorage), formatting (LKR 450,000.00 / DD/MM/YYYY), currency
   conversion, commission maths, roles, toasts, dialogs, approvals,
   activity log, alerts, gem SVG illustrations, payment & send dialogs.
   ===================================================================== */
(function () {
  'use strict';
  const ROOT = (document.body && document.body.dataset.root) || '';
  const LS_STATE = 'gemshop.state.v2'; // v8: new key (no VAT) – old v1 data is dropped
  try { localStorage.removeItem('gemshop.state.v1'); } catch (e) { }
  const LS_PREFS = 'gemshop.prefs.v1';

  /* ---------------- small utils ---------------- */
  const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const $ = (sel, root) => (root || document).querySelector(sel);
  const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));
  const qs = name => new URLSearchParams(location.search).get(name);
  const url = path => ROOT + path;
  const r2 = n => Math.round((Number(n) + Number.EPSILON) * 100) / 100;
  const ymd = d => { d = d ? new Date(d) : new Date(); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0'); };
  const todayYmd = () => ymd(new Date());
  const daysAgoYmd = n => { const d = new Date(); d.setDate(d.getDate() - n); return ymd(d); };
  const monthKey = iso => String(iso).slice(0, 7);
  const sum = (arr, f) => arr.reduce((a, x) => a + (Number(f ? f(x) : x) || 0), 0);
  const groupBy = (arr, f) => arr.reduce((m, x) => { const k = f(x); (m[k] = m[k] || []).push(x); return m; }, {});
  const initials = name => String(name || '?').split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase();

  /* ---------------- Calc (commission, cash) ---------------- */
  const Calc = {
    /* v8: no VAT in this system – the owner's prices already include it and VAT is not shown.
       net = total = the agreed amount; vat is always 0 (kept only so old fields still work). */
    split(gross) { return { net: r2(gross), vat: 0, total: r2(gross) }; },
    staffComm(net, st) {
      const slabs = (st || S).commissionRules.slabs;
      for (const s of slabs) if (s.upTo == null || net <= s.upTo) return s.amount;
      return slabs[slabs.length - 1].amount;
    },
    slabIndex(net, st) {
      const slabs = (st || S).commissionRules.slabs;
      for (let i = 0; i < slabs.length; i++) if (slabs[i].upTo == null || net <= slabs[i].upTo) return i;
      return slabs.length - 1;
    },
    saleTotals(items, guideId, st) {
      st = st || S;
      const subtotal = r2(sum(items, i => i.agreed));
      const t = Calc.split(subtotal, st);
      const g = guideId ? st.guides.find(x => x.id === guideId) : null;
      const guidePct = g ? g.pct : 0;
      const guideComm = r2(t.net * guidePct / 100);
      const staffComm = items.length ? Calc.staffComm(t.net, st) : 0;
      return { subtotal, net: t.net, vat: t.vat, total: t.total, guidePct, guideComm, staffComm, totalComm: r2(guideComm + staffComm) };
    },
    /* expected drawer cash for a date & branch (I3): opening + cash sales − change − drawer expenses − drawer commission payouts */
    expectedCash(st, date, branch) {
      st = st || S;
      const op = st.openings[date + '|' + branch] || { LKR: 0, USD: 0, EUR: 0 };
      const cash = { LKR: op.LKR || 0, USD: op.USD || 0, EUR: op.EUR || 0 };
      let card = 0, cashSalesLKR = 0, drawerOut = 0;
      st.sales.filter(s => s.branch === branch && ymd(s.date) === date && s.status !== 'cancelled').forEach(s => {
        s.payments.forEach(p => { if (p.method === 'card') card += p.lkr; else { cash[p.currency] += p.amount; cashSalesLKR += p.lkr; } });
        if (s.change) { cash.LKR -= s.change; cashSalesLKR -= s.change; }
      });
      st.expenses.filter(e => e.branch === branch && ymd(e.date) === date && e.method === 'Cash (drawer)').forEach(e => { cash.LKR -= e.amount; drawerOut += e.amount; });
      st.commissions.filter(c => c.method === 'Cash (drawer)' && c.paidAt && ymd(c.paidAt) === date && (c.branch || 'ella') === branch).forEach(c => { cash.LKR -= c.amount; drawerOut += c.amount; });
      return { opening: op, cash: { LKR: r2(cash.LKR), USD: r2(cash.USD), EUR: r2(cash.EUR) }, card: r2(card), cashSalesLKR: r2(cashSalesLKR), drawerOut: r2(drawerOut) };
    },
    closingDiff(c, st) {
      st = st || S;
      const d = ['LKR', 'USD', 'EUR'].reduce((a, k) => a + ((c.counted[k] || 0) - (c.expected[k] || 0)) * (k === 'LKR' ? 1 : st.rates[k]), 0);
      return r2(d + ((c.cardCounted || 0) - (c.cardExpected || 0)));
    },
    saleCost(s, st) { st = st || S; return sum(s.items.filter(i => !i.returned), i => (st.products.find(p => p.id === i.productId) || {}).cost); },
    saleComm(s, st) { st = st || S; return sum(st.commissions.filter(c => c.saleId === s.id && c.status !== 'cancelled'), c => c.amount); },
    saleNetActive(s, st) { // net sales excluding returned items
      st = st || S;
      if (s.status === 'cancelled') return 0;
      const active = s.items.filter(i => !i.returned);
      return Calc.split(sum(active, i => i.agreed), st).net;
    }
  };

  window.Calc = Calc; // needed by buildSeed() in data.js

  /* ---------------- State ---------------- */
  function loadState() {
    try { const raw = localStorage.getItem(LS_STATE); if (raw) return JSON.parse(raw); } catch (e) { console.warn('State read failed', e); }
    const st = window.buildSeed();
    try { localStorage.setItem(LS_STATE, JSON.stringify(st)); } catch (e) { }
    return st;
  }
  let S = loadState();
  const Store = {
    save() { try { localStorage.setItem(LS_STATE, JSON.stringify(S)); } catch (e) { toast('Could not save', 'Browser storage is full or blocked.', 'error'); } },
    refresh() { try { const raw = localStorage.getItem(LS_STATE); if (raw) { const n = JSON.parse(raw); Object.keys(S).forEach(k => delete S[k]); Object.assign(S, n); } } catch (e) { } },
    reset() { Store.clearExtras(); localStorage.removeItem(LS_STATE); S = loadState(); window.S = S; },
    /* PROTOTYPE ONLY – "Reset system": an empty shop. Keeps the logins and the shop set-up
       (shop details, branches, drop-down lists, commission, exchange rates, email/WhatsApp settings);
       removes every gem, supplier, guide, customer, sale, expense, approval, log line … */
    empty() {
      const st = window.buildSeed();
      const KEEP = ['version', 'shop', 'branches', 'users', 'lists', 'commissionRules', 'rates', 'messaging', 'seq'];
      Object.keys(st).forEach(k => {
        if (KEEP.includes(k)) return;
        const v = st[k]; if (Array.isArray(v)) st[k] = []; else if (v && typeof v === 'object') st[k] = {};
      });
      Object.keys(st.seq).forEach(k => { st.seq[k] = 0; });
      st.seq.user = st.users.length;
      st.rateHistory = [{ date: todayYmd(), USD: st.rates.USD, EUR: st.rates.EUR, by: st.users[0].id }];
      st.createdAt = new Date().toISOString(); st.emptySystem = true;
      Store.clearExtras();
      try { localStorage.setItem(LS_STATE, JSON.stringify(st)); } catch (e) { }
      S = st; window.S = S;
    },
    /* things kept outside the main data: the unfinished sale and saved tax packs */
    clearExtras() { ['gemshop.pos.draft', 'gemshop.taxpacks'].forEach(k => { try { localStorage.removeItem(k); } catch (e) { } }); }
  };
  /* PROTOTYPE ONLY – the two reset buttons (user menu and sitemap) */
  const DemoReset = {
    home: () => url(P.role === 'accountant' ? 'n-accountant/dashboard.html' : 'dashboard/index.html'),
    normal() {
      UI.confirm('Normal reset?', 'All changes made in this browser are thrown away and the prototype goes back to the starting sample data (gems, sales, customers …). Your login stays.',
        () => { Store.reset(); location.href = DemoReset.home(); }, { yes: 'Reset to sample data' });
    },
    system() {
      UI.confirm('Reset system – start empty?', 'Removes <b>all</b> data: gems, suppliers, purchases, customers, guides, sales, returns, expenses, cash days, approvals and the activity log. Only the staff logins and the shop set-up (shop details, branches, drop-down lists, commission, exchange rates) are kept. Use <b>Normal reset</b> to get the sample data back.',
        () => { Store.empty(); location.href = DemoReset.home(); }, { yes: 'Clear all data', danger: true });
    }
  };

  let P = { role: 'owner', branch: 'ella', currency: 'LKR', loggedIn: true };
  try { Object.assign(P, JSON.parse(localStorage.getItem(LS_PREFS) || '{}')); } catch (e) { }
  const Prefs = {
    set(k, v) { P[k] = v; try { localStorage.setItem(LS_PREFS, JSON.stringify(P)); } catch (e) { } }
  };
  const ROLES = { owner: 'Owner', salesperson: 'Salesperson', staff: 'Shop Staff', accountant: 'Accountant' };
  const currentUser = () => S.users.find(u => u.role === P.role) || S.users[0];
  const isRole = (...r) => r.includes(P.role);
  // Branch scope: salesperson & staff are locked to their branch; owner/accountant can pick "all".
  function branchScope() {
    if (isRole('salesperson', 'staff')) return P.branch === 'all' ? currentUser().branch : P.branch;
    return P.branch;
  }
  const inBranch = x => { const b = branchScope(); return b === 'all' || !x.branch || x.branch === b || x.branch === 'all'; };
  const activeBranch = () => { const b = branchScope(); return b === 'all' ? 'ella' : b; };

  /* ---------------- lookups ---------------- */
  const find = (coll, id) => (S[coll] || []).find(x => x.id === id);
  const prod = id => find('products', id);
  const cust = id => find('customers', id);
  const guide = id => find('guides', id);
  const user = id => find('users', id) || { name: '—', role: '' };
  const sup = id => find('suppliers', id);
  const sale = id => find('sales', id);
  const branchName = id => id === 'all' ? 'All branches' : ((find('branches', id) || {}).short || (find('branches', id) || {}).name || id);
  function nextId(key, prefix, pad) {
    S.seq[key] = (S.seq[key] || 0) + 1;
    return prefix + String(S.seq[key]).padStart(pad || 4, '0');
  }

  /* ---------------- formatting ---------------- */
  const fx = {
    rate: c => c === 'LKR' ? 1 : S.rates[c],
    toLKR: (amt, c) => r2(amt * fx.rate(c)),
    from: (lkr, c) => r2(lkr / fx.rate(c))
  };
  const nf = (n, d) => Number(n || 0).toLocaleString('en-US', { minimumFractionDigits: d == null ? 2 : d, maximumFractionDigits: d == null ? 2 : d });
  const fmt = {
    num: nf,
    money: (n, c) => (c || 'LKR') + ' ' + nf(n),
    lkr: n => 'LKR ' + nf(n),
    cur: (lkr, c) => { c = c || P.currency; return c + ' ' + nf(fx.from(lkr, c)); },
    tri: lkr => ['LKR', 'USD', 'EUR'].map(c => c + ' ' + nf(fx.from(lkr, c))).join(' · '),
    date: iso => { if (!iso) return '—'; const d = new Date(iso.length === 10 ? iso + 'T00:00:00' : iso); return String(d.getDate()).padStart(2, '0') + '/' + String(d.getMonth() + 1).padStart(2, '0') + '/' + d.getFullYear(); },
    time: iso => { const d = new Date(iso); return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0'); },
    dt: iso => iso ? fmt.date(iso) + ' ' + fmt.time(iso) : '—',
    ago: iso => { const m = Math.round((Date.now() - new Date(iso)) / 60000); if (m < 1) return 'just now'; if (m < 60) return m + ' min ago'; const h = Math.round(m / 60); if (h < 24) return h + ' h ago'; const d = Math.round(h / 24); return d + ' day' + (d > 1 ? 's' : '') + ' ago'; },
    ct: n => nf(n, 2) + ' ct',
    month: key => { const [y, m] = key.split('-'); return ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'][Number(m) - 1] + ' ' + y; },
    pct: n => nf(n, 0) + '%'
  };

  /* ---------------- gem illustrations (SVG, coloured by gem type) ---------------- */
  const GEM_COLORS = {
    'Blue Sapphire': ['#1f4fa3', '#5b8ef0', '#0c2a66'],
    'Yellow Sapphire': ['#e8b923', '#fde68a', '#a16207'],
    'Pink Sapphire': ['#e0559b', '#fbcfe8', '#9d174d'],
    'Padparadscha': ['#f08a5d', '#ffd3b6', '#b4502d'],
    'Star Sapphire': ['#5b6f95', '#c7d2e8', '#2f3e5c'],
    'Ruby': ['#c0172f', '#fb7185', '#7f0d1d'],
    "Cat's Eye": ['#c99a2e', '#fde7a7', '#7a5a12'],
    'Alexandrite': ['#1f8a70', '#c084fc', '#0f4d3f'],
    'Moonstone': ['#c9d6ea', '#ffffff', '#8aa0c4'],
    'Spinel': ['#b4508c', '#e9b4d6', '#6b2251'],
    'Garnet': ['#7a1426', '#d04a5f', '#430a14'],
    'Topaz': ['#4fb3d9', '#c8ecfa', '#1c6f91'],
    'Other': ['#64748b', '#cbd5e1', '#334155']
  };
  let svgN = 0;
  function gemSVG(gem, type, size) {
    size = size || 96;
    const c = GEM_COLORS[gem] || GEM_COLORS.Other;
    const id = 'g' + (++svgN);
    const cab = /Star|Cat|Moonstone/.test(gem);
    const defs = `<defs><radialGradient id="${id}" cx="40%" cy="35%" r="70%"><stop offset="0" stop-color="${c[1]}"/><stop offset=".55" stop-color="${c[0]}"/><stop offset="1" stop-color="${c[2]}"/></radialGradient></defs>`;
    const stone = (cx, cy, r) => {
      if (cab) {
        let extra = '';
        if (/Star/.test(gem)) extra = `<g stroke="#fff" stroke-opacity=".8" stroke-width="${r / 14}" stroke-linecap="round"><line x1="${cx}" y1="${cy - r * .7}" x2="${cx}" y2="${cy + r * .7}"/><line x1="${cx - r * .6}" y1="${cy - r * .35}" x2="${cx + r * .6}" y2="${cy + r * .35}"/><line x1="${cx - r * .6}" y1="${cy + r * .35}" x2="${cx + r * .6}" y2="${cy - r * .35}"/></g>`;
        if (/Cat/.test(gem)) extra = `<ellipse cx="${cx}" cy="${cy}" rx="${r * .09}" ry="${r * .78}" fill="#fffbe6" opacity=".85"/>`;
        if (/Moonstone/.test(gem)) extra = `<ellipse cx="${cx - r * .15}" cy="${cy - r * .2}" rx="${r * .55}" ry="${r * .35}" fill="#9ec5ff" opacity=".55"/>`;
        return `<ellipse cx="${cx}" cy="${cy}" rx="${r}" ry="${r * .82}" fill="url(#${id})" stroke="${c[2]}" stroke-width="1"/>${extra}<ellipse cx="${cx - r * .35}" cy="${cy - r * .38}" rx="${r * .25}" ry="${r * .14}" fill="#fff" opacity=".6"/>`;
      }
      const pts = [[0, -1], [.72, -.62], [1, 0], [.72, .62], [0, 1], [-.72, .62], [-1, 0], [-.72, -.62]].map(p => [cx + p[0] * r, cy + p[1] * r * .86]);
      const inner = pts.map(p => [cx + (p[0] - cx) * .5, cy + (p[1] - cy) * .5]);
      const poly = a => a.map(p => p.map(v => v.toFixed(1)).join(',')).join(' ');
      let facets = '';
      for (let i = 0; i < 8; i++) facets += `<line x1="${pts[i][0].toFixed(1)}" y1="${pts[i][1].toFixed(1)}" x2="${inner[i][0].toFixed(1)}" y2="${inner[i][1].toFixed(1)}"/>`;
      return `<polygon points="${poly(pts)}" fill="url(#${id})" stroke="${c[2]}" stroke-width="1"/><polygon points="${poly(inner)}" fill="${c[1]}" fill-opacity=".35" stroke="#fff" stroke-opacity=".55" stroke-width=".8"/><g stroke="#fff" stroke-opacity=".45" stroke-width=".7">${facets}</g><polygon points="${(cx - r * .45).toFixed(1)},${(cy - r * .5).toFixed(1)} ${(cx - r * .1).toFixed(1)},${(cy - r * .62).toFixed(1)} ${(cx - r * .25).toFixed(1)},${(cy - r * .2).toFixed(1)}" fill="#fff" opacity=".7"/>`;
    };
    const metal = '#d4af37', metal2 = '#a8862a';
    let body;
    switch (type) {
      case 'Ring': body = `<ellipse cx="50" cy="66" rx="26" ry="22" fill="none" stroke="${metal}" stroke-width="6"/><ellipse cx="50" cy="66" rx="26" ry="22" fill="none" stroke="${metal2}" stroke-width="1.2" stroke-dasharray="2 3"/><path d="M38 44 L44 36 L56 36 L62 44" fill="${metal}" stroke="${metal2}"/>${stone(50, 30, 17)}`; break;
      case 'Pendant': body = `<path d="M18 8 Q50 40 82 8" fill="none" stroke="${metal}" stroke-width="2" stroke-dasharray="3 2"/><circle cx="50" cy="30" r="4" fill="none" stroke="${metal}" stroke-width="2"/><path d="M50 34 L50 38" stroke="${metal}" stroke-width="2"/>${stone(50, 60, 22)}`; break;
      case 'Earrings': body = `<path d="M30 10 q-6 8 0 14" fill="none" stroke="${metal}" stroke-width="2"/><path d="M70 10 q-6 8 0 14" fill="none" stroke="${metal}" stroke-width="2"/><line x1="30" y1="24" x2="30" y2="36" stroke="${metal}" stroke-width="2"/><line x1="70" y1="24" x2="70" y2="36" stroke="${metal}" stroke-width="2"/>${stone(30, 54, 15)}${stone(70, 54, 15)}`; break;
      case 'Bracelet': body = `<ellipse cx="50" cy="55" rx="40" ry="26" fill="none" stroke="${metal}" stroke-width="3"/>${[[14, 50], [28, 33], [50, 29], [72, 33], [86, 50], [72, 77], [50, 81], [28, 77]].map(p => stone(p[0], p[1], 8)).join('')}`; break;
      case 'Necklace': body = `<path d="M10 10 Q50 75 90 10" fill="none" stroke="${metal}" stroke-width="2.5"/>${stone(28, 42, 7)}${stone(72, 42, 7)}${stone(39, 52, 8)}${stone(61, 52, 8)}${stone(50, 64, 12)}`; break;
      default: body = stone(50, 52, 34);
    }
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="${size}" height="${size}" role="img" aria-label="${esc(gem)} ${esc(type || '')}">${defs}${body}</svg>`;
  }
  const gemPic = (p, size, box) => `<div class="gem-pic" style="width:${box || size}px;height:${box || size}px">${gemSVG(p ? p.gem : 'Other', p ? p.type : '', size)}</div>`;

  /* ---------------- status badges ---------------- */
  const STATUS = {
    in_stock: ['In stock', 'success'], reserved: ['Reserved', 'gold'], sold: ['Sold', 'navy'], returned: ['Returned', 'blue'],
    in_transit: ['In transit', 'warning'], written_off: ['Written off', 'danger'], returned_supplier: ['Returned to supplier', 'default'], on_hold: ['On hold (approval)', 'warning'],
    pending: ['Pending', 'warning'], approved: ['Approved', 'success'], rejected: ['Rejected', 'danger'], completed: ['Completed', 'success'], cancelled: ['Cancelled', 'danger'],
    paid: ['Paid', 'success'], unpaid: ['Not yet paid', 'warning'], reversed: ['Reversed', 'danger'], active: ['Active', 'blue'], expired: ['Expired', 'default'], converted: ['Converted to sale', 'success'],
    sent: ['Sent', 'success'], draft: ['Draft', 'default'], received: ['Received', 'success'], opening: ['Opening Jan 2027', 'gold'], closed: ['Closed (locked)', 'navy'], open: ['Open', 'blue']
  };
  const badge = (status, label) => { const s = STATUS[status] || [status, 'default']; return `<span class="badge badge-${s[1]}">${esc(label || s[0])}</span>`; };
  const certBadge = c => c.type === 'lab' ? `<span class="badge badge-blue">Lab certificate</span>` : c.type === 'shop' ? `<span class="badge badge-gold">Shop certificate</span>` : `<span class="badge badge-default">No certificate</span>`;

  /* ---------------- toasts ---------------- */
  function toast(title, msg, type) {
    let t = $('.toaster'); if (!t) { t = document.createElement('div'); t.className = 'toaster'; document.body.appendChild(t); }
    const el = document.createElement('div'); el.className = 'toast ' + (type || '');
    el.innerHTML = `<b>${esc(title)}</b>${msg ? `<div class="muted">${esc(msg)}</div>` : ''}`;
    t.appendChild(el);
    setTimeout(() => { el.style.transition = 'opacity .3s'; el.style.opacity = '0'; setTimeout(() => el.remove(), 300); }, 3800);
  }

  /* ---------------- dialogs ---------------- */
  function modal(o) {
    const ov = document.createElement('div'); ov.className = 'dialog-overlay';
    ov.innerHTML = `<div class="dialog ${o.size || ''}" role="dialog" aria-modal="true"><div class="dialog-head"><div><h2>${esc(o.title)}</h2>${o.desc ? `<p>${o.desc}</p>` : ''}</div><button class="x-btn" aria-label="Close">×</button></div><div class="dialog-body"></div><div class="dialog-foot"></div></div>`;
    const body = $('.dialog-body', ov); if (typeof o.body === 'string') body.innerHTML = o.body; else if (o.body) body.appendChild(o.body);
    const foot = $('.dialog-foot', ov);
    const close = () => { ov.remove(); document.removeEventListener('keydown', onKey); o.onClose && o.onClose(); };
    const onKey = e => { if (e.key === 'Escape') close(); };
    (o.actions || [{ label: 'Close', cls: 'btn-outline' }]).forEach(a => {
      const b = document.createElement('button'); b.className = 'btn ' + (a.cls || ''); b.textContent = a.label; if (a.id) b.id = a.id;
      b.onclick = () => { if (a.onClick) { const r = a.onClick({ el: ov, close, body }); if (r === false) return; } close(); };
      foot.appendChild(b);
    });
    if (!o.actions || !o.actions.length) foot.remove();
    $('.x-btn', ov).onclick = close;
    ov.addEventListener('mousedown', e => { if (e.target === ov) close(); });
    document.addEventListener('keydown', onKey);
    document.body.appendChild(ov);
    const f = $('input:not([type=hidden]):not([readonly]),select,textarea', body); if (f && o.focus !== false) setTimeout(() => f.focus(), 30);
    return { el: ov, body, close };
  }
  function confirmDlg(title, text, onYes, opts) {
    opts = opts || {};
    return modal({ title, size: 'sm', body: `<p>${text}</p>`, actions: [{ label: 'Cancel', cls: 'btn-outline' }, { label: opts.yes || 'Confirm', cls: opts.danger ? 'btn-destructive' : 'btn-sapphire', onClick: onYes }] });
  }
  /* shown when a detail page has nothing to show (e.g. no sales yet after "Reset system") */
  function emptyPage(title, text, href, label) {
    const pg = $('#page'); if (pg) pg.innerHTML = `<div class="card" style="max-width:560px;margin:40px auto"><div class="card-content center" style="padding:40px 24px"><div style="font-size:42px;line-height:1">📭</div><h2 style="margin:12px 0 6px">${esc(title)}</h2><p class="muted" style="margin:0 0 18px">${esc(text)}</p>${href ? `<a class="btn btn-sapphire" href="${url(href)}">${esc(label)}</a>` : ''}</div></div>`;
  }
  /* dialog that requires a reason (D7, E1, G6, N6, B4) */
  function reasonDlg(title, desc, onSubmit, opts) {
    opts = opts || {};
    return modal({
      title, desc, body: `${opts.extra || ''}<div class="field"><label class="req">Reason</label><textarea id="rsn" placeholder="${esc(opts.placeholder || 'Explain why this is needed…')}"></textarea><span class="err">A reason is required.</span></div>${opts.rule ? `<div class="mt">${opts.rule}</div>` : ''}`,
      actions: [{ label: 'Cancel', cls: 'btn-outline' }, {
        label: opts.yes || 'Send for owner approval', cls: 'btn-sapphire', onClick: ({ el }) => {
          const v = $('#rsn', el).value.trim(); const f = $('#rsn', el).closest('.field');
          if (v.length < 4) { f.classList.add('invalid'); return false; }
          return onSubmit(v, el);
        }
      }]
    });
  }

  /* ---------------- form validation ---------------- */
  function validate(root) {
    let ok = true, first = null;
    $$('[required]', root).forEach(inp => {
      const f = inp.closest('.field'); const bad = inp.type === 'checkbox' ? !inp.checked : !String(inp.value || '').trim();
      let badNum = false;
      if (!bad && inp.type === 'number') { const v = Number(inp.value); if (inp.min !== '' && v < Number(inp.min)) badNum = true; }
      if (!bad && inp.type === 'email' && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(inp.value)) badNum = true;
      if (f) { f.classList.toggle('invalid', bad || badNum); if ((bad || badNum) && !$('.err', f)) { const e = document.createElement('span'); e.className = 'err'; e.textContent = 'This field is required.'; f.appendChild(e); } }
      if (bad || badNum) { ok = false; first = first || inp; }
    });
    if (!ok) { toast('Please complete the required fields', 'Fields marked * are required.', 'error'); first && first.focus(); }
    return ok;
  }
  document.addEventListener('input', e => { const f = e.target.closest && e.target.closest('.field.invalid'); if (f) f.classList.remove('invalid'); });

  /* ---------------- HTML builders ---------------- */
  function table(cols, rows, opts) {
    opts = opts || {};
    if (!rows.length) return `<div class="empty">${opts.empty || 'Nothing to show yet.'}</div>`;
    const th = cols.map(c => `<th class="${c.num ? 'num' : ''} ${c.cls || ''}" ${c.roles ? `data-roles="${c.roles}"` : ''}>${c.h}</th>`).join('');
    const tr = rows.map((r, i) => `<tr ${opts.rowAttr ? opts.rowAttr(r, i) : ''}>${cols.map(c => `<td class="${c.num ? 'num' : ''} ${c.cls || ''}" data-label="${esc(String(c.h).replace(/<[^>]+>/g, ''))}" ${c.roles ? `data-roles="${c.roles}"` : ''}>${c.f(r, i)}</td>`).join('')}</tr>`).join('');
    const foot = opts.foot ? `<tfoot><tr>${opts.foot.map((f, i) => `<td class="${cols[i] && cols[i].num ? 'num' : ''}" ${cols[i] && cols[i].roles ? `data-roles="${cols[i].roles}"` : ''}>${f}</td>`).join('')}</tr></tfoot>` : '';
    return `<div class="table-wrap"><table class="table ${opts.cls || ''}" ${opts.id ? `id="${opts.id}"` : ''}><thead><tr>${th}</tr></thead><tbody>${tr}</tbody>${foot}</table></div>`;
  }
  function pageHead(o) {
    const brd = (o.brd || []).map(b => `<span class="brd">${b}</span>`).join('');
    return `<div class="page-head"><div>${o.crumbs ? `<div class="crumbs">${o.crumbs}</div>` : ''}<h1>${o.title}<span class="brd-ids">${brd}</span></h1>${o.sub ? `<p class="muted" style="margin:4px 0 0">${o.sub}</p>` : ''}</div><div class="actions">${o.actions || ''}</div></div>`;
  }
  const kpi = (label, value, sub, extra) => `<div class="card kpi ${extra || ''}"><div class="k-label">${label}</div><div class="k-value">${value}</div>${sub ? `<div class="k-sub">${sub}</div>` : ''}</div>`;
  const ruleNote = (n, text) => `<div class="rule-note"><span class="rn-tag">Business rule ${n}</span><span>${text}</span></div>`;
  const prodCell = (p, sub) => p ? `<div class="prod-cell"><div class="thumb">${gemSVG(p.gem, p.type, 36)}</div><div><b>${esc(p.name)}</b><span class="muted mono" style="font-size:11px">${p.id}</span>${sub ? ` · <span class="muted">${sub}</span>` : ''}</div></div>` : '—';
  const opts = (arr, sel, ph) => (ph != null ? `<option value="">${esc(ph)}</option>` : '') + arr.map(o => { const v = typeof o === 'object' ? o.v : o, l = typeof o === 'object' ? o.l : o; return `<option value="${esc(v)}" ${String(v) === String(sel) ? 'selected' : ''}>${esc(l)}</option>`; }).join('');
  const link = (path, text) => `<a href="${url(path)}">${text}</a>`;

  /* ---------------- photo upload (bill photo, product photos, cert scan) ---------------- */
  function bindUpload(el, onFile) {
    if (!el) return;
    const inp = document.createElement('input'); inp.type = 'file'; inp.accept = el.dataset.accept || 'image/*,.pdf'; inp.style.display = 'none'; if (el.dataset.multiple) inp.multiple = true;
    el.after(inp);
    el.addEventListener('click', () => inp.click());
    inp.addEventListener('change', () => {
      const files = Array.from(inp.files || []); if (!files.length) return;
      el.classList.add('done'); el.dataset.file = files.map(f => f.name).join(', ');
      el.innerHTML = `✔ ${esc(files.length > 1 ? files.length + ' files selected' : files[0].name)}<br><small>Resized &amp; compressed to ~300 KB on upload (tech stack §7)</small>`;
      const f = files[0];
      if (f.type.startsWith('image/')) { const r = new FileReader(); r.onload = () => { const img = document.createElement('img'); img.src = r.result; el.appendChild(img); }; r.readAsDataURL(f); }
      onFile && onFile(files);
      const fld = el.closest('.field'); if (fld) fld.classList.remove('invalid');
    });
  }
  function demoUpload(el, name) { el.classList.add('done'); el.dataset.file = name; el.innerHTML = '✔ ' + esc(name) + '<br><small>Demo file attached</small>'; }

  /* ---------------- log, alerts, history ---------------- */
  const Log = { add(action, detail, branch) { S.log.unshift({ at: new Date().toISOString(), user: currentUser().id, action, detail, branch: branch || activeBranch() }); } };
  const Alerts = { add(type, text, link, branch) { S.alerts.unshift({ id: 'AL-' + Date.now() + Math.floor(Math.random() * 99), at: new Date().toISOString(), type, text, read: false, link: link || '', branch: branch || activeBranch() }); } };
  const Hist = { add(pid, action, detail) { const p = prod(pid); if (p) p.history.push({ at: new Date().toISOString(), user: currentUser().id, action, detail }); } };

  /* ---------------- month locking (N6, rule 11) ---------------- */
  const isLocked = iso => !!(S.months[monthKey(iso)] && S.months[monthKey(iso)].status === 'closed');
  function guardLocked(iso) {
    if (isLocked(iso)) { toast('Month is closed', fmt.month(monthKey(iso)) + ' is locked. Ask the owner to re-open it (rule 11).', 'error'); return true; }
    return false;
  }

  /* ---------------- approvals (K3 + F3, E1, G6, D7, M6, N6) ---------------- */
  const APPROVAL_TYPES = {
    below_min: 'Below-minimum price', return: 'Return / exchange', cancel_invoice: 'Invoice cancellation',
    stock_correction: 'Stock correction', expense: 'Large expense', month_reopen: 'Re-open closed month', transfer: 'Branch transfer'
  };
  const Approvals = {
    pending() { return S.approvals.filter(a => a.status === 'pending' && (inBranch(a) || isRole('owner'))); },
    request(o) {
      const a = Object.assign({ id: nextId('approval', 'AP-'), status: 'pending', at: new Date().toISOString(), requestedBy: currentUser().id, branch: activeBranch(), amount: 0 }, o);
      S.approvals.unshift(a);
      Alerts.add(a.type, a.title, 'k-owner-control/approvals.html', a.branch);
      Log.add('Approval requested', a.title, a.branch);
      Store.save();
      return a;
    },
    decide(id, approve, note) {
      const a = find('approvals', id); if (!a || a.status !== 'pending') return;
      a.status = approve ? 'approved' : 'rejected'; a.decidedAt = new Date().toISOString(); a.decidedBy = currentUser().id; a.note = note || '';
      const h = Approvals.handlers[a.type]; if (h) h(a, approve);
      Log.add(approve ? 'Approved' : 'Rejected', a.title + (note ? ' — ' + note : ''), a.branch);
      Store.save();
    },
    handlers: {
      below_min(a, ok) {
        const h = find('held', a.payload.heldId); if (!h) return;
        h.status = ok ? 'approved' : 'rejected';
        h.items.forEach(i => { const p = prod(i.productId); if (!p) return; if (!ok && p.status === 'on_hold') p.status = 'in_stock'; Hist.add(p.id, ok ? 'Price approved' : 'Price rejected', 'Below-minimum LKR ' + nf(i.agreed) + (ok ? ' approved by owner' : ' rejected – back in stock')); });
      },
      return(a, ok) {
        const r = find('returns', a.payload.returnId); if (!r) return;
        r.status = ok ? 'approved' : 'rejected';
        if (!ok) return;
        const s = sale(r.saleId); const p = prod(r.productId);
        const line = s.items.find(i => i.productId === r.productId); if (line) line.returned = true;
        p.status = 'returned'; p.branch = s.branch; p.saleId = null;
        Hist.add(p.id, 'Returned', 'Customer return approved (' + r.id + '), back in stock');
        const cn = { id: nextId('credit', 'CN-'), saleId: s.id, returnId: r.id, productId: p.id, amount: r.amount, vat: Calc.split(r.amount).vat, date: new Date().toISOString(), by: currentUser().id, commissionReversed: true };
        S.creditNotes.unshift(cn); r.creditNoteId = cn.id;
        Commissions.reverseFor(s, [line], 'Reversed – return ' + r.id);
        Alerts.add('return', 'Return approved: ' + p.id + ' · credit note ' + cn.id, 'e-returns/credit-note.html?id=' + cn.id, s.branch);
      },
      cancel_invoice(a, ok) {
        const s = sale(a.payload.saleId); if (!s) return;
        s.cancelRequested = null;
        if (!ok) return;
        s.status = 'cancelled'; s.cancelledAt = new Date().toISOString(); s.cancelReason = a.payload.reason;
        s.items.filter(i => !i.returned).forEach(i => { const p = prod(i.productId); if (p) { p.status = 'in_stock'; p.saleId = null; Hist.add(p.id, 'Back in stock', 'Invoice ' + s.id + ' cancelled'); } });
        Commissions.reverseFor(s, s.items.filter(i => !i.returned), 'Reversed – invoice cancelled');
        Alerts.add('cancel', 'Invoice ' + s.id + ' cancelled (kept on record, rule 4)', 'g-payments/invoice.html?id=' + s.id, s.branch);
      },
      stock_correction(a, ok) {
        const c = find('corrections', a.payload.correctionId); if (!c) return;
        c.status = ok ? 'approved' : 'rejected'; if (!ok) return;
        const p = prod(c.productId);
        if (c.field === 'status') p.status = c.to; else if (c.field === 'branch') p.branch = c.to; else if (c.field === 'carat') p.carat = Number(c.to);
        Hist.add(p.id, 'Stock correction', c.field + ': ' + c.from + ' → ' + c.to + ' (' + c.reason + ')');
      },
      expense(a, ok) { const e = find('expenses', a.payload.expenseId); if (e) e.status = ok ? 'approved' : 'rejected'; },
      month_reopen(a, ok) { if (ok) S.months[a.payload.month] = { status: 'open', reopenedAt: new Date().toISOString(), by: currentUser().id, reason: a.detail }; }
    }
  };

  /* ---------------- commissions ---------------- */
  const Commissions = {
    createFor(s) {
      if (s.guideId) S.commissions.push({ id: 'CM-' + s.id.slice(-4) + 'G', saleId: s.id, payee: 'guide', payeeId: s.guideId, amount: s.guideComm, pct: s.guidePct, status: 'unpaid', paidAt: null, method: null, date: s.date, branch: s.branch });
      S.commissions.push({ id: 'CM-' + s.id.slice(-4) + 'S', saleId: s.id, payee: 'staff', payeeId: s.staffId, amount: s.staffComm, status: 'unpaid', paidAt: null, method: null, date: s.date, branch: s.branch });
    },
    /* E2: reverse commissions proportional to returned lines (guide %) and recalc staff slab */
    reverseFor(s, lines, note) {
      const before = Calc.saleTotals(s.items.filter(i => !i.returned || lines.includes(i)), s.guideId);
      const remaining = s.items.filter(i => !i.returned && !lines.includes(i));
      const after = remaining.length && s.status !== 'cancelled' ? Calc.saleTotals(remaining, s.guideId) : { guideComm: 0, staffComm: 0 };
      const now = new Date().toISOString();
      const g = r2(before.guideComm - after.guideComm), st = r2(before.staffComm - after.staffComm);
      if (s.guideId && g) S.commissions.push({ id: 'CM-R' + Date.now() + 'G', saleId: s.id, payee: 'guide', payeeId: s.guideId, amount: -g, status: 'reversed', date: now, note, branch: s.branch });
      if (st) S.commissions.push({ id: 'CM-R' + Date.now() + 'S', saleId: s.id, payee: 'staff', payeeId: s.staffId, amount: -st, status: 'reversed', date: now, note, branch: s.branch });
    },
    payeeName(c) { return c.payee === 'guide' ? (guide(c.payeeId) || {}).name : user(c.payeeId).name; }
  };

  /* ---------------- sales (F/G) ---------------- */
  const Sales = {
    available(p) { return p && ['in_stock', 'returned'].includes(p.status) && inBranch(p); },
    complete(draft, pay) {
      const t = Calc.saleTotals(draft.items, draft.guideId);
      const s = {
        id: nextId('invoice', S.shop.invoicePrefix, 6), date: new Date().toISOString(), branch: activeBranch(), customerId: draft.customerId, guideId: draft.guideId || null, staffId: currentUser().id,
        items: draft.items.map(i => ({ productId: i.productId, listPrice: i.listPrice, agreed: i.agreed, minPrice: i.minPrice, approvalId: i.approvalId || null })),
        payments: pay.payments, change: pay.change || 0, status: 'completed', sentEmail: false, sentWhatsapp: false, notes: draft.notes || '',
        subtotal: t.subtotal, net: t.net, vat: t.vat, total: t.total, guidePct: t.guidePct, guideComm: t.guideComm, staffComm: t.staffComm, heldId: draft.heldId || null, quoteId: draft.quoteId || null
      };
      S.sales.push(s);
      s.items.forEach(i => { const p = prod(i.productId); p.status = 'sold'; p.saleId = s.id; p.soldAt = s.date; Hist.add(p.id, 'Sold', 'Invoice ' + s.id + ' · LKR ' + nf(i.agreed)); });
      Commissions.createFor(s);
      if (draft.heldId) { const h = find('held', draft.heldId); if (h) h.status = 'completed'; }
      if (draft.quoteId) { const q = find('quotes', draft.quoteId); if (q) q.status = 'converted'; }
      const c = cust(s.customerId);
      Log.add('Sale completed', s.id + ' · ' + (c ? c.name : 'Customer') + ' · ' + fmt.lkr(s.total), s.branch);
      Alerts.add('sale', 'Sale ' + s.id + ' · ' + fmt.lkr(s.total) + ' · ' + (c ? c.country : ''), 'g-payments/invoice.html?id=' + s.id, s.branch);
      Store.save();
      return s;
    }
  };

  /* ---------------- payment dialog (G1, G2) ---------------- */
  function paymentDialog(total, onDone) {
    const lines = [];
    const body = document.createElement('div');
    const usdFull = Math.ceil(fx.from(total, 'USD')), eurFull = Math.ceil(fx.from(total, 'EUR'));
    body.innerHTML = `
      <div class="card" style="background:var(--navy);color:#fff;border:0"><div class="card-content">
        <div class="muted" style="color:#aab8d6">Amount to pay</div>
        <div style="font-size:26px;font-weight:700">${fmt.lkr(total)}</div>
        <div style="color:var(--gold)">USD ${nf(fx.from(total, 'USD'))} · EUR ${nf(fx.from(total, 'EUR'))}</div>
        <div style="font-size:11px;color:#aab8d6;margin-top:4px">Today's rates: 1 USD = LKR ${nf(S.rates.USD)} · 1 EUR = LKR ${nf(S.rates.EUR)} (owner set, A4)</div>
      </div></div>
      <div class="row mt-s"><span class="label">Quick:</span>
        <button class="btn btn-outline btn-sm" data-q="card">Full by card</button>
        <button class="btn btn-outline btn-sm" data-q="LKR">Cash LKR</button>
        <button class="btn btn-outline btn-sm" data-q="USD">Cash USD ${usdFull}</button>
        <button class="btn btn-outline btn-sm" data-q="EUR">Cash EUR ${eurFull}</button>
        <button class="btn btn-outline btn-sm" data-q="split">Split: USD cash + card</button>
      </div>
      <div class="mt" id="payLines"></div>
      <button class="btn btn-ghost btn-sm mt-s" id="addLine">+ Add payment line</button>
      <div class="sep"></div>
      <div class="dl" id="paySum"></div>
      <div class="mt-s" id="payMsg"></div>`;
    function renderLines() {
      $('#payLines', body).innerHTML = lines.map((l, i) => `
        <div class="row" style="margin-bottom:8px" data-i="${i}">
          <select data-k="kind" style="width:150px">${opts([{ v: 'cash-LKR', l: 'Cash · LKR' }, { v: 'cash-USD', l: 'Cash · USD' }, { v: 'cash-EUR', l: 'Cash · EUR' }, { v: 'card-LKR', l: 'Card (machine)' }], l.method + '-' + l.currency)}</select>
          <input class="input" data-k="amount" type="number" min="0" step="0.01" value="${l.amount}" style="width:150px">
          <span class="muted" data-eq style="min-width:140px">= ${fmt.lkr(fx.toLKR(l.amount || 0, l.currency))}</span>
          <button class="btn btn-ghost btn-sm" data-del="${i}">Remove</button>
        </div>`).join('') || '<div class="muted">No payment lines yet – use a quick button or add a line.</div>';
    }
    function renderSum() {
      const paid = r2(sum(lines, l => fx.toLKR(l.amount || 0, l.currency)));
      const cashPaid = r2(sum(lines.filter(l => l.method === 'cash'), l => fx.toLKR(l.amount || 0, l.currency)));
      const remaining = r2(Math.max(0, total - paid));
      const change = r2(Math.max(0, paid - total));
      const cardOver = change > 0 && change > cashPaid + 0.001;
      $('#paySum', body).innerHTML = `<dt>Paid (LKR)</dt><dd>${fmt.lkr(paid)}</dd><dt>Remaining</dt><dd class="${remaining > 0 ? 'text-danger' : 'text-success'}">${fmt.lkr(remaining)}</dd><dt>Change due</dt><dd style="font-size:18px" class="${change > 0 ? 'text-gold bold' : ''}">${fmt.lkr(change)}${change > 0 ? ` <small class="muted">(USD ${nf(fx.from(change, 'USD'))})</small>` : ''}</dd>`;
      $('#payMsg', body).innerHTML = cardOver ? '<div class="alert danger">Card payments cannot be more than the amount due. Reduce the card amount.</div>' : '';
      const btn = $('#payDone'); if (btn) btn.disabled = remaining > 0 || cardOver || !lines.length;
    }
    function render() { renderLines(); renderSum(); }
    body.addEventListener('input', e => {
      const row = e.target.closest('[data-i]'); if (!row) return; const l = lines[row.dataset.i];
      if (e.target.dataset.k === 'amount') { l.amount = Number(e.target.value) || 0; $('[data-eq]', row).textContent = '= ' + fmt.lkr(fx.toLKR(l.amount, l.currency)); renderSum(); }
    });
    body.addEventListener('change', e => {
      const row = e.target.closest('[data-i]'); if (!row) return; const l = lines[row.dataset.i];
      if (e.target.dataset.k === 'kind') { const [m, c] = e.target.value.split('-'); l.method = m; l.currency = c; render(); }
    });
    body.addEventListener('click', e => {
      const q = e.target.dataset.q; const d = e.target.dataset.del;
      if (d != null) { lines.splice(Number(d), 1); render(); }
      if (q) {
        lines.length = 0;
        if (q === 'card') lines.push({ method: 'card', currency: 'LKR', amount: total });
        else if (q === 'LKR') lines.push({ method: 'cash', currency: 'LKR', amount: Math.ceil(total / 1000) * 1000 });
        else if (q === 'USD') lines.push({ method: 'cash', currency: 'USD', amount: usdFull });
        else if (q === 'EUR') lines.push({ method: 'cash', currency: 'EUR', amount: eurFull });
        else if (q === 'split') { const usd = Math.floor(fx.from(total * 0.4, 'USD') / 50) * 50 || 50; lines.push({ method: 'cash', currency: 'USD', amount: usd }); lines.push({ method: 'card', currency: 'LKR', amount: r2(total - fx.toLKR(usd, 'USD')) }); }
        render();
      }
      if (e.target.id === 'addLine') { const paid = sum(lines, l => fx.toLKR(l.amount || 0, l.currency)); lines.push({ method: 'cash', currency: 'LKR', amount: r2(Math.max(0, total - paid)) }); render(); }
    });
    const m = modal({
      title: 'Take payment', desc: 'Cash in LKR / USD / EUR, card, or split payment (BRD G1, G2)', size: 'lg', body, focus: false,
      actions: [{ label: 'Cancel', cls: 'btn-outline' }, {
        label: 'Confirm payment', cls: 'btn-success', id: 'payDone', onClick: () => {
          const payments = lines.filter(l => l.amount > 0).map(l => ({ method: l.method, currency: l.currency, amount: r2(l.amount), rate: fx.rate(l.currency), lkr: fx.toLKR(l.amount, l.currency) }));
          const paid = sum(payments, p => p.lkr);
          onDone({ payments, change: r2(Math.max(0, paid - total)) });
        }
      }]
    });
    render();
    return m;
  }

  /* ---------------- send invoice / document dialog (G4, J5) ---------------- */
  function sendDialog(docLabel, customer, onSent) {
    const wa = S.messaging.wa;
    const waOk = wa.enabled && wa.verified;
    const emailOk = S.messaging.emailOn && S.messaging.smtp.enabled;
    const body = `
      <div class="stack" style="gap:12px">
        <label class="check"><input type="checkbox" id="sdE" ${emailOk ? 'checked' : 'disabled'}> <span><b>Email</b> (PDF attached, shop SMTP)${emailOk ? '' : ' <span class="badge badge-danger">Email messages are switched off</span>'}</span></label>
        <div class="field"><label>Email address</label><input class="input" id="sdEmail" type="email" value="${esc(customer ? customer.email : '')}"></div>
        <label class="check"><input type="checkbox" id="sdW" ${waOk ? 'checked' : 'disabled'}> <span><b>WhatsApp</b> (PDF, utility template)${waOk ? '' : ' <span class="badge badge-warning">WhatsApp Premium is off / not verified</span>'}</span></label>
        <div class="field"><label>WhatsApp number</label><input class="input" id="sdWa" value="${esc(customer ? customer.whatsapp : '')}" ${waOk ? '' : 'disabled'}></div>
        ${waOk ? '' : `<div class="alert info">Turn on WhatsApp Business (Premium) in <a href="${url('j-customers-promotions/message-settings.html')}">Message settings</a> (BRD J7, tech stack §6.2).</div>`}
        <div class="card" style="background:var(--muted)"><div class="card-content" style="font-size:12px"><b>Message preview</b><br>Dear ${esc(customer ? customer.name.split(' ')[0] : 'customer')}, thank you for shopping at ${esc(S.shop.name)}, Ella. Please find your ${esc(docLabel)} attached as a PDF. Safe travels! 💎<br><span class="muted">— Demo only: no real email or WhatsApp is sent.</span></div></div>
      </div>`;
    modal({
      title: 'Send ' + docLabel, desc: 'Send to the customer by email and/or WhatsApp (BRD G4)', body,
      actions: [{ label: 'Cancel', cls: 'btn-outline' }, {
        label: 'Send', cls: 'btn-sapphire', onClick: ({ el }) => {
          const e = $('#sdE', el).checked, w = $('#sdW', el).checked;
          if (!e && !w) { toast('Choose email or WhatsApp', '', 'error'); return false; }
          if (e && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test($('#sdEmail', el).value)) { toast('Enter a valid email address', '', 'error'); return false; }
          const ch = [e && 'email (' + $('#sdEmail', el).value + ')', w && 'WhatsApp (' + $('#sdWa', el).value + ')'].filter(Boolean).join(' and ');
          toast(docLabel + ' sent', 'Sent by ' + ch + ' (demo).', 'success');
          onSent && onSent({ email: e, whatsapp: w });
        }
      }]
    });
  }

  /* ---------------- exports (L6) ---------------- */
  function exportExcel(tableEl, name) {
    if (!tableEl) { toast('Nothing to export', '', 'error'); return; }
    const rows = $$('tr', tableEl).filter(tr => tr.offsetParent !== null || true).map(tr => $$('th,td', tr).filter(c => getComputedStyle(c).display !== 'none').map(c => '"' + c.innerText.replace(/\s+/g, ' ').trim().replace(/"/g, '""') + '"').join(','));
    const blob = new Blob(['﻿' + rows.join('\r\n')], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = name.replace(/\s+/g, '_') + '_' + todayYmd() + '.csv'; document.body.appendChild(a); a.click(); a.remove();
    toast('Excel export ready', a.download + ' downloaded (CSV opens in Excel). Real build: Apache POI .xlsx.', 'success');
  }
  function exportPDF(name) { toast('Preparing PDF', name + ' – choose "Save as PDF" in the print dialog. Real build: server-side OpenPDF.', 'success'); setTimeout(() => window.print(), 400); }
  function exportButtons(tableSel, name) {
    return `<button class="btn btn-outline" onclick="UI.exportPDF('${esc(name)}')">⇩ Export PDF</button><button class="btn btn-outline" onclick="UI.exportExcel(document.querySelector('${tableSel}'),'${esc(name)}')">⇩ Export Excel</button>`;
  }

  /* ---------------- tag helpers (C1): Code 39 barcode + demo QR pattern ---------------- */
  const C39 = { '0': 'nnnwwnwnn', '1': 'wnnwnnnnw', '2': 'nnwwnnnnw', '3': 'wnwwnnnnn', '4': 'nnnwwnnnw', '5': 'wnnwwnnnn', '6': 'nnwwwnnnn', '7': 'nnnwnnwnw', '8': 'wnnwnnwnn', '9': 'nnwwnnwnn', 'A': 'wnnnnwnnw', 'B': 'nnwnnwnnw', 'C': 'wnwnnwnnn', 'D': 'nnnnwwnnw', 'E': 'wnnnwwnnn', 'F': 'nnwnwwnnn', 'G': 'nnnnnwwnw', 'H': 'wnnnnwwnn', 'I': 'nnwnnwwnn', 'J': 'nnnnwwwnn', 'K': 'wnnnnnnww', 'L': 'nnwnnnnww', 'M': 'wnwnnnnwn', 'N': 'nnnnwnnww', 'O': 'wnnnwnnwn', 'P': 'nnwnwnnwn', 'Q': 'nnnnnnwww', 'R': 'wnnnnnwwn', 'S': 'nnwnnnwwn', 'T': 'nnnnwnwwn', 'U': 'wwnnnnnnw', 'V': 'nwwnnnnnw', 'W': 'wwwnnnnnn', 'X': 'nwnnwnnnw', 'Y': 'wwnnwnnnn', 'Z': 'nwwnwnnnn', '-': 'nwnnnnwnw', '.': 'wwnnnnwnn', ' ': 'nwwnnnwnn', '*': 'nwnnwnwnn' };
  function barcodeSVG(text, h) {
    h = h || 40; const n = 1.4, w = 3.2; let x = 6, bars = '';
    ('*' + text.toUpperCase() + '*').split('').forEach(ch => {
      const pat = C39[ch] || C39['-'];
      pat.split('').forEach((p, i) => { const bw = p === 'w' ? w : n; if (i % 2 === 0) bars += `<rect x="${x.toFixed(1)}" y="0" width="${bw}" height="${h}"/>`; x += bw; });
      x += n;
    });
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${(x + 6).toFixed(0)} ${h}" width="100%" height="${h}" preserveAspectRatio="none" aria-label="Barcode ${esc(text)}"><rect width="100%" height="100%" fill="#fff"/><g fill="#000">${bars}</g></svg>`;
  }
  function qrSVG(text, size) {
    size = size || 72; const N = 25; let h = 2166136261; const bits = [];
    for (let i = 0; i < N * N; i++) { h ^= text.charCodeAt(i % text.length) + i; h = Math.imul(h, 16777619) >>> 0; bits.push((h >>> 7) & 1); }
    const finder = (r, c) => (r < 7 && c < 7) || (r < 7 && c >= N - 7) || (r >= N - 7 && c < 7);
    let cells = '';
    for (let r = 0; r < N; r++) for (let c = 0; c < N; c++) {
      let on;
      if (finder(r, c)) { const a = r >= N - 7 ? r - (N - 7) : r, b = c >= N - 7 ? c - (N - 7) : c; on = (a === 0 || a === 6 || b === 0 || b === 6) || (a >= 2 && a <= 4 && b >= 2 && b <= 4); }
      else if ((r === 7 || c === 7 || r === N - 8 || c === N - 8) && (r < 8 || c < 8 || r > N - 9)) on = false;
      else on = bits[r * N + c] === 1;
      if (on) cells += `<rect x="${c}" y="${r}" width="1" height="1"/>`;
    }
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="-1 -1 ${N + 2} ${N + 2}" width="${size}" height="${size}" shape-rendering="crispEdges" aria-label="QR code ${esc(text)}"><rect x="-1" y="-1" width="${N + 2}" height="${N + 2}" fill="#fff"/><g fill="#000">${cells}</g></svg>`;
  }

  /* ---------------- misc ---------------- */
  function accessDenied(msg) {
    return `<div class="card access-denied"><div class="card-content" style="padding:32px"><div style="font-size:40px">🔒</div><h2>Not available for the ${esc(ROLES[P.role])} role</h2><p class="muted mt-s">${msg || 'Switch the DEMO role in the top bar to see this screen.'}</p><a class="btn btn-outline mt" href="${url('dashboard/index.html')}">Back to dashboard</a></div></div>`;
  }
  // Re-read state when another tab (e.g. the owner's approvals inbox) changes it.
  const listeners = [];
  window.addEventListener('storage', e => { if (e.key === LS_STATE) { Store.refresh(); listeners.forEach(f => f()); } });
  function onStateChange(f) { listeners.push(f); }
  function poll(f, ms) { return setInterval(() => { Store.refresh(); f(); }, ms || 3000); }

  /* ---------------- expose ---------------- */
  window.S = S;
  Object.assign(window, {
    ROOT, esc, $, $$, qs, url, r2, ymd, todayYmd, daysAgoYmd, monthKey, sum, groupBy, initials,
    Calc, Store, DemoReset, P, Prefs, ROLES, currentUser, isRole, branchScope, inBranch, activeBranch,
    find, prod, cust, guide, user, sup, sale, branchName, nextId, fx, fmt, gemSVG, gemPic, GEM_COLORS,
    badge, certBadge, STATUS, Log, Alerts, Hist, isLocked, guardLocked, Approvals, APPROVAL_TYPES, Commissions, Sales,
    barcodeSVG, qrSVG, onStateChange, poll, toast,
    UI: { toast, modal, confirm: confirmDlg, empty: emptyPage, reason: reasonDlg, validate, table, pageHead, kpi, ruleNote, prodCell, opts, link, bindUpload, demoUpload, payment: paymentDialog, send: sendDialog, exportExcel, exportPDF, exportButtons, accessDenied }
  });
  // Calc must exist before buildSeed() runs on first load; S is created above after Calc.
})();

/* ---------------- Metrics shared by dashboards (K1, D6, I3) ---------------- */
(function () {
  const inB = (x, b) => b === 'all' || x.branch === b;
  window.Metrics = {
    STOCK_STATUSES: ['in_stock', 'reserved', 'returned', 'on_hold', 'in_transit'],
    salesOn(date, b) { return S.sales.filter(s => s.status !== 'cancelled' && ymd(s.date) === date && inB(s, b)); },
    salesBetween(from, to, b) { return S.sales.filter(s => ymd(s.date) >= from && ymd(s.date) <= to && inB(s, b || 'all')); },
    stock(b) {
      const ps = S.products.filter(p => Metrics.STOCK_STATUSES.includes(p.status) && inB(p, b));
      return { items: ps, count: ps.length, cost: sum(ps, p => p.cost), sell: sum(ps, p => p.price) };
    },
    cash(date, b) {
      const bs = b === 'all' ? S.branches.map(x => x.id) : [b];
      const out = { LKR: 0, USD: 0, EUR: 0, card: 0 };
      bs.forEach(id => { const e = Calc.expectedCash(S, date, id); out.LKR += e.cash.LKR; out.USD += e.cash.USD; out.EUR += e.cash.EUR; out.card += e.card; });
      return out;
    },
    cashLKR(c) { return c.LKR + fx.toLKR(c.USD, 'USD') + fx.toLKR(c.EUR, 'EUR'); },
    dailySeries(days, b) {
      const out = [];
      for (let i = days - 1; i >= 0; i--) { const d = daysAgoYmd(i); out.push({ d, v: sum(Metrics.salesOn(d, b), s => s.total) }); }
      return out;
    },
    bars(series, fmtV) {
      const max = Math.max(1, ...series.map(x => x.v));
      return `<div class="chart-bars">${series.map(x => `<div class="cb" title="${fmt.date(x.d)}: ${fmtV ? fmtV(x.v) : x.v}"><i style="height:${Math.round(x.v / max * 100)}%"></i><span>${x.d.slice(8)}</span></div>`).join('')}</div>`;
    }
  };
})();

/* ---------------- printable document header (G3, C10, F7, H4, E2) ---------------- */
(function () {
  UI.docHead = function (title, rightHTML) {
    const sh = S.shop;
    return `<div class="doc-head"><div class="logo"><img src="${url('assets/img/logo.svg')}" width="54" height="54" alt=""><div><h1 style="color:var(--navy)">${esc(sh.name)}</h1><div style="font-size:12px;color:#555">${esc(sh.tagline)}<br>${esc(sh.address)}<br>${esc(sh.phone)} · ${esc(sh.email)}${sh.printLicenceOnInvoice ? `<br><b>NGJA Licence:</b> ${esc(sh.ngjaNo)}` : ''}</div></div></div>
      <div style="text-align:right"><h1 style="color:var(--sapphire);font-size:20px;letter-spacing:.04em">${title}</h1>${rightHTML || ''}</div></div>`;
  };
  UI.docToolbar = function (backHref, backLabel, extra) {
    return `<div class="doc-toolbar no-print"><div style="flex:1"></div>${extra || ''}<button class="btn btn-sapphire" onclick="window.print()">🖨 Print</button></div>`;
  };
})();

/* ---------------- monthly finance figures (N4) ---------------- */
(function () {
  window.Fin = {
    months() { return [...new Set(S.sales.map(s => monthKey(s.date)).concat(S.expenses.map(e => monthKey(e.date)), [monthKey(todayYmd())]))].sort().reverse(); },
    month(mk, b) {
      const inB = x => !b || b === 'all' || x.branch === b;
      const sales = S.sales.filter(s => monthKey(s.date) === mk && s.status !== 'cancelled' && inB(s));
      const cns = S.creditNotes.filter(c => monthKey(c.date) === mk && inB(sale(c.saleId) || {}));
      const grossNet = sum(sales, s => sum(s.items, i => i.agreed));
      const cnNet = sum(cns, c => c.amount);
      const cogs = sum(sales, s => Calc.saleCost(s));
      const comm = sum(S.commissions.filter(c => monthKey(c.date) === mk && inB(sale(c.saleId) || {}) && (sale(c.saleId) || {}).status !== 'cancelled'), c => c.amount);
      const ex = S.expenses.filter(e => e.status === 'approved' && monthKey(e.date) === mk && inB(e));
      const expenses = sum(ex, e => e.amount);
      const salesNet = r2(grossNet - cnNet);
      return { mk, invoices: sales.length, salesNet, grossNet, cnNet, cogs, comm, expenses, gross: r2(salesNet - cogs), net: r2(salesNet - cogs - comm - expenses), byCat: groupBy(ex, e => e.category) };
    }
  };
})();

/* ---------------- campaign helpers (J2, J3, J4, J6) ---------------- */
(function () {
  window.Campaign = {
    gemsBought(c) { return [...new Set(S.sales.filter(s => s.customerId === c.id && s.status !== 'cancelled').flatMap(s => s.items.map(i => (prod(i.productId) || {}).gem)))]; },
    /* rule 8: only customers who gave permission and have not unsubscribed */
    audience(aud, channel) {
      const all = S.customers.filter(c => (!aud.countries.length || aud.countries.includes(c.country)) && (!aud.gems.length || Campaign.gemsBought(c).some(g => aud.gems.includes(g))));
      const hasContact = c => channel === 'email' ? !!c.email : !!c.whatsapp;
      const ok = all.filter(c => c.consent && !c.unsubscribed && hasContact(c));
      return { all, ok, excluded: all.length - ok.length };
    },
    audienceUI(prefix) {
      const countries = [...new Set(S.customers.map(c => c.country))].sort();
      const chip = (attr, v) => `<label class="badge badge-outline" style="cursor:pointer;padding:4px 10px"><input type="checkbox" ${attr} value="${esc(v)}" style="margin-right:4px">${esc(v)}</label>`;
      return `<div class="field"><label>Countries <small class="muted">(none ticked = all)</small></label><div class="row" style="gap:6px">${countries.map(c => chip('data-' + prefix + 'c', c)).join('')}</div></div>
        <div class="field mt"><label>Gem bought before <small class="muted">(none ticked = any)</small></label><div class="row" style="gap:6px">${S.lists.gemTypes.filter(g => g !== 'Other').map(g => chip('data-' + prefix + 'g', g)).join('')}</div></div>`;
    },
    readAudience(root, prefix) {
      return { countries: Array.from(root.querySelectorAll('[data-' + prefix + 'c]:checked')).map(i => i.value), gems: Array.from(root.querySelectorAll('[data-' + prefix + 'g]:checked')).map(i => i.value) };
    },
    send(c) {
      const a = Campaign.audience(c.audience, c.channel); const n = a.ok.length;
      const del = c.channel === 'email' ? Math.round(n * 0.97) : Math.round(n * 0.95);
      c.status = 'sent'; c.sentAt = new Date().toISOString(); c.by = currentUser().id;
      c.stats = { sent: n, delivered: del, opened: Math.round(del * (c.channel === 'email' ? 0.48 : 0.8)), clicked: Math.round(del * 0.18), unsubscribed: n > 5 ? 1 : 0 };
      if (!S.campaigns.includes(c)) S.campaigns.unshift(c);
      Log.add('Campaign sent', c.name + ' · ' + c.channel + ' · ' + n + ' recipients');
      Store.save();
      return n;
    }
  };
})();
