# Sample data

`prototype-v12.json` is the prototype's own demo data (`prototype/assets/js/data.js` → `buildSeed()`),
exported with `infra/scripts/export-prototype-data.js` on the date in `exportedOn`.

- Loaded by `SampleDataLoader` when the backend starts on an **empty** database, in `local`, `dev` and `uat` only
  (`app.sample-data.enabled`). Never in `prod` – the loader refuses even if switched on.
- Every date is moved forward to today, so it looks like the prototype: sales in the last 30 days,
  birthdays this week, the gem licence expiring in 45 days.
- Each module loads its own part (`SampleDataPart`). Loaded now: branches, users, shop details.
  Gems, suppliers, guides, customers, sales, expenses … are added by the heading that builds their tables.
- Fresh data: `sh infra/scripts/reset-data.sh` (or `reset-data.ps1`).
