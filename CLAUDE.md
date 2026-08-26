# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FinScope is a local-first personal investment research information workbench. It establishes stable information acquisition channels, checks fetched articles in Intake, identifies duplicate content across days, generates daily research briefs, and preserves valuable information as long-term maintainable Markdown knowledge notes.

**Key Principles:**
- This project intentionally avoids public hot lists, operational intervention backends, and enterprise publishing chains
- Positioning: personal learning, fall recruitment project showcase, and future self-media material accumulation
- Chinese and English mixed codebase (Chinese README/docs, code in English)

## Architecture

**Backend:** Java 21, Spring Boot 2.7, Maven multi-module, SQLite, Jsoup, Rome RSS
**Frontend:** React 18, TypeScript (strict), Vite 5, Vitest
**Market data sidecar:** Python 3.11+, FastAPI, Fuyao Tonghuashun API, AkShare, pytdx — `market-data-service/`
**Cache:** Redis 7 — disposable acceleration cache for research material and hot-query reads; SQLite stays the source of truth, and the app falls back to the live fetch path when Redis is down
**Storage:** SQLite `finance.db` + Markdown files in the vault (`data/vault/`)
**AI Extension:** OpenAI-compatible `LlmChatClient`, article interpretation Agent, research planning/decision agents, `agent_run` call traces

### Module Structure

```
backend/
  finscope-common/    Generic utilities, API response envelope
  finscope-domain/    Domain models and DTOs
  finscope-dao/       SQLite repositories and schema initialization
  finscope-rpc/       External adapters (RSS/Web/X, market, search, model)
  finscope-service/   Business orchestration, dedup, research agents, radar, vault, export
  finscope-web/       REST controllers, SSE, config, application assembly
frontend/             React/Vite workbench (features/ has one dir per product area)
market-data-service/  Python A-share market data acquisition, normalization, multi-source degradation
docs/                 Product requirements, technical designs, architecture notes
```

**Dependency Direction:** `web -> service -> dao/rpc -> domain/common`
- Controllers should NOT directly call Repository
- External fetching is unified behind `SourceAdapter`; all external API calls go through `finscope-rpc` or `market-data-service`
- Spring Boot config lives in `backend/finscope-web/src/main/resources/application.yml` (plus `application-docker.yml` for the Docker profile)

### Core Flows

**Article Ingestion:**
```
SourceAdapter -> RawItem -> ArticleIngestCoordinator
  -> Article + fingerprints + novelty decision
  -> InsightCardGenerator
  -> insight_card
  -> Intake / Daily Brief / Topic pipeline
```

**Source Adapter Strategy:**
```
Source or Manual URL
  -> SourceAdapterRegistry
  -> URL-aware adapter first (e.g., XPostSourceAdapter for x.com URLs)
  -> typed adapter fallback (RSS/WEB)
  -> RawItem(title/url/summary/body/contentType/extractionMethod/qualityScore)
```

**Knowledge Preservation:**
```
Article or Brief
  -> ArticleInterpretationAgent (if LLM configured)
  -> TopicExtractor fallback
  -> TopicService
  -> SQLite links + Markdown notes in data/vault/topics/
```

**Research Radar:** aggregates cross-source alerts into "what's happening now", ranked by five fixed rules (novelty, watchlist relevance, independent sources, source quality, timeliness). Personal short-term discovery layer, not a public hot board — no strategy backend or prompt configuration; auto-refreshed new items still require user confirmation before insertion.

**Market Data Gateway:** `MarketDataGateway` tries providers (Python sidecar first, then Java direct Eastmoney) with caching, hedge delay, request budget, circuit breaker, and snapshot fallback. Python provider priority is fixed higher; Java falls through on timeout/503. Python responses carry `source_code`/`source_family`/`as_of`/`attempts`/`warnings` and never fake success with empty data.

**Key Extension Points:**
- `SourceAdapter`: Add new RSS/Web/API sources without changing orchestration
- `ArticleInterpretationAgent`: LLM-based article interpretation with fallback
- `InsightCardGenerator`: Converts articles to insight cards (deterministic templates or Agent output)
- `NoveltyService`: Cross-day duplicate/subsequent development/new event detection
- `VaultWriter`: Isolates Markdown persistence from database persistence
- `MarketDataGateway`: Add Java providers; Python providers are added in `market-data-service`

## Common Commands

### Backend

```bash
# Build + run (package + java -jar avoids multi-module entry confusion with spring-boot:run)
export FINSCOPE_DATA_ROOT="$(cd .. && pwd)/data"
cd backend
mvn -pl finscope-web -am package -DskipTests
java -jar finscope-web/target/finscope-web-0.1.0-SNAPSHOT.jar

# Quick dev server
cd backend && mvn -pl finscope-web -am spring-boot:run

# All backend tests
cd backend && mvn test

# Single module tests
cd backend && mvn -pl finscope-service test

# Single test class
cd backend && mvn -pl finscope-service -Dtest=NoveltyServiceTest test
```

Backend runs on `http://localhost:8080`.

### Frontend

```bash
cd frontend && npm ci
npm run dev            # dev server on http://localhost:5173 (/api proxied to :8080)
npm test               # Vitest run (all)
npm test -- src/shared/foo.test.ts   # single test file
npm run build          # tsc -b && vite build
```

### Market Data Sidecar

```bash
cd market-data-service
uv sync --extra dev --extra ecosystem
uv run uvicorn finscope_market_data.app:app --host 127.0.0.1 --port 8000
uv run pytest -q       # unit tests use fixed responses, no external network
```

### Full Stack (Docker Compose)

```bash
docker compose up --build        # Redis + backend + frontend + market-data
docker compose up --build -d     # detached
docker compose logs -f backend
docker compose down              # keeps redis/market-data volumes; add -v to wipe caches
```

Serves `http://localhost:5173`. Requires `data/finance.db` to already exist at the repo root (the app refuses to silently create an empty DB at the wrong path).

## LLM/Agent Configuration

Uses the OpenAI-compatible Chat Completions interface, not bound to a specific provider. The LLM and search API keys are intentionally fixed in `backend/finscope-web/src/main/resources/application.yml` for the current local deployment. **Do not replace either `api-key` with an environment-variable expression unless the user explicitly asks for that migration**; do not print, duplicate, or relocate their values.

Common runtime overrides:

```bash
export FINSCOPE_LLM_ENABLED=true
export FINSCOPE_LLM_BASE_URL=https://your-model-service/v1
export FINSCOPE_LLM_MODEL=your_model_name
export FINSCOPE_PYTHON_MARKET_DATA_BASE_URL=http://127.0.0.1:8000
```

When enabled:
- New articles generate insight cards via the `article-interpret` Agent node
- Each call is traced in Agent Runs page with node, status, duration, and error info
- If the model call fails, the system preserves the fetch pipeline and uses deterministic fallback (never blocks the Intake flow)

## API Conventions

All `/api/**` JSON endpoints (except SSE, file streams, and `204 No Content`) return a unified envelope:

```json
{ "success": true, "code": "FS-0000", "message": "成功", "data": {}, "traceId": "...", "timestamp": "..." }
```

Error code ranges: `FS-1xxx` params/auth/permission/rate-limit, `FS-2xxx` missing/business-conflict/version-conflict, `FS-3xxx` external/market/model service, `FS-4xxx` db/file/task/data-integrity, `FS-5000` unclassified internal error. Clients may send `X-Request-Id`; it becomes the response `traceId` and the log MDC entry.

## Data & Storage

- Realtime data root defaults to the repo-sibling `../data/` (`FINSCOPE_DATA_ROOT` must be absolute when overridden). Under Docker Compose the repo's `./data/` is mounted at `/data`.
- SQLite URL is relative: `../data/finance.db?foreign_keys=on` from the backend run directory (WAL enabled, pool capped, busy timeout set).
- Vault Markdown files under the data root:
  - `vault/daily-briefs/`: Daily brief markdown files
  - `vault/topics/`: Topic knowledge notes
  - `vault/terms/`: Term definitions
  - `vault/learning-path/`: Learning path notes
- SQLite stays the primary store by design; MySQL/PostgreSQL are not being introduced.

## Git Conventions

- Commits use Conventional Commit types in English followed by a Chinese subject: `feat: 增加...`, `fix: 修复...`, `docs: 补充...`
- Commit in small, independently-verifiable batches and push to the current branch — don't accumulate everything until the task ends
- Unless asked to work on the current branch, branch off `main` for changes; no worktrees unless explicitly requested

## Data Safety

**Git-ignored:** `data/finance.db` (+ WAL/SHM), `data/raw/`, `data/exports/`, `data/financials/`, `.env`/`*.local`, backend `target/` dirs, Python venv/pytest cache, frontend `node_modules`/`dist`.

**Never commit:** Company internal data, code, credentials, proprietary prompts, or private documents. The two existing fixed local LLM/search keys are an explicit project convention — do not print, duplicate, or relocate their values.

## Important Notes

- When working with source adapters, URL-aware adapters take precedence over typed adapters
- Agent fallback is deterministic — the system never blocks the Intake flow if the LLM is unavailable
- Model/search/market external failures surface as explicit failure, partial success, or snapshot-degraded states — never empty data disguised as success
- Java 21 is the unified compile-and-run baseline and Maven Enforcer rejects other JDK feature releases. Use named virtual threads only for explicitly bounded blocking-I/O work; shared executors still require explicit capacity, queue, rejection, and naming policies. Do not enable preview features. The frontend uses TypeScript strict mode with Vite
- Detailed architecture notes live in `docs/` (start with `docs/架构说明.md`); the market data service contract is in `market-data-service/README.md`
