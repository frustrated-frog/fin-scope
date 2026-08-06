# News, Radar and Dashboard Cache Concurrency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以 Redis 页面快照、单飞缓存击穿保护和受控多源并发抓取，降低实时资讯、研究雷达及首页今日热点的读取延迟，同时保持现有生产快照与 SQLite 写入安全。

**Architecture:** `ResearchMaterialGateway` 继续作为来源聚合边界，在其内部为同一请求使用进程内 single-flight，并在不同 provider 间使用受限执行器并发抓取，仍由 `ProviderRequestGuard` 做端点/家族限频与熔断。三个只读页面使用版本化 Redis JSON 快照；业务写入只递增对应版本，TTL 自动回收旧键。雷达生产批次的写入与排序保持串行，成功完成后才让页面读新版本。

**Tech Stack:** Java 8、Spring Boot 2.7、Spring Data Redis `StringRedisTemplate`、Jackson、`CompletableFuture`、SQLite、JUnit 5/Mockito、React/Vite（接口保持不变）。

---

## 文件边界

- `backend/finscope-dao/.../cache/VersionedViewCacheRepository.java`：版本化 JSON 快照的 DAO 抽象。
- `backend/finscope-dao/.../cache/RedisVersionedViewCacheRepository.java`：Redis `GET/SET/INCR` 实现及不可用回退。
- `backend/finscope-service/.../news/NewsFeedService.java`：资讯视图缓存读取/回填；只负责视图缓存。
- `backend/finscope-service/.../research/material/ResearchMaterialGateway.java`：来源级并发与同 key single-flight；仍负责原始资料缓存。
- `backend/finscope-service/.../radar/ResearchRadarService.java`：雷达已完成快照的缓存读取/回填。
- `backend/finscope-service/.../dashboard/DashboardService.java`：首页 summary 快照读取/回填。
- `backend/finscope-service/.../radar/RadarHotspotProductionPipeline.java`：批次成功后失效雷达与首页快照；将 dashboard 历史分类修正移入生产后处理。
- `backend/finscope-service/.../dashboard/DashboardHotspotRankingService.java`：移除读请求写入。
- `backend/finscope-web/.../config/AppConfig.java`、`application.yml`：`newsFetchExecutor` 与 TTL/并发配置。
- `backend/finscope-web/.../controller/*`：在分类/事件写操作后调用对应失效服务。

### Task 1: 版本化 Redis 快照基础设施

**Files:**
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/cache/VersionedViewCacheRepository.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/cache/RedisVersionedViewCacheRepository.java`
- Create: `backend/finscope-dao/src/test/java/com/finscope/dao/cache/RedisVersionedViewCacheRepositoryTest.java`

- [ ] **Step 1: Write failing tests for a versioned hit and invalidation**

```java
@Test void returnsPayloadAtCurrentVersionAndMissesAfterInvalidation() {
    when(values.get("finscope:view:radar:version")).thenReturn("7", "8");
    when(values.get("finscope:view:radar:7:all")).thenReturn("{\"events\":[]}");
    assertThat(repository.get("radar", "all")).contains("{\"events\":[]}");
    repository.invalidate("radar");
    assertThat(repository.get("radar", "all")).isEmpty();
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `cd backend && mvn -pl finscope-dao -Dtest=RedisVersionedViewCacheRepositoryTest test`

Expected: FAIL because `VersionedViewCacheRepository` does not exist.

- [ ] **Step 3: Implement versioned JSON operations**

```java
public interface VersionedViewCacheRepository {
    Optional<String> get(String namespace, String variant);
    void put(String namespace, String variant, String payload, Duration ttl);
    void invalidate(String namespace);
}

private String key(String namespace, String variant) {
    return "finscope:view:" + namespace + ":" + version(namespace) + ":" + variant;
}

public void invalidate(String namespace) {
    redisTemplate.opsForValue().increment("finscope:view:" + namespace + ":version");
}
```

Catch Redis/serialization exceptions in `get`, `put`, and `invalidate`; `get` returns `Optional.empty()` and writes do nothing. Do not use `KEYS` or `SCAN` for invalidation.

- [ ] **Step 4: Run DAO tests and commit**

Run: `cd backend && mvn -pl finscope-dao -Dtest=RedisVersionedViewCacheRepositoryTest test`

Expected: PASS.

Run: `git add backend/finscope-dao/src/main/java/com/finscope/dao/cache backend/finscope-dao/src/test/java/com/finscope/dao/cache/RedisVersionedViewCacheRepositoryTest.java && git commit -m "feat: 增加版本化视图缓存"`

### Task 2: 原始资讯单飞与来源受控并发

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/material/ResearchMaterialGateway.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/config/AppConfig.java`
- Modify: `backend/finscope-web/src/main/resources/application.yml`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/material/ResearchMaterialGatewayTest.java`

- [ ] **Step 1: Write failing tests for parallel providers and same-key sharing**

```java
@Test void fetchesIndependentProvidersConcurrentlyButKeepsRouteOrder() {
    CountDownLatch started = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    provider("CLS", started, release, result("cls"));
    provider("THS", started, release, result("ths"));
    CompletableFuture<ResearchMaterialGatewayResult> call = supplyAsync(() -> gateway.search(NEWS_FLASH, request));
    assertThat(started.await(1, SECONDS)).isTrue(); release.countDown();
    assertThat(call.get(1, SECONDS).getMaterials()).extracting(ResearchMaterial::getExternalId)
        .containsExactly("cls", "ths");
}

@Test void concurrentCacheMissesShareOneProviderFanout() {
    CompletableFuture.allOf(supplyAsync(() -> gateway.search(NEWS_FLASH, request)),
        supplyAsync(() -> gateway.search(NEWS_FLASH, request))).join();
    verify(provider, times(1)).fetch(NEWS_FLASH, request);
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchMaterialGatewayTest test`

Expected: FAIL because providers are currently fetched one at a time and each caller invokes `fetch` independently.

- [ ] **Step 3: Add a bounded executor and implement deterministic fan-out**

```java
@Bean(name = "newsFetchExecutor")
public Executor newsFetchExecutor() {
    ThreadPoolTaskExecutor value = new ThreadPoolTaskExecutor();
    value.setThreadNamePrefix("news-fetch-"); value.setCorePoolSize(3);
    value.setMaxPoolSize(3); value.setQueueCapacity(6); value.initialize();
    return value;
}

private final ConcurrentMap<String, CompletableFuture<ResearchMaterialGatewayResult>> flights = new ConcurrentHashMap<>();

public ResearchMaterialGatewayResult search(ResearchMaterialType type, ResearchMaterialRequest request) {
    String key = cacheKey(type, request);
    return flights.computeIfAbsent(key, ignored -> CompletableFuture
        .supplyAsync(() -> searchOnce(type, request, key), executor)
        .whenComplete((result, error) -> flights.remove(key))).join();
}
```

`searchOnce` first checks the existing Redis material cache. For a miss, submit one future per provider returned by `routePolicy.orderExternal`; collect futures in that already-sorted list order; call `guard.execute(provider, capability, ...)` inside every task; convert each failure to the same provider warning format now used by `fetch`; merge with the existing `key(material)` de-duplication. Do not use `parallelStream`, unbounded executors, completion-order merging, or manual sleep/retry.

Add configuration:

```yaml
finscope:
  news:
    fetch-concurrency: 3
```

Wire that property into the executor sizes, validating to `[1, 3]`.

- [ ] **Step 4: Run tests and commit**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchMaterialGatewayTest test`

Expected: PASS, including existing cache/error tests.

Run: `git add backend/finscope-service/src/main/java/com/finscope/service/research/material/ResearchMaterialGateway.java backend/finscope-service/src/test/java/com/finscope/service/research/material/ResearchMaterialGatewayTest.java backend/finscope-web/src/main/java/com/finscope/web/config/AppConfig.java backend/finscope-web/src/main/resources/application.yml && git commit -m "perf: 并发抓取多源实时资讯"`

### Task 3: 实时资讯、雷达和首页视图快照

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/news/NewsFeedService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/dashboard/DashboardService.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/cache/ViewCacheCodec.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/cache/ViewCacheCodecTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/news/NewsFeedServiceTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/radar/ResearchRadarSnapshotReadTest.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/dashboard/DashboardServiceCacheTest.java`

- [ ] **Step 1: Write failing cache-hit, invalid-payload and fallback tests**

```java
@Test void radarStoredReadReturnsCachedSnapshotWithoutRepositoryQueries() {
    cache.put("radar", "category=ALL&watchlist=false&limit=20&state=ALL", json(view), Duration.ofMinutes(1));
    assertThat(service.loadStored("ALL", false, 20, "ALL")).isEqualTo(view);
    verify(repository, never()).findRanked(anyString(), anyBoolean(), anyInt());
}

@Test void malformedCachedPayloadFallsBackToCurrentReadPath() {
    when(cache.get(anyString(), anyString())).thenReturn(Optional.of("not-json"));
    assertThat(news.load("ALL", 100).getItems()).isNotEmpty();
}
```

- [ ] **Step 2: Run focused tests and verify they fail**

Run: `cd backend && mvn -pl finscope-service -Dtest=NewsFeedServiceTest,ResearchRadarSnapshotReadTest,DashboardServiceCacheTest,ViewCacheCodecTest test`

Expected: FAIL because no service reads a versioned view cache.

- [ ] **Step 3: Implement a codec and cache-aside helper at the service boundary**

```java
public <T> T readThrough(String namespace, String variant, Duration ttl, Class<T> type,
                         Supplier<T> loader) {
    Optional<String> cached = cache.get(namespace, variant);
    if (cached.isPresent()) try { return mapper.readValue(cached.get(), type); }
    catch (IOException ignored) { }
    T loaded = loader.get();
    try { cache.put(namespace, variant, mapper.writeValueAsString(loaded), ttl); }
    catch (JsonProcessingException ignored) { }
    return loaded;
}
```

Use exact variants:

```java
"category=" + category + "&limit=" + limit
"category=" + category + "&watchlist=" + watchlistOnly + "&limit=" + limit + "&state=" + state
"summary"
```

Use `Duration.ofSeconds(30)` for news/dashboard and `Duration.ofSeconds(60)` for stored radar. `load(... refresh=true)` must never cache a response carrying a transient “后台生产” warning; only `loadStored` is cacheable. Keep public controllers and JSON contracts unchanged.

- [ ] **Step 4: Run focused service tests and commit**

Run: `cd backend && mvn -pl finscope-service -Dtest=NewsFeedServiceTest,ResearchRadarSnapshotReadTest,DashboardServiceCacheTest,ViewCacheCodecTest test`

Expected: PASS.

Run: `git add backend/finscope-service/src/main/java/com/finscope/service/cache backend/finscope-service/src/main/java/com/finscope/service/news/NewsFeedService.java backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarService.java backend/finscope-service/src/main/java/com/finscope/service/dashboard/DashboardService.java backend/finscope-service/src/test/java/com/finscope/service && git commit -m "perf: 缓存资讯雷达与首页快照"`

### Task 4: 业务写入失效与首页分类读写分离

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarHotspotProductionPipeline.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/dashboard/DashboardHotspotRankingService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/news/NewsClassificationReviewService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/news/NewsClassificationCoordinator.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarEventWorkspaceService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarEventInterpretationService.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarHotspotProductionPipelineTest.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/cache/ViewCacheInvalidationTest.java`

- [ ] **Step 1: Write failing invalidation tests**

```java
@Test void successfulRadarProductionInvalidatesRadarAndDashboardOnly() {
    pipeline.run("ALL", "MANUAL", now);
    verify(cache).invalidate("radar"); verify(cache).invalidate("dashboard");
    verify(cache, never()).invalidate("news");
}

@Test void reviewedNewsCategoryInvalidatesNewsSnapshot() {
    reviewService.review(request);
    verify(cache).invalidate("news");
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run: `cd backend && mvn -pl finscope-service -Dtest=RadarHotspotProductionPipelineTest,ViewCacheInvalidationTest test`

Expected: FAIL because write paths do not invalidate view versions.

- [ ] **Step 3: Invalidate only after successful committed writes**

```java
List<RadarEvent> savedEvents = persist(ranked, now);
RadarRefreshRun completed = runs.completeRun(...);
viewCache.invalidate("radar"); viewCache.invalidate("dashboard");
return new ProductionResult(completed, snapshot, savedEvents);
```

Move `classifyEventsCreatedBeforeDashboardRankings()` out of `rankings()` into a production-completion method invoked before the two invalidations. `DashboardHotspotRankingService.rankings()` must contain only `findTopByDashboardCategory` reads. Invalidate `radar` after workspace/interpretation mutations have succeeded, and invalidate `news` only after review or classifier persistence succeeds.

- [ ] **Step 4: Run focused tests and commit**

Run: `cd backend && mvn -pl finscope-service -Dtest=RadarHotspotProductionPipelineTest,ViewCacheInvalidationTest,ResearchRadarSnapshotReadTest test`

Expected: PASS.

Run: `git add backend/finscope-service/src/main/java/com/finscope/service/radar backend/finscope-service/src/main/java/com/finscope/service/dashboard/DashboardHotspotRankingService.java backend/finscope-service/src/main/java/com/finscope/service/news backend/finscope-service/src/test/java/com/finscope/service && git commit -m "fix: 按热点写入失效页面缓存"`

### Task 5: 集成验证、容灾与文档

**Files:**
- Modify: `backend/finscope-web/src/test/java/com/finscope/web/controller/NewsFeedControllerTest.java`
- Modify: `backend/finscope-web/src/test/java/com/finscope/web/controller/ResearchRadarApiIntegrationTest.java`
- Modify: `backend/finscope-web/src/test/java/com/finscope/web/controller/DashboardControllerTest.java` (create if absent)
- Modify: `docs/superpowers/specs/2026-08-06-news-radar-dashboard-cache-concurrency-design.md`

- [ ] **Step 1: Add API compatibility and Redis-down tests**

```java
@Test void redisReadFailureStillReturnsNewsFeed() {
    when(cache.get(anyString(), anyString())).thenThrow(new RedisConnectionFailureException("down"));
    mvc.perform(get("/api/news")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray());
}
```

- [ ] **Step 2: Run module verification**

Run: `cd backend && mvn -pl finscope-web -am -Dtest=NewsFeedControllerTest,ResearchRadarApiIntegrationTest,DashboardControllerTest test`

Expected: PASS. If the repository still contains the three known unrelated DAO assertion failures, record them separately and do not change their production semantics in this task.

- [ ] **Step 3: Verify hot cache behavior against local Redis**

Run: `redis-cli -h 127.0.0.1 --scan --pattern 'finscope:view:*'`

Expected: view-version and short-lived view keys appear after requesting `/api/news`, `/api/research-radar?...&refresh=false`, and `/api/dashboard` twice.

Run: `for url in '/api/news?category=ALL&limit=100' '/api/research-radar?category=ALL&watchlistOnly=false&limit=20&state=ALL&refresh=false' '/api/dashboard'; do curl -sS -o /dev/null -w "$url %{time_total}s\\n" "http://127.0.0.1:8080$url"; done`

Expected: hot reads complete without provider calls; exact time is environment-dependent.

- [ ] **Step 4: Build, inspect, commit and push**

Run: `cd backend && mvn -pl finscope-web -am -DskipTests package && cd ../frontend && npm test -- --run && npm run build && cd .. && git diff --check && git status --short`

Expected: builds pass and only task files are staged. Preserve the two pre-existing untracked `RadarViewCacheRepository` files unless they are deliberately reconciled with Task 1.

Run: `git add docs/superpowers/specs/2026-08-06-news-radar-dashboard-cache-concurrency-design.md docs/superpowers/plans/2026-08-06-news-radar-dashboard-cache-concurrency.md && git commit -m "docs: 补充热点缓存并发方案" && git push origin HEAD`
