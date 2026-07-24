# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

FinScope is a local-first personal investment research information workbench. It establishes stable information acquisition channels, checks fetched articles in Inbox, identifies duplicate content across days, generates daily research briefs, and preserves valuable information as long-term maintainable Markdown knowledge notes.

**Key Principles:**
- This project intentionally avoids public hot lists, operational intervention backends, and enterprise publishing chains
- Positioning: personal learning, fall recruitment project showcase, and future self-media material accumulation
- Chinese and English mixed codebase (Chinese README/docs, code in English)

## Architecture

**Backend:** Java 8, Spring Boot 2.7, Maven multi-module, SQLite, Jsoup, Rome RSS
**Frontend:** React, TypeScript, Vite
**Storage:** `data/finance.db` (SQLite) and Markdown files in `data/vault/`
**AI Extension:** OpenAI-compatible `LlmChatClient`, article interpretation Agent, `agent_run` call traces

### Module Structure

```
backend/
  finscope-common/    Generic utilities, no business logic
  finscope-domain/    Domain models and DTOs
  finscope-dao/       SQLite repositories and schema initialization
  finscope-rpc/       External source adapters (RSS/Web/X/Twitter)
  finscope-service/   Business orchestration, deduplication, briefs, vault, export
  finscope-web/       REST controllers and application assembly
```

**Dependency Direction:** `web -> service -> dao/rpc -> domain/common`
- Controllers should NOT directly call Repository
- External fetching is unified behind `SourceAdapter`

### Core Flows

**Article Ingestion:**
```
SourceAdapter -> RawItem -> ArticleIngestCoordinator
  -> Article + fingerprints + novelty decision
  -> InsightCardGenerator
  -> insight_card
  -> Inbox / Daily Brief / Topic pipeline
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

**Key Extension Points:**
- `SourceAdapter`: Add new RSS/Web/API sources without changing orchestration
- `ArticleInterpretationAgent`: LLM-based article interpretation with fallback
- `InsightCardGenerator`: Converts articles to insight cards (supports deterministic rules or Agent output)
- `NoveltyService`: Cross-day duplicate/subsequent development/new event detection
- `VaultWriter`: Isolates Markdown persistence from database persistence

## Common Commands

### Backend

```bash
# Run backend (from project root)
cd backend
mvn -pl finscope-web -am spring-boot:run

# Run all backend tests
cd backend && mvn test

# Run specific module tests
cd backend && mvn -pl finscope-service test
```

Backend runs on `http://localhost:8080` by default.

### Frontend

```bash
# Install dependencies
cd frontend && npm install

# Run development server
cd frontend && npm run dev

# Run tests
cd frontend && npm test

# Build for production
cd frontend && npm run build
```

Frontend runs on `http://localhost:5173` by default (or 5174 if port occupied).
Frontend proxies `/api` to `http://localhost:8080`.

### LLM/Agent Configuration

The project uses OpenAI-compatible Chat Completions interface, not bound to specific providers. The LLM and search API keys are intentionally fixed in `backend/finscope-web/src/main/resources/application.yml` for the current local deployment. Do not replace either `api-key` with an environment-variable expression unless the user explicitly asks for that migration.

Other runtime settings can still be overridden through environment variables:

```bash
export FINSCOPE_LLM_ENABLED=true
export FINSCOPE_LLM_BASE_URL=https://your-model-service/v1
export FINSCOPE_LLM_MODEL=your_model_name
```

When enabled:
- New articles generate insight cards via `article-interpret` Agent node
- Each call is traced in Agent Runs page with node, status, duration, and error info
- If model call fails, system preserves fetch pipeline and uses deterministic fallback

## Key Implementation Details

### Article Ingest Flow

`ArticleIngestCoordinator.ingest()` orchestrates:
1. Article creation from `RawItem`
2. URL fingerprint, title normalization, body simhash
3. Novelty decision via `NoveltyService`
4. Insight card generation via `InsightCardGenerator`

### Source Adapters

Three adapters implement `SourceAdapter` interface:
- `RssSourceAdapter`: RSS/Atom feeds via Rome library
- `WebSourceAdapter`: Static HTML via Jsoup (no JavaScript execution)
- `XPostSourceAdapter`: X/Twitter status URLs, prioritizes public JSON adapter for X long posts

When adding new sites, create new adapters rather than adding logic to `UrlIngestService`.

### Insight Cards

`InsightCardGenerator` supports three deterministic templates:
- Financial news cards
- Research paper cards
- Social media long-form cards

Can also consume Agent's structured interpretation output.

### Vault Structure

Markdown files stored in `data/vault/`:
- `daily-briefs/`: Daily brief markdown files
- `topics/`: Topic knowledge notes
- `terms/`: Term definitions
- `learning-path/`: Learning path notes

## Data Safety

**Git-ignored:**
- `data/finance.db` (SQLite database)
- `data/raw/` (raw fetched content)
- `data/exports/` (export packages)
- `.env` and `*.local` files
- Additional API keys outside the two intentionally fixed local LLM/search entries

**Never commit:** Company internal data, code, credentials, proprietary prompts, or private documents. The two existing fixed local LLM/search keys are an explicit project convention; do not print, duplicate, or relocate their values.

## Important Notes

- When working with source adapters, URL-aware adapters take precedence over typed adapters
- Agent fallback is deterministic - system never blocks Inbox flow if LLM unavailable
- All external API calls should go through `finscope-rpc` module
- SQLite database path is relative: `../data/finance.db` from backend running directory
- Frontend uses TypeScript strict mode with Vite for fast development
