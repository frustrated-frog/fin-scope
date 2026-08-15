# Backtesting.py 量化验证层 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为单股预测和股票发现共用的 Python 量化内核增加 Backtesting.py 影子回测、逐笔差分审计和可解释参数鲁棒性，并在现有页面展示。

**Architecture:** `build_forecast()` 继续是唯一量化入口；滚动样本外观察先转换为标准信号，再分别进入原生回测和 Backtesting.py 适配器。差分审计以可选 v7 报告返回，第三方异常降级为 `UNAVAILABLE`，Java 只校验与透传，前端只展示。

**Tech Stack:** Python 3.11–3.13、Pydantic、Backtesting.py 0.6.6、pytest、Java 21/Spring/Jackson、React/TypeScript/Vitest

---

### Task 1: 锁定依赖并定义影子审计契约

**Files:**
- Modify: `market-data-service/pyproject.toml`
- Modify: `market-data-service/uv.lock`
- Modify: `market-data-service/src/finscope_market_data/forecast/schemas.py`
- Test: `market-data-service/tests/test_forecast_backtest_audit.py`

- [ ] **Step 1: 写失败的契约测试**

测试构造 `BacktestAudit`，要求 camelCase 输出包含 `status=PASS`、`mode=SHADOW`、两个引擎摘要、日期一致率、指标差异、mismatches 和 limitations；非法状态必须被 Pydantic 拒绝。

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd market-data-service && uv run pytest tests/test_forecast_backtest_audit.py -q`

Expected: FAIL，原因是 `BacktestAudit` 尚不存在。

- [ ] **Step 3: 实现最小契约并锁定依赖**

新增以下明确类型：

```python
class AuditEngineMetrics(ForecastModel):
    engine: str
    trade_count: int
    total_return: float
    max_drawdown: float
    sharpe_ratio: float
    total_cost: float

class AuditMismatch(ForecastModel):
    category: Literal["TRADE_COUNT", "ENTRY_DATE", "EXIT_DATE", "RETURN", "COST"]
    trade_index: int | None = None
    primary_value: str | float | int | None = None
    shadow_value: str | float | int | None = None
    detail: str

class BacktestAudit(ForecastModel):
    status: Literal["PASS", "WARNING", "UNAVAILABLE"]
    mode: Literal["SHADOW"] = "SHADOW"
    primary_engine: AuditEngineMetrics
    shadow_engine: AuditEngineMetrics | None = None
    trade_count_agreement: bool
    entry_date_agreement_rate: float
    exit_date_agreement_rate: float
    return_delta: float
    max_drawdown_delta: float
    sharpe_delta: float
    cost_delta: float
    duration_ms: int
    mismatches: list[AuditMismatch] = Field(default_factory=list)
    limitations: list[str] = Field(default_factory=list)
```

在 `SingleStockForecastResult` 增加可选 `backtest_audit`，报告版本改为 v7；不足数据分支允许为空。将 `backtesting==0.6.6` 加入依赖并更新 lock。

- [ ] **Step 4: 运行契约测试并确认通过**

Run: `cd market-data-service && uv run pytest tests/test_forecast_backtest_audit.py -q`

Expected: PASS。

- [ ] **Step 5: 提交并推送**

```bash
git add market-data-service/pyproject.toml market-data-service/uv.lock market-data-service/src/finscope_market_data/forecast/schemas.py market-data-service/tests/test_forecast_backtest_audit.py
git commit -m "feat: 增加影子回测审计契约"
git push
```

### Task 2: 标准信号与 Backtesting.py 独立适配器

**Files:**
- Create: `market-data-service/src/finscope_market_data/forecast/backtesting_adapter.py`
- Test: `market-data-service/tests/test_forecast_backtesting_adapter.py`

- [ ] **Step 1: 写失败的执行测试**

覆盖：概率低于阈值不交易；信号日后下一根开盘入场；固定持有后下一开盘退出；持仓期间忽略信号；无信号返回零交易；第三方异常返回结构化失败而不是抛出。

期望 API：

```python
events = build_signal_events(samples, observations, threshold=0.60)
result = run_shadow_backtest(bars, events, round_trip_cost=0.0015)
```

- [ ] **Step 2: 运行并确认 RED**

Run: `cd market-data-service && uv run pytest tests/test_forecast_backtesting_adapter.py -q`

Expected: FAIL，模块不存在。

- [ ] **Step 3: 实现独立适配器**

定义不可变 `SignalEvent`、`ShadowTrade`、`ShadowBacktestResult`。DataFrame 只从 `DailyBar` 构建；Strategy 只读取预先生成的 entry/exit 标记；使用下一根开盘成交、`exclusive_orders=True`、`hedging=False`、`finalize_trades=True`。不得调用 `simulate_strategy()` 或其私有数学函数。

- [ ] **Step 4: 运行并确认 GREEN**

Run: `cd market-data-service && uv run pytest tests/test_forecast_backtesting_adapter.py -q`

Expected: PASS。

- [ ] **Step 5: 提交并推送**

```bash
git add market-data-service/src/finscope_market_data/forecast/backtesting_adapter.py market-data-service/tests/test_forecast_backtesting_adapter.py
git commit -m "feat: 接入独立影子回测引擎"
git push
```

### Task 3: 逐笔差分审计器

**Files:**
- Create: `market-data-service/src/finscope_market_data/forecast/backtest_audit.py`
- Test: `market-data-service/tests/test_forecast_backtest_audit.py`

- [ ] **Step 1: 写失败的差分测试**

覆盖完全一致为 `PASS`；浮点容差内为 `PASS`；交易缺失、入场错位、退出错位为 `WARNING`；影子异常为 `UNAVAILABLE`；mismatch 必须定位交易序号和两边值。

- [ ] **Step 2: 运行并确认 RED**

Run: `cd market-data-service && uv run pytest tests/test_forecast_backtest_audit.py -q`

Expected: FAIL，`audit_backtests()` 不存在。

- [ ] **Step 3: 实现审计器**

使用固定容差常量，并将原生 `BacktestReport` 与 `ShadowBacktestResult` 分别映射为公开 schema。结构性差异一定为 `WARNING`；不可用时原生摘要仍完整返回。

- [ ] **Step 4: 运行并确认 GREEN**

Run: `cd market-data-service && uv run pytest tests/test_forecast_backtest_audit.py -q`

Expected: PASS。

- [ ] **Step 5: 提交并推送**

```bash
git add market-data-service/src/finscope_market_data/forecast/backtest_audit.py market-data-service/tests/test_forecast_backtest_audit.py
git commit -m "feat: 增加逐笔差分回测审计"
git push
```

### Task 4: 接入共同预测入口并增强参数鲁棒性

**Files:**
- Modify: `market-data-service/src/finscope_market_data/forecast/service.py`
- Modify: `market-data-service/src/finscope_market_data/forecast/stability.py`
- Modify: `market-data-service/src/finscope_market_data/forecast/schemas.py`
- Modify: `market-data-service/src/finscope_market_data/discovery/service.py`
- Test: `market-data-service/tests/test_forecast_service.py`
- Test: `market-data-service/tests/test_forecast_stability.py`
- Test: `market-data-service/tests/test_discovery_service.py`

- [ ] **Step 1: 写失败的集成测试**

要求完整预测返回 v7 `backtest_audit`；模拟影子引擎异常时预测仍成功且状态为 `UNAVAILABLE`；股票发现的深度证据携带相同 `forecast_report.backtestAudit`，资格门禁保持原样。稳定性报告要求均值、中位数、跑赢比例、方差、稳健区域大小和场景数。

- [ ] **Step 2: 运行并确认 RED**

Run: `cd market-data-service && uv run pytest tests/test_forecast_service.py tests/test_forecast_stability.py tests/test_discovery_service.py -q`

Expected: FAIL，v7 审计与鲁棒性字段缺失。

- [ ] **Step 3: 最小接入**

在原生 `simulate_strategy()` 完成后调用影子适配器与审计器。所有异常仅在影子边界转换为 `UNAVAILABLE`。扩展 `StabilityReport` 和 `ParameterStability`，只聚合预声明场景，不选取最优参数。发现服务只从共同报告中抽取审计摘要。

- [ ] **Step 4: 运行并确认 GREEN**

Run: `cd market-data-service && uv run pytest tests/test_forecast_service.py tests/test_forecast_stability.py tests/test_discovery_service.py -q`

Expected: PASS。

- [ ] **Step 5: 提交并推送**

```bash
git add market-data-service/src/finscope_market_data/forecast market-data-service/src/finscope_market_data/discovery/service.py market-data-service/tests
git commit -m "feat: 为共同量化入口增加独立验证"
git push
```

### Task 5: Java v7 契约与严格校验

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/forecast/SingleStockForecast.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quant/PythonSingleStockForecastClient.java`
- Modify: `backend/finscope-rpc/src/test/java/com/finscope/rpc/quant/PythonSingleStockForecastClientTest.java`
- Modify: `backend/finscope-domain/src/test/java/com/finscope/domain/quant/forecast/SingleStockForecastContractTest.java`

- [ ] **Step 1: 写失败的 Java 契约测试**

要求 v7 正确反序列化审计指标；非法 status、超界一致率、负耗时或 `PASS` 但不存在影子指标时抛出 `SCHEMA_DRIFT`；v6 历史报告继续可读。

- [ ] **Step 2: 运行并确认 RED**

Run: `cd backend && mvn -pl finscope-rpc -am -Dtest=PythonSingleStockForecastClientTest,SingleStockForecastContractTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，Java 类型和 v7 校验缺失。

- [ ] **Step 3: 实现领域类型和 RPC 校验**

内部 DTO 放在 `finscope-domain` 的 `SingleStockForecast` 稳定契约中；RPC 仅负责外部协议严格校验，不重新计算量化结论。保持现有兼容版本分支。

- [ ] **Step 4: 运行并确认 GREEN**

Run: `cd backend && mvn -pl finscope-rpc -am -Dtest=PythonSingleStockForecastClientTest,SingleStockForecastContractTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

- [ ] **Step 5: 提交并推送**

```bash
git add backend/finscope-domain backend/finscope-rpc
git commit -m "feat: 增加量化验证层跨服务契约"
git push
```

### Task 6: 前端专业化展示

**Files:**
- Create: `frontend/src/features/strategy/BacktestAuditPanel.tsx`
- Create: `frontend/src/features/strategy/BacktestAuditPanel.test.tsx`
- Modify: `frontend/src/features/strategy/quantTypes.ts`
- Modify: `frontend/src/features/strategy/SingleStockForecastPanel.tsx`
- Modify: `frontend/src/features/strategy/StockDiscoveryPanel.tsx`
- Modify: `frontend/src/features/strategy/SingleStockForecastPanel.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: 写失败的组件测试**

覆盖 `PASS`、`WARNING`、`UNAVAILABLE`；显示两套指标、日期一致率、影子说明和差异明细；旧报告无字段时不渲染空壳。股票发现候选显示审计徽标。

- [ ] **Step 2: 运行并确认 RED**

Run: `cd frontend && npm test -- BacktestAuditPanel.test.tsx SingleStockForecastPanel.test.tsx`

Expected: FAIL，新组件和类型不存在。

- [ ] **Step 3: 实现组件与样式**

组件只消费报告数据；视觉采用现有研究纸张、细分隔线和状态色系统，避免独立设计语言。`WARNING` 使用琥珀色，`PASS` 使用低饱和绿色，`UNAVAILABLE` 使用中性灰；所有状态同时有文字，不仅依赖颜色。

- [ ] **Step 4: 运行并确认 GREEN**

Run: `cd frontend && npm test -- BacktestAuditPanel.test.tsx SingleStockForecastPanel.test.tsx StockDiscoveryPanel.test.tsx`

Expected: PASS。

- [ ] **Step 5: 提交并推送**

```bash
git add frontend/src
git commit -m "feat: 展示独立回测审计与鲁棒性"
git push
```

### Task 7: 完整验证与文档收口

**Files:**
- Modify: `docs/量化研究-多周期单股预测论文调研与工程决策.md`

- [ ] **Step 1: 运行 Python 全量测试**

Run: `cd market-data-service && uv run pytest -q`

Expected: PASS，无 warning/error。

- [ ] **Step 2: 运行后端相关与全量测试**

Run: `cd backend && mvn test`

Expected: BUILD SUCCESS。

- [ ] **Step 3: 运行前端测试和生产构建**

Run: `cd frontend && npm test && npm run build`

Expected: PASS 且构建成功。

- [ ] **Step 4: 执行规范自检**

检查依赖方向、Java 字段注入、所有 Java `if/for` 大括号、异常降级边界、报告兼容、无密钥输出、`git diff --check` 和工作树范围。

- [ ] **Step 5: 更新文档并提交推送**

记录 Backtesting.py 0.6.6、AGPL-3.0、影子模式、失败降级、指标定义和“本期不参与推荐门禁”。

```bash
git add docs/量化研究-多周期单股预测论文调研与工程决策.md
git commit -m "docs: 补充独立回测验证说明"
git push
```
