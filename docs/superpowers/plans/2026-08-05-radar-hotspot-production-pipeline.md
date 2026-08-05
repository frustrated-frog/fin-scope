# Radar Hotspot Production Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前实时资讯雷达改造成后台生产式热点链路：按批次抓取现有本地信源、标准化并记录来源排名、跨源聚合、计算热点分数、持久化可追踪的生产快照，前端请求只读取最近快照。

**Architecture:** 保留 `ResearchMaterialGateway` 及现有 Provider 作为数据入口，把生产流程拆成抓取快照、信号落库、聚合、排序、增强和批次记录几个阶段。`RadarHotspotRefreshService` 使用互斥运行保护执行整批流程，定时器负责后台刷新，`ResearchRadarService` 在页面请求时只读已生产数据；手动刷新仅触发后台请求，不在 HTTP 线程内抓取。使用 SQLite 保存批次和阶段状态，不引入目标项目的 ES、Redis、MQ 或内部数据接口，也不建立临时榜单/正式榜单双态。

**Tech Stack:** Java 8, Spring Boot 2.7, SQLite/JDBC, JUnit 5/Mockito, React/TypeScript/Vite。

---

### Task 1: Persist production batches and source ranking metadata

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarSignal.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarRefreshRun.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarRefreshStep.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarRepository.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarRefreshRunRepository.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/radar/RadarRefreshRunRepositoryTest.java`

- [ ] **Step 1: Write the failing repository test**

  Add an in-memory SQLite test that creates a run, records a `FETCH` step, completes the run with counts, and verifies the latest completed run can be read with status/counts/timestamps. Also verify a captured signal can retain `sourceRank`, `previousSourceRank`, and `sourceWeight`.

- [ ] **Step 2: Run the focused DAO test and verify it fails**

  Run `cd backend && mvn -pl finscope-dao -am -Dtest=RadarRefreshRunRepositoryTest test`; expect compilation/test failure because the run model, tables, repository, and signal metadata do not exist.

- [ ] **Step 3: Implement the run/step models and SQLite schema**

  Add immutable-style Java bean models matching existing domain conventions, and create `radar_refresh_run` and `radar_refresh_step` tables with unique `run_key` and `(run_id, step_code)` constraints. Add nullable source-ranking columns to `radar_signal`; keep existing columns and semantics unchanged.

- [ ] **Step 4: Implement repository writes and reads**

  Add repository methods for `startRun`, `startStep`, `completeStep`, `completeRun`, `failRun`, `findLatestCompletedRun`, and signal upsert/update. Preserve parameterized SQL, SQLite timestamp conversion, and the repository’s existing upsert style.

- [ ] **Step 5: Run the focused test and commit**

  Run `cd backend && mvn -pl finscope-dao -am -Dtest=RadarRefreshRunRepositoryTest test`; expect PASS. Commit with `git add ... && git commit -m "feat: 增加雷达生产批次追踪"`.

### Task 2: Add deterministic multi-stage hotspot production pipeline

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarHotspotProductionPipeline.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarHotspotScoreService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/news/NewsFeedService.java` only if a stable provider/rank view is required
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarHotspotScoreServiceTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarHotspotProductionPipelineTest.java`

- [ ] **Step 1: Write failing score tests**

  Test that a signal with a better source rank, recent publication, repeated source coverage, and stronger source quality receives a higher normalized score; test empty and one-item inputs return bounded scores without division-by-zero.

- [ ] **Step 2: Run the score tests and verify red**

  Run `cd backend && mvn -pl finscope-service -am -Dtest=RadarHotspotScoreServiceTest test`; expect failure because the score service does not exist.

- [ ] **Step 3: Implement bounded deterministic hotspot scoring**

  Implement min-max normalization and weighted scoring for source quality, source rank, recency, source diversity, and cluster size. Keep the score in `[0, 100]`, make all tie-breaking deterministic, and expose score reasons for traceability. Do not call an LLM in the ranking path.

- [ ] **Step 4: Write the pipeline orchestration test**

  Mock `NewsFeedService`, `RadarRepository`, `RadarClusteringService`, `RadarPriorityService`, `RadarRefreshRunRepository`, and the enhancement scheduler. Verify one run executes ordered stages (`FETCH`, `NORMALIZE`, `AGGREGATE`, `RANK`, `PERSIST`), captures all input signals, writes links/events sorted by hotspot score, and does not require a temporary/formal list state.

- [ ] **Step 5: Run the pipeline test and verify red**

  Run `cd backend && mvn -pl finscope-service -am -Dtest=RadarHotspotProductionPipelineTest test`; expect failure because the pipeline and stage records are not implemented.

- [ ] **Step 6: Implement the pipeline using existing acquisition contracts**

  Fetch through `NewsFeedService`/`ResearchMaterialGateway`, assign deterministic per-provider source ranks, normalize and deduplicate the current snapshot, reuse `RadarClusteringService` for connected-component aggregation, calculate hotspot scores, reuse `RadarPriorityService` for personal research priority, persist signals/events/links, then schedule optional interpretation after the ranked snapshot is written. Use the existing provider guards and warnings; do not copy target project source URLs, gateway calls, or middleware.

- [ ] **Step 7: Run focused service tests and commit**

  Run `cd backend && mvn -pl finscope-service -am -Dtest=RadarHotspotScoreServiceTest,RadarHotspotProductionPipelineTest test`; expect PASS. Commit with `git add ... && git commit -m "feat: 增加热点聚合生产流水线"`.

### Task 3: Separate background production from page reads

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarHotspotRefreshService.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarHotspotRefreshScheduler.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/config/AppConfig.java` only if a dedicated executor is needed
- Modify: `backend/finscope-web/src/main/resources/application.yml`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarHotspotRefreshServiceTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/radar/ResearchRadarServiceTest.java`

- [ ] **Step 1: Write failing refresh isolation tests**

  Verify that `requestRefresh()` coalesces concurrent requests, the second request does not run a second pipeline, and `ResearchRadarService.load(..., true, ...)` returns stored data while requesting background refresh instead of calling the news gateway itself.

- [ ] **Step 2: Run the focused refresh tests and verify red**

  Run `cd backend && mvn -pl finscope-service -am -Dtest=RadarHotspotRefreshServiceTest,ResearchRadarServiceTest test`; expect failure because refresh coordination and read-only loading are not implemented.

- [ ] **Step 3: Implement refresh coordination and scheduling**

  Add a single-flight guard around the pipeline, persist run failure details, expose a nonblocking refresh request, and add a fixed-delay scheduler with a conservative default interval. Make startup/manual refresh safe when no completed snapshot exists; return the existing empty/fallback page shape with a refresh warning.

- [ ] **Step 4: Change radar page loading to read the latest snapshot**

  Keep the current API contract and `refresh` query parameter. When `refresh=false`, read stored events/signals only. When `refresh=true`, call the nonblocking refresh request and then read the same stored snapshot. Preserve detail loading and asynchronous interpretation behavior.

- [ ] **Step 5: Run service tests and commit**

  Run `cd backend && mvn -pl finscope-service -am test`; expect PASS. Commit with `git add ... && git commit -m "feat: 将雷达页面改为生产快照读取"`.

### Task 4: Expose production status and verify the end-to-end contract

**Files:**
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/ResearchRadarController.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/radar/ResearchRadarView.java` if status is not already represented
- Modify: `frontend/src/features/news/NewsView.tsx` only to show refresh-in-progress/latest-batch metadata when available
- Test: existing backend controller/service tests and frontend build
- Modify: `docs/superpowers/specs/2026-07-31-personal-research-radar-mvp-design.md` with the new production snapshot behavior

- [ ] **Step 1: Write a failing status contract test**

  Verify the radar response can report the latest completed batch time and whether a refresh is currently running without exposing provider credentials or raw internal errors.

- [ ] **Step 2: Implement safe status projection**

  Add a small DTO/status projection backed by the refresh repository; expose only status, completed time, item/event counts, and user-safe warning text. Keep raw error details in logs/SQLite only.

- [ ] **Step 3: Update the page and documentation**

  Keep the existing radar layout, add a compact “最近生产”/“后台刷新中” indicator, and document that page reads are snapshot reads while refresh is asynchronous.

- [ ] **Step 4: Run complete verification**

  Run `cd backend && mvn test` and `cd frontend && npm run build`; inspect `git diff --check`, `git status`, and the final diff for unrelated changes. If all checks are green, create the final commit `feat: 完成热点生产链路接入`.

---

## Self-review checklist

- Source and middleware boundaries remain FinScope’s existing Provider/Gateway/SQLite contracts.
- The process has no temporary-vs-formal ranking state; there is one latest completed production snapshot.
- The page never performs external acquisition in the request thread.
- Each stage is observable through refresh run/step records.
- Ranking is deterministic, bounded, and testable without an LLM.
- Existing clustering, interpretation fallback, provider guards, and detail APIs remain reusable.
