# 股票学习卡库 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让股票学习卡入口自动恢复已有股票卡片，并通过卡片进入该股票最新六维解读详情。

**Architecture:** 新增轻量列表契约，Repository 只读取学习卡、股票和最新运行摘要，不加载完整证据；现有按代码详情接口保持不变。前端由 `StockLearningCardPanel` 管理列表/选中股票/轮询状态，`StockLearningCardDetail` 专注六维详情呈现。

**Tech Stack:** Java 8、Spring Boot 2.7、JdbcTemplate、SQLite、React、TypeScript、Vitest。

---

### Task 1: 清理旧学习卡运行

**Files:**
- Data: `/Users/machengqian/code/javaProject/data/finance.db`

- [ ] **Step 1: 再次确认删除目标与最新运行**

```sql
SELECT c.latest_run_id,r.id,r.status,r.created_at
FROM stock_learning_card c
JOIN stock_learning_card_run r ON r.card_id=c.id
JOIN instrument i ON i.id=c.instrument_id
WHERE i.code='603618'
ORDER BY r.id DESC;
```

预期：`latest_run_id=3`，删除候选仅为 run 1、run 2。

- [ ] **Step 2: 在事务中删除旧运行**

```sql
PRAGMA foreign_keys=ON;
BEGIN IMMEDIATE;
DELETE FROM stock_learning_card_run
WHERE id IN (1,2)
  AND card_id=(SELECT c.id FROM stock_learning_card c JOIN instrument i ON i.id=c.instrument_id WHERE i.code='603618')
  AND id<>(SELECT latest_run_id FROM stock_learning_card WHERE id=card_id);
COMMIT;
```

- [ ] **Step 3: 验证只剩最新运行且关联数据仍可读取**

重新查询运行、claim、evidence 和 watch item，预期 603618 仅保留 run 3。

### Task 2: 学习卡摘要列表 API

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/learningcard/StockLearningCardSummary.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/learningcard/StockLearningCardRepository.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningCardService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/StockLearningCardController.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/learningcard/StockLearningCardRepositoryTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/learningcard/StockLearningCardServiceTest.java`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/controller/StockLearningCardControllerTest.java`

- [ ] **Step 1: 写 Repository 失败测试**

创建两个股票学习卡及其最新运行，断言 `repository.summaries()` 按更新时间倒序返回每只股票一条，并正确统计最新运行的 `READY` claim 数量。

- [ ] **Step 2: 运行测试确认 RED**

```bash
cd backend && mvn -q -pl finscope-dao -am -Dtest=StockLearningCardRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：因 `summaries()` 和摘要类型不存在而编译失败。

- [ ] **Step 3: 实现摘要领域模型和一次查询**

`StockLearningCardSummary` 提供 `code`、`name`、`status`、`stage`、`summary`、`completedDimensions`、`totalDimensions`、`updatedAt`、`completedAt`。Repository 使用最新运行关联和 claim 聚合子查询返回摘要。

- [ ] **Step 4: 写 Service 与 Controller 失败测试**

断言 `GET /api/stock-learning-cards` 返回股票代码、状态和维度完成数；Service 直接返回 Repository 摘要，不触发标的解析或详情加载。

- [ ] **Step 5: 实现 Service 与 Controller 列表入口**

```java
@GetMapping
public ApiResponse<List<StockLearningCardSummary>> list() {
    return ApiResponses.success(learningCardService.list());
}
```

- [ ] **Step 6: 运行学习卡后端测试确认 GREEN**

```bash
cd backend && mvn -q -pl finscope-web -am -Dtest='StockLearningCardControllerTest,StockLearningCardRepositoryTest,com.finscope.service.learningcard.*Test' -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 7: 提交并推送后端批次**

```bash
git commit -m 'feat: 增加股票学习卡列表接口'
git push origin codex/liujie-stock-learning-card
```

### Task 3: 卡片首页与六维详情

**Files:**
- Create: `frontend/src/features/strategy/StockLearningCardDetail.tsx`
- Modify: `frontend/src/features/strategy/StockLearningCardPanel.tsx`
- Modify: `frontend/src/features/strategy/StockLearningCardPanel.test.tsx`
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: 写首页恢复和详情导航失败测试**

Mock `GET /api/stock-learning-cards` 返回 603618 摘要，断言首次渲染出现“杭电股份”小卡且不直接出现六维正文；点击后断言请求 `/api/stock-learning-cards/603618` 并显示六维详情；点击“返回全部股票”恢复列表。

- [ ] **Step 2: 运行组件测试确认 RED**

```bash
cd frontend && npm test -- --run StockLearningCardPanel.test.tsx
```

预期：首页没有自动请求列表，测试失败。

- [ ] **Step 3: 扩展前端类型并拆分详情组件**

新增 `StockLearningCardSummary` 类型。把现有结果 JSX 移入 `StockLearningCardDetail`，通过 `view`、`onBack` 和 `onRegenerate` props 呈现。

- [ ] **Step 4: 实现列表加载、卡片导航和轮询**

`StockLearningCardPanel` 首次挂载加载摘要；点击摘要加载详情；生成后进入详情；运行中每 2.5 秒刷新详情，终态后同步列表。卡片使用六格维度进度轨道表达完成度。

- [ ] **Step 5: 实现响应式样式**

桌面端使用自适应卡片网格；窄屏改为单列。卡片为可聚焦按钮，提供明确 hover/focus 状态；详情返回操作保持在标题区。

- [ ] **Step 6: 运行组件测试与生产构建确认 GREEN**

```bash
cd frontend && npm test -- --run StockLearningCardPanel.test.tsx
npm run build
```

- [ ] **Step 7: 提交并推送前端批次**

```bash
git commit -m 'feat: 增加股票学习卡库页面'
git push origin codex/liujie-stock-learning-card
```

### Task 4: 回归验证

- [ ] **Step 1: 运行完整前端测试**

```bash
cd frontend && npm test -- --run
```

- [ ] **Step 2: 检查变更与工作区**

```bash
git diff --check
git status --short
```

预期：只保留用户已有的 `AGENTS.md` 未提交修改。
