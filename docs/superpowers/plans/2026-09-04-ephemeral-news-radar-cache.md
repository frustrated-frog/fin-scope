# Ephemeral News and Radar Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move live-news classifications and all radar-derived content from SQLite to a bounded 36-hour Redis cache while preserving complete, durable major-event snapshots.

**Architecture:** Add a DAO-level Redis JSON state store that owns radar keys, serialization, pruning, deterministic ephemeral IDs, and the 36-hour TTL. Existing service-facing repository APIs remain stable so clustering, ranking, detail, and dashboard projections do not cross persistence boundaries; news classification gets a dedicated per-item Redis repository. Major-event creation resolves cached news/radar objects and writes an independent SQLite snapshot.

**Tech Stack:** Java 21, Spring Boot 2.7, Spring Data Redis, Jackson, SQLite/JdbcTemplate, JUnit 5, Mockito, React, TypeScript, Vitest.

---

### Task 1: Add the bounded Redis state foundation

**Files:**
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/cache/EphemeralContentCacheProperties.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarCacheState.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RedisRadarCacheStore.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/radar/RedisRadarCacheStoreTest.java`

- [ ] **Step 1: Write failing tests** proving the store writes `finscope:radar:state`, uses a 129600-second TTL, derives repeatable positive IDs from business keys, removes signals/events older than 36 hours, and removes event-owned children when their event expires.
- [ ] **Step 2: Run** `cd backend && mvn -pl finscope-dao -Dtest=RedisRadarCacheStoreTest test` and confirm failures are caused by the missing store.
- [ ] **Step 3: Implement** a typed `@ConfigurationProperties(prefix = "finscope.ephemeral-content")` object with `ttlHours=36`, a package-private Jackson state DTO, and a synchronized Redis store with these APIs:

```java
public RadarCacheState read();
public <T> T update(Function<RadarCacheState, T> mutation);
public long stableId(String namespace, String businessKey);
public Duration remainingTtl(LocalDateTime baseTime, LocalDateTime now);
```

  `update` must read, prune, mutate, serialize, and write with the configured TTL. Pruning uses `publishedAt/firstSeenAt/lastSeenAt` and never refreshes an individual object's absolute expiry.
- [ ] **Step 4: Re-run the focused test**, then run `cd backend && mvn -pl finscope-dao test`.
- [ ] **Step 5: Commit** with `feat: 增加资讯雷达临时缓存基座`.

### Task 2: Move per-news classification state to Redis

**Files:**
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/news/NewsClassificationRepository.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/news/NewsItemClassification.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/news/NewsClassificationRepositoryTest.java`

- [ ] **Step 1: Replace the SQLite-oriented tests with failing Redis contract tests** covering first claim, duplicate pending claim, retry of an old failure, automatic classification, manual review, batch reads, and expiry.
- [ ] **Step 2: Run** `cd backend && mvn -pl finscope-dao -Dtest=NewsClassificationRepositoryTest test` and confirm the expected database-backed behavior fails the Redis assertions.
- [ ] **Step 3: Implement** per-item JSON keys under `finscope:news:classification:{sha256(itemId)}`. Preserve `itemId` inside the value, apply the remaining absolute TTL based on the item's first claim time, and use Redis compare/set semantics under the repository's local synchronization for claim/retry transitions. Do not call `JdbcTemplate`.
- [ ] **Step 4: Run the focused and DAO test suites.**
- [ ] **Step 5: Commit** with `refactor: 将新闻分类迁移到临时缓存`.

### Task 3: Replace primary radar SQLite repositories with the shared cache state

**Files:**
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarRepository.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarRefreshRunRepository.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarEventSnapshotRepository.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarEvidenceRepository.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarEventInterpretationRepository.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarPairDecisionRepository.java`
- Test: corresponding repository tests under `backend/finscope-dao/src/test/java/com/finscope/dao/radar/`

- [ ] **Step 1: Write failing repository contract tests** against an in-memory fake of the shared cache store. Cover deterministic signal/event IDs, upsert, active-window filtering, ranking, event-signal replacement, snapshot history, evidence replacement, interpretation history, pair-decision lookup, refresh-run transitions, and child cleanup after expiry.
- [ ] **Step 2: Run the focused radar DAO tests** and verify they fail before production changes.
- [ ] **Step 3: Rewrite each repository to use `RedisRadarCacheStore` through `@Resource` field injection.** Preserve existing public methods and `Long` DTO IDs; IDs are deterministic hashes of `itemId` or `eventKey`, so refreshes reuse identity without SQLite sequences. All query methods operate on a copied/pruned state and retain current sort and limit behavior.
- [ ] **Step 4: Run all radar DAO tests and `mvn -pl finscope-service -am -DskipTests compile`.**
- [ ] **Step 5: Commit** with `refactor: 将雷达核心数据迁移到临时缓存`.

### Task 4: Move radar workspace state to cache and remove expiring user notes

**Files:**
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarEventWorkspaceRepository.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarEventWorkspaceService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/ResearchRadarController.java`
- Modify: `frontend/src/features/news/radareventdetaildrawer.tsx`
- Modify: `frontend/src/features/news/newsview.test.tsx`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/radar/RadarEventWorkspaceRepositoryTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarEventWorkspaceServiceTest.java`

- [ ] **Step 1: Write failing tests** proving read/follow/disposition/timeline/notification state is cache-backed and removed with the owning event, and proving the UI no longer exposes observation creation, completion, or deletion.
- [ ] **Step 2: Run the focused Maven and Vitest tests and confirm expected failures.**
- [ ] **Step 3: Implement cache-backed state, summaries, timelines, research links, and notifications.** Remove observation mutation endpoints and their UI controls; return no observation records from legacy read paths during the compatibility window. Change visible copy from “关注” to “临时关注”.
- [ ] **Step 4: Re-run focused tests plus `cd frontend && npm test -- newsview.test.tsx`.**
- [ ] **Step 5: Commit** with `refactor: 将雷达交互状态限制在缓存窗口`.

### Task 5: Make the 36-hour snapshots authoritative for news, radar, and the homepage

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/news/NewsFeedService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarHotspotProductionPipeline.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarSnapshotProjectionService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/NewsFeedController.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/news/NewsFeedServiceTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarHotspotProductionPipelineTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarSnapshotProjectionServiceTest.java`

- [ ] **Step 1: Write failing tests** for filtering news older than 36 hours, preventing repeated refresh from extending absolute expiry, keeping the prior radar snapshot on partial write failure, and applying 36-hour TTL to radar/homepage snapshots.
- [ ] **Step 2: Run the focused tests and confirm expected failures.**
- [ ] **Step 3: Add clock-based expiry filtering to `NewsFeedService`; set projection TTL from the shared property; keep versioned write-then-activate behavior; ensure no service path falls back to SQLite content when Redis is absent.**
- [ ] **Step 4: Run service and web tests.**
- [ ] **Step 5: Commit** with `feat: 统一资讯与首页热点三十六小时窗口`.

### Task 6: Persist only deliberate major-event snapshots

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/majorevent/MajorEventService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/request/CreateMajorEventRequest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/majorevent/MajorEventServiceTest.java`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/controller/MajorEventControllerTest.java`

- [ ] **Step 1: Write failing tests** proving NEWS_ITEM and RADAR_EVENT commands resolve the current cached object, save a complete independent snapshot, remain readable after cache eviction, reject expired/missing cache objects, and stay idempotent by stable origin key.
- [ ] **Step 2: Run focused tests and verify expected failures.**
- [ ] **Step 3: Make the backend authoritative for snapshot fields.** For NEWS_ITEM and RADAR_EVENT, ignore client-supplied title/summary/source metadata when the cache contains the source object. Store the radar `eventKey` rather than an incidental numeric ID as the durable `originKey`.
- [ ] **Step 4: Run focused service/web tests.**
- [ ] **Step 5: Commit** with `feat: 仅将大事记资讯快照持久化`.

### Task 7: Retire legacy SQLite content and document offline compaction

**Files:**
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/config/EphemeralContentMigration.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/config/EphemeralContentMigrationTest.java`
- Modify: `README.md`

- [ ] **Step 1: Write a failing SQLite migration test** with populated major-event, classification, radar, and dependent radar tables. Assert the migration preserves `major_event` and `news_category` while clearing all retired transient tables, and assert a second run is harmless.
- [ ] **Step 2: Run the migration test and confirm failure.**
- [ ] **Step 3: Implement an idempotent version marker and dependency-ordered cleanup.** Stop initializing new transient tables after compatibility cleanup, but do not drop them or run `VACUUM` at application startup. Add an explicit offline `sqlite3 data/finance.db 'VACUUM;'` maintenance instruction with backup and stopped-application prerequisites.
- [ ] **Step 4: Run DAO tests twice to prove idempotency.**
- [ ] **Step 5: Commit** with `chore: 清理旧资讯雷达临时表数据`.

### Task 8: Full verification and standards audit

**Files:**
- Modify only files required by verification findings.

- [ ] **Step 1: Run** `cd backend && mvn test`.
- [ ] **Step 2: Run** `cd frontend && npm test`.
- [ ] **Step 3: Run** `cd frontend && npm run build`.
- [ ] **Step 4: Search changed Java files for constructor-injected Spring beans and brace-less `if`/`for`; fix violations and rerun affected tests.**
- [ ] **Step 5: Confirm** `git diff --check`, inspect the full diff, and verify `application.yml` remains excluded from all feature commits.
- [ ] **Step 6: Commit any verification-only corrections** with `fix: 修正资讯缓存改造验证问题`.
