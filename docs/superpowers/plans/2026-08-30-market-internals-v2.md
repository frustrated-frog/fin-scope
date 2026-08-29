# Market Internals V2 Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use executing-plans to implement this plan task-by-task.

**Goal:** 将 Market Pulse 的市场宽度升级为包含涨跌分布、趋势宽度、新高新低、A-D Line、60 日轨迹和日间变化摘要的市场结构驾驶舱。

**Architecture:** Python 服务基于现货和本地全 A 日 K 面板计算结构化市场内部指标；Java RPC 校验并映射 v2 合同，Service 生成产品化变化摘要；React 在市场宽度、今日雷达和历史演变三个视图中消费同一份快照。既有股票发现和指数展示边界保持不变。

**Tech Stack:** Python 3 / FastAPI / Pydantic / pandas、Java 21 / Spring Boot / Jackson / JUnit 5、React 18 / TypeScript / Vitest / CSS/SVG

---

### Task 1: 固定 Python v2 合同与计算口径

**Files:**
- Modify: `market-data-service/tests/test_breadth.py`
- Modify: `market-data-service/src/finscope_market_data/models.py`
- Modify: `market-data-service/src/finscope_market_data/snapshot_store.py`
- Modify: `market-data-service/src/finscope_market_data/breadth.py`

1. 先新增失败测试，使用多股票、至少 250 日的确定性日 K 面板断言七档分布、MA20/60/120/250、新高新低、净上涨家数、A-D Line 和 60 日历史。
2. 运行 `cd market-data-service && pytest tests/test_breadth.py -q`，确认新测试因合同字段缺失而失败。
3. 在 Pydantic 中增加 v2 子模型，将快照合同升级到 `market-breadth-v2`。
4. 为 `SnapshotStore` 增加按日期加载日 K 面板的方法；所有计算只读取本地行情快照。
5. 在 `MarketBreadthService` 实现分布与历史指标计算；历史样本不足时返回空指标和明确警告。
6. 重新运行测试直至通过，并提交、推送 Python 批次。

### Task 2: Java 领域模型与 RPC 合同

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/MarketReturnDistributionBucket.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/MarketTrendBreadth.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/MarketNewHighLow.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/MarketInternalHistoryPoint.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/MarketBreadthSnapshot.java`
- Modify: `backend/finscope-rpc/src/test/java/com/finscope/rpc/marketpulse/PythonMarketBreadthSourceTest.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketpulse/PythonMarketBreadthSource.java`

1. 先把 RPC fixture 升级为 v2 并断言所有新增对象和历史点映射，运行 RPC 测试确认失败。
2. 在 domain 正确包中增加独立 DTO，不在 RPC 或 Service 内定义公共内部类。
3. 扩展解析器，严格校验比例、非负计数、日期顺序和分布计数总和。
4. 运行 `cd backend && mvn -pl finscope-rpc -am -Dtest=PythonMarketBreadthSourceTest -Dsurefire.failIfNoSpecifiedTests=false test` 直至通过。
5. 检查 Java 大括号、字段注入和模块依赖规范，提交并推送合同批次。

### Task 3: 昨日到今日变化摘要

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/MarketBreadthChangeSummary.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/MarketBreadthSnapshot.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/marketpulse/MarketBreadthServiceTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketBreadthService.java`

1. 先新增 Service 失败测试，断言相邻历史点能生成扩散、MA20、新高减新低、成交额变化和中文摘要。
2. 运行相关 Service 测试，确认摘要缺失导致失败。
3. 在 `MarketBreadthService` 内实现确定性摘要，使用 RPC 历史点，不额外请求外部数据。
4. 对历史不足场景返回空摘要而不是误报变化。
5. 运行相关 Service 测试直至通过，提交并推送服务批次。

### Task 4: React 市场结构驾驶舱

**Files:**
- Modify: `frontend/src/features/market-pulse/marketPulseTypes.ts`
- Modify: `frontend/src/features/market-pulse/MarketPulseView.test.tsx`
- Modify: `frontend/src/features/market-pulse/MarketPulseView.tsx`
- Modify: `frontend/src/styles.css`

1. 先扩展 fixture 和失败测试，断言“涨跌幅分布”“趋势宽度”“新高 / 新低”“A-D Line”“今日结构变化”“60D INTERNALS”可见。
2. 运行 `cd frontend && npm test -- MarketPulseView.test.tsx`，确认新增测试失败。
3. 扩展 TypeScript 合同，并将新增内容拆为小型展示组件。
4. 市场宽度页增加七档分布、四周期趋势宽度、新高新低与 A-D Line；今日雷达增加变化摘要。
5. 历史演变页增加无第三方依赖的响应式 SVG 多轨图和选中日详情；保留原冻结判断列表。
6. 调整仅限 Market Pulse 的字号、间距、颜色与窄屏布局，不影响其他页面。
7. 运行目标测试和 `npm run build`，提交并推送前端批次。

### Task 5: 集成回归与真实数据检查

**Files:**
- Modify only if verification exposes defects.

1. 运行 `cd market-data-service && pytest -q`。
2. 运行 `cd backend && mvn test`。
3. 运行 `cd frontend && npm test && npm run build`。
4. 启动或调用本地 Python breadth 接口，确认 v2 JSON、60 日序列和部分数据降级行为。
5. 对照《项目开发规范与代码评审清单.md》逐项自检，查看 `git diff --check` 和 `git status`。
6. 修复回归后提交并推送最终批次；输出实现范围、验证结果和剩余的二期增强项。
