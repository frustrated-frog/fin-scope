# Topbar Command Strip Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 FinScope 顶栏右侧重构为层级清晰、具有研究终端科技感的轻量控制台。

**Architecture:** 保留 `AppShell` 的现有属性和事件流，仅为统计、控制和状态三类内容增加语义分组与专用类名。所有视觉规则集中在现有全局样式的顶栏区段，并通过现有 `App.test.tsx` 防止结构回退。

**Tech Stack:** React 18、TypeScript、CSS、Vitest、Testing Library、Vite

---

### Task 1: 固定顶栏控制台语义

**Files:**
- Modify: `frontend/src/App.test.tsx`

- [x] **Step 1: 写失败测试**

在现有顶栏测试中断言 `数据概览` 分组、`系统状态` 状态节点，以及刷新按钮中的可见动作标签。

- [x] **Step 2: 验证测试失败**

Run: `cd frontend && npm test -- --run src/App.test.tsx`

Expected: FAIL，因为新的语义分组尚不存在。

### Task 2: 实现轻量控制台结构与样式

**Files:**
- Modify: `frontend/src/app/AppShell.tsx`
- Modify: `frontend/src/styles.css`

- [x] **Step 1: 实现最小结构**

将统计项包入 `topbar-readouts`，为主题和刷新按钮添加图标结构，将消息包装为 `topbar-status`，保持所有回调和文案不变。

- [x] **Step 2: 实现视觉系统**

增加共享数据舱、紧凑控制按钮、发光状态轨道、悬停与焦点反馈，以及 1280px 和移动端布局规则。

- [x] **Step 3: 验证目标测试通过**

Run: `cd frontend && npm test -- --run src/App.test.tsx src/app/AppShell.test.tsx`

Expected: PASS。

### Task 3: 完整验证

**Files:**
- Verify: `frontend/src/**/*`

- [x] **Step 1: 运行完整测试**

Run: `cd frontend && npm test -- --run`

Expected: 所有测试通过。

- [x] **Step 2: 运行生产构建**

Run: `cd frontend && npm run build`

Expected: 构建成功。

- [x] **Step 3: 检查补丁格式**

Run: `git diff --check`

Expected: 无空白错误。
