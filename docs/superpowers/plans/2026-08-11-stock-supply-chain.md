# Stock Supply Chain Evidence Map Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a persisted, evidence-backed supply-chain tab to the existing stock detail drawer, including navigation from fund holdings.

**Architecture:** Store one current versioned JSON snapshot per instrument and separate refresh-run records. An asynchronous service freezes public search evidence, asks a strict synthesis agent for upstream/company/downstream nodes, validates every evidence reference, then atomically replaces the snapshot only on success. The React drawer reads snapshots lazily and polls refresh runs without blocking K-line data.

**Tech Stack:** Java 8, Spring Boot 2.7, JdbcTemplate, SQLite, Jackson, existing search and LLM gateways, React, TypeScript, Vitest.

---

### Task 1: Persist supply-chain snapshots and refresh runs

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/supplychain/StockSupplyChainNode.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/supplychain/StockSupplyChainEvidence.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/supplychain/StockSupplyChainSnapshot.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/supplychain/StockSupplyChainRefreshRun.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/supplychain/StockSupplyChainRepository.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/supplychain/StockSupplyChainRepositoryTest.java`

- [ ] Write a failing repository test that initializes an in-memory SQLite database, saves a READY snapshot containing all three layers and evidence refs, reads it back, records a failed refresh run, and verifies the snapshot is unchanged.
- [ ] Run `cd backend && mvn -pl finscope-dao -am -Dtest=StockSupplyChainRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`; expect compilation or missing-table failure.
- [ ] Add `stock_supply_chain_snapshot` with unique `instrument_id`, JSON payload, schema version, model, evidence time, generated time and update time. Add `stock_supply_chain_refresh_run` with status, stage, message, error, retry flag and lifecycle times plus a partial unique index for one RUNNING row per instrument.
- [ ] Implement repository JSON serialization through the shared `ObjectMapper`; expose `findSnapshot`, `latestRun`, `activeRun`, `createRun`, `updateRun`, and transactional `replaceSnapshotAndComplete`.
- [ ] Re-run the focused DAO test and commit with `feat: 持久化股票产业链快照`.

### Task 2: Build and validate the evidence-backed synthesis pipeline

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/supplychain/StockSupplyChainSynthesisAgent.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/supplychain/StockSupplyChainRefreshExecutor.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/supplychain/StockSupplyChainSynthesisAgentTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/supplychain/StockSupplyChainRefreshExecutorTest.java`

- [ ] Write failing synthesis tests for a valid three-layer JSON result, an unknown evidence ref, an unsupported confidence value, and invented extra fields.
- [ ] Run the focused service tests; expect missing classes.
- [ ] Implement a strict prompt that requires `summary`, `position`, `limitations`, and `nodes`; allow only layers `UPSTREAM/COMPANY/DOWNSTREAM`, confidence `HIGH/MEDIUM/LOW`, known evidence codes, bounded text, and no trading language.
- [ ] Write failing executor tests proving it searches a company-specific public-material query, enriches a bounded evidence set, saves only a validated result, and marks the run failed without replacing a prior snapshot.
- [ ] Implement collection through `SearchEvidenceGateway` and `SearchEvidenceContentService`, then schedule execution on a named `stockSupplyChainExecutor` bean added to `AppConfig`.
- [ ] Re-run focused service tests and commit with `feat: 生成股票产业链证据图谱`.

### Task 3: Expose read and asynchronous refresh APIs

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/supplychain/StockSupplyChainService.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/controller/StockSupplyChainController.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/response/StockSupplyChainViewResponse.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/supplychain/StockSupplyChainServiceTest.java`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/controller/StockSupplyChainControllerTest.java`

- [ ] Write failing service tests for empty read, refresh creation, active-run conflict, stale-run expiry, and use of `StrategyInstrumentResolver` for a fund-held stock not already in the watchlist.
- [ ] Implement `get(code)` and `refresh(code)` with a 30-minute run lease and queue-rejection handling; never delete a prior snapshot on failure.
- [ ] Write failing MVC tests for `GET /api/stocks/688012/supply-chain` and `POST /api/stocks/688012/supply-chain/refresh`.
- [ ] Implement the controller and explicit response mapping so API fields remain stable.
- [ ] Run `cd backend && mvn -pl finscope-web -am -Dtest=StockSupplyChainRepositoryTest,StockSupplyChainSynthesisAgentTest,StockSupplyChainRefreshExecutorTest,StockSupplyChainServiceTest,StockSupplyChainControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` and commit with `feat: 提供股票产业链接口`.

### Task 4: Add the stock detail tabs and evidence-map UI

**Files:**
- Create: `frontend/src/features/watchlist/StockSupplyChainPanel.tsx`
- Create: `frontend/src/features/watchlist/StockSupplyChainPanel.test.tsx`
- Modify: `frontend/src/features/watchlist/WatchlistKlineDrawer.tsx`
- Modify: `frontend/src/features/watchlist/WatchlistKlineDrawer.test.tsx`
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/styles.css`

- [ ] Write failing component tests for lazy GET on the supply-chain tab, automatic POST when no snapshot exists, polling until READY, manual refresh while retaining the old snapshot, evidence links, and retry after failure.
- [ ] Add strict TypeScript types for snapshot, node, evidence, refresh run and view.
- [ ] Implement `StockSupplyChainPanel` with an abort-safe request sequence and bounded polling; do not fetch until its tab becomes active.
- [ ] Upgrade `WatchlistKlineDrawer` to accessible `行情走势 / 产业链` tabs while keeping行情 as the default and preserving K-line cache behavior.
- [ ] Implement the evidence rail visual system: cold-blue upstream, dark central company, warm-orange downstream; desktop three columns, mobile one column, visible focus and reduced-motion support.
- [ ] Run the focused frontend tests and commit with `feat: 增加股票产业链详情页签`.

### Task 5: Connect fund holdings to the unified stock detail

**Files:**
- Modify: `frontend/src/features/watchlist/WatchlistFundHoldingsDrawer.tsx`
- Modify: `frontend/src/features/watchlist/WatchlistFundHoldingsDrawer.test.tsx`
- Modify: `frontend/src/features/watchlist/WatchlistView.tsx`
- Modify: `frontend/src/features/watchlist/WatchlistView.test.tsx`

- [ ] Write failing tests showing a fund holding stock is keyboard-clickable, opens the unified stock drawer, and returns to the still-loaded fund holding detail without a second fund request.
- [ ] Add `onOpenStock` to the fund drawer and render stock names as restrained text buttons.
- [ ] Keep the fund item in parent state while the stock drawer is open; hide rather than unmount the fund drawer path, then restore it when stock detail closes. Show an explicit `返回基金持仓` action in the stock drawer.
- [ ] Run all watchlist tests and commit with `feat: 串联基金持仓与股票产业链`.

### Task 6: Verify and hand off

- [ ] Run focused backend tests for repository, agent, service and controller.
- [ ] Run `cd frontend && npm test -- --run` and confirm every frontend test passes.
- [ ] Run `cd frontend && npm run build`; restore only the generated `frontend/tsconfig.tsbuildinfo` afterward.
- [ ] Run `git diff --check`, inspect scope, commit any verification-only corrections, and push `codex/fund-holdings-detail`.

