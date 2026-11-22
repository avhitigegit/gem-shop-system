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
