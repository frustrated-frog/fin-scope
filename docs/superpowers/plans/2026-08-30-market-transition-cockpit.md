# Market Transition Cockpit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将“市场状态与机会”从收盘复盘看板升级为能识别结构转折、衡量主线脆弱度、生成次日情景并把市场上下文交给股票发现的决策驾驶舱。

**Architecture:** 第一阶段只使用 `MarketPulseWorkspace` 已有的全A宽度、成交压力、历史内部结构和行业轮动数据，在前端纯函数中派生转折模型，避免增加不稳定外部行情依赖。`MarketTransitionPanel` 负责决策展示，App 只保存一次性的股票发现上下文，股票发现页面消费后显示环境约束，不复制市场指标或个股候选。

**Tech Stack:** React 18、TypeScript 5.6、Vitest、Testing Library、原生 SVG、现有全局 CSS token。

---

## Design direction

- **Subject / audience / job:** 面向每日收盘后复盘的 A 股个人研究者；页面唯一任务是把市场内部结构翻译成次日研究姿态。
- **Palette:** 延续深色研究终端现有变量：`--mp-blue #397b8e` 表示结构信息、`--mp-red #c45f4c` 表示风险升温、`--mp-green #27856b` 表示风险释放或防守、`--mp-amber #c28a38` 表示转折观察，背景继续使用 `--surface` / `--surface-2`。
- **Typography:** 中文标题使用现有 `Iowan Old Style` + `Songti SC`，正文使用系统无衬线，数值和状态使用 `SFMono-Regular`。新增正文不低于 13px，标签不低于 11px。
- **Layout:** 新增“转折与情景”Tab；上方是当前转折结论和四个结构仪表，中部左侧为十日状态迁移 SVG、右侧为三情景路径，底部为股票发现上下文交接。
- **Signature:** 十日状态迁移图用一条有方向的轨迹穿过“风险释放 / 修复扩散 / 震荡轮动 / 拥挤退潮”四象限，是页面唯一高识别度视觉；其余卡片保持安静、紧凑。
- **Self-critique:** 不新增大号渐变数字或重复指数卡片；转折图承载唯一视觉风险，其余层级通过排版、线框和状态色完成。

### Task 1: Build the pure market decision model

**Files:**
- Create: `frontend/src/features/market-pulse/marketPulseDecision.ts`
- Create: `frontend/src/features/market-pulse/marketPulseDecision.test.ts`
- Modify: `frontend/src/features/market-pulse/marketPulseTypes.ts`

- [ ] **Step 1: Write failing model tests**

```ts
test('detects a strengthening repair and builds three next-session scenarios', () => {
  const decision = buildMarketTransitionDecision(workspace);
  expect(decision.transition.code).toBe('REPAIR_EXPANSION');
  expect(decision.scenarios).toHaveLength(3);
});

test('creates a stock-discovery context without individual candidates', () => {
  const decision = buildMarketTransitionDecision(workspace);
  expect(decision.discoveryContext.preferredSectors).toContain('创新药');
  expect(decision.discoveryContext).not.toHaveProperty('candidates');
});
```

- [ ] **Step 2: Verify RED**

Run: `cd frontend && npm test -- marketPulseDecision.test.ts`

Expected: FAIL because `marketPulseDecision` does not exist.

- [ ] **Step 3: Implement the model**

Define typed output for transition summary, four gauges, ten-day trajectory points, three scenarios and `StockDiscoveryMarketContext`. Clamp every derived score to 0–100, preserve missing-data semantics, and use only `MarketPulseWorkspace` inputs.

- [ ] **Step 4: Verify GREEN**

Run: `cd frontend && npm test -- marketPulseDecision.test.ts`

Expected: PASS with all model assertions green.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/market-pulse/marketPulseDecision.ts frontend/src/features/market-pulse/marketPulseDecision.test.ts frontend/src/features/market-pulse/marketPulseTypes.ts
git commit -m "feat: 增加市场转折决策模型"
git push
```

### Task 2: Build the transition and scenario UI

**Files:**
- Create: `frontend/src/features/market-pulse/MarketTransitionPanel.tsx`
- Create: `frontend/src/features/market-pulse/MarketTransitionPanel.css`
- Create: `frontend/src/features/market-pulse/MarketTransitionPanel.test.tsx`
- Modify: `frontend/src/features/market-pulse/MarketPulseView.tsx`
- Modify: `frontend/src/features/market-pulse/MarketPulseView.test.tsx`

- [ ] **Step 1: Write failing component tests**

```tsx
expect(screen.getByRole('heading', { name: '市场转折雷达' })).toBeInTheDocument();
expect(screen.getByRole('img', { name: '十日市场状态迁移轨迹' })).toBeInTheDocument();
expect(screen.getByRole('heading', { name: '下一交易日情景' })).toBeInTheDocument();
expect(screen.getAllByTestId('market-scenario')).toHaveLength(3);
```

- [ ] **Step 2: Verify RED**

Run: `cd frontend && npm test -- MarketTransitionPanel.test.tsx MarketPulseView.test.tsx`

Expected: FAIL because the new panel and tab are absent.

- [ ] **Step 3: Implement the component and styles**

Render the transition thesis, participation / momentum / leadership / fragility gauges, accessible SVG trajectory, scenario cards with concrete triggers and posture, and responsive layouts at 1260px and 760px. Respect `prefers-reduced-motion`, keyboard focus and the existing page typography floor.

- [ ] **Step 4: Integrate the new tab**

Extend the view union with `transition`, add “转折与情景” between today review and breadth, memoize the pure decision model, and render `MarketTransitionPanel` without changing the existing review, breadth, rotation or history behavior.

- [ ] **Step 5: Verify GREEN**

Run: `cd frontend && npm test -- MarketTransitionPanel.test.tsx MarketPulseView.test.tsx MarketPulseResponsive.test.ts MarketPulseReadabilityStyles.test.ts`

Expected: PASS with the new tab, SVG and readability constraints covered.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/features/market-pulse/MarketTransitionPanel.tsx frontend/src/features/market-pulse/MarketTransitionPanel.css frontend/src/features/market-pulse/MarketTransitionPanel.test.tsx frontend/src/features/market-pulse/MarketPulseView.tsx frontend/src/features/market-pulse/MarketPulseView.test.tsx
git commit -m "feat: 构建市场转折与情景驾驶舱"
git push
```

### Task 3: Hand market context to stock discovery

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/features/strategy/StrategyView.tsx`
- Modify: `frontend/src/features/strategy/QuantWorkspace.tsx`
- Modify: `frontend/src/features/strategy/StockDiscoveryPanel.tsx`
- Modify: `frontend/src/features/strategy/StockDiscoveryPanel.test.tsx`

- [ ] **Step 1: Write the failing handoff test**

```tsx
render(<StockDiscoveryPanel marketContext={context} addToast={vi.fn()} setMessage={vi.fn()} />);
expect(await screen.findByText('来自市场转折雷达')).toBeInTheDocument();
expect(screen.getByText('优先研究：创新药')).toBeInTheDocument();
```

- [ ] **Step 2: Verify RED**

Run: `cd frontend && npm test -- StockDiscoveryPanel.test.tsx`

Expected: FAIL because `marketContext` is not supported.

- [ ] **Step 3: Implement one-way context handoff**

Change `onOpenStockDiscovery` to receive `StockDiscoveryMarketContext`; App stores it, opens Strategy, and passes it through StrategyView and QuantWorkspace. StockDiscoveryPanel renders a compact market-context strip containing business date, risk posture, preferred sectors, avoid sectors and chase policy. It must not alter or fabricate the existing stock candidates.

- [ ] **Step 4: Verify GREEN**

Run: `cd frontend && npm test -- StockDiscoveryPanel.test.tsx QuantWorkspace.test.tsx MarketPulseView.test.tsx`

Expected: PASS with the context visible and existing discovery behavior unchanged.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/App.tsx frontend/src/features/strategy/StrategyView.tsx frontend/src/features/strategy/QuantWorkspace.tsx frontend/src/features/strategy/StockDiscoveryPanel.tsx frontend/src/features/strategy/StockDiscoveryPanel.test.tsx
git commit -m "feat: 联动市场状态与股票发现"
git push
```

### Task 4: Visual and regression verification

**Files:**
- Modify only if verification reveals a scoped defect.

- [ ] **Step 1: Run the complete frontend test suite**

Run: `cd frontend && npm test`

Expected: all tests pass with zero failures.

- [ ] **Step 2: Run the production build**

Run: `cd frontend && npm run build`

Expected: TypeScript and Vite complete with exit code 0.

- [ ] **Step 3: Inspect the page at desktop and mobile widths**

Run the existing frontend and backend, open Market Pulse, inspect the transition tab at desktop and 760px/mobile widths, then verify no horizontal overflow, clipped labels, invisible focus states or unreadable text.

- [ ] **Step 4: Review the final diff and project checklist**

Run: `git diff origin/codex/market-internals-v2...HEAD --check` and inspect `git status --short`.

Expected: no whitespace errors, no unrelated changes, and a clean worktree after the final commit.

