# Watchlist Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make watchlist quotes, attribution data, and attribution progress correct, recoverable, and fast for stocks, funds, and sectors.

**Architecture:** Keep the modular monolith boundaries. Quote adapters stay in `finscope-rpc`, caching and watchlist orchestration stay in `finscope-service`, persistence queries stay in `finscope-dao`, and API/React state remain thin consumers of typed responses.

**Tech Stack:** Java 8, Spring Boot 2.7, SQLite, React, TypeScript, Vitest, JUnit.

---

### Task 1: Add type-safe quote support and cache

**Files:**
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quote/EastmoneySectorQuoteAdapter.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quote/FundQuoteAdapter.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/instrument/QuoteService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/instrument/WatchlistService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/config/AppConfig.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/instrument/QuoteServiceTest.java`

- [ ] Write failing tests for sector routing, cache reuse, and code validation.
- [ ] Implement the sector adapter, four-worker fund fetch executor, 30-second quote cache, and per-type validation.
- [ ] Run cached Maven module tests when available; otherwise report Maven verification skipped.

### Task 2: Make attribution identity and summaries type-aware

**Files:**
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/attribution/AttributionRepository.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/instrument/WatchlistService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/response/WatchlistItemResponse.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/AttributionController.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionService.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/attribution/AttributionRepositoryTest.java`

- [ ] Write failing tests proving same-code different-type reports remain isolated and watchlist summaries load in one query.
- [ ] Add type-aware repository APIs, compound index, and a single batch summary query.
- [ ] Expose summary in the watchlist response and require type for attribution history/latest.

### Task 3: Harden attribution evidence and task lifecycle

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/attribution/AttributionReport.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionAgent.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/AttributionController.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/attribution/AttributionAgentTest.java`

- [ ] Write failing tests for URL dedupe, all-search-failed warning, partial-search warning, and executor rejection.
- [ ] Deduplicate evidence before rank/prompt/persist, record search outcomes, persist `warning_message`, and return `reportId` from start.
- [ ] Mark rejected jobs failed immediately rather than leaving reports generating.

### Task 4: Recover frontend progress and remove N+1 calls

**Files:**
- Create: `frontend/src/features/watchlist/watchlistApi.ts`
- Modify: `frontend/src/features/watchlist/WatchlistView.tsx`
- Modify: `frontend/src/features/watchlist/AttributionReaderView.tsx`
- Modify: `frontend/src/shared/types/index.ts`
- Test: `frontend/src/features/watchlist/WatchlistView.test.tsx`
- Test: `frontend/src/features/watchlist/AttributionReaderView.test.tsx`

- [ ] Write failing tests proving watchlist load makes no per-card latest-report calls and SSE disconnect polls the returned report to completion.
- [ ] Move request helpers out of the view, consume the embedded summary, and add report-status polling after EventSource errors.
- [ ] Run focused tests, all frontend tests, and production build.
