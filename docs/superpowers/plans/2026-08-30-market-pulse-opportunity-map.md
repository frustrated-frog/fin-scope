# Market Pulse Opportunity Map Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Market Pulse 收束为市场宽度与行业轮动工作台，移除重复的指数和股票候选展示，并把个股筛选明确交给现有股票发现页面。

**Architecture:** 保持现有 Market Pulse API 与冻结快照不变，先用已有全 A 宽度、行业 1/5/20 日收益、超额收益、资金流和轮动阶段构建今日雷达、行业热力图与相对轮动图。Market Pulse 只展示行业级概览；通过显式导航回调进入 Strategy 的股票发现页。同步兼容扶摇全市场导出的真实签名 URL 字段，为下一批完整宽度历史计算打通入口。

**Tech Stack:** React 18、TypeScript、CSS、Vitest、Python 3、pytest、Pydantic、FastAPI

---

### Task 1: 兼容扶摇全市场导出签名 URL

**Files:**
- Modify: `market-data-service/tests/test_fuyao_provider.py`
- Modify: `market-data-service/src/finscope_market_data/providers/fuyao.py`

- [ ] **Step 1: Write the failing test**

将成功样例改为真实契约字段，并断言有效期字段被标准化：

```python
api = FakeAsyncApiClient({
    "/api/dump/market-dumps/daily-k-10d/download-url": {
        "presigned_url": "https://storage.example/daily-k.parquet?signature=short-lived",
        "presigned_url_expires_at": "2026-08-30T16:00:00+08:00",
        "expires_in_seconds": 300,
    }
})
assert result["download_url"].startswith("https://storage.example/")
assert result["expires_at"] == "2026-08-30T16:00:00+08:00"
assert result["expires_in"] == 300
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd market-data-service && pytest tests/test_fuyao_provider.py -k market_dump -q`

Expected: FAIL，提示响应缺少下载链接。

- [ ] **Step 3: Write minimal implementation**

在 `FuyaoMarketDumpClient.download_url()` 中按 `presigned_url -> download_url -> url` 读取链接，并将 `presigned_url_expires_at`、`expires_in_seconds` 映射到内部稳定字段 `expires_at`、`expires_in`。

- [ ] **Step 4: Run test to verify it passes**

Run: `cd market-data-service && pytest tests/test_fuyao_provider.py -k market_dump -q`

Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add market-data-service/src/finscope_market_data/providers/fuyao.py market-data-service/tests/test_fuyao_provider.py
git commit -m "fix: 兼容扶摇全市场导出签名地址"
```

### Task 2: 明确 Market Pulse 与股票发现的页面边界

**Files:**
- Modify: `frontend/src/features/market-pulse/MarketPulseView.test.tsx`
- Modify: `frontend/src/features/market-pulse/MarketPulseView.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Write the failing test**

新增断言：市场结构中不出现示例股票、校准概率或股票候选标题；出现“进入股票发现”按钮；点击时调用 `onOpenStockDiscovery`。

```tsx
const onOpenStockDiscovery = vi.fn();
render(<MarketPulseView addToast={vi.fn()} setMessage={vi.fn()} onOpenStockDiscovery={onOpenStockDiscovery} />);
fireEvent.click(await screen.findByRole('button', { name: '进入股票发现' }));
expect(onOpenStockDiscovery).toHaveBeenCalledOnce();
expect(screen.queryByRole('heading', { name: '示例医药' })).not.toBeInTheDocument();
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- MarketPulseView.test.tsx`

Expected: FAIL，组件尚无该回调和按钮。

- [ ] **Step 3: Write minimal implementation**

为 `MarketPulseView` 增加 `onOpenStockDiscovery`；移除股票研究候选区；在行业区尾部加入职责说明与跳转按钮。`App.tsx` 传入 `() => setView('strategy')`，Strategy 默认打开股票发现，无需复制筛选状态。

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- MarketPulseView.test.tsx`

Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/App.tsx frontend/src/features/market-pulse/MarketPulseView.tsx frontend/src/features/market-pulse/MarketPulseView.test.tsx
git commit -m "refactor: 分离市场脉搏与股票发现职责"
```

### Task 3: 移除重复指数展示并重写市场内部结构

**Files:**
- Modify: `frontend/src/features/market-pulse/MarketPulseView.test.tsx`
- Modify: `frontend/src/features/market-pulse/MarketPulseView.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Write the failing test**

断言页面不再显示“指数全景”、上证指数和中证 1000，宽度区仍显示上涨/下跌家数、上涨比例、成交额、涨跌停和中位数。

```tsx
expect(screen.queryByText('指数全景')).not.toBeInTheDocument();
expect(screen.queryByText('上证指数')).not.toBeInTheDocument();
expect(screen.queryByText('中证1000')).not.toBeInTheDocument();
expect(screen.getByText('3,200')).toBeInTheDocument();
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- MarketPulseView.test.tsx`

Expected: FAIL，旧指数区域仍存在。

- [ ] **Step 3: Write minimal implementation**

删除 `market-pulse-index-grid` 渲染和样式；复盘首屏只保留“市场内部”，并将底部“量化证据”改为不重复事实清单的“下一步观察”。指数字段继续保留在后端供状态分类使用。

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- MarketPulseView.test.tsx MarketPulseResponsive.test.ts`

Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/market-pulse/MarketPulseView.tsx frontend/src/features/market-pulse/MarketPulseView.test.tsx frontend/src/styles.css
git commit -m "refactor: 移除市场脉搏重复指数展示"
```

### Task 4: 增加行业机会地图与轮动下钻

**Files:**
- Create: `frontend/src/features/market-pulse/SectorOpportunityMap.tsx`
- Create: `frontend/src/features/market-pulse/SectorOpportunityMap.test.tsx`
- Modify: `frontend/src/features/market-pulse/marketPulseTypes.ts`
- Modify: `frontend/src/features/market-pulse/MarketPulseView.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Write the failing component tests**

测试热力格能按行业渲染，点击后更新行业详情；轮动图为每个行业输出可访问按钮；按钮能触发股票发现跳转。

```tsx
render(<SectorOpportunityMap sectors={sectors} onOpenStockDiscovery={onOpenStockDiscovery} />);
fireEvent.click(screen.getByRole('button', { name: /创新药/ }));
expect(screen.getByRole('heading', { name: '创新药' })).toBeInTheDocument();
expect(screen.getByText('持续')).toBeInTheDocument();
fireEvent.click(screen.getByRole('button', { name: '到股票发现筛选该方向' }));
expect(onOpenStockDiscovery).toHaveBeenCalledOnce();
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- SectorOpportunityMap.test.tsx`

Expected: FAIL，组件尚不存在。

- [ ] **Step 3: Write minimal implementation**

新增独立组件，包含：

```tsx
type Mode = 'heatmap' | 'rotation';
```

- 热力图：颜色使用 5 日收益，面积用 CSS grid span 表示轮动分层，不伪造市值。
- 轮动图：横轴使用已有 `excessReturn5d`，纵轴使用 `return1d - return5d / 5` 表示短期动量变化，并清楚标注为“相对强度/短期加速度”。
- 行业详情：展示 1/5/20 日表现、行业宽度、资金流、持续天数、拥挤度与阶段解释；不展示或筛选个股。
- 交接按钮：统一进入股票发现。

- [ ] **Step 4: Integrate into Market Pulse**

将原行业排名长列表替换为机会地图，事件区保留为催化观察；市场结构页改名“行业轮动”，并增加单独“市场宽度”页签。

- [ ] **Step 5: Run tests and build**

Run: `cd frontend && npm test -- SectorOpportunityMap.test.tsx MarketPulseView.test.tsx MarketPulseResponsive.test.ts`

Expected: PASS。

Run: `cd frontend && npm run build`

Expected: exit 0。

- [ ] **Step 6: Commit**

```bash
git add frontend/src/features/market-pulse/SectorOpportunityMap.tsx frontend/src/features/market-pulse/SectorOpportunityMap.test.tsx frontend/src/features/market-pulse/marketPulseTypes.ts frontend/src/features/market-pulse/MarketPulseView.tsx frontend/src/styles.css
git commit -m "feat: 增加行业机会地图与轮动下钻"
```

### Task 5: 全量验证与推送

**Files:**
- Verify only

- [ ] **Step 1: Run Python tests**

Run: `cd market-data-service && pytest tests/test_fuyao_provider.py -q`

Expected: PASS。

- [ ] **Step 2: Run frontend tests**

Run: `cd frontend && npm test`

Expected: PASS。

- [ ] **Step 3: Run frontend production build**

Run: `cd frontend && npm run build`

Expected: exit 0。

- [ ] **Step 4: Review the diff and project checklist**

Run: `git diff main...HEAD --check && git status --short`

Expected: 无空白错误；只包含本计划文件和目标代码。

- [ ] **Step 5: Push current branch**

Run: `git push origin HEAD`

Expected: 当前分支推送成功。
