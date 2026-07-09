# Intake 信息摄入工作流 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Sources 和 Article 之间新增 Intake 候选池，让所有抓取内容先经过 Agent 中文预审和人工打标，只有人工 Promote 后才进入 Article 并生成 Insight Card。

**Architecture:** 后端新增 `fetch_batch`、`intake_candidate`、repository、service、controller 和 Agent review 组件；现有 `SourceAdapterRegistry`、`RawItemSelector`、`ArticleIngestCoordinator` 继续作为抓取、筛选、正式入库骨架。前端新增 Intake tab，并增强 Sources tab 的源配置、手动 intake fetch 和最近批次展示。

**Tech Stack:** Java 8、Spring Boot 2.7、SQLite/JdbcTemplate、React 18、TypeScript、Vite、Vitest、JUnit 5、Spring MockMvc。

---

## 文件结构

后端新增：

- `backend/finscope-domain/src/main/java/com/finscope/domain/intake/FetchBatch.java`：抓取批次领域对象。
- `backend/finscope-domain/src/main/java/com/finscope/domain/intake/IntakeCandidate.java`：候选内容领域对象。
- `backend/finscope-domain/src/main/java/com/finscope/domain/intake/IntakeEnums.java`：批次状态、触发类型、Agent 建议、人工状态常量。
- `backend/finscope-domain/src/main/java/com/finscope/domain/intake/CandidateReview.java`：Agent 单条预审结构。
- `backend/finscope-dao/src/main/java/com/finscope/dao/intake/FetchBatchRepository.java`：`fetch_batch` 持久化。
- `backend/finscope-dao/src/main/java/com/finscope/dao/intake/IntakeCandidateRepository.java`：`intake_candidate` 持久化与查询。
- `backend/finscope-rpc/src/main/java/com/finscope/rpc/source/WebListSourceAdapter.java`：网页列表页抽取。
- `backend/finscope-service/src/main/java/com/finscope/service/intake/CandidateReviewAgent.java`：单条候选 Agent 预审与 fallback。
- `backend/finscope-service/src/main/java/com/finscope/service/intake/BatchSummaryAgent.java`：批次总结与 fallback。
- `backend/finscope-service/src/main/java/com/finscope/service/intake/IntakeService.java`：抓取、候选落库、预审、状态、Promote 编排。
- `backend/finscope-service/src/main/java/com/finscope/service/intake/IntakeScheduler.java`：固定时间调度。
- `backend/finscope-web/src/main/java/com/finscope/web/controller/IntakeController.java`：Intake REST API。
- `backend/finscope-web/src/main/java/com/finscope/web/request/UpdateIntakeCandidateStatusRequest.java`：人工状态更新请求。

后端修改：

- `backend/finscope-domain/src/main/java/com/finscope/domain/source/Source.java`：新增 `maxItemsPerRun`、`scheduleTimes`、`scheduledEnabled`。
- `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`：新增表和 source 列。
- `backend/finscope-dao/src/main/java/com/finscope/dao/source/SourceRepository.java`：读写新增 source 字段。
- `backend/finscope-web/src/main/java/com/finscope/web/controller/SourceController.java`：新增 `/api/sources/{id}/intake-fetch`。

前端新增：

- `frontend/src/features/intake/IntakeView.tsx`：候选审核工作台。

前端修改：

- `frontend/src/shared/types/index.ts`：新增 Intake 类型，扩展 Source。
- `frontend/src/app/AppShell.tsx`：新增 Intake 导航项。
- `frontend/src/App.tsx`：加载 batches/candidates 并渲染 Intake。
- `frontend/src/features/sources/SourcesView.tsx`：增强表单、编辑、删除、手动 intake fetch、最近批次。
- `frontend/src/App.test.tsx`：覆盖 Sources 配置、Intake 列表、状态更新、Promote。
- `frontend/src/styles.css`：新增 Intake/Sources 工作台样式。

---

### Task 1: 后端模型、schema 和 repository

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/intake/FetchBatch.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/intake/IntakeCandidate.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/intake/IntakeEnums.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/intake/CandidateReview.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/intake/FetchBatchRepository.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/intake/IntakeCandidateRepository.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/source/Source.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/source/SourceRepository.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/intake/IntakeRepositoryTest.java`

- [ ] **Step 1: 写失败测试**

新增 `IntakeRepositoryTest`，验证 source 新字段、batch 保存、candidate 保存、状态更新和按状态查询。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -nsu -pl finscope-dao -am -DfailIfNoTests=false -Dtest=IntakeRepositoryTest test`

Expected: FAIL，原因是 domain/repository/schema 尚不存在。

- [ ] **Step 3: 实现 domain、schema、repository**

新增 `fetch_batch` 和 `intake_candidate` 表；为 `source` 表补 `max_items_per_run`、`schedule_times`、`scheduled_enabled`；repository 使用 `JdbcTemplate` 和 `TimeUtil`，跟现有 DAO 风格一致。

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend && mvn -nsu -pl finscope-dao -am -DfailIfNoTests=false -Dtest=IntakeRepositoryTest test`

Expected: PASS。

### Task 2: Intake workflow、Agent fallback 和 Promote

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/intake/CandidateReviewAgent.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/intake/BatchSummaryAgent.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/intake/IntakeService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/SourceController.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/controller/IntakeController.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/request/UpdateIntakeCandidateStatusRequest.java`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/FinScopeApiIntegrationTest.java`

- [ ] **Step 1: 写失败集成测试**

在 `FinScopeApiIntegrationTest` 新增测试：

1. 创建带 `maxItemsPerRun=1` 的 RSS source。
2. 调用 `POST /api/sources/1/intake-fetch`。
3. 断言只产生 1 条候选。
4. 断言候选有中文 `decisionSummary` 和 `agentStatus=FALLBACK`。
5. 调用 `POST /api/intake/candidates/{id}/promote`。
6. 断言返回 `articleId`，Article 列表出现该文章，candidate 状态为 `PROMOTED`。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -nsu -pl finscope-web -am -DfailIfNoTests=false -Dtest=FinScopeApiIntegrationTest#intakeFetchCreatesReviewedCandidatesAndPromotesToArticle test`

Expected: FAIL，原因是 API 不存在。

- [ ] **Step 3: 实现 Intake workflow**

`IntakeService.intakeFetch(sourceId, triggerType)` 创建 batch，调用 adapter，执行 3 天过滤和 `maxItemsPerRun`，保存候选，运行 review fallback 或 LLM review，生成 batch summary，并记录兼容 `fetch_run`。

- [ ] **Step 4: 实现 Promote**

`IntakeService.promote(candidateId)` 从 candidate 构造 `RawItem`，调用 `ArticleIngestCoordinator.ingest(source, rawItem)`，写入 `promoted_article_id` 和 `human_status=PROMOTED`。重复 Promote 返回已有 article ID。

- [ ] **Step 5: 运行测试确认通过**

Run: `cd backend && mvn -nsu -pl finscope-web -am -DfailIfNoTests=false -Dtest=FinScopeApiIntegrationTest#intakeFetchCreatesReviewedCandidatesAndPromotesToArticle test`

Expected: PASS。

### Task 3: Web List adapter 和定时调度

**Files:**
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/source/WebListSourceAdapter.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/intake/IntakeScheduler.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/source/WebListSourceAdapterTest.java`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/FinScopeApiIntegrationTest.java`

- [ ] **Step 1: 写失败测试**

`WebListSourceAdapterTest` 用静态 HTML 验证列表页抽取多条绝对链接，并通过 `WebArticleExtractor` 产出 `RawItem`。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -nsu -pl finscope-rpc -am -DfailIfNoTests=false -Dtest=WebListSourceAdapterTest test`

Expected: FAIL，原因是 adapter 不存在。

- [ ] **Step 3: 实现 WEB_LIST adapter**

支持 `WEB_LIST` 类型；用 Jsoup 抓列表页，选择 `article a[href]`、`h1/h2/h3 a[href]`、`a[href]` 候选链接，去重后最多抓取前 20 条，单条内容复用 `WebArticleExtractor`。

- [ ] **Step 4: 实现 Scheduler**

Spring scheduled poller 每分钟扫描 `scheduledEnabled=true` 且当前 `HH:mm` 命中的 source；通过当天同 source/time slot 的 batch 去重后触发 `IntakeService.intakeFetch(sourceId, "SCHEDULED")`。

- [ ] **Step 5: 运行测试确认通过**

Run: `cd backend && mvn -nsu -pl finscope-rpc -am -DfailIfNoTests=false -Dtest=WebListSourceAdapterTest test`

Expected: PASS。

### Task 4: 前端 Sources 增强和 Intake tab

**Files:**
- Create: `frontend/src/features/intake/IntakeView.tsx`
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/app/AppShell.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/features/sources/SourcesView.tsx`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/App.test.tsx`

- [ ] **Step 1: 写失败前端测试**

新增/扩展测试：

1. Sources 页面展示 `每次抓取条数`、`每天抓取时间`、`开启定时抓取`。
2. 点击 Intake 导航后展示候选中文标题、分数、决策摘要、核心事实。
3. 点击“入文章库”调用 `/api/intake/candidates/{id}/promote` 并显示 Article ID。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd frontend && npm test -- App.test.tsx`

Expected: FAIL，原因是 Intake 导航和 UI 不存在。

- [ ] **Step 3: 实现类型、App 数据加载和导航**

新增 `FetchBatch`、`IntakeCandidate` 类型；App 加载 `/api/intake/batches` 和 `/api/intake/candidates?status=PENDING`；AppShell 增加 Intake nav item。

- [ ] **Step 4: 实现 SourcesView**

支持新增/编辑/删除 source，配置 `maxItemsPerRun`、`scheduleTimes`、`scheduledEnabled`，手动调用 `/api/sources/{id}/intake-fetch`。

- [ ] **Step 5: 实现 IntakeView**

显示批次列表、批次 summary、候选卡片、状态过滤和操作按钮；Promote 后刷新候选和文章数据。

- [ ] **Step 6: 运行测试确认通过**

Run: `cd frontend && npm test -- App.test.tsx`

Expected: PASS。

### Task 5: 全量验证和收口

**Files:**
- Modify as needed based on test results.

- [ ] **Step 1: 后端全量测试**

Run: `cd backend && mvn test`

Expected: PASS。

- [ ] **Step 2: 前端全量测试和 build**

Run: `cd frontend && npm test`

Expected: PASS。

Run: `cd frontend && npm run build`

Expected: PASS。

- [ ] **Step 3: 验收清单**

逐条核对：

1. Sources 可以配置类型、URL、每次抓取条数、是否开启定时、每天定时点。
2. 手动 intake fetch 能创建 fetch batch 和 candidate records。
3. Candidates 经过 Agent 或 deterministic fallback 预审，并以中文展示。
4. Intake tab 支持过滤和人工状态修改。
5. 没有 candidate 会自动进入 Article。
6. 人工 Promote 能创建正常 Article 和 Insight Card。
7. 抓取、预审、Promote 失败都可见且可恢复。
8. 自动化测试覆盖核心后端和前端路径。

## 自检

1. Spec 覆盖：本计划覆盖中文设计中的 Source 配置、fetch batch、intake candidate、Agent 预审、批次总结、Intake API、Sources/Intake 前端、定时调度、Promote、测试验收。
2. 无未完成占位符。
3. 类型命名保持一致：`FetchBatch`、`IntakeCandidate`、`CandidateReview`、`humanStatus`、`agentRecommendation`、`maxItemsPerRun`、`scheduleTimes`、`scheduledEnabled`。
