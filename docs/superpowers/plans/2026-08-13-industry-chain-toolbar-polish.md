# 产业链工具栏视觉优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将产业链搜索与操作按钮统一为简洁、克制且有即时反馈的 Apple 风格工具条。

**Architecture:** 保留 `IndustryChainView` 现有语义结构与事件处理，仅重构 `industry-chain.css` 中工具栏控件的视觉状态。使用现有样式契约测试锁定尺寸、单层输入、主次按钮、按下反馈与辅助偏好。

**Tech Stack:** React、TypeScript、CSS、Vitest

---

### Task 1: 工具栏控件视觉契约

**Files:**
- Modify: `frontend/src/features/industry-chain/IndustryChainCreateStyles.test.ts`
- Modify: `frontend/src/features/industry-chain/industry-chain.css`

- [ ] **Step 1: Write the failing test**

新增样式契约，断言 `.ic-search`、`.ic-focus-button`、`.ic-refresh-button` 统一为 `44px` 高度和 `12px` 圆角；搜索输入保持透明无边框；刷新按钮使用青色材质；按钮存在 `:active` 缩放、`:focus-visible` 描边，以及 reduced-motion 和 reduced-transparency 覆盖。

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- --run src/features/industry-chain/IndustryChainCreateStyles.test.ts`

Expected: FAIL，因为当前控件没有统一高度、圆角、强调层级和完整交互状态。

- [ ] **Step 3: Write minimal implementation**

在 `industry-chain.css` 中将工具栏控件改为统一尺度的单层半透明材质，增加 hover、active、focus-visible、disabled 以及辅助偏好样式；不修改 JSX。

- [ ] **Step 4: Run tests and build**

Run: `cd frontend && npm test -- --run src/features/industry-chain/IndustryChainCreateStyles.test.ts src/features/industry-chain/IndustryChainView.test.tsx && npm run build`

Expected: 样式与组件测试全部通过，TypeScript 和 Vite 生产构建成功。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/industry-chain/IndustryChainCreateStyles.test.ts frontend/src/features/industry-chain/industry-chain.css
git commit -m "fix: 优化产业链工具栏视觉层级"
git push github codex/industry-chain-flush-header
```
