# Single-Stock Forecast Research Ledger Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将单股概率预测升级为可持久化回看的完整研究报告，包含同股买入持有基准、风险与交易指标、样本内外、年度和趋势阶段、因子解释、参数稳定性及隔离的真实持仓上下文。

**Architecture:** Python 生成版本化的完整量化报告，Java 仅校验、附加持仓快照并将每次运行作为不可变 JSON 存入 SQLite，React 提供运行历史与报告阅读器。历史详情不重新计算；真实持仓不进入模型和回测。

**Tech Stack:** Python 3.13、NumPy、FastAPI、Pydantic、Java 8、Spring Boot 2.7、SQLite、Jackson、React、TypeScript、Vitest。

---

## 文件结构

- Create `market-data-service/src/finscope_market_data/forecast/factor_catalog.py`：七个因子的知识元数据和当前贡献解释。
- Create `market-data-service/src/finscope_market_data/forecast/performance.py`：净值、基准、回撤、交易、年度和阶段指标。
- Create `market-data-service/src/finscope_market_data/forecast/stability.py`：固定邻域参数稳健性分析。
- Modify `market-data-service/src/finscope_market_data/forecast/features.py`：支持可配置持有期并暴露因子代码。
- Modify `market-data-service/src/finscope_market_data/forecast/logistic.py`：暴露标准化值与因子贡献。
- Modify `market-data-service/src/finscope_market_data/forecast/walk_forward.py`：补充样本内和可交易的样本外序列。
- Modify `market-data-service/src/finscope_market_data/forecast/schemas.py`：定义版本化完整报告 DTO。
- Modify `market-data-service/src/finscope_market_data/forecast/service.py`：编排报告并生成四级结论。
- Create `backend/finscope-domain/src/main/java/com/finscope/domain/quant/forecast/SingleStockForecastRun.java`：历史运行摘要与详情载体。
- Create `backend/finscope-dao/src/main/java/com/finscope/dao/quant/SingleStockForecastRunRepository.java`：不可变记录写入和查询。
- Modify `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`：创建预测运行表和索引。
- Modify `backend/finscope-domain/src/main/java/com/finscope/domain/strategy/StrategyHolding.java`：增加可选数量和平均成本。
- Modify `backend/finscope-dao/src/main/java/com/finscope/dao/strategy/StrategyHoldingRepository.java`：持久化持仓成本并按股票代码查询。
- Modify `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/SingleStockForecastService.java`：运行、持仓快照、保存、列表和详情编排。
- Modify `backend/finscope-web/src/main/java/com/finscope/web/controller/SingleStockForecastController.java`：增加历史 API。
- Modify `frontend/src/features/strategy/quantTypes.ts`：完整报告类型。
- Rewrite `frontend/src/features/strategy/SingleStockForecastPanel.tsx`：历史轨与分区报告阅读器。
- Modify `frontend/src/styles.css`：新增响应式报告样式。

### Task 1: Python 特征契约与因子知识

**Files:**
- Create: `market-data-service/src/finscope_market_data/forecast/factor_catalog.py`
- Modify: `market-data-service/src/finscope_market_data/forecast/features.py`
- Modify: `market-data-service/src/finscope_market_data/forecast/logistic.py`
- Test: `market-data-service/tests/test_forecast_features.py`
- Test: `market-data-service/tests/test_forecast_logistic.py`

- [ ] **Step 1: 写失败测试**

增加断言：`build_samples(..., horizon_days=15)` 的退出日是 T+15；七个因子顺序与目录一致；模型能够返回每个因子的标准化值和 `weight * normalized_value` 贡献。

```python
assert sample.exit_date == bars[75].trade_date
assert tuple(item.code for item in FACTORS) == FEATURE_CODES
assert len(model.contributions(current)) == 7
```

- [ ] **Step 2: 验证测试因缺少接口而失败**

Run: `cd market-data-service && .venv/bin/pytest -q tests/test_forecast_features.py tests/test_forecast_logistic.py`

- [ ] **Step 3: 最小实现**

`build_samples` 接收 `horizon_days=20`；新增固定 `FEATURE_CODES`。`FactorDefinition` 包含 code/name/category/formula/window/economic_meaning/boundary。模型新增：

```python
def normalized(self, features: Sequence[float]) -> tuple[float, ...]: ...
def contributions(self, features: Sequence[float]) -> tuple[float, ...]: ...
```

- [ ] **Step 4: 运行测试并提交**

Run: `cd market-data-service && .venv/bin/pytest -q tests/test_forecast_features.py tests/test_forecast_logistic.py`

Commit: `feat: 增加单股预测因子解释契约`

### Task 2: Python 虚拟策略和同股基准

**Files:**
- Create: `market-data-service/src/finscope_market_data/forecast/performance.py`
- Create: `market-data-service/tests/test_forecast_performance.py`

- [ ] **Step 1: 写失败测试**

使用短的确定性价格序列与概率序列，验证 T+1 开盘买入、到期收盘卖出、持仓不重叠、总成本 15 bps、同股基准和空仓日净值。

```python
report = simulate_strategy(bars, observations, threshold=0.60, holding_days=20, round_trip_cost=0.0015)
assert report.trades[0].entry_date == observations[0].entry_date
assert report.total_cost > 0
assert report.benchmark.label == "同股买入并持有"
```

- [ ] **Step 2: 验证 RED**

Run: `cd market-data-service && .venv/bin/pytest -q tests/test_forecast_performance.py`

- [ ] **Step 3: 实现绩效模块**

定义 `EquityPoint`、`TradeSummary`、`DrawdownEpisode`、`PerformanceSummary` 和 `BacktestReport`。使用 242 个交易日年化，Sharpe 无风险利率默认 0；最大回撤持续时间从峰值日计至恢复日，未恢复时截止评估末日。

- [ ] **Step 4: 补充风险与交易测试并提交**

覆盖年化波动率、Sharpe、日胜率、盈利交易胜率、单边换手率、持仓占比、平均持仓期和未恢复回撤。

Commit: `feat: 实现单股策略与同股基准评估`

### Task 3: 年度、趋势阶段和样本内外

**Files:**
- Modify: `market-data-service/src/finscope_market_data/forecast/performance.py`
- Modify: `market-data-service/src/finscope_market_data/forecast/walk_forward.py`
- Test: `market-data-service/tests/test_forecast_performance.py`
- Test: `market-data-service/tests/test_forecast_service.py`

- [ ] **Step 1: 写年度和阶段失败测试**

断言跨年净值按年度边界计算；阶段只使用当前日前 120 日收益，分别输出 `UPTREND`、`RANGE`、`DOWNTREND`。

- [ ] **Step 2: 写样本隔离失败测试**

断言初始 60% 样本只进入样本内诊断；每个样本外观察的 `training_through < signal_date`；最终评级不使用样本内结果升级。

- [ ] **Step 3: 实现并验证**

Run: `cd market-data-service && .venv/bin/pytest -q tests/test_forecast_performance.py tests/test_forecast_service.py`

- [ ] **Step 4: 提交**

Commit: `feat: 增加单股分期与样本内外评估`

### Task 4: 相邻参数稳定性与完整 Python 报告

**Files:**
- Create: `market-data-service/src/finscope_market_data/forecast/stability.py`
- Modify: `market-data-service/src/finscope_market_data/forecast/schemas.py`
- Modify: `market-data-service/src/finscope_market_data/forecast/service.py`
- Test: `market-data-service/tests/test_forecast_stability.py`
- Test: `market-data-service/tests/test_forecast_service.py`
- Test: `market-data-service/tests/test_api.py`

- [ ] **Step 1: 写五场景失败测试**

固定场景为 `(20, .60)`、`(20, .55)`、`(20, .65)`、`(15, .60)`、`(25, .60)`；验证主场景不因邻域收益变化而被替换。

- [ ] **Step 2: 定义完整 Pydantic DTO**

根对象增加 `reportSchemaVersion`、`modelVersion`、`strategyPolicy`、`factorExplanations`、`performance`、`inSample`、`outOfSample`、`annualPerformance`、`regimePerformance`、`parameterStability` 和 `equityCurve`。

- [ ] **Step 3: 实现四级确定性结论**

结论只取 `ROBUST`、`CONDITIONAL`、`NO_CLEAR_EDGE`、`INSUFFICIENT_DATA`；判定同时检查独立样本数、相对基准超额收益、Sharpe、回撤和邻域方向一致率。

- [ ] **Step 4: 运行 Python 全量并提交**

Run: `cd market-data-service && .venv/bin/pytest -q`

Commit: `feat: 输出单股完整稳健性研究报告`

### Task 5: Java 报告契约与不可变运行表

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/forecast/SingleStockForecast.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/forecast/SingleStockForecastRun.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/quant/SingleStockForecastRunRepository.java`
- Create: `backend/finscope-dao/src/test/java/com/finscope/dao/quant/SingleStockForecastRunRepositoryTest.java`

- [ ] **Step 1: 写 DAO 失败测试**

连续保存两条相同指纹运行，断言生成不同 ID，第二条 `sameDataAsPrevious=true`；按股票筛选倒序；详情 JSON 与保存内容一致。

- [ ] **Step 2: 创建表和索引**

```sql
CREATE TABLE IF NOT EXISTS single_stock_forecast_run (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 instrument_code TEXT NOT NULL,
 as_of_date TEXT NOT NULL,
 status TEXT NOT NULL,
 up_probability REAL,
 data_fingerprint TEXT NOT NULL,
 model_version TEXT NOT NULL,
 report_schema_version TEXT NOT NULL,
 same_data_as_previous INTEGER NOT NULL DEFAULT 0,
 report_json TEXT NOT NULL,
 holding_snapshot_json TEXT,
 created_at TEXT NOT NULL
)
```

- [ ] **Step 3: 实现仓储并验证**

Run: `cd backend && mvn -pl finscope-dao -am -Dtest=SingleStockForecastRunRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 4: 提交**

Commit: `feat: 持久化单股预测研究记录`

### Task 6: 真实持仓快照和服务编排

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/strategy/StrategyHolding.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/strategy/StrategyHoldingRepository.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/strategy/StrategyHoldingService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/SingleStockForecastService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/quant/forecast/SingleStockForecastServiceTest.java`

- [ ] **Step 1: 写服务失败测试**

验证预测完成后保存一次、相同数据仍保存、股票持仓快照包含数量/均价但不传给 Python client、查询旧详情不调用 Python。

- [ ] **Step 2: 扩展持仓字段**

使用幂等 `ensureColumn` 增加可空 `quantity REAL` 与 `average_cost REAL`；保存和更新时允许两者同时为空，填写时必须为非负数。

- [ ] **Step 3: 实现运行编排**

`forecast(code)` 顺序固定为：调用 Python → 严格校验 → 查询同代码股票持仓 → 生成确定性持仓解释 → 在单一保存操作中写入报告和快照 → 返回带 ID 的详情。

- [ ] **Step 4: 运行测试并提交**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=SingleStockForecastServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Commit: `feat: 关联单股预测与真实持仓快照`

### Task 7: 历史 REST API

**Files:**
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/SingleStockForecastController.java`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/controller/SingleStockForecastControllerTest.java`

- [ ] **Step 1: 写失败测试**

覆盖 POST 返回运行 ID、GET 列表的 code/limit 校验和 GET 详情 404。

- [ ] **Step 2: 实现接口**

```java
@GetMapping public ApiResponse<List<SingleStockForecastRun>> history(...)
@GetMapping("/{id}") public ApiResponse<SingleStockForecastRun> detail(@PathVariable Long id)
```

- [ ] **Step 3: 运行测试并提交**

Run: `cd backend && mvn -pl finscope-web -am -Dtest=SingleStockForecastControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Commit: `feat: 提供单股预测历史查询接口`

### Task 8: 前端完整报告阅读器

**Files:**
- Modify: `frontend/src/features/strategy/quantTypes.ts`
- Rewrite: `frontend/src/features/strategy/SingleStockForecastPanel.tsx`
- Modify: `frontend/src/features/strategy/SingleStockForecastPanel.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: 写前端失败测试**

验证初始加载历史、每次运行后新增记录、点击旧记录只 GET 详情、页面出现“同股买入并持有”、回撤持续时间、Sharpe、持仓占比、因子解释、样本内/样本外、参数稳定性、年度和趋势阶段。

- [ ] **Step 2: 使用现有组件和 CSS 实现布局**

不用新增图表依赖；双净值曲线用响应式 SVG polyline，历史轨和表格使用语义化按钮/table，窄屏转为单列。详情区按“结论 → 绩效 → 因子 → 稳定性 → 数据出处”排序。

- [ ] **Step 3: 验证无障碍和响应式行为**

曲线提供文字摘要和 `aria-label`，表格表头完整，状态不只依赖颜色，历史按钮具有选中状态。

- [ ] **Step 4: 运行测试与构建并提交**

Run: `cd frontend && npm test -- --run src/features/strategy/SingleStockForecastPanel.test.tsx && npm run build`

Commit: `feat: 完善单股预测历史与研究报告页面`

### Task 9: 全链路验证

**Files:**
- Verify only.

- [ ] **Step 1: 运行全量验证**

Run:

```bash
cd market-data-service && .venv/bin/pytest -q
cd backend && mvn test
cd frontend && npm test -- --run && npm run build
```

- [ ] **Step 2: 真实股票验收**

启动 Python 与 Java，连续两次预测 `603618`；断言两次均为 HTTP 200、ID 不同、第二次 `sameDataAsPrevious=true`，历史列表含两条记录，详情的策略基准标签为“同股买入并持有”，且概率、指纹与 Python 报告一致。

- [ ] **Step 3: 检查仓库并推送**

Run: `git diff --check && git status --short --branch && git push`

Expected: 无未提交源码改动，当前分支与远端同步。
