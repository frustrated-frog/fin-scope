# Tonghuashun Hot Sector Constituents Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Tonghuashun the only hot-sector ranking authority, prefer its constituents without accepting partial pages as complete, and remove untradeable boards before quant work.

**Architecture:** Separate ranking from constituent resolution. A Tonghuashun page adapter returns measured complete/partial batches; a resolver accepts only verified complete direct data, a 30-day complete snapshot, or a sufficiently complete supplemental source. A shared trading-scope policy filters STAR Market and Beijing Stock Exchange codes before any daily-bar request.

**Tech Stack:** Python 3.13, FastAPI, Pydantic, standard-library HTML parsing and atomic JSON snapshots, Java 21/Spring Boot/Jackson, React/TypeScript/Vitest.

---

### Task 1: Define constituent and trading-scope contracts

**Files:**
- Create: `market-data-service/src/finscope_market_data/discovery/constituents.py`
- Create: `market-data-service/src/finscope_market_data/discovery/trading_scope.py`
- Modify: `market-data-service/src/finscope_market_data/discovery/schemas.py`
- Test: `market-data-service/tests/test_discovery_constituents.py`
- Test: `market-data-service/tests/test_discovery_trading_scope.py`

- [ ] Write failing tests for complete/partial Tonghuashun pages, cache expiry, source audit and code-prefix classification.
- [ ] Run focused tests and confirm failures are caused by missing contracts.
- [ ] Implement `ConstituentBatch`, `TonghuashunConstituentProvider`, atomic `ConstituentSnapshotStore` and `TradingScopePolicy`.
- [ ] Run focused tests and confirm green.
- [ ] Commit with `feat: 增加同花顺成分完整性契约` and push.

### Task 2: Enforce Tonghuashun-only ranking and constituent resolution

**Files:**
- Modify: `market-data-service/src/finscope_market_data/discovery/providers.py`
- Modify: `market-data-service/src/finscope_market_data/discovery/service.py`
- Modify: `market-data-service/src/finscope_market_data/app.py`
- Modify: `market-data-service/tests/test_discovery_providers.py`
- Modify: `market-data-service/tests/test_discovery_service.py`

- [ ] Write failing tests proving non-Tonghuashun rankings cannot replace the list, partial sectors are skipped, complete cache/supplement can recover, and excluded codes never request bars.
- [ ] Run the focused provider/service tests and verify red.
- [ ] Register only Tonghuashun for ranking, resolve constituents independently, enforce complete batches, and preserve a Tonghuashun-only ranking snapshot.
- [ ] Filter scope before admission and publish raw/excluded/eligible counts.
- [ ] Run focused tests and confirm green.
- [ ] Commit with `feat: 锁定同花顺热门板块候选池` and push.

### Task 3: Strengthen Java evidence contract

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/discovery/StockDiscoveryReport.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quant/PythonStockDiscoveryClient.java`
- Modify: `backend/finscope-rpc/src/test/java/com/finscope/rpc/quant/PythonStockDiscoveryClientTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/quant/discovery/StockDiscoveryService.java`

- [ ] Write failing tests rejecting a non-Tonghuashun ranking source and inconsistent raw/excluded/eligible counts.
- [ ] Run the focused RPC tests and verify red.
- [ ] Add domain fields, strict validation and policy version `stock-discovery-v2`.
- [ ] Run focused Java tests and confirm green.
- [ ] Commit with `feat: 增加同花顺选股证据门禁` and push.

### Task 4: Present source quality and scope filtering

**Files:**
- Modify: `frontend/src/features/strategy/quantTypes.ts`
- Modify: `frontend/src/features/strategy/StockDiscoveryPanel.tsx`
- Modify: `frontend/src/features/strategy/StockDiscoveryPanel.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] Write a failing UI test for Tonghuashun authority, constituent source/coverage and scope-exclusion counts.
- [ ] Run the focused Vitest test and verify red.
- [ ] Add a compact data-provenance panel and responsive styling without a new dependency.
- [ ] Run focused UI tests and confirm green.
- [ ] Commit with `feat: 展示同花顺候选池数据质量` and push.

### Task 5: Verify production behavior

**Files:**
- Modify only files revealed by verification defects.

- [ ] Run all Python tests and a real read-only Tonghuashun ranking/constituent sample.
- [ ] Run Java 21 full reactor tests.
- [ ] Run all frontend tests and production build.
- [ ] Run `git diff --check`, inspect source placement/injection/braces against the review checklist, and confirm the worktree is clean.
- [ ] Commit any verification-only correction separately and push the branch.

