# Stock Supply Chain Results View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将股票产业链页改为只呈现产业链结果与相关结论，不展示证据明细。

**Architecture:** 保留现有 `StockSupplyChainSnapshot` 数据契约和刷新流程，仅在 `StockSupplyChainPanel` 中移除证据 UI，并调整结果页文案与时间元数据。对应 CSS 删除证据专属样式，组件测试直接约束“结果可见、证据不可见”。

**Tech Stack:** React、TypeScript、Vitest、Testing Library、CSS

---

### Task 1: 锁定结果页展示契约

**Files:**
- Modify: `frontend/src/features/watchlist/StockSupplyChainPanel.test.tsx`

- [ ] **Step 1: Write the failing test**

在持久化快照测试中断言结论、三层节点、可信度、更新时间和“结论边界”可见；同时断言“证据索引”、证据标题、E1/E2 链接与“更新证据”不可见，并断言操作名称为“更新产业链”。

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --run src/features/watchlist/StockSupplyChainPanel.test.tsx`

Expected: FAIL，因为当前页面仍渲染证据索引、证据链接和旧文案。

### Task 2: 实现结论优先界面

**Files:**
- Modify: `frontend/src/features/watchlist/StockSupplyChainPanel.tsx`
- Modify: `frontend/src/features/watchlist/WatchlistKlineDrawer.tsx`
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/features/watchlist/WatchlistKlineDrawer.test.tsx`

- [ ] **Step 1: Write minimal implementation**

删除 `evidenceByCode`、节点证据引用和完整证据索引；工具栏改为“SUPPLY CHAIN · CONCLUSION / 产业链结论”，按钮改为“更新产业链”；摘要时间使用 `updatedAt || generatedAt`；边界标题改为“结论边界”。同步更新生成、更新、错误和空状态文案。

同时将弹层标题改为“产业链结论”，移除“上下游证据关系”文案，并确保产业链页签不渲染 K 线行情指标。结论边界展示前清除内部 E/T 编号并使用面向用户的“公开信息”表述。

- [ ] **Step 2: Remove obsolete CSS**

删除 `.stock-chain-evidence-refs`、`.stock-chain-sources` 相关规则及移动端来源列表规则；保留现有三层轨道视觉并优化摘要时间标签和结论边界层级。

- [ ] **Step 3: Run focused tests to verify they pass**

Run: `npm test -- --run src/features/watchlist/StockSupplyChainPanel.test.tsx src/features/watchlist/WatchlistKlineDrawer.test.tsx`

Expected: PASS。

### Task 3: 验证并交付

**Files:**
- Verify: `frontend/src/features/watchlist/StockSupplyChainPanel.tsx`
- Verify: `frontend/src/styles.css`

- [ ] **Step 1: Run the full frontend test suite**

Run: `npm test -- --run`

Expected: 0 failures。

- [ ] **Step 2: Run the production build**

Run: `npm run build`

Expected: exit code 0；允许保留项目既有的 chunk size warning。

- [ ] **Step 3: Verify the diff**

Run: `git diff --check && git status --short`

Expected: 无空白错误，仅包含本功能相关文件。

- [ ] **Step 4: Commit and push**

```bash
git add docs/superpowers/specs/2026-08-11-stock-supply-chain-results-view-design.md \
  docs/superpowers/plans/2026-08-11-stock-supply-chain-results-view.md \
  frontend/src/features/watchlist/StockSupplyChainPanel.tsx \
  frontend/src/features/watchlist/StockSupplyChainPanel.test.tsx \
  frontend/src/styles.css
git commit -m "feat: 精简股票产业链结论页"
git push origin codex/fund-holdings-detail
```
