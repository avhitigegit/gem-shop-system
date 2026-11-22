# Gem Shop System

Sales, stock and money system for a gem shop with two branches (Ceylon Gem House).
Built to match the approved **prototype v12**, **demo guide v12** (75 screens) and **BRD v1.3**.

| Part | Folder | Stack |
|---|---|---|
| Backend | `server/` | Spring Boot 4 · Java 21 · PostgreSQL · JPA · Flyway · JWT |
| Frontend | `client/` | Next.js 16 · TypeScript · Tailwind · shadcn/ui · PWA |
| Infrastructure | `infra/` | Docker Compose · Nginx · GitHub Actions · AWS (EC2, S3, SES) |
| Prototype | `prototype/` | Approved clickable prototype v12 (open `prototype/index.html`) |

The prototype keeps its full history; its versions are tagged `prototype-v3` … `prototype-v12`.
App releases use `v0.x` and `v1.0.0`.

## Branches

| Branch | Purpose |
|---|---|
| `main` | Production. Only from `uat` at go-live (tag `v1.0.0`), later `hotfix/*`. |
| `dev` | Development – the **default** branch. All work is built and tested locally here. |
| `uat` | Client testing. Created near the end (`P6-02`); gets everything from `dev` at once. |
| `feature/*`, `bugfix/*` | Always start from `dev`, go back to `dev` by pull request. |

`main` and `dev` are protected on GitHub: changes only by pull request.

Branch names follow the task breakdown, for example `feature/p3-01-be-new-sale` and `feature/p3-01-fe-new-sale`.

## How the work is tracked

Every piece of work is a heading in the master task breakdown (`doc/MASTER_TASK_BREAKDOWN.md`, kept outside git).
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

Needs **Docker Desktop**. One command from this folder starts everything:

```sh
docker compose up -d --build
```

| Open | What |
|---|---|
| http://localhost:8100 | The app (Nginx → frontend and backend) |
| http://localhost:8100/health | System health page (frontend → backend → database) |
| http://localhost:8100/swagger-ui.html | API documentation (Swagger) |
| http://localhost:8100/prototype/ | The approved prototype, to compare screens |
| http://localhost:8025 | MailHog – every test email the system sends |

Stop: `docker compose down` · Stop and delete the local database: `docker compose down -v`

**Reset the local data** (empty database → tables rebuilt → fresh sample data; local Docker only):

```sh
sh infra/scripts/reset-data.sh                                   # Git Bash / Linux / macOS
powershell -ExecutionPolicy Bypass -File infra\scriptseset-data.ps1   # Windows PowerShell
```

Add `-y` (Bash) or `-Yes` (PowerShell) to skip the question.

Ports used on this computer: 8100 app, 8081 backend, 5433 database, 8025 / 1025 MailHog.

### Working on one part (faster)

```sh
docker compose up -d db mailhog          # database + test email only

cd server && ./mvnw spring-boot:run      # backend on http://localhost:8081
cd client && npm install && npm run dev  # frontend on http://localhost:3000 (/api goes to :8081)
```

### Checks

GitHub Actions (`.github/workflows/ci.yml`) runs on every pull request and every merge into `dev`, `uat`, `main`:
backend build + tests, frontend lint + type check + format check + build, Docker image builds (not pushed), Compose / Nginx config check.
A pull request can be merged only when all checks are green.

The same checks locally:

```sh
cd server && ./mvnw verify               # backend build + tests (needs Docker for the test database)
cd client && npm run check               # lint + type check + format check
cd client && npm run build
```

After changing an API, with the backend running: `cd client && npm run api:generate` (updates `src/lib/api/schema.d.ts`).

### Settings per environment

Backend profiles in `server/src/main/resources`: `application-local.yml` (default), `-dev`, `-uat`, `-prod`.
Secrets (database, email, WhatsApp keys) always come from environment variables – never from files in git.
