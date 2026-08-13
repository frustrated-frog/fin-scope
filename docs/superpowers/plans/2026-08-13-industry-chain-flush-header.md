# 产业链顶部无缝衔接 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除全局顶部栏与产业链工作台之间的空白带，且不影响其他视图。

**Architecture:** AppShell 根据当前 `view` 输出产业链专属 workspace 状态类；全局样式通过该状态类精确覆盖顶部栏下外边距。组件测试验证类名分流，样式测试验证覆盖规则。

**Tech Stack:** React、TypeScript、CSS、Vitest、Testing Library

---

### Task 1: 产业链顶部无缝衔接

**Files:**
- Modify: `frontend/src/app/AppShell.tsx`
- Modify: `frontend/src/app/AppShell.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Write the failing test**

在 `AppShell.test.tsx` 渲染 `industryChain` 和普通视图，断言只有产业链主工作区包含 `workspace--flush-content`；同时读取 `styles.css`，断言该状态类将直属 `.topbar` 的 `margin-bottom` 设为 `0`。

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- --run src/app/AppShell.test.tsx`

Expected: FAIL，因为产业链专属状态类和覆盖规则尚不存在。

- [ ] **Step 3: Write minimal implementation**

将 AppShell 的主内容类名改为：

```tsx
<main className={view === 'industryChain' ? 'workspace workspace--flush-content' : 'workspace'}>
```

增加直属顶部栏覆盖：

```css
.workspace--flush-content > .topbar {
  margin-bottom: 0;
}
```

- [ ] **Step 4: Run tests and build**

Run: `cd frontend && npm test -- --run src/app/AppShell.test.tsx src/features/industry-chain/IndustryChainView.test.tsx && npm run build`

Expected: 全部测试通过，TypeScript 与 Vite 生产构建成功。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/AppShell.tsx frontend/src/app/AppShell.test.tsx frontend/src/styles.css
git commit -m "fix: 消除产业链顶部空白间距"
```
