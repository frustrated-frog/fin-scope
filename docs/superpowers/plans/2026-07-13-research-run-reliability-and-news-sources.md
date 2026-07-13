# Research Run Reliability And News Sources Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让一次研究运行在有限时间内持续更新进度、进入明确终态，并使用一组无需 Key 的新闻源生成完整简报。

**Architecture:** Spring Boot 继续作为唯一研究编排器。RSS 来源沿用现有 `RssSourceAdapter`，研究批处理期间对单篇文章使用确定性分析和证据降级，最后保留一次简报综合；所有运行计数从 `research_run_output` 增量刷新。

**Tech Stack:** Java 8、Spring Boot 2.7、SQLite、Jsoup、ROME、JUnit 5、Mockito。

---

### Task 1: Enforce the per-source item budget

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/fetch/RawItemSelector.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/fetch/RawItemSelectorTest.java`

- [x] Add a failing test that sets `maxItemsPerRun=2`, supplies three selectable items, and expects only the two highest-ranked items.
- [x] Run `mvn -pl finscope-service -am -Dtest=RawItemSelectorTest -Dsurefire.failIfNoSpecifiedTests=false test` and confirm the new assertion fails with size 3.
- [x] After sorting, return at most `source.getMaxItemsPerRun()` items while preserving source ranks.
- [x] Re-run the focused test and confirm it passes.

### Task 2: Keep external LLM calls outside long research ingestion loops

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/ResearchRunContext.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/agent/ArticleInterpretationAgent.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/EvidenceService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/LearningTaskService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/ContentIdeaService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/article/ArticleIngestCoordinator.java`
- Test: focused service tests for the affected agents and transaction boundary.

- [x] Add failing tests proving an active `ResearchRunContext` does not call the per-article LLM but still returns deterministic cards, evidence, learning tasks, and content ideas.
- [x] Run the focused tests and confirm the LLM invocation assertions fail.
- [x] Add `ResearchRunContext.isBatchResearch()` and make the four per-article services select their existing fallback paths while it is true.
- [x] Remove `@Transactional` from the public article-ingest overloads so LLM work cannot keep a SQLite write transaction open.
- [x] Re-run focused tests and confirm all deterministic outputs remain present.

### Task 3: Persist visible progress and a terminal result

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/ResearchService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/ResearchServiceHarnessTest.java`

- [x] Add a failing test with two sources that captures `updateResult` calls and requires an update after the first source with `status=RUNNING`, `fetchedSourceCount=1`, and current article/event/evidence counts.
- [x] Run the focused test and confirm no intermediate update exists.
- [x] Refresh counts from `ResearchRunOutputService` and call `updateResult` after every source.
- [x] On final success or failure, refresh all available output counts before persisting the terminal state.
- [x] Inject the output-service mock into existing harness fixtures so tests exercise the successful path instead of failing on a null fixture.
- [x] Re-run the harness and confirm successful, partial-success, and rejected-executor paths all terminate correctly.

### Task 4: Install a small, idempotent recommended news catalog

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/source/SourceService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/SourceController.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/source/SourceServiceTest.java`

- [x] Add a failing test that installs the catalog twice and verifies no duplicate create calls.
- [x] Add an explicit `POST /api/sources/recommended-news` operation that creates or updates sources by normalized URL; do not mutate source data on application startup.
- [x] Give each source `maxItemsPerRun=3..5`, explicit credibility, and theme tags used by the current planner.
- [x] Re-run the service and web integration tests and confirm the catalog is idempotent.
- [x] Strip a leading UTF-8 BOM before RSS parsing so the Federal Reserve feed works with ROME.

### Task 5: End-to-end verification

**Files:**
- Test: `backend/finscope-web/src/test/java/com/finscope/web/FinScopeApiIntegrationTest.java`

- [x] Verify the research integration path reaches a terminal status with non-zero event output and a generated report.
- [x] Run service and RPC test suites.
- [x] Run the focused web integration test with its local HTTP feed fixture.
- [x] Smoke-test the four live RSS URLs through an isolated application instance.
- [x] Run a live three-theme research: 4/4 sources, 16 articles, 10 events, 16 evidence items, 18 learning tasks, 11 content ideas, and an 18 KB report.
- [x] Inspect the final diff and preserve all pre-existing user document changes.
