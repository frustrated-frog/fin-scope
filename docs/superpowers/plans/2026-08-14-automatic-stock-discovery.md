# Automatic Stock Discovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a fully automatic A-share stock discovery tab that narrows external hot sectors, quantifies every affordable candidate, deeply validates the strongest candidates, and presents at most five final research results.

**Architecture:** The existing Python market-data service owns provider parsing, admission rules, factor computation, batch ranking, and deep forecasting. Java owns schedule/Kafka orchestration, idempotent state, transactional persistence, and REST views. React reads the latest completed snapshot and renders the complete evidence trail without requiring a manual refresh.

**Tech Stack:** Python 3.11+ / FastAPI / httpx / NumPy / AkShare, Java 21 / Spring Boot 2.7 / Kafka / Redis / SQLite, React / TypeScript / Vite / Vitest.

---

### Task 1: Python stock-discovery contracts and deterministic ranking

**Files:**
- Create: `market-data-service/src/finscope_market_data/discovery/schemas.py`
- Create: `market-data-service/src/finscope_market_data/discovery/ranking.py`
- Create: `market-data-service/src/finscope_market_data/discovery/__init__.py`
- Test: `market-data-service/tests/test_discovery_ranking.py`

- [ ] Write failing tests proving budget is admission-only, ordering is input-independent, and fewer than five qualified candidates are returned.
- [ ] Run `cd market-data-service && uv run pytest tests/test_discovery_ranking.py -q` and confirm failures identify missing discovery modules.
- [ ] Add Pydantic request/report contracts and pure ranking functions with versioned scoring explanations.
- [ ] Run the focused tests and confirm all pass.
- [ ] Commit with `feat: 增加股票发现量化排序契约` and push the branch.

### Task 2: Python provider acquisition and full discovery pipeline

**Files:**
- Create: `market-data-service/src/finscope_market_data/discovery/providers.py`
- Create: `market-data-service/src/finscope_market_data/discovery/service.py`
- Modify: `market-data-service/src/finscope_market_data/app.py`
- Modify: `market-data-service/README.md`
- Test: `market-data-service/tests/test_discovery_service.py`
- Test: `market-data-service/tests/test_api.py`

- [ ] Write provider tests using fixed same-format frames for Tonghuashun failure, Eastmoney fallback, constituent de-duplication, and candidate-level data failure.
- [ ] Run the tests and confirm the discovery endpoint and provider classes are absent.
- [ ] Implement bounded provider access, sector/constituent normalization, QFQ admission, batch factor ranking, top-15 deep forecast, and final qualification.
- [ ] Add `POST /v1/quant/stock-discoveries`, wiring the service through app state so tests can inject deterministic fakes.
- [ ] Run all Python tests with `cd market-data-service && uv run pytest -q`.
- [ ] Commit with `feat: 实现Python自动股票发现流水线` and push.

### Task 3: Java persistence and Python RPC contract

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/discovery/StockDiscoveryRun.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/discovery/StockDiscoverySector.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/discovery/StockDiscoveryCandidate.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/quant/StockDiscoveryRepository.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quant/PythonStockDiscoveryClient.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/quant/StockDiscoveryRepositoryTest.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/quant/PythonStockDiscoveryClientTest.java`

- [ ] Write failing schema/repository tests for the unique business key, report transaction, and latest successful run query.
- [ ] Write failing RPC tests for request serialization, response parsing, timeouts, and provider errors.
- [ ] Add domain DTOs in the domain module, SQLite operations in DAO, and HTTP protocol mapping in RPC using project-standard field injection.
- [ ] Run `cd backend && mvn -pl finscope-dao,finscope-rpc -am test`.
- [ ] Commit with `feat: 增加股票发现持久化与Python契约` and push.

### Task 4: Java automatic orchestration and read APIs

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/discovery/StockDiscoveryRequestedEvent.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/quant/discovery/StockDiscoveryService.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/quant/discovery/StockDiscoveryScheduler.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/quant/discovery/StockDiscoveryEventPublisher.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quant/KafkaStockDiscoveryEventPublisher.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/messaging/StockDiscoveryListener.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/controller/StockDiscoveryController.java`
- Modify: `backend/finscope-web/src/main/resources/application.yml`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/quant/discovery/StockDiscoveryServiceTest.java`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/controller/StockDiscoveryControllerTest.java`

- [ ] Write failing tests for idempotent scheduling, completion, candidate-level partial failures, failed-run preservation, and latest/history reads.
- [ ] Implement the DB-first event flow: create-or-find run, publish versioned event, execute outside a DB transaction, and transactionally freeze the completed report.
- [ ] Add the 15:30 Asia/Shanghai schedule plus startup/missed-run compensation.
- [ ] Add latest, status, run history, and run detail endpoints.
- [ ] Run `cd backend && mvn -pl finscope-web -am test`.
- [ ] Commit with `feat: 实现股票发现自动调度与查询` and push.

### Task 5: High-fidelity stock-discovery tab

**Files:**
- Create: `frontend/src/features/strategy/StockDiscoveryPanel.tsx`
- Create: `frontend/src/features/strategy/StockDiscoveryPanel.test.tsx`
- Modify: `frontend/src/features/strategy/StrategyView.tsx`
- Modify: `frontend/src/features/strategy/QuantWorkspace.tsx`
- Modify: `frontend/src/features/strategy/quantTypes.ts`
- Modify: the strategy/quant stylesheet resolved from existing imports

- [ ] Write failing UI tests for navigation, latest summary, source provenance, final candidate evidence, funnel counts, degraded status, and empty state.
- [ ] Add “股票发现” before “单股预测”, keeping it inside the existing Quant workspace.
- [ ] Build the daily research hero, sector strip, ranked candidate cards, evidence details, funnel, and deep-review watch table.
- [ ] Add responsive and reduced-motion styles matching the existing visual system.
- [ ] Run `cd frontend && npm test -- --run` and `cd frontend && npm run build`.
- [ ] Commit with `feat: 增加自动股票发现工作台` and push.

### Task 6: End-to-end verification and documentation

**Files:**
- Modify: `README.md`
- Modify: `market-data-service/README.md`
- Modify: `项目开发规范与代码评审清单.md` only if the feature exposes a missing reusable checklist item; otherwise leave unchanged.

- [ ] Run Python, backend, and frontend focused suites followed by full build commands.
- [ ] Start the Python and Java services and execute one deterministic/manual internal recovery run to verify persistence and reads; do not add a normal UI refresh button.
- [ ] Inspect the rendered tab at desktop and narrow widths, checking overflow, hierarchy, source labels, and empty/error states.
- [ ] Review every changed Java `if`/`for`, Spring dependency, and type location against `项目开发规范与代码评审清单.md`.
- [ ] Remove unused code and temporary diagnostics.
- [ ] Commit with `docs: 补充自动股票发现运行说明` and push.
