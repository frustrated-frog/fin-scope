# Fact and Knowledge Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不修改数据库和后端的前提下，将 Events 与 Evidence 的日常入口收敛进 Knowledge，并提供可用真实数据预览的事实核验工作台。

**Architecture:** 保留现有事件、证据 API 和旧页面作为兼容层。新增独立的前端事实投影与展示组件，由 `KnowledgeView` 在 `facts` 内部区段加载 `/api/events` 和 `/api/evidence` 数据，所有核验状态均为无副作用的派生结果。

**Tech Stack:** React 18、TypeScript、Vitest、Testing Library、现有 CSS 设计系统。

---

### Task 1: 收敛一级入口

**Files:**
- Modify: `frontend/src/app/AppShell.tsx`
- Test: `frontend/src/app/AppShell.test.tsx`

- [ ] **Step 1: 写失败测试**

在 `AppShell.test.tsx` 中断言 Knowledge 仍存在，同时 Events 和 Evidence 不再出现在侧边栏。

- [ ] **Step 2: 验证测试失败**

Run: `cd frontend && npm test -- src/app/AppShell.test.tsx`

Expected: Events 或 Evidence 按钮仍存在，断言失败。

- [ ] **Step 3: 最小实现**

从 `AppShell.tsx` 的“知识与判断”导航组删除 Events 与 Evidence 项，将 Knowledge 标签改为“Facts & Knowledge”，提示改为“事实与知识”。不修改 `View` 类型和旧页面渲染分支。

- [ ] **Step 4: 验证测试通过**

Run: `cd frontend && npm test -- src/app/AppShell.test.tsx`

Expected: 通过。

- [ ] **Step 5: 提交并推送**

```bash
git add frontend/src/app/AppShell.tsx frontend/src/app/AppShell.test.tsx docs/superpowers
git commit -m "feat: 收敛事实与知识工作台入口"
git push -u origin codex/fact-knowledge-workbench
```

### Task 2: 建立只读事实投影

**Files:**
- Create: `frontend/src/features/knowledge/facts/factProjection.ts`
- Test: `frontend/src/features/knowledge/facts/factProjection.test.ts`

- [ ] **Step 1: 写失败测试**

覆盖三种状态：直接事实与一手来源齐备得到 `SUBSTANTIAL`；仅具备其中一种得到 `NEEDS_CORROBORATION`；两者均缺失得到 `UNVERIFIED`。同时验证证据按来源质量和置信度排序。

- [ ] **Step 2: 验证测试失败**

Run: `cd frontend && npm test -- src/features/knowledge/facts/factProjection.test.ts`

Expected: 模块不存在，测试失败。

- [ ] **Step 3: 最小实现**

定义 `FactCandidate`、`FactVerificationState` 和 `projectFactCandidates(events, evidence)`。函数只组合既有数据，不发请求、不修改输入、不写入状态。

- [ ] **Step 4: 验证测试通过**

Run: `cd frontend && npm test -- src/features/knowledge/facts/factProjection.test.ts`

Expected: 通过。

- [ ] **Step 5: 提交并推送**

```bash
git add frontend/src/features/knowledge/facts
git commit -m "feat: 增加只读事实核验投影"
git push
```

### Task 3: 增加事实核验工作台

**Files:**
- Create: `frontend/src/features/knowledge/facts/FactWorkbench.tsx`
- Create: `frontend/src/features/knowledge/facts/FactWorkbench.test.tsx`
- Modify: `frontend/src/features/knowledge/knowledgeTypes.ts`
- Modify: `frontend/src/features/knowledge/KnowledgeNavigation.tsx`
- Modify: `frontend/src/features/knowledge/KnowledgeView.tsx`

- [ ] **Step 1: 写失败测试**

测试 `?section=facts` 可恢复，页面展示事实候选、核验状态、证据材料；状态筛选和关键词搜索会更新列表；无数据时展示明确空状态。

- [ ] **Step 2: 验证测试失败**

Run: `cd frontend && npm test -- src/features/knowledge/facts/FactWorkbench.test.tsx src/features/knowledge/KnowledgeView.test.tsx`

Expected: `facts` 区段和组件不存在，测试失败。

- [ ] **Step 3: 最小实现**

将 `facts` 加入 `KnowledgeSection` 和合法 URL 区段，并将内部主导航收敛为工作台、事实核验、知识库。`KnowledgeView` 仅在该区段读取现有 `/api/events/paged?page=0&pageSize=100` 与 `/api/evidence/paged?page=0&pageSize=200`，将结果传入 `FactWorkbench`。组件实现摘要指标、搜索、状态筛选、默认隐藏无材料候选、候选列表和证据详情。

- [ ] **Step 4: 验证测试通过**

Run: `cd frontend && npm test -- src/features/knowledge/facts/FactWorkbench.test.tsx src/features/knowledge/KnowledgeView.test.tsx`

Expected: 通过。

- [ ] **Step 5: 提交并推送**

```bash
git add frontend/src/features/knowledge
git commit -m "feat: 增加事实核验工作台"
git push
```

### Task 4: 完成视觉整合与回归验证

**Files:**
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/features/knowledge/facts/FactWorkbench.test.tsx`

- [ ] **Step 1: 补充行为与无障碍断言**

断言工作台具有命名区域、筛选控件、当前选中事实和可访问的原文链接；不依赖颜色单独表达状态。

- [ ] **Step 2: 实现视觉样式**

沿用 Knowledge 的纸张式设计语言，为事实工作台增加克制的主从布局、状态标签、来源层级、证据列表和响应式单列布局，不改全局品牌视觉。

- [ ] **Step 3: 运行目标测试**

Run: `cd frontend && npm test -- src/app/AppShell.test.tsx src/features/knowledge/KnowledgeView.test.tsx src/features/knowledge/facts`

Expected: 全部通过。

- [ ] **Step 4: 运行完整前端测试与构建**

Run: `cd frontend && npm test && npm run build`

Expected: Vitest 全部通过，TypeScript 与 Vite 构建成功。

- [ ] **Step 5: 提交并推送**

```bash
git add frontend/src/styles.css frontend/src/features/knowledge/facts
git commit -m "feat: 完善事实核验工作台体验"
git push
```
