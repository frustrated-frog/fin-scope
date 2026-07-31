# Quant Strategy Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a provenance-safe strategy catalog that imports the upstream equity strategy index, evaluates FinScope compatibility, and hands selected candidates into the existing validated quant draft workflow.

**Architecture:** A fixed-source RPC provider fetches and parses the GitHub catalog. A deterministic service layer evaluates candidates and persists snapshots through a dedicated repository. New REST endpoints expose synchronization and candidate-to-draft handoff, while a focused React catalog panel extends the existing Quant workspace without bypassing user confirmation.

**Tech Stack:** Java 8, Spring Boot 2.7, JdbcTemplate, SQLite, JUnit 5, React, TypeScript, Vitest, Testing Library.

---

### Task 1: Catalog domain and Markdown parser

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/catalog/QuantStrategyCatalogEntry.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/catalog/QuantStrategyCatalogSnapshot.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quant/catalog/QuantStrategyCatalogProvider.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quant/catalog/AwesomeTradingMarkdownParser.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/quant/catalog/AwesomeTradingMarkdownParserTest.java`

- [ ] Write parser tests covering the equity section boundary, relative URL expansion, `N/A` metrics, and malformed/empty tables.
- [ ] Run `cd backend && mvn -pl finscope-rpc -am -Dtest=AwesomeTradingMarkdownParserTest -Dsurefire.failIfNoSpecifiedTests=false test` and confirm RED because the parser API is absent.
- [ ] Implement immutable catalog input objects, provider contract, and a line-oriented six-column Markdown parser that rejects an empty equity section.
- [ ] Re-run the focused test and confirm GREEN.
- [ ] Commit with `feat: 建立量化策略目录解析边界` and push.

### Task 2: GitHub provider and deterministic compatibility

**Files:**
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quant/catalog/GithubAwesomeTradingCatalogProvider.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/quant/catalog/QuantStrategyCompatibilityService.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/catalog/QuantStrategyCompatibility.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/quant/catalog/GithubAwesomeTradingCatalogProviderTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/quant/catalog/QuantStrategyCompatibilityServiceTest.java`

- [ ] Write provider tests against an injected HTTP reader and compatibility tests for BP, reversal, low volatility, momentum, missing-factor, unsupported and unknown strategies.
- [ ] Run both focused test classes and confirm RED for missing implementations.
- [ ] Implement a fixed allowlisted provider using the shared acquisition runtime, with bounded payload size and GitHub commit/README endpoints.
- [ ] Implement ordered compatibility rules returning status, mapped factors, missing factors and semantic caveats.
- [ ] Re-run focused tests and confirm GREEN.
- [ ] Commit with `feat: 增加策略候选兼容性评估` and push.

### Task 3: Schema, repository and synchronized snapshot service

**Files:**
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/quant/QuantStrategyCatalogRepository.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/quant/catalog/QuantStrategyCatalogService.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/quant/QuantStrategyCatalogRepositoryTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/quant/catalog/QuantStrategyCatalogServiceTest.java`

- [ ] Write repository integration tests for idempotent upsert, archive-on-missing, query filtering and origin links; write service tests for sync counts and provider failure preservation.
- [ ] Run focused DAO/service tests and confirm RED because schema and services are absent.
- [ ] Add the three catalog tables and indexes through idempotent forward-only initialization.
- [ ] Implement repository upsert/query/origin operations and transactional service orchestration.
- [ ] Re-run focused tests and confirm GREEN.
- [ ] Commit with `feat: 持久化量化策略素材快照` and push.

### Task 4: Candidate-to-draft provenance and REST API

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/quant/strategy/QuantStrategyService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/QuantController.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/request/quant/CreateCatalogStrategyDraftRequest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/quant/catalog/QuantStrategyCandidateDraftServiceTest.java`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/controller/QuantStrategyCatalogControllerTest.java`

- [ ] Write service tests proving unsupported candidates are rejected and adaptable candidates generate a source-bounded prompt; write controller tests for sync/list/detail/draft endpoints and invalid parameters.
- [ ] Run focused tests and confirm RED.
- [ ] Add candidate draft orchestration and origin persistence, keeping existing validation and confirmation unchanged except for linking a confirmed version when an origin exists.
- [ ] Add the four `/api/quant/catalog` endpoints and request DTO.
- [ ] Re-run focused tests and confirm GREEN.
- [ ] Commit with `feat: 接通策略候选与受限草案链路` and push.

### Task 5: Strategy catalog interface

**Files:**
- Modify: `frontend/src/features/strategy/quantTypes.ts`
- Create: `frontend/src/features/strategy/StrategyCatalogPanel.tsx`
- Create: `frontend/src/features/strategy/StrategyCatalogPanel.test.tsx`
- Modify: `frontend/src/features/strategy/QuantWorkspace.tsx`
- Modify: `frontend/src/features/strategy/QuantWorkspace.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] Write component tests for first-sync empty state, compatibility filtering, search, detail selection, provenance labels, dataset selection, disabled unsupported action, successful draft handoff and sync failure recovery.
- [ ] Run `cd frontend && npm test -- StrategyCatalogPanel.test.tsx QuantWorkspace.test.tsx` and confirm RED because the panel and catalog pane are absent.
- [ ] Add catalog API types and implement the focused panel with filters, candidate list and evidence drawer.
- [ ] Integrate the fourth Quant pane and hand returned drafts back to the laboratory pane without changing the experiment flow.
- [ ] Add scoped responsive styles, visible focus states and reduced-motion handling.
- [ ] Re-run focused frontend tests and confirm GREEN.
- [ ] Commit with `feat: 增加量化策略素材库界面` and push.

### Task 6: Full verification and visual critique

**Files:**
- Modify only files required by failures found during verification.

- [ ] Run `cd backend && mvn test` and require exit code 0 with no failed tests.
- [ ] Run `cd frontend && npm test` and require all suites to pass.
- [ ] Run `cd frontend && npm run build` and require exit code 0.
- [ ] Start backend and frontend, inspect the strategy catalog at desktop and mobile widths, and capture screenshots under `output/playwright/` without staging generated output.
- [ ] Check empty, loading, populated, filtered, detail and error states; fix any observed issue through a new failing regression test before implementation.
- [ ] Review `git diff --check`, `git status --short`, and the commit list; confirm only intended source/docs files are tracked.
- [ ] Commit any verification fixes with an appropriate Conventional Commit Chinese subject and push the current branch.
