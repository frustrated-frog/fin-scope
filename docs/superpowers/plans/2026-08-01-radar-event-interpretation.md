# Radar Event Interpretation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不修改雷达分类、聚类和评分的前提下，将雷达页重构为“最新变化 + 高优先级事件”，并提供不阻塞页面的按需异步事件解读。

**Architecture:** 雷达列表继续走现有同步确定性链路，只新增轻量数据库状态读取。事件详情立即返回基础信息；单独的 POST 接口幂等提交低并发后台解读任务，结果按事件内容指纹持久化，前端只轮询当前打开的详情抽屉。

**Tech Stack:** Java 8、Spring Boot 2.7、JdbcTemplate、SQLite、Jackson、React、TypeScript、Vitest、Testing Library

---

## File Structure

- `backend/finscope-domain/.../radar/RadarEventInterpretation.java`：解读任务、结果和生命周期模型。
- `backend/finscope-dao/.../radar/RadarEventInterpretationRepository.java`：按事件版本保存、更新和批量查询解读。
- `backend/finscope-dao/.../config/DatabaseInitializer.java`：新增解读表与索引。
- `backend/finscope-service/.../radar/RadarEventInterpretationAgent.java`：严格 JSON 解读和字段校验。
- `backend/finscope-service/.../radar/RadarEventInterpretationService.java`：指纹、幂等提交、异步执行和缓存复用。
- `backend/finscope-service/.../radar/ResearchRadarService.java`：组装最近变化与详情解读，不改变分类、聚类和评分。
- `backend/finscope-service/.../radar/ResearchRadarView.java`：扩展列表和详情 DTO。
- `backend/finscope-web/.../controller/ResearchRadarController.java`：新增解读提交接口。
- `backend/finscope-web/.../config/AppConfig.java`：独立低并发执行器。
- `frontend/src/features/news/NewsView.tsx`：自动应用快照、全宽双区布局。
- `frontend/src/features/news/RadarEventCard.tsx`：收敛为列表卡与解读入口。
- `frontend/src/features/news/RadarEventDetailDrawer.tsx`：异步解读、证据和轨迹详情。
- `frontend/src/features/news/researchRadarTypes.ts`：新增最近变化和解读类型。
- `frontend/src/styles.css`：双区、变化条和详情抽屉样式。

### Task 1: 解读持久化模型

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarEventInterpretation.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarEventInterpretationRepository.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Create: `backend/finscope-dao/src/test/java/com/finscope/dao/radar/RadarEventInterpretationRepositoryTest.java`

- [ ] **Step 1: Write the failing repository test**

Create an in-memory SQLite test that initializes:

```sql
CREATE TABLE radar_event_interpretation(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  event_id INTEGER NOT NULL,
  event_fingerprint TEXT NOT NULL,
  status TEXT NOT NULL,
  result_json TEXT,
  failure_code TEXT,
  failure_message TEXT,
  duration_ms INTEGER,
  created_at TEXT NOT NULL,
  started_at TEXT,
  completed_at TEXT,
  UNIQUE(event_id,event_fingerprint)
)
```

Assert that saving the same `(eventId, eventFingerprint)` twice reuses one row, a completed result round-trips through Jackson, and `findLatestByEventIds` returns one latest row per event.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `cd backend && mvn -pl finscope-dao -Dtest=RadarEventInterpretationRepositoryTest test`

Expected: compilation failure because the model and repository do not exist.

- [ ] **Step 3: Implement the model, repository and schema**

Use these lifecycle values: `QUEUED`, `RUNNING`, `SUCCESS`, `FAILED`, `UNAVAILABLE`. The result contains:

```java
private String factSummary;
private String newDevelopment;
private String whyItMatters;
private List<String> impactChain = new ArrayList<String>();
private List<String> uncertainties = new ArrayList<String>();
private List<String> nextObservations = new ArrayList<String>();
private List<String> evidenceRefs = new ArrayList<String>();
```

Use `INSERT ... ON CONFLICT(event_id,event_fingerprint) DO NOTHING`, select the persisted row afterward, and serialize only `result_json` with the injected `ObjectMapper`.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `cd backend && mvn -pl finscope-dao -Dtest=RadarEventInterpretationRepositoryTest test`

Expected: `BUILD SUCCESS` with zero failures.

- [ ] **Step 5: Commit and push**

```bash
git add backend/finscope-domain backend/finscope-dao
git commit -m "feat: 持久化雷达事件解读"
git push
```

### Task 2: 严格事件解读 Agent

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarEventInterpretationAgent.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarEventInterpretationAgentTest.java`

- [ ] **Step 1: Write failing Agent tests**

Cover a valid JSON result, unsupported evidence references, investment advice text, unknown JSON fields, and unconfigured LLM. The valid response must resemble:

```json
{
  "factSummary":"公司发布新产品，两家来源确认发布事实。",
  "newDevelopment":"新增量产时间信息。",
  "whyItMatters":"量产节奏可能影响相关产业链订单预期。",
  "impactChain":["产品发布→量产验证→供应链订单"],
  "uncertainties":["价格尚未披露"],
  "nextObservations":["观察公司正式公告"],
  "evidenceRefs":["signal:1","evidence:31"]
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `cd backend && mvn -pl finscope-service -Dtest=RadarEventInterpretationAgentTest test`

Expected: compilation failure because `RadarEventInterpretationAgent` does not exist.

- [ ] **Step 3: Implement strict parsing and validation**

Build a payload from the event, signals and evidence with stable refs (`signal:<id>`, `evidence:<id>`). Reject blank required text, more than eight entries per list, list entries longer than 180 characters, refs outside the input set, and the terms `买入`, `卖出`, `加仓`, `减仓`, `目标价`. Record `radar-event-interpretation` through `RadarAgentTraceRecorder` without raw prompts.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `cd backend && mvn -pl finscope-service -Dtest=RadarEventInterpretationAgentTest test`

Expected: all Agent tests pass.

- [ ] **Step 5: Commit and push**

```bash
git add backend/finscope-service
git commit -m "feat: 增加雷达事件解读Agent"
git push
```

### Task 3: 按需异步编排与接口

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarEventInterpretationService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarView.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/ResearchRadarController.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/config/AppConfig.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarEventInterpretationServiceTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/radar/ResearchRadarServiceTest.java`
- Modify: `backend/finscope-web/src/test/java/com/finscope/web/controller/ResearchRadarApiIntegrationTest.java`

- [ ] **Step 1: Write failing service and API tests**

Assert that `request(eventId)` returns `QUEUED` before a blocked fake Agent completes, duplicate requests schedule once, a matching successful fingerprint is reused, model failure updates `FAILED`, and `POST /api/research-radar/events/{id}/interpretation` delegates to the async service and returns the unified envelope.

Add a radar view assertion that `latestChanges` is time-descending while `events` remains score-descending. Assert `liveItems` is empty in the radar response.

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```bash
cd backend && mvn -pl finscope-service -Dtest=RadarEventInterpretationServiceTest,ResearchRadarServiceTest test
cd backend && mvn -pl finscope-web -am -Dtest=ResearchRadarApiIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: failures for missing service, endpoint and DTO fields.

- [ ] **Step 3: Implement the async boundary**

Add a dedicated executor:

```java
@Bean(name = "radarInterpretationExecutor")
public Executor radarInterpretationExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("radar-interpretation-");
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(12);
    executor.initialize();
    return executor;
}
```

`request` must load only persisted radar data, compute SHA-256 from event title/summary plus sorted signal content hashes and evidence fields, persist `QUEUED`, and submit the task. It must never be called from `ResearchRadarService.load`.

Expose:

```java
@PostMapping("/events/{id}/interpretation")
public ApiResponse<ResearchRadarView.InterpretationView> requestInterpretation(@PathVariable Long id)
```

The detail GET includes the latest interpretation, marks it stale when its fingerprint differs from the current fingerprint, and otherwise remains non-blocking.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the two commands from Step 2 and expect zero failures.

- [ ] **Step 5: Commit and push**

```bash
git add backend
git commit -m "feat: 接通雷达异步事件解读"
git push
```

### Task 4: 雷达双区页面与解读抽屉

**Files:**
- Modify: `frontend/src/features/news/researchRadarTypes.ts`
- Modify: `frontend/src/features/news/NewsView.tsx`
- Modify: `frontend/src/features/news/RadarEventCard.tsx`
- Create: `frontend/src/features/news/RadarEventDetailDrawer.tsx`
- Modify: `frontend/src/features/news/NewsView.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Write failing UI tests**

Update fixtures with `latestChanges`, `changeType`, `changeSummary`, `interpretationStatus`, and a detail interpretation. Assert:

```typescript
expect(screen.getByRole('heading', { name: '最新变化' })).toBeInTheDocument();
expect(screen.getByRole('heading', { name: '高优先级事件' })).toBeInTheDocument();
expect(screen.queryByRole('heading', { name: '实时发生' })).not.toBeInTheDocument();
```

With fake timers, make the second poll return a new event and assert it appears without clicking a pending-update button. Opening “查看解读” must immediately show the drawer, submit `POST /api/research-radar/events/10/interpretation` only when status is absent/failed/stale, poll the detail while queued, then display `whyItMatters`, evidence links and sanitized Agent trace.

- [ ] **Step 2: Run UI tests and verify RED**

Run: `cd frontend && npm test -- --run src/features/news/NewsView.test.tsx`

Expected: failures for missing headings, drawer and automatic snapshot application.

- [ ] **Step 3: Implement the page**

Remove `pendingSnapshot`, `pendingCount`, `liveItems` filtering and the right-hand live panel from `ResearchRadarPanel`. On every valid poll set both `snapshotRef.current` and `snapshot` directly.

Render `snapshot.latestChanges` in a compact time-descending strip and `snapshot.events` as the existing score-descending list. Move detail fetching and polling into `RadarEventDetailDrawer`; stop its timer on close/unmount or terminal status.

The drawer uses `role="dialog"`, `aria-modal="true"`, a labelled heading, a close button, and sections named “事件解读”, “证据来源” and “运行状态”.

- [ ] **Step 4: Run UI tests and production build**

Run:

```bash
cd frontend && npm test -- --run src/features/news/NewsView.test.tsx
cd frontend && npm run build
```

Expected: tests pass and Vite build exits 0.

- [ ] **Step 5: Commit and push**

```bash
git add frontend
git commit -m "feat: 丰富雷达事件解读界面"
git push
```

### Task 5: Full Regression Verification

**Files:**
- Modify only files required by failures directly caused by Tasks 1–4.

- [ ] **Step 1: Run backend regression suite**

Run: `cd backend && mvn test`

Expected: `BUILD SUCCESS`, zero failures.

- [ ] **Step 2: Run frontend regression suite and build**

Run:

```bash
cd frontend && npm test -- --run
cd frontend && npm run build
```

Expected: all Vitest tests pass and build exits 0.

- [ ] **Step 3: Verify scope and repository state**

Run:

```bash
git diff main...HEAD --check
git status --short --branch
git log --oneline main..HEAD
```

Confirm no classification, `RadarEventMatchAgent`, clustering or priority formula file was changed except test constructor wiring required by the new read-only downstream service.

- [ ] **Step 4: Commit any verification-only corrections and push**

If corrections were required:

```bash
git add backend/finscope-service backend/finscope-web frontend/src
git commit -m "fix: 修正雷达事件解读回归问题"
git push
```
