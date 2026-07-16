# Market Intel 龙虎榜自动后台刷新 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 已有龙虎榜立即展示，并在首次、过期降级或超过一个工作日时自动后台刷新一次。

**Architecture:** 用纯策略函数判断刷新时机，`MarketIntelView` 复用现有刷新运行 API 并用选择版本隔离异步结果，`DragonTigerPanel` 只展示后台刷新状态。自动刷新失败不清空已有事实，也不进入循环。

**Tech Stack:** React 18、TypeScript、Vitest、Testing Library、现有 Market Intel REST API

---

### Task 1: 自动刷新策略

**Files:**
- Create: `frontend/src/features/market-intel/dragonTigerRefreshPolicy.ts`
- Create: `frontend/src/features/market-intel/dragonTigerRefreshPolicy.test.ts`

- [ ] **Step 1: 写策略失败测试**

覆盖 `NOT_REFRESHED`、`STALE_FALLBACK`、超过一个工作日、仅经过一个工作日、周末和新鲜数据。

- [ ] **Step 2: 验证测试因模块不存在而失败**

Run: `npm test -- --run src/features/market-intel/dragonTigerRefreshPolicy.test.ts`

Expected: FAIL，提示无法导入 `dragonTigerRefreshPolicy`。

- [ ] **Step 3: 实现最小策略**

实现：

```ts
export function businessDaysElapsed(from: Date, to: Date): number
export function shouldAutoRefreshDragonTiger(view: DragonTigerView, now: Date): boolean
```

判断规则：

```text
NOT_REFRESHED -> true
STALE_FALLBACK -> true
asOf 缺失 -> false
businessDaysElapsed(asOf, now) > 1 -> true
其他 -> false
```

- [ ] **Step 4: 验证策略测试通过**

Run: `npm test -- --run src/features/market-intel/dragonTigerRefreshPolicy.test.ts`

Expected: PASS。

### Task 2: 页面自动刷新状态机

**Files:**
- Modify: `frontend/src/features/market-intel/MarketIntelView.tsx`
- Modify: `frontend/src/features/market-intel/MarketIntelView.test.tsx`

- [ ] **Step 1: 写首次自动刷新失败测试**

让初次龙虎榜查询返回 `NOT_REFRESHED`，断言页面立即显示未刷新状态、自动调用一次 `POST /refresh`、显示后台更新提示，并在成功后重新查询。

- [ ] **Step 2: 验证测试失败**

Run: `npm test -- --run src/features/market-intel/MarketIntelView.test.tsx`

Expected: FAIL，因为当前页面不会自动调用刷新 API。

- [ ] **Step 3: 实现后台刷新**

新增后台状态：

```ts
const [autoRefreshingInstrumentId, setAutoRefreshingInstrumentId] = useState<number | null>(null);
const [autoRefreshError, setAutoRefreshError] = useState<string | null>(null);
```

初次查询完成后调用策略；需要刷新时，以当前 `selectionVersion` 启动一次后台任务。任务完成后重新读取两个维度，仅在版本仍匹配时写回。

- [ ] **Step 4: 写失败保留旧数据测试**

让旧龙虎榜事实返回 `STALE_FALLBACK`，后台刷新失败；断言旧记录仍在，面板显示失败提示，且没有重复 POST。

- [ ] **Step 5: 完成最小错误处理**

自动模式不调用全局错误 Toast；只设置面板错误。手动刷新继续沿用原有 Toast。

- [ ] **Step 6: 验证页面测试通过**

Run: `npm test -- --run src/features/market-intel/MarketIntelView.test.tsx`

Expected: PASS。

### Task 3: 龙虎榜面板反馈

**Files:**
- Modify: `frontend/src/features/market-intel/DragonTigerPanel.tsx`
- Modify: `frontend/src/features/market-intel/DragonTigerPanel.test.tsx`

- [ ] **Step 1: 写后台更新提示失败测试**

断言 `refreshing=true` 时显示“后台更新中”，旧事实仍保留；`refreshError` 存在时显示失败但不清空记录。

- [ ] **Step 2: 验证测试失败**

Run: `npm test -- --run src/features/market-intel/DragonTigerPanel.test.tsx`

Expected: FAIL，因为组件尚不接受后台刷新属性。

- [ ] **Step 3: 实现展示属性**

组件签名调整为：

```ts
export function DragonTigerPanel({
  view,
  refreshing = false,
  refreshError
}: {
  view: DragonTigerView;
  refreshing?: boolean;
  refreshError?: string | null;
})
```

后台状态只增加面板内提示，不替换记录列表或已确认空窗口。

- [ ] **Step 4: 验证组件测试通过**

Run: `npm test -- --run src/features/market-intel/DragonTigerPanel.test.tsx`

Expected: PASS。

### Task 4: 回归验证与提交

**Files:**
- Modify: `docs/产品需求-A股标的研究数据中心.md`

- [ ] **Step 1: 更新产品实现状态**

记录龙虎榜首次/过期自动后台刷新、手动刷新保留以及工作日近似边界。

- [ ] **Step 2: 运行 Market Intel 测试**

Run:

```bash
npm test -- --run src/features/market-intel/dragonTigerRefreshPolicy.test.ts \
  src/features/market-intel/DragonTigerPanel.test.tsx \
  src/features/market-intel/MarketIntelView.test.tsx
```

Expected: PASS。

- [ ] **Step 3: 运行前端全量测试**

Run: `npm test -- --run`

Expected: PASS。

- [ ] **Step 4: 运行生产构建**

Run: `npm run build`

Expected: build success；允许现有 chunk size warning。

- [ ] **Step 5: 检查差异并提交**

Run:

```bash
git diff --check
git status --short
git add docs frontend
git commit -m "feat: 自动刷新龙虎榜事实"
```
