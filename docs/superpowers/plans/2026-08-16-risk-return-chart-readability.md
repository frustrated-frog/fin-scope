# Risk Return Chart Readability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让股票发现风险收益图的坐标轴充分利用卡片空间，并以可读字号展示数值刻度。

**Architecture:** 保持 `RiskReturnMap` 为纯展示组件，在既有 SVG 内扩大绘图区并生成等距横纵轴刻度。横轴固定从零回撤开始，CSS 只负责字号和响应式尺寸，不改变候选数据或排名。

**Tech Stack:** React、TypeScript、原生 SVG、CSS、Vitest、Testing Library

---

### Task 1: 坐标轴可读性

**Files:**
- Modify: `frontend/src/features/strategy/StockDiscoveryVisuals.test.tsx`
- Modify: `frontend/src/features/strategy/StockDiscoveryVisuals.tsx`
- Modify: `frontend/src/features/strategy/QuantVisualizations.css`

- [x] **Step 1: 写失败测试**

断言风险收益 SVG 使用更高的内部画布、具有横纵轴数值刻度，并且横轴包含 `0.0%` 零回撤刻度。

- [x] **Step 2: 确认测试按预期失败**

Run: `cd frontend && npm test -- --run src/features/strategy/StockDiscoveryVisuals.test.tsx`

Expected: FAIL，因为现有图没有 `data-axis` 数值刻度且 viewBox 仍为 `0 0 720 270`。

- [x] **Step 3: 实现完整绘图区和刻度**

将 SVG 改为 `viewBox="0 0 600 430"`，绘图区使用 `x=95..570`、`y=26..365`；横轴从零开始，横纵轴各生成五个等距刻度。轴标题、刻度和气泡名称使用独立 CSS 类，保持气泡为正圆。左侧为负数刻度保留独立宽度，并让纵轴标题与刻度至少相隔 60 个 SVG 单位。该坐标比例来自真实双栏卡片截图复核，可使绘图区在横纵两个方向都接近卡片正文边界。

- [x] **Step 4: 运行聚焦测试**

Run: `cd frontend && npm test -- --run src/features/strategy/StockDiscoveryVisuals.test.tsx src/features/strategy/StockDiscoveryPanel.test.tsx`

Expected: 两个测试文件全部通过。

- [x] **Step 5: 浏览器视觉验收**

在现有股票发现真实批次中检查桌面和 390px 视口：绘图区无大块无效留白，轴标题和刻度可读，气泡未变形，控制台没有错误。

- [x] **Step 6: 全量验证并提交**

Run: `cd frontend && npm test && npm run build`

Run: `git diff --check`

Commit: `fix: 提升风险收益图坐标可读性`
