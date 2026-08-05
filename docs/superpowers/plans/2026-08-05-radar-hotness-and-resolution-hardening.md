# Radar Hotness and Resolution Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不引入新的外部中间件的前提下，把个人热点雷达的来源质量、PRD 热点评分、事件聚类和本地可追踪性提升到可持续迭代的基线。

**Architecture:** 保留现有 Redis、ResearchMaterialGateway、后台生产线程和 SQLite 生产快照；将 `hotspotScore` 与面向个人研究的 `priorityScore` 分离。热点分数使用 PRD 的来源广度、发布速度、来源权威性、新意、跨平台传播和持续性六类可用指标，行情反应与用户互动在数据未接入时不进入有效权重。聚类继续使用确定性规则和异步灰区判定，但先按候选召回缩小比较范围，并要求新信号与事件代表及事件核心特征同时一致，避免连通分量链式漂移。

**Tech Stack:** Java 8, Spring Boot 2.7, SQLite/JDBC, Redis（保留现有缓存接入）, JUnit 5/Mockito, React/TypeScript/Vite。

---

### Task 1: Normalize radar source quality contracts

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarSourceQuality.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarHotspotProductionPipeline.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarPriorityService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarHotspotScoreService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarSourceQualityTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarHotspotScoreServiceTest.java`

- [ ] **Step 1: Write the failing normalization tests**

  Assert that `T1`, `TIER_1`, `PRIMARY`, `OFFICIAL` map to the same highest quality; `T2` and `TIER_2` map to the middle quality; unknown or blank values map to the lowest quality. Add a score regression using a real-provider-style `T2` signal and assert it receives the middle quality rather than the fallback quality.

- [ ] **Step 2: Run the focused tests and verify they fail**

  Run `cd backend && mvn -pl finscope-service -am -DfailIfNoTests=false -Dtest=RadarSourceQualityTest,RadarHotspotScoreServiceTest test`.

- [ ] **Step 3: Implement one canonical radar quality mapping**

  Add a small immutable value object or utility with normalized tier code, quality weight, and priority points. Route pipeline source weights, priority source-quality points, and hotspot authority through this mapping. Keep existing external `T1/T2` values unchanged at Provider boundaries.

- [ ] **Step 4: Run the focused tests and commit**

  Re-run the command from Step 2; expect all focused tests to pass. Commit only the source-quality files and tests with `fix: 统一雷达来源质量契约`.

### Task 2: Persist local event snapshots and implement PRD-inspired hotness

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarEventSnapshot.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarEventSnapshotRepository.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarHotspotScoreService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarHotspotProductionPipeline.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarEvent.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarRepository.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/radar/RadarEventSnapshotRepositoryTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarHotspotScoreServiceTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarHotspotProductionPipelineTest.java`

- [ ] **Step 1: Write failing snapshot repository tests**

  Create an in-memory SQLite schema for `radar_event_snapshot`, insert two snapshots for one event, and verify the latest prior snapshot can be read by event ID. Verify the unique key `(event_id, snapshot_at)` is idempotent.

- [ ] **Step 2: Run the repository test and verify it fails**

  Run `cd backend && mvn -pl finscope-dao -am -DfailIfNoTests=false -Dtest=RadarEventSnapshotRepositoryTest test` and confirm failure because the model, table, and repository are absent.

- [ ] **Step 3: Write failing hotness tests**

  Cover the following behaviors:
  - independent provider families increase `SourceBreadth` and do not count duplicate signals from the same provider twice;
  - a current 30-minute burst over the previous snapshot window increases `PublishVelocity`;
  - a high-quality source increases `SourceAuthority`;
  - a newly observed event receives higher `Novelty` than a quiet older event;
  - repeated snapshots with continued updates increase `Persistence`;
  - missing market reaction and user engagement do not produce fake data and the final score remains bounded in `[0,100]`;
  - the personal `priorityScore` remains independently calculated and still ranks watchlist-related events.

- [ ] **Step 4: Run the hotness tests and verify they fail**

  Run `cd backend && mvn -pl finscope-service -am -DfailIfNoTests=false -Dtest=RadarHotspotScoreServiceTest,RadarHotspotProductionPipelineTest test` and confirm the new snapshot-aware API is missing.

- [ ] **Step 5: Implement snapshot persistence and score inputs**

  Add a SQLite table containing event ID, snapshot time, signal count, independent source count, velocity, raw hotness score, final hotness score, and lifecycle state. Write one snapshot after the event is persisted. Read the previous snapshot before calculating the next score. Keep all writes local and idempotent.

- [ ] **Step 6: Implement the PRD-inspired score with effective available weights**

  Use the PRD base weights for `SourceBreadth`, `PublishVelocity`, `SourceAuthority`, `Novelty`, `CrossPlatformSpread`, and `Persistence`. Normalize only across components with reliable local inputs; do not assign invented market or user-engagement values. Return a structured explanation containing component values, effective weights, and unavailable components. Keep `RadarPriorityService` separate and use it only for personal research ordering.

- [ ] **Step 7: Run DAO and service tests and commit**

  Run the focused DAO and service commands from Steps 2 and 4, then run the existing radar production tests. Commit with `feat: 增加个人热点热度快照评分`.

### Task 3: Add candidate recall and anti-chain constraints to event clustering

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarTextAnalyzer.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarClusteringService.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarClusteringServiceTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarClusteringEvaluationTest.java`
- Modify: `backend/finscope-service/src/test/resources/radar/clustering-cases.json`

- [ ] **Step 1: Add failing clustering regression cases**

  Add a case where A matches B and B matches C but A and C have different subjects/actions; assert the cluster does not merge all three. Add a case where two articles use a six-digit stock code and company alias and should be recalled as candidates. Add a case outside the candidate time window that must not be compared.

- [ ] **Step 2: Run clustering tests and verify the regressions fail**

  Run `cd backend && mvn -pl finscope-service -am -DfailIfNoTests=false -Dtest=RadarClusteringServiceTest,RadarClusteringEvaluationTest test`; confirm the current connected-component behavior fails the new anti-chain assertion.

- [ ] **Step 3: Implement deterministic candidate recall**

  Extend extracted features with normalized subject tokens, action tokens, variable tokens, numeric tokens, and a time bucket. Only compare signals sharing a category, a subject/stock token, a variable token with title overlap, or a bounded recent time bucket. Keep a deterministic fallback candidate for title-normalization equality.

- [ ] **Step 4: Implement core-compatible grouping**

  Select the oldest/highest-quality signal as the cluster representative. A signal may join only when it matches the representative and does not conflict with the cluster’s accumulated subject/action/variable/core tokens. Do not use transitive adjacency alone as proof of same event. Preserve cached/async gray-zone pair decisions and their conservative behavior.

- [ ] **Step 5: Run clustering and production tests and commit**

  Run the focused clustering tests, `RadarHotspotProductionPipelineTest`, and the existing radar service tests. Commit with `fix: 限制雷达事件链式误合并`.

### Task 4: Align API projections and complete verification

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarView.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarService.java`
- Modify: `frontend/src/features/news/researchRadarTypes.ts`
- Modify: `frontend/src/features/news/RadarEventCard.tsx`
- Modify: `frontend/src/features/news/NewsView.tsx`
- Test: `frontend/src/features/news/NewsView.test.tsx`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/ResearchRadarProductionStatusTest.java`

- [ ] **Step 1: Write failing projection tests**

  Assert the response exposes hotness score, priority score, score explanations, snapshot/lifecycle status, and production metadata without breaking existing fields. Assert overview counts remain based on the stored result set rather than only the requested page limit.

- [ ] **Step 2: Implement backward-compatible projections**

  Add optional hotness explanation/component fields and lifecycle state. Keep existing `priorityScore`, card labels, and event detail contracts. Show “热点” and “研究优先级” as separate values in the card, and only show lifecycle labels when snapshot history is available.

- [ ] **Step 3: Run complete verification**

  Run `cd backend && mvn test -DfailIfNoTests=false`, `cd frontend && npm test`, `cd frontend && npm run build`, and `git diff --check`. Inspect `git status` and ensure the user’s existing PRD modification is not staged in the implementation commits.

- [ ] **Step 4: Commit the projection batch**

  Commit with `feat: 完善个人热点雷达评分呈现` and push the current branch only after all verification commands pass.

---

## Scope decisions

- Redis remains enabled for the existing research-material cache; this plan does not add a second radar cache or distributed lock.
- No Kafka/RocketMQ, Elasticsearch/OpenSearch, vector database, or object storage is introduced in this iteration.
- Market reaction and user engagement remain explicit unavailable components until reliable project-local data contracts exist.
- The product remains a personal research radar: hotness is objective event attention, while priority is personalized research ordering.
