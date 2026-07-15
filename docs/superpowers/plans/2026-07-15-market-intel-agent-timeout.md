# Market Intel Agent Timeout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将资金行为 Agent 的模型等待预算提高到 60 秒，并在前端提供实时、分阶段的等待反馈。

**Architecture:** 服务端为首次解读和 JSON 修复设置独立的超时常量；前端保持现有轮询协议，仅扩展轮询窗口，并由展示组件基于本地 elapsed seconds 渲染等待状态。超时后继续沿用规则兜底，不新增协议或持久化字段。

**Tech Stack:** Java 8、Spring Boot、JUnit 5、React、TypeScript、Vitest、Testing Library。

---

### Task 1: 服务端分级超时预算

**Files:**
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/marketintel/CapitalInterpretationAgentTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalInterpretationAgent.java`

- [x] **Step 1: 写失败测试**

增加一个记录三参数 `complete` 调用的测试客户端：首次返回非法 JSON，第二次返回合法结果，并断言收到的超时依次为 `60000`、`30000`。

- [x] **Step 2: 验证测试失败**

Run: `mvn -q -pl finscope-service -am -Dtest=CapitalInterpretationAgentTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，实际超时仍为 `15000`、`15000`。

- [x] **Step 3: 最小实现**

在 `CapitalInterpretationAgent` 中用 `PRIMARY_TIMEOUT_MS = 60_000` 和 `REPAIR_TIMEOUT_MS = 30_000` 替代单一常量，并分别传给首次调用和修复调用。

- [x] **Step 4: 验证测试通过**

Run: `mvn -q -pl finscope-service -am -Dtest=CapitalInterpretationAgentTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 2: 前端可感知等待状态

**Files:**
- Create: `frontend/src/features/market-intel/agentWaitPresentation.ts`
- Create: `frontend/src/features/market-intel/CapitalAgentInterpretationPanel.test.tsx`
- Modify: `frontend/src/features/market-intel/CapitalAgentInterpretationPanel.tsx`
- Modify: `frontend/src/features/market-intel/MarketIntelView.tsx`

- [x] **Step 1: 写失败测试**

测试运行按钮随时间显示 `Agent 解读中 · 0s`、`Agent 解读中 · 12s`、`Agent 解读中 · 31s`；同时验证三个阶段提示和按钮禁用状态。

- [x] **Step 2: 验证测试失败**

Run: `npm test -- CapitalAgentInterpretationPanel.test.tsx`

Expected: FAIL，当前按钮只有静态的 `Agent 分析中…`。

- [x] **Step 3: 最小实现**

新增纯函数根据 elapsed seconds 返回阶段文案；组件在 `busy=true` 时启动每秒定时器，以 `Date.now()` 计算经过时间，并在结束或卸载时清理。将轮询次数从 100 提高到 185，使前端等待窗口达到约 120 秒。

- [x] **Step 4: 验证测试通过**

Run: `npm test -- CapitalAgentInterpretationPanel.test.tsx MarketIntelView.test.tsx`

Expected: PASS。

### Task 3: 全量验证与提交

**Files:**
- Verify all modified files.

- [x] **Step 1: 前端全量验证**

Run: `npm test && npm run build`

Expected: 全部测试通过且 Vite 构建退出码为 0。

- [x] **Step 2: 后端全量验证**

Run: `mvn -q test`

Expected: Maven 退出码为 0。

- [x] **Step 3: 差异检查并提交**

Run: `git diff --check`

Expected: 无输出。提交信息使用 `feat: 优化资金行为Agent等待体验`。
