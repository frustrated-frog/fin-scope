# Quant Probabilistic Ranking Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为单股预测和股票发现增加收益分布、时序可信区间、横截面排序挑战者及回测选择偏差审计。

**Architecture:** Python 保持量化计算唯一实现，新增三个职责单一的模块；现有 forecast/discovery service 只编排并输出 V10 契约。Java 仅承接稳定 DTO 和持久化 JSON，React 使用现有研究终端样式展示新增证据。

**Tech Stack:** Python 3.11、NumPy、scikit-learn、FastAPI/Pydantic、Java 21/Spring Boot 2.7/Lombok、React 18/TypeScript/Vitest。

---

### Task 1: 收益分布与时序可信区间

**Files:**
- Create: `market-data-service/src/finscope_market_data/forecast/return_distribution.py`
- Create: `market-data-service/tests/test_forecast_return_distribution.py`
- Modify: `market-data-service/src/finscope_market_data/forecast/schemas.py`
- Modify: `market-data-service/src/finscope_market_data/forecast/service.py`
- Modify: `market-data-service/tests/test_forecast_service.py`

- [ ] 写失败测试，证明分位数有序、校准区与锁定区隔离、conformal 区间覆盖审计可输出。
- [ ] 运行 `pytest -q tests/test_forecast_return_distribution.py`，确认因模块不存在失败。
- [ ] 实现三个轻量 `HistGradientBoostingRegressor(loss="quantile")`、时序切分和 conformal 残差修正。
- [ ] 将 `ReturnDistributionReport` 接入 `SingleStockForecastResult`，V10 使用 P10/P50/P90 替换历史相似样本区间。
- [ ] 运行目标测试和 `tests/test_forecast_service.py`。
- [ ] 使用 `feat: 增加收益分布与时序可信区间` 提交并推送。

### Task 2: 横截面 pairwise 排序挑战者

**Files:**
- Create: `market-data-service/src/finscope_market_data/discovery/pairwise_ranker.py`
- Create: `market-data-service/tests/test_discovery_pairwise_ranker.py`
- Modify: `market-data-service/src/finscope_market_data/discovery/schemas.py`
- Modify: `market-data-service/src/finscope_market_data/discovery/ranking.py`
- Modify: `market-data-service/src/finscope_market_data/discovery/service.py`
- Modify: `market-data-service/tests/test_discovery_ranking.py`
- Modify: `market-data-service/tests/test_discovery_service.py`

- [ ] 写失败测试，证明只生成同日 pair、输入顺序不影响排名、样本不足时返回 `SHADOW_ACCUMULATING`。
- [ ] 运行 `pytest -q tests/test_discovery_pairwise_ranker.py`，确认预期失败。
- [ ] 实现带 L2 正则的 pairwise 排序模型、日期切分、Rank IC 与 Top-K 超额评测。
- [ ] 将挑战者报告接入 discovery 契约；没有历史冻结观察时正式路径保持 V9 排序。
- [ ] 运行 discovery 目标测试。
- [ ] 使用 `feat: 增加股票发现排序挑战者` 提交并推送。

### Task 3: DSR、PBO 与试验审计

**Files:**
- Create: `market-data-service/src/finscope_market_data/forecast/selection_bias.py`
- Create: `market-data-service/tests/test_forecast_selection_bias.py`
- Modify: `market-data-service/src/finscope_market_data/forecast/schemas.py`
- Modify: `market-data-service/src/finscope_market_data/forecast/service.py`
- Modify: `market-data-service/tests/test_forecast_service.py`

- [ ] 写失败测试，覆盖单策略不足、试验数惩罚、固定种子 PBO 与非有限输入拒绝。
- [ ] 运行 `pytest -q tests/test_forecast_selection_bias.py`，确认预期失败。
- [ ] 实现 PSR、DSR、最小历史长度和有界 CSCV/PBO。
- [ ] 从模型资格赛与参数稳定性场景生成真实试验计数和样本外收益，接入 V10 报告。
- [ ] 将 DSR/PBO 纳入稳健结论降级逻辑，但不阻断基础报告。
- [ ] 运行 forecast 目标测试。
- [ ] 使用 `feat: 增加量化选择偏差审计` 提交并推送。

### Task 4: Java 稳定契约

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/forecast/SingleStockForecast.java`
- Modify: `backend/finscope-rpc/src/test/java/com/finscope/rpc/quant/PythonSingleStockForecastClientTest.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/discovery/StockDiscoveryReport.java`
- Modify: `backend/finscope-rpc/src/test/java/com/finscope/rpc/quant/PythonStockDiscoveryClientTest.java`

- [ ] 先扩展 RPC 契约测试，要求 V10 收益分布、选择偏差和排序挑战者字段正确反序列化。
- [ ] 运行两个 RPC 测试，确认因 DTO 缺失失败。
- [ ] 使用 Lombok `@Data` 在 domain 正确位置增加稳定 DTO，不在 Controller 或 Service 重复定义。
- [ ] 运行 domain/rpc 目标测试。
- [ ] 使用 `feat: 承接量化V10专业证据契约` 提交并推送。

### Task 5: 双页面专业可视化

**Files:**
- Modify: `frontend/src/features/strategy/quantTypes.ts`
- Create: `frontend/src/features/strategy/ForecastReturnPrism.tsx`
- Create: `frontend/src/features/strategy/ForecastProfessionalAudit.tsx`
- Modify: `frontend/src/features/strategy/SingleStockForecastPanel.tsx`
- Modify: `frontend/src/features/strategy/StockDiscoveryVisuals.tsx`
- Modify: `frontend/src/features/strategy/SingleStockForecastPanel.test.tsx`
- Modify: `frontend/src/features/strategy/StockDiscoveryVisuals.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] 写失败组件测试，要求收益轴、覆盖率、DSR/PBO、排序挑战者状态和降级说明可见。
- [ ] 运行目标 Vitest，确认新增文案不存在而失败。
- [ ] 实现“概率—收益棱镜”及专业审计组件，复用现有颜色和排版系统并支持移动端。
- [ ] 在股票发现候选中展示微型收益区间，缺失时显示明确积累状态。
- [ ] 运行目标前端测试与生产构建。
- [ ] 使用 `feat: 展示量化收益分布与过拟合审计` 提交并推送。

### Task 6: 全量验证与规范自检

**Files:**
- Modify: `docs/superpowers/specs/2026-08-30-quant-probabilistic-ranking-audit-design.md`（仅在验证发现契约偏差时）
- Modify: `docs/superpowers/plans/2026-08-30-quant-probabilistic-ranking-audit.md`（勾选完成项）

- [ ] 对照《项目开发规范与代码评审清单》检查模块落点、Java 字段注入、完整大括号、异常边界和未使用代码。
- [ ] 运行 `cd market-data-service && pytest -q`。
- [ ] 运行 `cd backend && mvn test`。
- [ ] 运行 `cd frontend && npm test -- --run && npm run build`。
- [ ] 检查 `git diff --check`、`git status --short` 和提交历史。
- [ ] 使用 `docs: 完成量化V10实施与验证记录` 提交并推送。
