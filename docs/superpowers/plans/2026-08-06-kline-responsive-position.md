# K 线浮层响应式定位 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 K 线浮层在桌面端相对主工作区居中，并在平板和手机断点恢复全视口响应式布局。

**Architecture:** 保留现有 SVG 图表和数据流，由 K 线浮层读取 workspace 与顶栏的实际几何边界，并通过 CSS 变量约束 fixed 遮罩。组件测试锁定边界测量，样式回归测试锁定窄屏断点，真实浏览器验证最终位置。

**Tech Stack:** React 18、TypeScript、CSS、Vitest、Playwright CLI

---

### Task 1: 锁定响应式定位规则

**Files:**
- Create: `frontend/src/features/watchlist/WatchlistKlineResponsive.test.ts`
- Modify: `frontend/src/features/watchlist/WatchlistKlineDrawer.test.tsx`
- Modify: `frontend/src/features/watchlist/WatchlistKlineDrawer.tsx`
- Modify: `frontend/src/styles.css`

- [x] **Step 1: 写失败测试**

组件测试模拟 workspace 左边界与顶栏底边，断言遮罩写入对应 CSS 变量。读取 `styles.css`，断言遮罩消费动态边界，并断言 `@media (max-width: 980px)` 重置定位；保留 700px 贴底规则。

- [x] **Step 2: 验证测试失败**

运行 `npm test -- WatchlistKlineResponsive.test.ts`，预期因缺少桌面侧栏偏移而失败。

- [x] **Step 3: 最小实现**

在浮层组件中测量 workspace 与顶栏边界、监听窗口和元素尺寸变化；CSS 使用测量值定位，并在 980px 响应式区块中重置为全视口。

- [x] **Step 4: 验证测试通过**

运行 `npm test -- WatchlistKlineResponsive.test.ts WatchlistKlineDrawer.test.tsx`，预期全部通过。

- [x] **Step 5: 浏览器与构建验证**

运行 `npm run build`，再用 Playwright CLI 在主工作区为桌面侧栏扣除后的宽度下检查弹窗左右间距相等；在 980px 以下检查弹窗相对全视口居中。

- [ ] **Step 6: 提交并推送**

提交信息使用 `fix: 修复K线浮层响应式定位`，推送当前分支。
