# Previous Week Market Pulse Backfill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 2026-08-17 至 2026-08-21 生成五个点时一致的市场判断，并在 Market Pulse 页面逐日查看。

**Architecture:** Python 行情服务从本地日 K 快照面板计算缺失日期的历史样本宽度；Java 增加有界批量回填用例，复用现有确定性复盘与持久化流程；React 在历史演变中触发回填并打开单日复盘。今日刷新仍只允许最新交易日。

**Tech Stack:** Python 3.13、FastAPI、SQLite、Java 21、Spring Boot、React、TypeScript、Vitest。

---

### Task 1: 历史样本宽度

**Files:**
- Modify: `market-data-service/src/finscope_market_data/snapshot_store.py`
- Modify: `market-data-service/src/finscope_market_data/breadth.py`
- Modify: `market-data-service/tests/test_snapshot_store.py`
- Modify: `market-data-service/tests/test_breadth.py`

- [ ] 先写失败测试：从每只股票目标日和前一交易日收盘计算涨跌、成交额、中位涨幅，并标记 `PARTIAL_FRESH` 与样本口径警告。
- [ ] 运行 `uv run pytest tests/test_snapshot_store.py tests/test_breadth.py -q`，确认失败来自历史面板能力缺失。
- [ ] 在 SnapshotStore 增加按目标日期读取相邻日 K 对的方法；MarketBreadthService 对非最近交易日使用该面板并冻结同日宽度。
- [ ] 再次运行定向测试并确认通过。
- [ ] 提交并推送：`feat: 增加历史样本市场宽度`。

### Task 2: 五日有界回填

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/MarketPulseBackfillResult.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketPulseFeatureService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketBreadthService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketPulseSectorService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketPulseService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/MarketPulseController.java`
- Modify corresponding service/web tests.

- [ ] 先写失败测试：只接受最多十天且不晚于最新交易日的区间；按真实交易日升序逐日保存；单日失败继续；历史指数允许从有界日 K 计算。
- [ ] 运行相关 Maven 定向测试，确认新契约尚未实现。
- [ ] 增加 `POST /api/market-pulse/backfill?startDate=&endDate=`，内部逐日复用市场工作区生成流程。
- [ ] 历史行业只使用同花顺历史收益，不把当前资金流写入过去日期；错误按日期收集。
- [ ] 运行定向测试并确认通过。
- [ ] 提交并推送：`feat: 增加市场判断区间回填`。

### Task 3: 页面五日展示

**Files:**
- Modify: `frontend/src/features/market-pulse/marketPulseTypes.ts`
- Modify: `frontend/src/features/market-pulse/MarketPulseView.tsx`
- Modify: `frontend/src/features/market-pulse/MarketPulseView.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] 先写失败测试：展示“补全 8.17–8.21 判断”按钮、调用回填接口、刷新五条历史记录、点击历史行进入当日复盘。
- [ ] 运行 Market Pulse 前端测试确认失败。
- [ ] 实现回填进度、结果提示和逐日查看入口，沿用现有响应式布局。
- [ ] 运行前端定向测试与生产构建。
- [ ] 提交并推送：`feat: 展示上一周逐日市场判断`。

### Task 4: 实际回填与验证

**Files:**
- Modify: `docs/superpowers/plans/2026-08-24-previous-week-market-pulse-backfill.md`

- [ ] 运行 Python、Java、前端全量测试和前端生产构建。
- [ ] 重启本地 Python 与 Java 服务，使当前分支代码生效。
- [ ] 调用五日回填接口，确认日期列表包含 2026-08-17 至 2026-08-21，并逐日读取完整复盘。
- [ ] 浏览器检查桌面与窄屏历史演变页面。
- [ ] 记录验证结果，提交并推送：`docs: 记录上一周市场判断回填结果`。
