# News Classification Quality Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为实时资讯增加分类计数、低置信度待确认、可解释展示和人工纠错闭环，同时保留 Agent 原始判断。

**Architecture:** `news_item_classification` 保留现有 Agent 字段并增加独立人工复核字段；领域对象提供有效分类语义，DAO 负责原子保存人工结论。`NewsFeedService` 在每次资讯快照上计算分类统计并支持 `PENDING_REVIEW` 查询，独立复核服务校验分类目录后写入结果。前端在现有单轮询页面中增加统计徽标和原地复核控件。

**Tech Stack:** Java 8、Spring Boot 2.7、Spring JDBC、SQLite、JUnit 5、Mockito、React、TypeScript、Vitest、Testing Library。

---

## 文件结构

- 修改 `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`：兼容旧数据库增加复核字段与索引。
- 修改 `backend/finscope-domain/src/main/java/com/finscope/domain/news/NewsItemClassification.java`：表达 Agent 原始结果、人工结果和有效分类。
- 修改 `backend/finscope-dao/src/main/java/com/finscope/dao/news/NewsClassificationRepository.java`：读取复核字段、按阈值标记、保存人工复核。
- 新建 `backend/finscope-service/src/main/java/com/finscope/service/news/NewsClassificationReviewService.java`：复核用例及业务校验。
- 新建 `backend/finscope-service/src/main/java/com/finscope/service/news/NewsClassificationReviewRequest.java`：复核输入 DTO。
- 新建 `backend/finscope-service/src/main/java/com/finscope/service/news/NewsClassificationView.java`：复核响应 DTO。
- 修改 `backend/finscope-service/src/main/java/com/finscope/service/news/NewsFeedItem.java`：返回有效分类和完整可解释字段。
- 修改 `backend/finscope-service/src/main/java/com/finscope/service/news/NewsFeedSnapshot.java`：返回分类计数和待分类数量。
- 修改 `backend/finscope-service/src/main/java/com/finscope/service/news/NewsFeedService.java`：有效分类过滤、待确认过滤和快照统计。
- 修改 `backend/finscope-web/src/main/java/com/finscope/web/controller/NewsFeedController.java`：增加复核接口。
- 修改 `frontend/src/features/news/LiveNewsPanel.tsx`：数量徽标、解释栏、待确认切换和人工复核。
- 修改现有 DAO、Service、Controller 和前端测试文件，分别覆盖对应边界。

### Task 1: 持久化 Agent 原始判断与人工复核结果

**Files:**
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/news/NewsItemClassification.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/news/NewsClassificationRepository.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/news/NewsClassificationRepositoryTest.java`

- [ ] **Step 1: 写失败测试**

增加两个用例：`marksLowConfidenceItemsForReview()` 断言 `0.69` 得到 `PENDING_REVIEW`；`manualCorrectionPreservesAgentDecision()` 先写入 Agent 的 `COMPANY/0.65`，再执行 `review("INDUSTRY", "产业链影响")`，断言原始分类仍为 `COMPANY`，有效分类为 `INDUSTRY`，状态为 `CORRECTED`。

```java
classifications.markClassified("CLS:1", "COMPANY", 0.65, "公司公告", "model-a", now);
classifications.review("CLS:1", "INDUSTRY", "产业链影响", now.plusMinutes(1));
NewsItemClassification value = classifications.findByItemIds(Collections.singleton("CLS:1")).get("CLS:1");
assertEquals("COMPANY", value.getCategoryCode());
assertEquals("INDUSTRY", value.getEffectiveCategoryCode());
assertEquals("CORRECTED", value.getReviewStatus());
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -pl finscope-dao -am -Dtest=NewsClassificationRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，缺少 `review`、`reviewStatus` 或新构造参数。

- [ ] **Step 3: 实现兼容迁移和 DAO**

创建表时增加四列，并使用现有 `hasColumn(table, column)` 方式为旧库执行：

```sql
ALTER TABLE news_item_classification ADD COLUMN manual_category_code TEXT;
ALTER TABLE news_item_classification ADD COLUMN manual_reason TEXT;
ALTER TABLE news_item_classification ADD COLUMN review_status TEXT;
ALTER TABLE news_item_classification ADD COLUMN reviewed_at TEXT;
```

初始化后执行一次幂等回填：`CLASSIFIED` 且 `confidence < 0.70` 为 `PENDING_REVIEW`，其他已分类记录为 `AUTO_CONFIRMED`。`markClassified` 用 SQL `CASE` 保留已有人工结果；`review` 始终写入 `manual_category_code`，并根据它是否等于 Agent 分类写 `CONFIRMED` 或 `CORRECTED`。

领域对象新增：

```java
public String getEffectiveCategoryCode() {
    return manualCategoryCode == null ? categoryCode : manualCategoryCode;
}
public boolean isPendingReview() { return "PENDING_REVIEW".equals(reviewStatus); }
public boolean isManuallyReviewed() { return manualCategoryCode != null; }
```

- [ ] **Step 4: 运行 DAO 测试确认通过**

Run: `cd backend && mvn -pl finscope-dao -am -Dtest=NewsClassificationRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: `NewsClassificationRepositoryTest` 全部 PASS。

- [ ] **Step 5: 提交并推送**

```bash
git add backend/finscope-domain backend/finscope-dao
git commit -m "feat: 增加资讯分类人工复核存储"
git push github HEAD:codex/personal-research-radar
```

### Task 2: 增加复核服务与 HTTP 接口

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/news/NewsClassificationReviewRequest.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/news/NewsClassificationView.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/news/NewsClassificationReviewService.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/news/NewsClassificationReviewServiceTest.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/NewsFeedController.java`
- Modify: `backend/finscope-web/src/test/java/com/finscope/web/controller/NewsFeedControllerTest.java`

- [ ] **Step 1: 写失败测试**

服务测试覆盖有效纠正、分类记录不存在、记录未完成和分类停用。Controller 测试发送：

```json
{"itemId":"CLS:1","categoryCode":"INDUSTRY","reason":"产业链影响"}
```

并验证 `reviewService.review(request)` 被调用且响应包含 `effectiveCategoryCode`。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -pl finscope-web -am -Dtest=NewsClassificationReviewServiceTest,NewsFeedControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，复核服务、DTO 或路由不存在。

- [ ] **Step 3: 实现复核用例**

`NewsClassificationReviewService.review` 依次执行：去空白并校验 `itemId/categoryCode`；加载分类记录并要求状态为 `CLASSIFIED`；通过 `NewsCategoryRepository.findEnabledByCode` 校验目标分类；调用 repository `review`；重新读取并映射响应。无效输入统一抛出 `BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, message)`。

Controller 增加：

```java
@PostMapping("/classifications/review")
public ApiResponse<NewsClassificationView> review(@RequestBody NewsClassificationReviewRequest request) {
    return ApiResponses.success(newsClassificationReviewService.review(request));
}
```

- [ ] **Step 4: 运行 Service 与 Web 测试确认通过**

Run: `cd backend && mvn -pl finscope-web -am -Dtest=NewsClassificationReviewServiceTest,NewsFeedControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: 两个测试类全部 PASS。

- [ ] **Step 5: 提交并推送**

```bash
git add backend/finscope-service backend/finscope-web
git commit -m "feat: 提供资讯分类复核接口"
git push github HEAD:codex/personal-research-radar
```

### Task 3: 增加有效分类过滤和快照质量统计

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/news/NewsFeedItem.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/news/NewsFeedSnapshot.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/news/NewsFeedService.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/news/NewsFeedServiceTest.java`
- Modify: `backend/finscope-web/src/test/java/com/finscope/web/controller/NewsFeedControllerTest.java`

- [ ] **Step 1: 写失败测试**

新增用例断言：人工纠正后的资讯按 `effectiveCategoryCode` 过滤；`PENDING_REVIEW` 仅返回低置信度未处理记录；统计包含 `ALL`、各有效分类和 `PENDING_REVIEW`；无记录及失败记录计入 `unclassifiedCount`。

```java
assertEquals(Integer.valueOf(2), result.getCategoryCounts().get("ALL"));
assertEquals(Integer.valueOf(1), result.getCategoryCounts().get("COMPANY"));
assertEquals(Integer.valueOf(1), result.getCategoryCounts().get("PENDING_REVIEW"));
assertEquals(1, result.getUnclassifiedCount());
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=NewsFeedServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，快照字段和待确认查询尚不存在。

- [ ] **Step 3: 实现统计和过滤**

允许 `PENDING_REVIEW` 绕过业务分类目录校验。先对完整去重列表加载分类，再基于有效分类计算统计，最后按请求维度过滤和截断。`NewsFeedItem` 同时返回 `agentCategoryCode`、`classificationConfidence`、`classificationReason`、`reviewStatus`、`manuallyReviewed` 和有效 `categoryCode/categoryName`。

`categoryCounts` 使用不可变 `LinkedHashMap`，`ALL` 为去重资讯总数，`PENDING_REVIEW` 为低置信度未处理数；`unclassifiedCount` 统计无记录或状态不为 `CLASSIFIED` 的资讯。

- [ ] **Step 4: 运行相关后端测试确认通过**

Run: `cd backend && mvn -pl finscope-web -am -Dtest=NewsFeedServiceTest,NewsFeedControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: 测试全部 PASS，JSON 包含 `categoryCounts` 和 `unclassifiedCount`。

- [ ] **Step 5: 提交并推送**

```bash
git add backend/finscope-service backend/finscope-web
git commit -m "feat: 增加资讯分类质量统计"
git push github HEAD:codex/personal-research-radar
```

### Task 4: 增加前端分类解释与人工复核交互

**Files:**
- Modify: `frontend/src/features/news/LiveNewsPanel.tsx`
- Modify: `frontend/src/features/news/NewsView.test.tsx`
- Modify: `frontend/src/styles.css`（若实际样式入口不同，使用 `rg "news-category-rail" frontend/src` 定位现有文件）

- [ ] **Step 1: 写失败测试**

扩展 `newsSnapshot`，断言标签显示“公司动态 1”“待确认 1”和“待分类 1”；卡片显示“65%”“公司公告”“待确认”；展开“确认/修正”，选择 `INDUSTRY` 并保存后验证：

```ts
expect(api).toHaveBeenCalledWith('/api/news/classifications/review', {
  method: 'POST',
  body: JSON.stringify({ itemId: 'CLS:1', categoryCode: 'INDUSTRY', reason: '' })
});
```

另加失败用例，拒绝复核请求时断言错误 Toast 被调用且原卡片仍存在。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd frontend && npm test -- --run src/features/news/NewsView.test.tsx`

Expected: FAIL，数量徽标、解释栏或复核按钮不存在。

- [ ] **Step 3: 实现前端交互**

扩展 TypeScript 类型；导航末尾增加固定工作流项 `{code: 'PENDING_REVIEW', name: '待确认'}`，业务分类仍全部来自后端。每个标签从 `snapshot.categoryCounts[code]` 取数，旁边展示 `待分类 {unclassifiedCount}`。

抽取 `ClassificationReview` 小组件，接收资讯、业务分类和 `onReviewed`。`details` 展开后提供 `select`、可选备注和保存按钮；保存成功调用当前 `load(false, false, selectedCategoryRef.current)`，失败调用 `addToast(message, 'error')`。

- [ ] **Step 4: 运行前端测试和构建**

Run: `cd frontend && npm test -- --run src/features/news/NewsView.test.tsx && npm run build`

Expected: `NewsView.test.tsx` 全部 PASS，Vite build 成功。

- [ ] **Step 5: 提交并推送**

```bash
git add frontend/src
git commit -m "feat: 增加资讯分类复核交互"
git push github HEAD:codex/personal-research-radar
```

### Task 5: 完整回归验证与文档同步

**Files:**
- Modify: `README.md`（仅在现有功能清单存在实时资讯说明时补充一句）
- Modify: `docs/superpowers/specs/2026-08-01-news-classification-quality-loop-design.md`（仅修正实现中发现的契约差异）

- [ ] **Step 1: 检查实现与设计逐条一致**

核对原始 Agent 字段保留、阈值为 `0.70`、待确认实时轮询、人工覆盖优先、计数来自当前快照、研究雷达未被修改这六项。

- [ ] **Step 2: 运行完整验证**

Run: `cd backend && mvn test`

Expected: Maven Reactor `BUILD SUCCESS`。

Run: `cd frontend && npm test -- --run && npm run build`

Expected: Vitest 全部 PASS，TypeScript/Vite build 成功。

- [ ] **Step 3: 检查差异质量**

Run: `git diff --check && git status --short && git diff --stat HEAD~4..HEAD`

Expected: `git diff --check` 无输出；只包含本功能相关文件。

- [ ] **Step 4: 提交必要文档并推送**

```bash
git add README.md docs/superpowers/specs/2026-08-01-news-classification-quality-loop-design.md
git commit -m "docs: 补充资讯分类质量闭环说明"
git push github HEAD:codex/personal-research-radar
```

若 README 与设计文档无需调整，则跳过空提交，只确认所有既有提交已推送。
