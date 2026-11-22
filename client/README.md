# Frontend (client)

Next.js 16 · TypeScript · Tailwind CSS 4 · shadcn/ui. See the main [README](../README.md) for running everything.

```sh
npm install
npm run dev            # http://localhost:3000, /api goes to the backend on :8081
npm run check          # lint + type check + format check
npm run api:generate   # refresh src/lib/api/schema.d.ts from the running backend
```

- `src/lib/api/client.ts` – typed API client (`api.GET("/api/health")`) and the `ApiError` type.
- `src/components/ui/` – shadcn/ui components.

## Screens and design (P1-03)

- The look is copied from the prototype: `src/styles/gemshop.css` = `prototype/assets/css/app.css`
  (same colours, sizes, spacing, fonts). Tailwind colours use the same names (`bg-navy`, `text-sapphire`, `border-gold` …).
- Building blocks in `src/components/ui` – see them all on the **Design kit** page (`/design`, user menu).
- The menu is defined once in `src/lib/nav.ts` (8 sections, pages per role, step bars).
  Pages not built yet show "built in a later step" with a link to the same prototype screen.
- Formats in `src/lib/format.ts`: `LKR 1,234.00`, `dd/mm/yyyy`, Sri Lanka time.
- Until login (P1-02) the top bar has a **PREVIEW** box to see the app as each role.
