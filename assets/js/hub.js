/* =====================================================================
   Section home pages (hub/*.html). One renderer for all sections:
     1. "Needs attention" – things waiting in this section, with one button each
     2. Step bar for work done in order (cash day, month end)
     3. Groups from SECTIONS in layout.js: big task tiles, look-up cards with
        live counts, and a short list of less-used screens
     4. A small "recent" list where it helps (last invoices, newest gems)
   ===================================================================== */
(function () {
  'use strict';
  const sec = window.CUR_SECTION; if (!sec || !sec.hub || !window.PAGE_ALLOWED) return;
  const { visible } = NavInfo;
  const b = branchScope(), today = todayYmd();
  const inB = x => b === 'all' || !x || x.branch === b || x.branch === 'all';
  const days = (a, z) => Math.round((new Date(z) - new Date(a)) / 864e5);
  const mkNow = monthKey(new Date().toISOString());

  /* ---------- live counts on the look-up cards ---------- */
  const COUNT = {
    'g-payments/invoices.html': () => { const n = Metrics.salesOn(today, b).length; return n + ' today'; },
    'f-pos/reservations.html': () => { const n = S.quotes.filter(q => q.status === 'active' && inB(q)).length; return n ? n + ' active' : 'none'; },
    'e-returns/returns.html': () => { const n = S.returns.filter(r => r.status === 'pending' && inB(sale(r.saleId))).length; return n ? { t: n + ' waiting', tone: 'warning' } : 'none waiting'; },
    'e-returns/credit-note.html': () => S.creditNotes.length + ' in total',
    'c-products/products.html': () => Metrics.stock(b).count + ' in stock',
    'd-stock/stock.html': () => S.branches.length + ' branches',
    'b-purchasing/suppliers.html': () => S.suppliers.length + ' suppliers',
    'b-purchasing/purchase-history.html': () => S.purchases.filter(p => monthKey(p.date) === mkNow).length + ' this month',
    'j-customers-promotions/customers.html': () => S.customers.length + ' customers',
    'h-commissions/guides.html': () => S.guides.length + ' guides',
    'h-commissions/commissions-payable.html': () => { const v = sum(S.commissions.filter(c => c.status === 'unpaid' && c.payee === 'guide' && inB(c)), c => c.amount); return v ? { t: fmt.lkr(v) + ' to pay', tone: 'warning' } : 'all paid'; },
    'j-customers-promotions/campaign-report.html': () => S.campaigns.filter(c => c.status === 'sent').length + ' sent',
    'm-expenses/expenses.html': () => fmt.lkr(sum(S.expenses.filter(e => monthKey(e.date) === mkNow && e.status !== 'rejected' && inB(e)), e => e.amount)) + ' this month',
    'm-expenses/recurring.html': () => S.recurring.filter(r => r.active).length + ' bills',
    'l-reports/sales.html': () => fmt.cur(sum(Metrics.salesOn(today, b), s => s.total)) + ' today',
    'n-accountant/month-closing.html': () => { const open = Object.keys(S.months).length; return open + ' closed'; },
    'a-setup/currencies.html': () => 'USD ' + fmt.num(S.rates.USD) + ' · EUR ' + fmt.num(S.rates.EUR),
    'a-setup/users.html': () => S.users.length + ' logins',
    'a-setup/shop-vat.html': () => { const d = days(today, S.shop.ngjaExpiry); return d <= S.shop.ngjaRemindDays ? { t: 'Licence: ' + d + ' days left', tone: 'warning' } : 'Licence OK'; }
  };

  /* ---------- "needs attention" per section ---------- */
  const A = (tone, icon, text, href, btn) => ({ tone, icon, text, href, btn });
  const ATT = {
    sell() {
      const out = [];
      if (isRole('owner', 'salesperson')) {
        S.held.filter(h => h.status === 'approved' && inB(h)).forEach(h => out.push(A('success', '✅', `<b>Owner approved the price</b> for ${esc(prod(h.items[0].productId).name)} – finish the sale and take payment.`, 'f-pos/pos.html?held=' + h.id, 'Finish sale')));
        const own = isRole('owner');
        S.held.filter(h => h.status === 'pending' && inB(h)).forEach(h => out.push(A('warning', '⏳', `<b>${own ? 'Price needs your approval' : 'Waiting for the owner'}</b> – below minimum for ${esc(prod(h.items[0].productId).name)} (${fmt.ago(h.at)}).`, own ? 'k-owner-control/approvals.html#' + h.approvalId : 'f-pos/pos.html?held=' + h.id, own ? 'Approve' : 'Open')));
        S.quotes.filter(q => q.status === 'active' && q.kind === 'reservation' && inB(q) && days(today, q.expires) <= 2).forEach(q => out.push(A('info', '📌', `<b>Reservation ends ${days(today, q.expires) <= 0 ? 'today' : 'on ' + fmt.date(q.expires)}</b> – ${esc((cust(q.customerId) || {}).name)}, ${q.items.length} gem(s).`, 'f-pos/reservations.html', 'View')));
        S.returns.filter(r => r.status === 'pending' && inB(sale(r.saleId))).forEach(r => out.push(A('warning', '↩️', `<b>${own ? 'Return needs your approval' : 'Return waiting for the owner'}</b> – ${esc(prod(r.productId).name)} (${r.kind === 'exchange' ? 'exchange' : 'refund'}).`, 'e-returns/new-return.html?id=' + r.id, own ? 'Approve' : 'View')));
        S.returns.filter(r => r.status === 'approved' && r.creditNoteId && days(r.at, new Date().toISOString()) <= 2 && inB(sale(r.saleId))).forEach(r => out.push(A('success', '📄', `<b>Return approved</b> – give the customer credit note ${r.creditNoteId}${r.kind === 'exchange' ? ' and start the new sale' : ' and refund'}.`, 'e-returns/credit-note.html?id=' + r.creditNoteId, 'Credit note')));
      }
      return out;
    },
    stock() {
      const out = [];
      const tr = S.transfers.filter(t => t.status === 'in_transit');
      if (tr.length && isRole('owner', 'staff')) out.push(A('info', '🚚', `<b>${tr.length} transfer(s) on the way</b> – mark them received when the gems arrive.`, 'd-stock/transfers.html', 'Open'));
      const sc = S.corrections.filter(c => c.status === 'pending');
      if (sc.length) out.push(A('warning', '✎', `<b>${sc.length} stock correction(s) waiting for the owner.</b>`, 'k-owner-control/approvals.html', 'Approvals'));
      const back = S.products.filter(p => p.status === 'returned' && inB(p));
      if (back.length && isRole('owner', 'staff', 'salesperson')) out.push(A('info', '↩️', `<b>${back.length} gem(s) came back from customers</b> and can be sold again – check the tag is still on.`, 'c-products/products.html', 'See gems'));
      return out;
    },
    people() {
      const out = [];
      if (isRole('owner', 'accountant')) {
        const by = groupBy(S.commissions.filter(c => c.status === 'unpaid' && c.payee === 'guide' && inB(c)), c => c.payeeId);
        const n = Object.keys(by).length;
        if (n) out.push(A('warning', '🚐', `<b>${n} guide(s) / driver(s) are waiting for commission</b> – ${fmt.lkr(sum(Object.values(by).flat(), c => c.amount))} in total.`, 'h-commissions/commissions-payable.html', 'Pay now'));
      }
      return out;
    },
    money() {
      const out = [];
      const br = activeBranch();
      const opened = S.openings[today + '|' + br], closed = S.closings.find(c => c.date === today && c.branch === br);
      if (isRole('owner', 'salesperson')) {
        if (!opened) out.push(A('info', '☀️', '<b>The day is not started.</b> Count the cash in the drawer first.', 'i-daily-closing/opening-cash.html', 'Start the day'));
        else if (!closed && new Date().getHours() >= 17) out.push(A('warning', '🌙', '<b>Closing time</b> – count the cash and close the day.', 'i-daily-closing/day-end.html', 'Close the day'));
      }
      const pe = S.expenses.filter(e => e.status === 'pending' && inB(e));
      if (pe.length && isRole('owner', 'accountant')) out.push(A('warning', '🧾', `<b>${pe.length} large expense(s) waiting for the owner.</b>`, 'k-owner-control/approvals.html', 'Approvals'));
      const short = S.closings.filter(c => inB(c) && days(c.date, today) <= 7).filter(c => Math.abs(Calc.closingDiff(c) || 0) > 1);
      if (short.length && isRole('owner', 'accountant')) out.push(A('info', '⚖️', `<b>${short.length} day(s) this week did not match the expected cash.</b>`, 'l-reports/daily-closing.html', 'See days'));
      return out;
    },
    reports() {
      const lm = new Date(); lm.setDate(1); lm.setMonth(lm.getMonth() - 1);
      const mk = monthKey(lm.toISOString()); const m = S.months[mk];
      return m && m.status === 'closed' ? [] : [A('info', '🔒', `<b>${fmt.month(mk)} is not closed yet.</b> Check the VAT and Profit & Loss, then close the month.`, 'n-accountant/month-closing.html', 'Close month')];
    },
    settings() {
      const out = []; const d = days(today, S.shop.ngjaExpiry);
      if (d <= S.shop.ngjaRemindDays) out.push(A('warning', '📜', `<b>Gem dealer licence expires in ${d} days</b> (${fmt.date(S.shop.ngjaExpiry)}).`, 'a-setup/shop-vat.html', 'Update'));
      if (S.rates.updatedAt && ymd(S.rates.updatedAt) !== today) out.push(A('info', '💱', '<b>Exchange rates were not updated today.</b>', 'a-setup/currencies.html', 'Update rates'));
      return out;
    }
  };

  /* ---------- small "recent" lists ---------- */
  const RECENT = {
    sell() {
      if (!isRole('owner', 'salesperson', 'accountant')) return '';
      const list = S.sales.filter(s => inB(s)).sort((a, z) => z.date.localeCompare(a.date)).slice(0, 6);
      return `<div class="card"><div class="card-header"><h3>Latest invoices</h3><a class="btn btn-sm btn-ghost" href="../g-payments/invoices.html">All invoices →</a></div>
        <ul class="list hub-recent">${list.map(s => `<li><a href="../g-payments/invoice.html?id=${s.id}">${gemSVG(prod(s.items[0].productId).gem, prod(s.items[0].productId).type, 34)}<span style="flex:1"><b>${esc((cust(s.customerId) || {}).name || '—')}</b> <small class="muted">${esc((cust(s.customerId) || {}).country || '')}</small><br><small class="muted mono">${s.id} · ${fmt.dt(s.date)} · ${s.items.length} gem(s)</small></span><span class="right"><b>${fmt.cur(s.total)}</b><br>${badge(s.status)}</span></a></li>`).join('')}</ul></div>`;
    },
    stock() {
      if (!isRole('owner', 'salesperson', 'staff')) return '';
      const list = S.products.filter(p => inB(p) && Metrics.STOCK_STATUSES.includes(p.status)).sort((a, z) => (z.createdAt || z.id).localeCompare(a.createdAt || a.id)).slice(0, 6);
      return `<div class="card"><div class="card-header"><h3>Newest gems</h3><a class="btn btn-sm btn-ghost" href="../c-products/products.html">All gems →</a></div>
        <ul class="list hub-recent">${list.map(p => `<li><a href="../c-products/product-detail.html?id=${p.id}">${gemSVG(p.gem, p.type, 34)}<span style="flex:1"><b>${esc(p.name)}</b><br><small class="muted mono">${p.id} · ${fmt.ct(p.carat)}</small></span><span class="right"><b>${fmt.cur(p.price)}</b><br>${badge(p.status)}</span></a></li>`).join('')}</ul></div>`;
    }
  };

  /* ---------- render ---------- */
  const countHTML = page => {
    const f = COUNT[NavInfo.strip(page)]; if (!f) return '';
    let v; try { v = f(); } catch (e) { return ''; }
    const o = typeof v === 'object' ? v : { t: v };
    return `<span class="hc-count ${o.tone || ''}">${esc(o.t)}</span>`;
  };
  const groupHTML = g => {
    const items = visible(g.items); if (!items.length) return '';
    if (g.show === 'tiles') return `<h2 class="hub-h">${esc(g.name)}</h2><div class="home-tiles hub-tiles">${items.map((it, i) => `<a class="home-tile ${i === 0 ? 'primary' : ''}" href="../${it[0]}"><span class="ht-i">${it[2]}</span><b>${esc(it[1])}</b>${it[3] ? `<small>${esc(it[3])}</small>` : ''}</a>`).join('')}</div>`;
    if (g.show === 'cards') return `<h2 class="hub-h">${esc(g.name)}</h2><div class="hub-cards">${items.map(it => `<a class="hub-card" href="../${it[0]}"><span class="hc-i">${it[2]}</span><span class="hc-t"><b>${esc(it[1])}</b>${it[3] ? `<small>${esc(it[3])}</small>` : ''}</span>${countHTML(it[0])}<span class="hc-go">›</span></a>`).join('')}</div>`;
    return `<details class="hub-more" ${sessionStorage.getItem('hubmore.' + sec.key) === '1' ? 'open' : ''}><summary>${esc(g.name)} <small class="muted">(${items.length})</small></summary><div class="hub-links">${items.map(it => `<a href="../${it[0]}"><span>${it[2]}</span>${esc(it[1])}${it[3] ? ` <small class="muted">– ${esc(it[3])}</small>` : ''}</a>`).join('')}</div></details>`;
  };

  const att = (ATT[sec.key] ? ATT[sec.key]() : []);
  const flow = sec.key === 'money' && isRole('owner', 'salesperson') ? Flows.bar('day', null)
    : sec.key === 'reports' ? Flows.bar('monthend', null)
    : sec.key === 'stock' && isRole('owner') ? Flows.bar('newstock', null, { compact: true }) : '';
  const recent = RECENT[sec.key] ? RECENT[sec.key]() : '';
  const groups = sec.groups.map(groupHTML).join('');

  $('#page').innerHTML = `
    <div class="hub-head"><span class="hh-i">${sec.icon}</span><div><h1>${esc(sec.title)}</h1><p class="muted">${esc(sec.intro || '')}${b !== 'all' ? ' · ' + esc(branchName(b)) : ''}</p></div></div>
    ${att.length ? `<div class="att-list mb">${att.map(a => `<div class="att ${a.tone}"><span class="att-i">${a.icon}</span><span class="att-t">${a.text}</span>${a.href ? `<a class="btn btn-sm ${a.tone === 'success' ? 'btn-success' : 'btn-outline'}" href="../${a.href}">${esc(a.btn || 'Open')}</a>` : ''}</div>`).join('')}</div>` : `<div class="att-ok mb">✓ Nothing needs your attention here right now.</div>`}
    ${flow ? `<div class="mb">${flow}</div>` : ''}
    <div class="${recent ? 'hub-grid' : ''}"><div>${groups}</div>${recent ? `<div class="hub-side">${recent}</div>` : ''}</div>`;
  $$('.hub-more').forEach(d => d.addEventListener('toggle', () => { try { sessionStorage.setItem('hubmore.' + sec.key, d.open ? '1' : '0'); } catch (e) { } }));
})();
