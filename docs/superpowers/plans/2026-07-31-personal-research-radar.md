# Personal Research Radar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有 News Wire 升级为零策略配置的个人研究雷达，聚合跨来源实时资讯，按固定可解释规则排序，并说明与自选标的的直接关系、判断依据和信息缺口。

**Architecture:** 保留 `/api/news` 原契约，新增短期 `radar_signal/radar_event/radar_event_signal` 持久化层和 `/api/research-radar` 组合用例。第一版使用确定性文本特征、标题相似度和固定五因子评分；`EventCluster` 继续只承载长期研究资产。前端沿用 `news` View，将导航改名为“研究雷达”，主栏展示聚合事件，侧栏保留实时快讯。

**Tech Stack:** Java 8、Spring Boot 2.7、JdbcTemplate、SQLite、JUnit 5、Mockito、React 18、TypeScript、Vite、Vitest、Testing Library。

---

## 文件结构

### 后端新增

- `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarSignal.java`：短期资讯信号。
- `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarEvent.java`：聚合事件和分项解释。
- `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarEventSignal.java`：事件与信号关系。
- `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarRepository.java`：三张表的幂等写入、事务刷新和查询。
- `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarTextAnalyzer.java`：规范化、主体/动作/变量提取与相似度。
- `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarClusteringService.java`：保守聚合、稳定标题和归并解释。
- `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarPriorityService.java`：固定五因子评分和自选直接关联。
- `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarView.java`：页面组合读模型。
- `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarService.java`：刷新锁、采集、聚合、降级和组合查询。
- `backend/finscope-web/src/main/java/com/finscope/web/controller/ResearchRadarController.java`：统一信封 API。

### 后端修改

- `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`：创建雷达表和索引。
- `backend/finscope-service/src/main/java/com/finscope/service/news/NewsFeedItem.java`：保持现有字段，不增加雷达语义。
- `README.md`、`docs/架构说明.md`：记录短期雷达与长期事件边界。

### 前端新增或修改

- `frontend/src/features/news/researchRadarTypes.ts`：雷达 API 类型。
- `frontend/src/features/news/RadarEventCard.tsx`：小白可读事件卡和依据展开。
- `frontend/src/features/news/NewsView.tsx`：改为研究雷达容器，保留轮询竞态保护。
- `frontend/src/features/news/NewsView.test.tsx`：雷达、筛选、详情、降级和预填测试。
- `frontend/src/app/AppShell.tsx`、`frontend/src/App.tsx`：导航和标题改名，传递研究问题预填动作。
- `frontend/src/features/research/ResearchView.tsx`：接收可选预填问题并只填充表单，不自动运行。
- `frontend/src/styles.css`：雷达概览、卡片、原因和详情样式。

---

### Task 1: 雷达 Schema、领域对象和持久化

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarSignal.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarEvent.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarEventSignal.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarRepository.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/radar/RadarRepositoryTest.java`

- [ ] **Step 1: 写失败的 Repository 测试**

测试必须覆盖同一 `itemId` 幂等、分类补全、事件关系往返、过期和稳定事件键：

```java
repository.capture(signal("CLS:1", "宁德时代发布新电池", null), now);
repository.capture(signal("CLS:1", "宁德时代发布新电池", "COMPANY"), now.plusMinutes(1));
assertEquals(1, repository.findActiveSignals(now.minusHours(48), 500).size());
assertEquals("COMPANY", repository.findActiveSignals(now.minusHours(48), 500).get(0).getCategoryCode());

RadarEvent saved = repository.saveEvent(event("COMPANY:宁德时代:发布:电池"));
repository.replaceEventSignals(saved.getId(), singletonList(link(saved.getId(), signalId)));
assertEquals(1, repository.findSignalsByEventId(saved.getId()).size());
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && mvn -pl finscope-dao -am -Dtest=RadarRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，原因是雷达领域对象或 Repository 尚不存在。

- [ ] **Step 3: 增加 Schema**

在 `DatabaseInitializer` 的资讯分类表之后创建：

```sql
CREATE TABLE IF NOT EXISTS radar_signal (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  item_id TEXT NOT NULL UNIQUE,
  provider_code TEXT,
  source_name TEXT,
  source_tier TEXT,
  category_code TEXT,
  title TEXT NOT NULL,
  content TEXT,
  url TEXT,
  published_at TEXT,
  first_seen_at TEXT NOT NULL,
  last_seen_at TEXT NOT NULL,
  content_hash TEXT NOT NULL,
  status TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS radar_event (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  event_key TEXT NOT NULL UNIQUE,
  canonical_title TEXT NOT NULL,
  summary TEXT,
  category_code TEXT,
  status TEXT NOT NULL,
  first_seen_at TEXT NOT NULL,
  last_seen_at TEXT NOT NULL,
  source_count INTEGER NOT NULL DEFAULT 0,
  signal_count INTEGER NOT NULL DEFAULT 0,
  priority_score INTEGER NOT NULL DEFAULT 0,
  score_explanation TEXT,
  watchlist_relevance INTEGER NOT NULL DEFAULT 0,
  watchlist_explanation TEXT,
  uncertainty TEXT,
  next_observation TEXT,
  updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS radar_event_signal (
  event_id INTEGER NOT NULL,
  signal_id INTEGER NOT NULL,
  relation_type TEXT NOT NULL,
  match_score REAL NOT NULL,
  match_reason TEXT,
  PRIMARY KEY(event_id, signal_id),
  FOREIGN KEY(event_id) REFERENCES radar_event(id) ON DELETE CASCADE,
  FOREIGN KEY(signal_id) REFERENCES radar_signal(id) ON DELETE CASCADE
);
```

同时增加 `status/last_seen_at`、`category_code/priority_score` 和 `signal_id` 索引。

- [ ] **Step 4: 实现领域对象与 Repository**

领域对象使用 JavaBean，不引入 Lombok。`capture` 使用 `INSERT ... ON CONFLICT(item_id) DO UPDATE`，只能更新可补全字段和 `last_seen_at`，不能改变 `first_seen_at`。时间统一通过 `TimeUtil`。

`RadarRepository` 至少提供：

```java
RadarSignal capture(RadarSignal signal, LocalDateTime now);
List<RadarSignal> findActiveSignals(LocalDateTime since, int limit);
RadarEvent saveEvent(RadarEvent event);
void replaceEventSignals(Long eventId, List<RadarEventSignal> links);
List<RadarEvent> findRanked(String category, boolean watchlistOnly, int limit);
Optional<RadarEvent> findEvent(Long id);
List<RadarSignal> findSignalsByEventId(Long eventId);
void expireSignals(LocalDateTime before, LocalDateTime now);
void markEventsQuietOrExpired(Set<String> activeKeys, LocalDateTime activeSince);
```

- [ ] **Step 5: 运行测试并确认通过**

Run: `cd backend && mvn -pl finscope-dao -am -Dtest=RadarRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

- [ ] **Step 6: 提交并推送**

```bash
git add backend/finscope-domain backend/finscope-dao
git commit -m "feat: 增加研究雷达数据基础"
git push
```

### Task 2: 确定性文本分析与事件聚合

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarTextAnalyzer.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarClusteringService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarClusteringServiceTest.java`

- [ ] **Step 1: 写聚合行为测试**

至少固定以下样本：

```java
assertSameEvent("宁德时代发布新一代电池", "宁德时代新电池正式发布");
assertDifferentEvent("宁德时代发布新电池", "小米发布新款汽车");
assertDifferentEvent("美联储宣布维持利率", "中国央行开展逆回购");
assertEquals("AMBIGUOUS", service.decide(ambiguousA, ambiguousB).getReasonCode());
```

并验证跨来源相同事件合并、同源重复只保留一个独立来源、代表标题刷新后不抖动。

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=RadarClusteringServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，类不存在。

- [ ] **Step 3: 实现 `RadarTextAnalyzer`**

规范化规则必须确定：小写、全半角统一、去标点、折叠空白；主体从已知自选名称、常见机构和“公司/集团/银行/指数”前后文提取；动作限定为发布、公告、上涨、下跌、收购、签约、增持、减持、降息、加息、处罚等稳定词表。

提供：

```java
SignalFeatures analyze(String category, String title, String content);
double similarity(SignalFeatures left, SignalFeatures right);
boolean hasSubjectConflict(SignalFeatures left, SignalFeatures right);
String eventKey(SignalFeatures features);
```

相似度使用现有 `FingerprintService.titleSimilarity`，再叠加主体、动作和变量重合；分类冲突且无主体重合直接返回 0。

- [ ] **Step 4: 实现保守聚合**

固定阈值：`>= 0.78` 合并，`< 0.58` 分开，中间区间返回 `AMBIGUOUS` 并新建事件。禁止调用 LLM。

聚合返回 `ClusterResult`，包含事件、成员和每条关系的 `matchScore/matchReason`。代表信号按来源层级、正文完整度、发布时间排序；已有事件标题满足条件时继续复用。

- [ ] **Step 5: 运行测试并确认通过**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=RadarClusteringServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

- [ ] **Step 6: 提交并推送**

```bash
git add backend/finscope-service
git commit -m "feat: 增加雷达事件确定性聚合"
git push
```

### Task 3: 固定优先级、自选关联与解释

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarPriorityService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarPriorityServiceTest.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/instrument/WatchlistRepository.java`

- [ ] **Step 1: 写评分测试**

测试总分严格等于五项之和，且每项不超过上限：

```java
PriorityResult result = service.score(event, signals, watchlist("300750", "宁德时代"), now);
assertEquals(25, result.getWatchlistScore());
assertTrue(result.getReasons().contains("与自选「宁德时代」直接相关"));
assertEquals(result.componentTotal(), result.getTotalScore());
```

增加无自选、同源转载、缺少发布时间、单一低层级来源和明确代码命中的测试。

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=RadarPriorityServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL。

- [ ] **Step 3: 增加无行情副作用的自选读取**

直接复用 `WatchlistRepository.findByTypes(Arrays.asList("STOCK", "FUND"))`，禁止调用 `WatchlistService.listWithQuotes()`，避免雷达刷新附带行情网络请求。

- [ ] **Step 4: 实现五因子评分**

```text
新意 25 + 自选相关性 25 + 独立来源 20 + 来源质量 15 + 时效 15
```

自选只通过名称、证券代码和显式别名命中；不推断产业链。`PriorityResult` 保存分项、最多三个主要原因、不确定性和下一观察项。无直接关系时固定输出“未发现与当前自选标的的直接关系”。

- [ ] **Step 5: 运行测试并确认通过**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=RadarPriorityServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

- [ ] **Step 6: 提交并推送**

```bash
git add backend/finscope-service backend/finscope-dao
git commit -m "feat: 增加雷达研究优先级解释"
git push
```

### Task 4: 雷达刷新用例和 API

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarView.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarService.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/controller/ResearchRadarController.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/ResearchRadarServiceTest.java`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/ResearchRadarApiIntegrationTest.java`

- [ ] **Step 1: 写刷新与降级测试**

覆盖成功刷新、并发未获得锁返回最近结果、资讯失败返回最近结果、没有最近结果时仍返回 `liveItems=[]` 和 warning、未知分类返回参数错误。

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && mvn -pl finscope-web -am -Dtest=ResearchRadarServiceTest,ResearchRadarApiIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL。

- [ ] **Step 3: 实现组合读模型**

`ResearchRadarView` 包含：

```java
Overview overview;
List<EventCard> events;
List<NewsFeedItem> liveItems;
List<String> warnings;
LocalDateTime refreshedAt;
```

`EventCard` 必须包含总分、推荐级别、三个原因、自选解释、来源数、信号数、不确定性和 `suggestedResearchQuestion`。详情额外包含全部 `SignalView` 和归并原因。

- [ ] **Step 4: 实现 `ResearchRadarService`**

使用 `ReentrantLock.tryLock()`；成功路径为加载资讯、捕获信号、取最近 48 小时最多 500 条、聚合、评分、事务保存、组装结果。失败时从 Repository 读取最近成功持久化结果并追加 warning。不得吞掉参数错误。

- [ ] **Step 5: 实现 Controller**

```java
@GetMapping
ApiResponse<ResearchRadarView> radar(@RequestParam(defaultValue="ALL") String category,
                                     @RequestParam(defaultValue="false") boolean watchlistOnly,
                                     @RequestParam(defaultValue="20") int limit)

@GetMapping("/events/{id}")
ApiResponse<ResearchRadarView.EventDetail> detail(@PathVariable Long id)
```

- [ ] **Step 6: 运行测试并确认通过**

Run: `cd backend && mvn -pl finscope-web -am -Dtest=ResearchRadarServiceTest,ResearchRadarApiIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

- [ ] **Step 7: 提交并推送**

```bash
git add backend/finscope-service backend/finscope-web
git commit -m "feat: 提供个人研究雷达接口"
git push
```

### Task 5: News Wire 升级为研究雷达

**Files:**
- Create: `frontend/src/features/news/researchRadarTypes.ts`
- Create: `frontend/src/features/news/RadarEventCard.tsx`
- Modify: `frontend/src/features/news/NewsView.tsx`
- Modify: `frontend/src/features/news/NewsView.test.tsx`
- Modify: `frontend/src/app/AppShell.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/features/research/ResearchView.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: 改写前端测试为雷达契约**

Mock `/api/research-radar`，验证：

```tsx
expect(screen.getByRole('heading', { name: '今天值得关注' })).toBeInTheDocument();
expect(screen.getByText('与自选「宁德时代」直接相关')).toBeInTheDocument();
await userEvent.click(screen.getByRole('button', { name: '查看依据' }));
expect(screen.getByText('3 个独立来源共同报道')).toBeInTheDocument();
```

增加“与我相关”、warning、轮询迟到响应、小屏结构语义和“围绕此事研究只预填不运行”测试。

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd frontend && npm test -- --run src/features/news/NewsView.test.tsx`

Expected: FAIL。

- [ ] **Step 3: 增加类型和事件卡**

`ResearchRadarSnapshot` 与后端字段一致。`RadarEventCard` 默认显示推荐级别、标题、摘要、原因、自选解释和来源数；点击“查看依据”展开来源列表、不确定性和观察项。

- [ ] **Step 4: 重构 NewsView**

保留当前 45 秒轮询、请求序号和“发现 N 条新资讯”的竞态保护，但请求改为 `/api/research-radar`。主网格左侧“今天值得关注”，右侧“实时发生”；分类保留动态目录并增加“与我相关”。自动刷新不得打断阅读。

- [ ] **Step 5: 修改导航与研究预填**

`AppShell` 中 `news` 改为：

```ts
{ id: 'news', label: '研究雷达', hint: '市场与自选', code: 'RD' }
```

`App` 标题改为“研究雷达 · 市场与自选”。点击“围绕此事研究”时设置 `researchQuestionDraft` 后切换到 `research`；`ResearchView` 只初始化输入框，绝不自动调用创建运行接口。

- [ ] **Step 6: 增加响应式样式**

桌面使用 `minmax(0, 1.65fr) minmax(300px, .8fr)`；900px 以下单列并保持事件先于快讯。复用现有 CSS 变量，不增加组件库。

- [ ] **Step 7: 运行测试与构建**

Run: `cd frontend && npm test -- --run src/features/news/NewsView.test.tsx src/App.test.tsx`

Expected: PASS。

Run: `cd frontend && npm run build`

Expected: PASS，无 TypeScript 错误。

- [ ] **Step 8: 提交并推送**

```bash
git add frontend
git commit -m "feat: 将实时资讯升级为个人研究雷达"
git push
```

### Task 6: 聚类质量基线与文档

**Files:**
- Create: `backend/finscope-service/src/test/resources/radar/clustering-cases.json`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarClusteringEvaluationTest.java`
- Modify: `README.md`
- Modify: `docs/架构说明.md`

- [ ] **Step 1: 增加正负样本集**

JSON 每条包含 `leftTitle/rightTitle/expectedSame/reason`，至少五组应合并和五组不应合并，覆盖公司、宏观、行业和市场异动。

- [ ] **Step 2: 增加确定性离线评测**

测试计算 TP/FP/FN，要求误合并为 0，正样本召回率不低于 80%，失败时输出具体样本标题。

- [ ] **Step 3: 运行雷达后端回归**

Run: `cd backend && mvn -pl finscope-web -am -Dtest='*Radar*Test' -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

- [ ] **Step 4: 更新文档**

README 说明“研究雷达是短期发现层，不是公共热榜”；架构文档增加 `NewsFeed -> RadarSignal -> RadarEvent`，并强调显式研究后才进入长期 Event/Evidence。

- [ ] **Step 5: 提交并推送**

```bash
git add backend/finscope-service/src/test README.md docs/架构说明.md
git commit -m "test: 增加研究雷达聚类质量基线"
git push
```

### Task 7: 全量验证和交付检查

**Files:**
- Modify only if verification exposes a radar regression.

- [ ] **Step 1: 运行全部后端测试**

Run: `cd backend && mvn test`

Expected: BUILD SUCCESS。

- [ ] **Step 2: 运行全部前端测试和构建**

Run: `cd frontend && npm test -- --run`

Expected: PASS。

Run: `cd frontend && npm run build`

Expected: PASS。

- [ ] **Step 3: 检查改动边界**

Run: `git status --short && git diff main...HEAD --stat && git log --oneline main..HEAD`

Expected: 用户原有 `EastmoneyCapitalFlowProvider.java` 修改仍未暂存；雷达改动按批次提交；没有 API Key、数据库或运行数据进入提交。

- [ ] **Step 4: 推送最终分支**

Run: `git push -u github codex/personal-research-radar`

Expected: 分支与远端同步。
