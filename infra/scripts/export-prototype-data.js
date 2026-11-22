// Runs the prototype's own buildSeed() (prototype/assets/js/data.js) in Node and writes the demo state as JSON.
// Usage (from Gold House):
//   node infra/scripts/export-prototype-data.js prototype server/src/main/resources/sample-data/prototype-v12.json 2026-11-22
// The backend moves every date forward from that day to the real today when it loads the data.
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const [protoDir, outFile, today] = process.argv.slice(2);
const fixedNow = new Date(today + 'T10:30:00+05:30').getTime();
const RealDate = Date;
class FixedDate extends RealDate {
  constructor(...a) { if (a.length === 0) super(fixedNow); else super(...a); }
  static now() { return fixedNow; }
}

const store = {};
const noop = () => {};
const el = new Proxy(function () {}, { get: (t, k) => (k === Symbol.toPrimitive ? () => '' : el), apply: () => el, set: () => true });
const ctx = {
  Date: FixedDate, Math, JSON, console, Intl, Number, String, Array, Object, Promise, setTimeout: noop, clearTimeout: noop,
  localStorage: { getItem: k => store[k] ?? null, setItem: (k, v) => { store[k] = String(v); }, removeItem: k => delete store[k] },
  sessionStorage: { getItem: () => null, setItem: noop, removeItem: noop },
  location: { href: 'http://x/index.html', pathname: '/index.html', search: '', hash: '' },
  navigator: { userAgent: 'node' },
  addEventListener: noop,
  document: { addEventListener: noop, querySelector: () => null, querySelectorAll: () => [], getElementById: () => null,
              createElement: () => el, body: el, documentElement: el, readyState: 'loading', title: '' },
};
ctx.window = ctx; ctx.self = ctx; ctx.globalThis = ctx;
vm.createContext(ctx);
for (const f of ['data.js', 'app.js']) {
  try { vm.runInContext(fs.readFileSync(path.join(protoDir, 'assets/js', f), 'utf8'), ctx, { filename: f }); }
  catch (e) { console.error('load', f, e.message); }
}
if (typeof ctx.buildSeed !== 'function') throw new Error('buildSeed not found');
const S = ctx.buildSeed();
const out = { exportedOn: today, source: 'prototype v12 – assets/js/data.js buildSeed()', state: S };
fs.writeFileSync(outFile, JSON.stringify(out, null, 1));
console.log(Object.entries(S).map(([k, v]) => k + '=' + (Array.isArray(v) ? v.length : typeof v)).join(' '));
