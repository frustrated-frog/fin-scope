# 雷达与首页热点缓存优先 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 在雷达生产完成时直接物化 Redis 快照，使首页热点榜单和雷达列表不再查询 SQLite。

**Architecture:** 生产链路仍持久化事件以维持 Agent、详情和历史的外键语义，但快照服务只消费本轮 ProductionResult.events。缓存仓储先将四个 JSON 视图写到下一 revision，再激活 revision 并发送现有 SSE 通知；读取控制器只能读取快照，缺失时返回空视图。

**Tech Stack:** Java 8、Spring Boot 2.7、Spring Data Redis、Jackson、JUnit 5、Mockito、React、TypeScript、Vitest。

---

## File structure

- Modify: backend/finscope-dao/src/main/java/com/finscope/dao/cache/VersionedViewCacheRepository.java and RedisVersionedViewCacheRepository.java — 支持写入指定 revision 和在全部快照写完后激活 revision。
- Modify: backend/finscope-service/src/main/java/com/finscope/service/cache/ViewSnapshotCacheService.java and ViewRevisionService.java — 提供只读 JSON、批量预热和指定 revision 的发布接口。
- Create: backend/finscope-service/src/main/java/com/finscope/service/radar/RadarSnapshotProjectionService.java — 从一轮已生成事件构造默认雷达视图及三类首页榜单，不回查 Repository。
- Modify: RadarHotspotRefreshService.java — 在生产成功后调用投影服务并仅在预热成功时发布 revision。
- Modify: DashboardService.java, DashboardController.java, ResearchRadarService.java, ResearchRadarController.java — 拆出热点端点，页面列表只读取快照。
- Modify: frontend/src/App.tsx, DashboardView.tsx, shared/types/index.ts — 首页概览与榜单并行加载。

### Task 1: 增加可预写的版本化缓存协议

**Files:**
- Modify: backend/finscope-dao/src/main/java/com/finscope/dao/cache/VersionedViewCacheRepository.java
- Modify: backend/finscope-dao/src/main/java/com/finscope/dao/cache/RedisVersionedViewCacheRepository.java
- Test: backend/finscope-dao/src/test/java/com/finscope/dao/cache/RedisVersionedViewCacheRepositoryTest.java

- [ ] **Step 1: Write the failing test**

\`\`\`java
@Test
void writesTheNextRevisionBeforeMakingItCurrent() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("finscope:view:radar:revision")).thenReturn("7");
    RedisVersionedViewCacheRepository repository = new RedisVersionedViewCacheRepository(redisTemplate);

    assertEquals(8L, repository.nextRevision("radar"));
    assertTrue(repository.put("radar", 8L, "default", "{\"events\":[]}", Duration.ofSeconds(60)));
    repository.activateRevision("radar", 8L);

    verify(valueOperations).set(eq("finscope:view:radar:8:default"), eq("{\"events\":[]}"), eq(60000L), eq(TimeUnit.MILLISECONDS));
    verify(valueOperations).set("finscope:view:radar:revision", "8");
}
\`\`\`

- [ ] **Step 2: Run test to verify it fails**

Run: cd backend && mvn -pl finscope-dao -Dtest=RedisVersionedViewCacheRepositoryTest test

Expected: compilation failure because nextRevision, revision-aware put, and activateRevision do not exist.

- [ ] **Step 3: Write minimal implementation**

\`\`\`java
public interface VersionedViewCacheRepository {
    long nextRevision(String namespace);
    boolean put(String namespace, long revision, String variant, String payload, Duration ttl);
    void activateRevision(String namespace, long revision);
}

public long nextRevision(String namespace) { return currentRevision(namespace) + 1L; }
public boolean put(String namespace, long revision, String variant, String payload, Duration ttl) {
    redisTemplate.opsForValue().set(snapshotKey(namespace, revision, variant), payload, ttl.toMillis(), TimeUnit.MILLISECONDS);
    return true;
}
public void activateRevision(String namespace, long revision) {
    redisTemplate.opsForValue().set(revisionKey(namespace), String.valueOf(revision));
}
\`\`\`

Keep the existing put(namespace, variant, ...) as a compatibility default that writes at currentRevision(namespace); the noop implementation returns false for the revision-aware method.

- [ ] **Step 4: Run test to verify it passes**

Run: cd backend && mvn -pl finscope-dao -Dtest=RedisVersionedViewCacheRepositoryTest test

Expected: exit code 0.

- [ ] **Step 5: Commit**

\`\`\`bash
git add backend/finscope-dao/src/main/java/com/finscope/dao/cache backend/finscope-dao/src/test/java/com/finscope/dao/cache
git commit -m "feat: 支持预写页面缓存版本"
\`\`\`

### Task 2: 从生产结果直接物化四个页面快照

**Files:**
- Create: backend/finscope-service/src/main/java/com/finscope/service/radar/RadarSnapshotProjectionService.java
- Modify: backend/finscope-service/src/main/java/com/finscope/service/dashboard/DashboardHotspotRankingService.java
- Modify: backend/finscope-service/src/main/java/com/finscope/service/cache/ViewSnapshotCacheService.java
- Modify: backend/finscope-service/src/main/java/com/finscope/service/cache/ViewRevisionService.java
- Test: backend/finscope-service/src/test/java/com/finscope/service/radar/RadarSnapshotProjectionServiceTest.java

- [ ] **Step 1: Write the failing test**

\`\`\`java
@Test
void prewarmsDefaultRadarAndThreeDashboardBoardsFromProductionEvents() {
    RadarEvent finance = event(1L, "FINANCE", 90, 80);
    RadarEvent technology = event(2L, "TECHNOLOGY", 80, 70);
    RadarEvent politics = event(3L, "POLITICS", 70, 60);

    assertTrue(projection.prewarm(Arrays.asList(finance, technology, politics), successRun()));

    verify(cache).put(eq("radar"), eq(1L), eq("category=ALL&watchlist=false&limit=20&state=ALL"), contains("events"), any());
    verify(cache).put(eq("dashboard"), eq(1L), eq("hotspots"), contains("FINANCE"), any());
    verify(revisions).publish("radar", 1L, successRun().getCompletedAt());
    verify(revisions).publish("dashboard", 1L, successRun().getCompletedAt());
}
\`\`\`

- [ ] **Step 2: Run test to verify it fails**

Run: cd backend && mvn -pl finscope-service -Dtest=RadarSnapshotProjectionServiceTest test

Expected: compilation failure because the projection service does not exist.

- [ ] **Step 3: Write minimal implementation**

\`\`\`java
public boolean prewarm(List<RadarEvent> events, RadarRefreshRun run) {
    long radarRevision = cache.nextRevision("radar");
    long dashboardRevision = cache.nextRevision("dashboard");
    ResearchRadarView radar = views.defaultView(events, run);
    List<DashboardHotspotRankingService.Ranking> boards = rankings.rankings(events);
    boolean written = snapshots.write("radar", radarRevision, DEFAULT_RADAR_VARIANT, radar, TTL)
            && snapshots.write("dashboard", dashboardRevision, "hotspots", boards, TTL);
    if (!written) return false;
    revisions.publish("radar", radarRevision, run.getCompletedAt());
    revisions.publish("dashboard", dashboardRevision, run.getCompletedAt());
    return true;
}
\`\`\`

rankings(events) filters ACTIVE and QUIET events, groups by dashboard category, sorts by hotspotScore DESC, lastSeenAt DESC, id DESC, and takes five. The default radar projection sorts by priority, derives latest changes, and creates ResearchRadarView.EventCard directly from each in-memory event.

- [ ] **Step 4: Run test to verify it passes**

Run: cd backend && mvn -pl finscope-service -Dtest=RadarSnapshotProjectionServiceTest test

Expected: exit code 0.

- [ ] **Step 5: Commit**

\`\`\`bash
git add backend/finscope-service/src/main/java/com/finscope/service/cache backend/finscope-service/src/main/java/com/finscope/service/radar backend/finscope-service/src/main/java/com/finscope/service/dashboard backend/finscope-service/src/test/java/com/finscope/service/radar
git commit -m "feat: 生产结果直接预热雷达首页快照"
\`\`\`

### Task 3: 生产完成后发布快照，不再仅失效缓存

**Files:**
- Modify: backend/finscope-service/src/main/java/com/finscope/service/radar/RadarHotspotRefreshService.java
- Test: backend/finscope-service/src/test/java/com/finscope/service/radar/RadarHotspotRefreshServiceTest.java

- [ ] **Step 1: Write the failing test**

\`\`\`java
@Test
void publishesOnlyAfterTheCompleteProductionResultHasBeenPrewarmed() {
    when(pipeline.run(any(), any(), any())).thenReturn(result());
    when(projection.prewarm(result().getEvents(), result().getRun())).thenReturn(true);

    assertTrue(service.requestScheduledRefresh());

    verify(projection).prewarm(result().getEvents(), result().getRun());
    verify(revisions, never()).invalidate(any(), any());
}
\`\`\`

- [ ] **Step 2: Run test to verify it fails**

Run: cd backend && mvn -pl finscope-service -Dtest=RadarHotspotRefreshServiceTest test

Expected: compilation failure or Mockito verification failure because refresh only invalidates today.

- [ ] **Step 3: Write minimal implementation**

\`\`\`java
RadarHotspotProductionPipeline.ProductionResult result = pipeline.run("ALL", triggerType, now());
if (result != null && snapshots != null) {
    snapshots.prewarm(result.getEvents(), result.getRun());
}
\`\`\`

Remove the failure-path invalidation: on an unsuccessful production run the former complete Redis revision remains readable.

- [ ] **Step 4: Run test to verify it passes**

Run: cd backend && mvn -pl finscope-service -Dtest=RadarHotspotRefreshServiceTest test

Expected: exit code 0.

- [ ] **Step 5: Commit**

\`\`\`bash
git add backend/finscope-service/src/main/java/com/finscope/service/radar/RadarHotspotRefreshService.java backend/finscope-service/src/test/java/com/finscope/service/radar/RadarHotspotRefreshServiceTest.java
git commit -m "feat: 生产完成后发布热点快照"
\`\`\`

### Task 4: 将列表接口限制为快照读取并拆出首页榜单接口

**Files:**
- Modify: backend/finscope-service/src/main/java/com/finscope/service/dashboard/DashboardService.java
- Modify: backend/finscope-web/src/main/java/com/finscope/web/controller/DashboardController.java
- Modify: backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarService.java
- Modify: backend/finscope-web/src/main/java/com/finscope/web/controller/ResearchRadarController.java
- Test: backend/finscope-web/src/test/java/com/finscope/web/controller/ResearchRadarApiIntegrationTest.java
- Create: backend/finscope-web/src/test/java/com/finscope/web/controller/DashboardHotspotControllerTest.java

- [ ] **Step 1: Write the failing tests**

\`\`\`java
@Test
void dashboardHotspotsReadsThePrewarmedPayload() throws Exception {
    when(snapshots.read("dashboard", "hotspots")).thenReturn(Optional.of(mapper.readTree("[{\"categoryCode\":\"FINANCE\",\"items\":[]}]")));
    mvc.perform(get("/api/dashboard/hotspots"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].categoryCode").value("FINANCE"));
    verifyNoInteractions(dashboardService);
}

@Test
void radarCacheMissReturnsAnEmptyViewWithoutLoadingStoredEvents() throws Exception {
    when(snapshots.read("radar", "category=ALL&watchlist=false&limit=20&state=ALL")).thenReturn(Optional.empty());
    mvc.perform(get("/api/research-radar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.events").isEmpty());
    verify(service, never()).loadStored(any(), anyBoolean(), anyInt(), any());
}
\`\`\`

- [ ] **Step 2: Run tests to verify they fail**

Run: cd backend && mvn -pl finscope-web -am -Dtest=DashboardHotspotControllerTest,ResearchRadarApiIntegrationTest test

Expected: compilation failure because read and /api/dashboard/hotspots do not exist, or verification failure because radar invokes loadStored.

- [ ] **Step 3: Write minimal implementation**

\`\`\`java
@GetMapping("/hotspots")
public ApiResponse<JsonNode> hotspots() {
    return ApiResponses.success(snapshots.read("dashboard", "hotspots").orElseGet(() -> mapper.createArrayNode()));
}

JsonNode cached = snapshots.read("radar", variant)
        .orElseGet(() -> mapper.valueToTree(service.emptyStored()));
return ApiResponses.success(cached);
\`\`\`

Remove hotspotRankings from DashboardService.summary(). ResearchRadarService.emptyStored() computes only refresh status and an empty ResearchRadarView; it must not call RadarRepository.

- [ ] **Step 4: Run tests to verify they pass**

Run: cd backend && mvn -pl finscope-web -am -Dtest=DashboardHotspotControllerTest,ResearchRadarApiIntegrationTest test

Expected: exit code 0.

- [ ] **Step 5: Commit**

\`\`\`bash
git add backend/finscope-service/src/main/java/com/finscope/service/dashboard backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarService.java backend/finscope-web/src/main/java/com/finscope/web/controller backend/finscope-web/src/test/java/com/finscope/web/controller
git commit -m "feat: 拆分首页热点快照读取接口"
\`\`\`

### Task 5: 首页并行加载独立榜单

**Files:**
- Modify: frontend/src/shared/types/index.ts
- Modify: frontend/src/App.tsx
- Modify: frontend/src/features/dashboard/DashboardView.tsx
- Test: frontend/src/App.test.tsx

- [ ] **Step 1: Write the failing test**

\`\`\`ts
test('loads homepage summary and hotspot rankings from independent endpoints', async () => {
  render(<App />);
  await screen.findByRole('heading', { name: '今日热点' });
  expect(fetch).toHaveBeenCalledWith('/api/dashboard', expect.anything());
  expect(fetch).toHaveBeenCalledWith('/api/dashboard/hotspots', expect.anything());
  expect(screen.getByText('央行宣布下调存款准备金率')).toBeInTheDocument();
});
\`\`\`

- [ ] **Step 2: Run test to verify it fails**

Run: cd frontend && npm test -- App.test.tsx

Expected: failure because no standalone hotspot request is made.

- [ ] **Step 3: Write minimal implementation**

\`\`\`ts
const [hotspotRankings, setHotspotRankings] = useState<DashboardHotspotRanking[]>([]);
const results = await Promise.allSettled([
  api<Dashboard>('/api/dashboard'),
  api<DashboardHotspotRanking[]>('/api/dashboard/hotspots'),
  // existing requests
]);
\`\`\`

Pass hotspotRankings to DashboardView, and remove hotspotRankings from the Dashboard type and from dashboard.hotspotRankings access. The dashboard revision callback refreshes both endpoints with Promise.all.

- [ ] **Step 4: Run test to verify it passes**

Run: cd frontend && npm test -- App.test.tsx && npm run build

Expected: both commands exit 0.

- [ ] **Step 5: Commit**

\`\`\`bash
git add frontend/src/App.tsx frontend/src/App.test.tsx frontend/src/features/dashboard/DashboardView.tsx frontend/src/shared/types/index.ts
git commit -m "feat: 首页独立读取热点榜单"
\`\`\`

### Task 6: 端到端验证与交付

- [ ] **Step 1: Run backend verification**

Run: cd backend && mvn test

Expected: exit code 0.

- [ ] **Step 2: Run frontend verification**

Run: cd frontend && npm test && npm run build

Expected: both commands exit 0.

- [ ] **Step 3: Inspect the final diff**

Run: git diff main...HEAD --check && git status --short

Expected: no whitespace errors and no uncommitted implementation files.

- [ ] **Step 4: Push the verified commits**

\`\`\`bash
git push
\`\`\`
