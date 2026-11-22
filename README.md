# Gem Shop System

Sales, stock and money system for a gem shop with two branches (Ceylon Gem House).
Built to match the approved **prototype v12**, **demo guide v12** (75 screens) and **BRD v1.3**.

| Part | Folder | Stack |
|---|---|---|
| Backend | `backend/` | Spring Boot 3 · Java 21 · PostgreSQL · JPA · Flyway · JWT |
| Frontend | `frontend/` | Next.js · TypeScript · Tailwind · shadcn/ui · PWA |
| Infrastructure | `infra/` | Docker Compose · Nginx · GitHub Actions · AWS (EC2, S3, SES) |
| Documents | `docs/` | Master task breakdown, test plan, notes |

The project skeleton is added in `P0-02`. Until then the folders are empty.

## Branches

| Branch | Purpose |
|---|---|
| `main` | Production. Only from `uat` at go-live (tag `v1.0.0`), later `hotfix/*`. |
| `dev` | Development – the **default** branch. All work is built and tested locally here. |
| `uat` | Client testing. Created near the end (`P6-02`); gets everything from `dev` at once. |
| `feature/*`, `bugfix/*` | Always start from `dev`, go back to `dev` by pull request. |

Branch names follow the task breakdown, for example `feature/p3-01-be-new-sale` and `feature/p3-01-fe-new-sale`.

## How the work is tracked

Every piece of work is a heading in [`docs/MASTER_TASK_BREAKDOWN.md`](docs/MASTER_TASK_BREAKDOWN.md).
One heading = one branch (or one backend and one frontend branch) = one pull request into `dev`.
When a heading is merged, its tasks are ticked and its **Status** changes.

## Rules for every screen

- Look and work the same as the prototype screen and the demo guide.
- No VAT anywhere.
- The customer pays the same price by cash or card. The 3% card bank fee is a shop cost.
- Offers and birthday wishes only to customers who gave permission.
- Every API checks role and branch. Cost and profit only for the owner (and accountant where the BRD says).
- Money as exact decimals. Currencies LKR, USD, EUR only.

## Running locally

Added in `P0-02` (one Docker Compose command starts everything).
