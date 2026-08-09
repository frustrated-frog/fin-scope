# 股票学习卡独立 Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将股票学习卡从通用研究运行中解耦，改为按六个固定维度独立搜索、合成并呈现局部失败的专属 Agent。

**Architecture:** `StockLearningCardService` 只负责领域入口和持久化，`StockLearningCardAgentExecutor` 异步编排六个维度，直接复用 `SearchEvidenceGateway`、`SearchEvidenceContentService` 和 `LlmChatClient`。运行与维度都使用学习卡自己的状态和错误契约，不创建主题、命题或通用研究运行。

**Tech Stack:** Java 8、Spring Boot 2.7、SQLite/JdbcTemplate、OpenAI 兼容 LLM、React、TypeScript、Vitest。

---

### Task 1: 独立运行与维度错误契约

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/learningcard/StockLearningCardRun.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/learningcard/StockLearningCardClaim.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/learningcard/StockLearningCardRepository.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/learningcard/StockLearningCardRepositoryTest.java`

- [ ] Write a repository test that persists `stage`, `errorCode`, `userMessage`, `retryable`, claim `status` and `failureMessage`.
- [ ] Run `cd backend && mvn -pl finscope-dao -Dtest=StockLearningCardRepositoryTest test`; expect assertions to fail because the fields are absent.
- [ ] Add idempotent columns with `ensureColumn`, map them in the domain and repository, and add `updateRun` for in-place asynchronous progress.
- [ ] Re-run the repository test; expect PASS.
- [ ] Commit with `feat: 增加学习卡独立运行状态`.

### Task 2: 六维独立 Agent

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningCardAgentExecutor.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningCardSynthesisAgent.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningCardEvidence.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningFramework.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningCardService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/learningcard/StockLearningCardAgentExecutorTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/learningcard/StockLearningCardServiceTest.java`

- [ ] Write failing tests proving start accepts only a stock code and never calls a generic research service, six fixed queries execute, and one failed dimension leaves five successful dimensions.
- [ ] Run `cd backend && mvn -pl finscope-service -Dtest=StockLearningCardServiceTest,StockLearningCardAgentExecutorTest test`; expect compilation/test failure for the missing executor.
- [ ] Implement fixed dimension queries, bounded search/full-text acquisition, strict JSON synthesis, forbidden-language validation and per-dimension fallback.
- [ ] Make `StockLearningCardService.start` persist a QUEUED run and schedule the executor; make GET a pure read.
- [ ] Re-run the focused service tests; expect PASS.
- [ ] Commit with `feat: 实现股票学习卡独立Agent`.

### Task 3: 独立执行线程与 API 回归

**Files:**
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/config/AppConfig.java`
- Modify: `backend/finscope-web/src/test/java/com/finscope/web/controller/StockLearningCardControllerTest.java`

- [ ] Add a failing context/controller test for the body-free POST response containing `stage=QUEUED` and no research-theme contract.
- [ ] Run `cd backend && mvn -pl finscope-web -Dtest=StockLearningCardControllerTest test`; expect the new stage assertion to fail.
- [ ] Register a bounded `stockLearningCardExecutor` and keep the controller body-free.
- [ ] Re-run the web test; expect PASS.
- [ ] Commit with `feat: 接入学习卡独立执行队列`.

### Task 4: 前端阶段与局部失败

**Files:**
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/features/strategy/StockLearningCardPanel.tsx`
- Modify: `frontend/src/features/strategy/StockLearningCardPanel.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] Write failing component tests for translated run stages, one failed dimension beside successful cards, and retryable user-facing failure text.
- [ ] Run `cd frontend && npm test -- StockLearningCardPanel.test.tsx`; expect missing text failures.
- [ ] Extend types and render stage progress, dimension status/failure and run-level user message without showing themes or technical exceptions.
- [ ] Re-run the component test and `npm run build`; expect exit code 0.
- [ ] Commit with `feat: 展示学习卡独立Agent状态`.

### Task 5: 全量验证与文档

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-08-09-liujie-stock-learning-card-design.md`

- [ ] Record that the old generic Research Runtime projection has been replaced by the independent learning-card agent.
- [ ] Run `cd backend && mvn test`.
- [ ] Run `cd frontend && npm test && npm run build`.
- [ ] Run `git diff --check` and inspect `git status --short`; never stage the user-owned `AGENTS.md`.
- [ ] Commit with `docs: 更新股票学习卡Agent说明` and push all batches.
