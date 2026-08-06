# 日 K 行情工作台优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复日 K 图边界和标签问题，并用现有缓存数据增加均线与关键统计。

**Architecture:** 保持 `KlineChart` 为纯 SVG 几何组件，在内部计算安全边距和移动平均线；`WatchlistKlineDrawer` 负责从已有 bars 派生摘要指标。所有增强均为前端纯计算，不改变缓存或请求链路。

**Tech Stack:** React 18、TypeScript、SVG、Vitest、Testing Library、CSS

---

### Task 1: 修复图表几何与坐标语义

**Files:**
- Modify: `frontend/src/features/watchlist/KlineChart.tsx`
- Test: `frontend/src/features/watchlist/WatchlistKlineDrawer.test.tsx`

- [x] 写失败测试，断言首尾实体处于背景框内，并断言“价格（元）”“成交量（手）”标签存在。
- [x] 运行 `npm test -- --run src/features/watchlist/WatchlistKlineDrawer.test.tsx`，确认测试因现有边界和标签失败。
- [x] 把蜡烛坐标映射到 `PLOT_LEFT + gutter` 至 `PLOT_RIGHT - gutter`，并在 SVG 内分别绘制价格轴与成交量副图标题。
- [x] 重跑定向测试，确认通过。

### Task 2: 增加缓存内趋势图层与指标

**Files:**
- Modify: `frontend/src/features/watchlist/KlineChart.tsx`
- Modify: `frontend/src/features/watchlist/WatchlistKlineDrawer.tsx`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/features/watchlist/WatchlistKlineDrawer.test.tsx`

- [x] 写失败测试，断言 MA5/MA20/MA60 图层和成交额、换手率、20 日涨跌、区间高低被渲染。
- [x] 运行定向测试，确认因图层和指标缺失而失败。
- [x] 在 `KlineChart` 中计算有完整窗口的移动平均并绘制折线；在 Drawer 中用 bars 派生统计值，缺失值显示 `--`。
- [x] 调整标题、图例、内容区和指标网格 CSS，确保桌面与移动端都有稳定信息层级。
- [x] 重跑定向测试并修正回归。

### Task 3: 验证与交付

**Files:**
- Verify: `frontend/src/features/watchlist/*`

- [x] 运行 `npm test`，预期全部测试通过。
- [x] 运行 `npm run build`，预期 TypeScript 与 Vite 构建成功。
- [x] 运行 `git diff --check` 并检查只包含本次相关文件。
- [ ] 使用 `fix: 优化日K图表布局与指标` 提交并推送当前分支。
