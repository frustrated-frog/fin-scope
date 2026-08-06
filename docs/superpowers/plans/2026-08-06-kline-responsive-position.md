# K 线浮层响应式定位 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 K 线浮层在桌面端相对主工作区居中，并在平板和手机断点恢复全视口响应式布局。

**Architecture:** 保留现有 React 组件和 SVG 图表，仅调整浮层 CSS 定位边界。使用一个样式回归测试锁定桌面和窄屏断点，真实浏览器负责验证最终几何位置。

**Tech Stack:** React 18、TypeScript、CSS、Vitest、Playwright CLI

---

### Task 1: 锁定响应式定位规则

**Files:**
- Create: `frontend/src/features/watchlist/WatchlistKlineResponsive.test.ts`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: 写失败测试**

读取 `styles.css`，断言 `.watchlist-kline-backdrop` 桌面规则包含 `left: 224px`，并断言 `@media (max-width: 980px)` 将其重置为 `left: 0`；保留 700px 贴底规则。

- [ ] **Step 2: 验证测试失败**

运行 `npm test -- WatchlistKlineResponsive.test.ts`，预期因缺少桌面侧栏偏移而失败。

- [ ] **Step 3: 最小实现**

在桌面浮层规则中使用 `left: 224px`，并在现有 980px 响应式区块中重置为 `left: 0`。

- [ ] **Step 4: 验证测试通过**

运行 `npm test -- WatchlistKlineResponsive.test.ts WatchlistKlineDrawer.test.tsx`，预期全部通过。

- [ ] **Step 5: 浏览器与构建验证**

运行 `npm run build`，再用 Playwright CLI 在主工作区为桌面侧栏扣除后的宽度下检查弹窗左右间距相等；在 980px 以下检查弹窗相对全视口居中。

- [ ] **Step 6: 提交并推送**

提交信息使用 `fix: 修复K线浮层响应式定位`，推送当前分支。
