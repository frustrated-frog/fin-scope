# 刘杰框架股票学习卡 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让用户选择一只 A 股后，由受控 Agent 自动生成并保存六维刘杰投研框架学习卡。

**Architecture:** 新增独立的 `stock-learning-card` 领域，持久化卡片身份、每次生成运行、六维判断与学习清单。服务层创建现有公司研究运行，研究完成后以受证据约束的合成 Agent 生成卡片；页面只读取卡片与研究进度，不要求用户填写研究内容。

**Tech Stack:** Java 8、Spring Boot 2.7、SQLite/JdbcTemplate、现有 OpenAI 兼容 LLM、React 18、TypeScript、Vitest。

---

### Task 1: 学习卡持久化与领域契约

**Files:**
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/learningcard/StockLearningCard.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/learningcard/StockLearningCardRun.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/learningcard/StockLearningCardClaim.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/learningcard/StockLearningCardWatchItem.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/learningcard/StockLearningCardRepository.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/learningcard/StockLearningCardRepositoryTest.java`

- [x] Write a failing SQLite repository test that creates a card, appends a READY run with six unique claims and one watch item, and reads the same immutable run back.
- [ ] Run `cd backend && mvn -pl finscope-dao -Dtest=StockLearningCardRepositoryTest test`; expect compilation failure because the learning-card classes do not exist.
- [ ] Add the four tables and indexes, focused domain records, and repository save/read methods. Enforce `(instrument_id)` uniqueness for cards and `(run_id, dimension_code)` uniqueness for claims.
- [x] Re-run the repository test; expect PASS.
- [x] Commit the persistence batch with `feat: 增加股票学习卡持久化`.

### Task 2: 受控建卡与降级合成服务

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningFramework.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningCardService.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningCardSynthesisAgent.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningCardDraftValidator.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/ResearchService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/learningcard/StockLearningCardServiceTest.java`

- [ ] Write failing service tests for: fixed six-dimension framework creation; rejection of a second running card for the same stock; model output whose evidence reference is absent from the research run; and controlled fallback that marks every uncovered dimension as evidence-insufficient.
- [ ] Run `cd backend && mvn -pl finscope-service -Dtest=StockLearningCardServiceTest test`; expect failure because the service does not exist.
- [ ] Implement framework constants, typed strict-JSON synthesis, evidence-reference validation, forbidden-trade-language validation, Agent Run trace recording, and deterministic fallback. Reuse `ResearchThesisService`, `ResearchService`, `ResearchReportService`, existing evidence repositories, and `StrategyInstrumentResolver`; do not add arbitrary tools.
- [ ] Re-run the service test; expect PASS.
- [x] Reuse the existing Research Runtime as the only Agent execution, project its terminal report into six fixed dimensions, and apply a trade-language guard plus deterministic degraded fallback.

### Task 3: REST API 与异步状态投影

**Files:**
- Create: `backend/finscope-web/src/main/java/com/finscope/web/controller/StockLearningCardController.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/response/StockLearningCardResponse.java`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/controller/StockLearningCardControllerTest.java`

- [ ] Write failing MVC tests proving that `POST /api/stock-learning-cards/{code}/runs` starts a card without a request body, `GET /api/stock-learning-cards/{code}` returns a card response, and a duplicate running request returns the service business error.
- [ ] Run `cd backend && mvn -pl finscope-web -Dtest=StockLearningCardControllerTest test`; expect failure because the controller is absent.
- [ ] Add thin controller and response mapping. The GET endpoint invokes the service’s terminal-run reconciliation so normal polling automatically publishes a completed learning card without a human confirmation step.
- [ ] Re-run the controller test; expect PASS.
- [x] Commit the Web batch with `feat: 提供股票学习卡接口`.

### Task 4: 学习卡前端阅读器

**Files:**
- Create: `frontend/src/features/strategy/StockLearningCardView.tsx`
- Create: `frontend/src/features/strategy/StockLearningCardView.test.tsx`
- Modify: `frontend/src/features/strategy/LongTermStrategyView.tsx`
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/styles.css`

- [ ] Write a failing component test that selects a stock, starts the body-free generation request, then renders the six dimensions and the mandatory learning-only disclaimer from a READY response.
- [ ] Run `cd frontend && npm test -- StockLearningCardView.test.tsx`; expect failure because the view is absent.
- [ ] Implement the learning-card tab, polling while a run is active, card history selection, evidence links and degraded-state notice. Do not render text inputs, investment actions, target prices, or position recommendations.
- [ ] Re-run the component test; expect PASS.
- [ ] Run `cd frontend && npm run build`; expect exit code 0.
- [x] Commit the frontend batch with `feat: 增加股票学习卡阅读器`.

### Task 5: 全量验证与文档同步

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-08-09-liujie-stock-learning-card-design.md`

- [ ] Add the finished learning-card capability to the product-scope list and record any implementation-level constraint that differs from the design.
- [ ] Run `cd backend && mvn test` and `cd frontend && npm test && npm run build`.
- [ ] Inspect `git diff --check` and confirm only intended files are staged; preserve the user-owned `AGENTS.md` modification.
- [ ] Commit documentation and verification changes with `docs: 补充股票学习卡使用说明` and push each completed commit to the feature branch.
