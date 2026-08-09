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

- [x] Write a repository test that persists `stage`, `errorCode`, `userMessage`, `retryable`, claim `status` and `failureMessage`.
- [x] Run `cd backend && mvn -pl finscope-dao -Dtest=StockLearningCardRepositoryTest test`; expect assertions to fail because the fields are absent.
- [x] Add idempotent columns with `ensureColumn`, map them in the domain and repository, and add `updateRun` for in-place asynchronous progress.
- [x] Re-run the repository test; expect PASS.
- [x] Commit with `feat: 增加学习卡独立运行状态`.

### Task 2: 六维独立 Agent

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningCardAgentExecutor.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningCardSynthesisAgent.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningCardEvidence.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningFramework.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningCardService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/learningcard/StockLearningCardAgentExecutorTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/learningcard/StockLearningCardServiceTest.java`

- [x] Write failing tests proving start accepts only a stock code and never calls a generic research service, six fixed queries execute, and one failed dimension leaves five successful dimensions.
- [x] Run `cd backend && mvn -pl finscope-service -Dtest=StockLearningCardServiceTest,StockLearningCardAgentExecutorTest test`; expect compilation/test failure for the missing executor.
- [x] Implement fixed dimension queries, bounded search/full-text acquisition, strict JSON synthesis, forbidden-language validation and per-dimension fallback.
- [x] Make `StockLearningCardService.start` persist a QUEUED run and schedule the executor; make GET a pure read.
- [x] Re-run the focused service tests; expect PASS.
- [x] Commit with `feat: 实现股票学习卡独立Agent`.

### Task 3: 独立执行线程与 API 回归

**Files:**
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/config/AppConfig.java`
- Modify: `backend/finscope-web/src/test/java/com/finscope/web/controller/StockLearningCardControllerTest.java`

- [x] Add a controller contract assertion for the body-free POST response containing `stage=QUEUED` and no generic research run.
- [x] Run `cd backend && mvn -pl finscope-web -Dtest=StockLearningCardControllerTest test`.
- [x] Register a bounded `stockLearningCardExecutor` and keep the controller body-free.
- [x] Re-run the web test; expect PASS.
- [x] Commit with `feat: 接入学习卡独立执行队列`.

### Task 4: 前端阶段与局部失败

**Files:**
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/features/strategy/StockLearningCardPanel.tsx`
- Modify: `frontend/src/features/strategy/StockLearningCardPanel.test.tsx`
- Modify: `frontend/src/styles.css`

- [x] Write failing component tests for translated run stages, one failed dimension beside successful cards, and retryable user-facing failure text.
- [x] Run `cd frontend && npm test -- StockLearningCardPanel.test.tsx`; expect missing text failures.
- [x] Extend types and render stage progress, dimension status/failure and run-level user message without showing themes or technical exceptions.
- [x] Re-run the component test and `npm run build`; expect exit code 0.
- [x] Commit with `feat: 展示学习卡独立Agent状态`.

### Task 5: 运行可靠性与证据追溯

**Files:**
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/learningcard/StockLearningCardRepository.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/learningcard/StockLearningCardEvidence.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningCardService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningCardAgentExecutor.java`
- Modify: `frontend/src/features/strategy/StockLearningCardPanel.tsx`

- [x] 用数据库局部唯一索引保证一张学习卡只能有一个 RUNNING 版本，并将并发冲突翻译为学习卡领域消息。
- [x] 为超过 30 分钟的中断运行增加租约过期恢复，避免服务重启后永久阻塞。
- [x] 将 GET 改为纯读取，不再为尚未生成的股票创建占位行。
- [x] 按运行和维度持久化公开证据，生成 SHA-256 来源指纹，并在页面展示 `[E1]` 来源链接。
- [x] 扩充交易语言拦截，并记录脱敏的维度级失败日志。

### Task 6: 全量验证与文档

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-08-09-liujie-stock-learning-card-design.md`

- [x] Record that the old generic Research Runtime projection has been replaced by the independent learning-card agent.
- [ ] Run `cd backend && mvn test`。已执行；本次相关测试通过，但全量测试被既有 `ResearchMaterialSearchToolTest` 异常类型断言不一致阻断（809 个测试中 1 个失败，相关文件未被本分支修改）。
- [x] Run `cd frontend && npm test && npm run build`（61 个测试文件、343 个测试通过，生产构建成功）。
- [x] Run `git diff --check` and inspect `git status --short`; never stage the user-owned `AGENTS.md`.
- [ ] Commit with `docs: 更新股票学习卡Agent说明` and push all batches.
