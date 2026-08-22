# Major Events Timeline Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将大事记页面改造成可快速扫读、可按来源过滤、按真实发生日倒序排列的研究时间档案。

**Architecture:** 保留 `MajorEventView` 单一数据入口和现有 API，只在组件内增加加载状态、稳定排序和派生统计。样式集中在现有 `styles.css` 的大事记区块，避免新增依赖或跨模块抽象。

**Tech Stack:** React 18、TypeScript、原生 CSS、Vitest、Testing Library

---

### Task 1: 固化时间档案行为

**Files:**
- Create: `frontend/src/features/major-events/MajorEventView.test.tsx`

- [ ] **Step 1: 写失败测试**

测试使用模拟的 `api` 返回跨月份、不同来源且顺序打乱的数据，断言页面展示“市场记忆”、记录数、覆盖月份、最近日期，事件按日期倒序排列，并能通过“文章研究”筛选触发 `/api/major-events?originType=ARTICLE`。

- [ ] **Step 2: 验证测试按预期失败**

Run: `cd frontend && npm test -- src/features/major-events/MajorEventView.test.tsx`

Expected: FAIL，缺少新的标题、统计或筛选按钮。

- [ ] **Step 3: 提交测试基线**

Run: `git add frontend/src/features/major-events/MajorEventView.test.tsx docs/superpowers && git commit -m "test: 固化大事记时间档案交互"`

### Task 2: 实现档案封面与研究刻度轨

**Files:**
- Modify: `frontend/src/features/major-events/MajorEventView.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: 实现最小组件改动**

增加 `isLoading`，将数据按 `occurredDate` 稳定倒序排列，派生月份数、最近日期与各来源数量；用档案封面、分段筛选、语义化月份分组和时间轴条目替换现有压缩 JSX。

- [ ] **Step 2: 实现响应式样式**

用现有主题变量完成深墨封面、琥珀日期刻度、克制的事件卡片、键盘焦点、移动端布局和 reduced-motion 规则。

- [ ] **Step 3: 验证目标测试通过**

Run: `cd frontend && npm test -- src/features/major-events/MajorEventView.test.tsx`

Expected: PASS，全部大事记测试通过。

- [ ] **Step 4: 提交实现**

Run: `git add frontend/src/features/major-events/MajorEventView.tsx frontend/src/styles.css && git commit -m "feat: 重塑大事记研究时间轴"`

### Task 3: 全量验证与视觉验收

**Files:**
- Modify only if verification reveals a scoped defect.

- [ ] **Step 1: 运行完整前端测试**

Run: `cd frontend && npm test`

Expected: PASS，0 个失败测试。

- [ ] **Step 2: 运行生产构建**

Run: `cd frontend && npm run build`

Expected: exit 0，TypeScript 与 Vite 构建成功。

- [ ] **Step 3: 本地截图检查**

启动前端并在桌面与手机视口打开 Timeline，检查档案封面、筛选、时间轴、深色模式与空状态；若发现问题，先增加或调整回归测试再修复。

- [ ] **Step 4: 推送当前分支**

Run: `git push origin codex/quant-accuracy-loop`

Expected: 当前提交成功同步到远端分支。
