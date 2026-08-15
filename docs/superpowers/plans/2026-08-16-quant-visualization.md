# Quant Visualization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有单股预测和股票发现页面中增加六种基于冻结量化证据的专业可视化，不改变预测与排名算法。

**Architecture:** 新建两个纯展示组件分别承载单股和发现可视化，组件只接收既有 TypeScript 契约并生成 SVG/HTML 投影。页面负责选择数据和布局，所有显示归一化封装在组件内，不回写业务数据。

**Tech Stack:** React、TypeScript、原生 SVG、CSS、Vitest、Testing Library

---

### Task 1: 单股预测可视化组件

**Files:**
- Create: `frontend/src/features/strategy/SingleStockQuantVisuals.tsx`
- Create: `frontend/src/features/strategy/SingleStockQuantVisuals.test.tsx`
- Create: `frontend/src/features/strategy/QuantVisualizations.css`
- Modify: `frontend/src/features/strategy/SingleStockForecastPanel.tsx`

- [x] **Step 1: 写失败测试**

测试因子贡献图包含零轴、正负贡献、历史分位；收益图包含策略、同股基准和水下回撤；参数面板包含主方案、稀疏单元格和超额收益。

- [x] **Step 2: 确认测试因组件缺失而失败**

Run: `cd frontend && npm test -- SingleStockQuantVisuals.test.tsx`

- [x] **Step 3: 实现纯展示组件**

导出 `FactorContributionChart`、`EquityDrawdownChart` 和 `ParameterStabilityMap`。对空数组返回带操作含义的空状态；SVG 提供 `role="img"` 和描述性 `aria-label`。

- [x] **Step 4: 嵌入现有单股报告**

替换旧的单层净值图，在因子知识区前加入贡献坐标轴，在稳定性表格前加入参数面板；保留原表格作为精确数据账本。

- [x] **Step 5: 运行聚焦测试并提交**

Run: `cd frontend && npm test -- SingleStockQuantVisuals.test.tsx SingleStockForecastPanel.test.tsx`

Commit: `feat: 增加单股量化证据可视化`

### Task 2: 股票发现可视化组件

**Files:**
- Create: `frontend/src/features/strategy/StockDiscoveryVisuals.tsx`
- Create: `frontend/src/features/strategy/StockDiscoveryVisuals.test.tsx`
- Modify: `frontend/src/features/strategy/StockDiscoveryPanel.tsx`
- Modify: `frontend/src/features/strategy/quantTypes.ts`
- Modify: `frontend/src/features/strategy/QuantVisualizations.css`

- [x] **Step 1: 写失败测试**

测试漏斗保留率、深度候选风险收益分布、最终前五高亮、因子矩阵六项指标和空候选状态。

- [x] **Step 2: 确认测试因组件缺失而失败**

Run: `cd frontend && npm test -- StockDiscoveryVisuals.test.tsx`

- [x] **Step 3: 实现纯展示组件**

导出 `DiscoveryFunnel`, `RiskReturnMap` 和 `CandidateFactorMatrix`。漏斗以阶段计数计算展示保留率；散点图使用全部深度候选；矩阵只对最终候选做批次内相对归一化并展示原始值。

- [x] **Step 4: 嵌入现有股票发现页面**

用增强漏斗替换旧漏斗，在最终候选列表上方加入风险收益分布和因子矩阵；没有最终候选时仍展示漏斗和深度候选分布。

- [x] **Step 5: 运行聚焦测试并提交**

Run: `cd frontend && npm test -- StockDiscoveryVisuals.test.tsx StockDiscoveryPanel.test.tsx`

Commit: `feat: 增加股票发现比较可视化`

### Task 3: 视觉验收与全量验证

**Files:**
- Modify: `frontend/src/features/strategy/QuantVisualizations.css`
- Modify: `docs/superpowers/specs/2026-08-16-quant-visualization-design.md` only if verified behavior needs clarification

- [x] **Step 1: 运行前端全量测试**

Run: `cd frontend && npm test`

- [x] **Step 2: 运行生产构建**

Run: `cd frontend && npm run build`

- [x] **Step 3: 浏览器视觉验收**

检查桌面和移动端：无裁切、文本可读、颜色不是唯一信息载体、空状态明确、没有新增手动刷新按钮。

- [x] **Step 4: 规范自检**

运行 `git diff --check`，确认没有业务契约漂移、没有浏览器侧重新排名、没有无关文件修改。

- [x] **Step 5: 提交并推送**

Commit: `test: 完成量化可视化验收`
