# Quant Accuracy Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立股票发现的真实到期验证账本、点时因子增强、模型赛马和预测能力仪表盘。

**Architecture:** Python 保持量化计算权威，提供点时因子与无状态评测；Java 复用现有行情 RPC、SQLite、定时任务和 REST 基座完成冻结、幂等结算与编排；React 在股票发现页独立加载评测，不阻断最新选股结果。

**Tech Stack:** Python 3.13、FastAPI、Pydantic、NumPy；Java 21、Spring Boot、JdbcTemplate、SQLite；React、TypeScript、Vitest。

---

### Task 1: Python 真实评测内核

**Files:**
- Create: `market-data-service/src/finscope_market_data/discovery/evaluation.py`
- Modify: `market-data-service/src/finscope_market_data/discovery/schemas.py`
- Modify: `market-data-service/src/finscope_market_data/app.py`
- Test: `market-data-service/tests/test_discovery_evaluation.py`
- Test: `market-data-service/tests/test_api.py`

- [ ] 先写失败测试，定义重复观察拒绝、Brier/Log Loss/ECE、滚动窗口、Top-K 对照、板块表现和模型晋升门槛。
- [ ] 运行 `market-data-service/.venv/bin/pytest -q market-data-service/tests/test_discovery_evaluation.py`，确认因评测类型和函数不存在而失败。
- [ ] 实现 `evaluate_discovery_outcomes(request)`，所有聚合按 `as_of_date` 稳定排序，空样本返回 `ACCUMULATING`。
- [ ] 增加 `POST /v1/quant/stock-discovery-evaluations`，使用 Pydantic `extra='forbid'` 契约。
- [ ] 运行聚焦测试并提交 `feat: 增加股票发现真实评测内核`。

### Task 2: Python 点时上下文与截面因子

**Files:**
- Create: `market-data-service/src/finscope_market_data/discovery/context_factors.py`
- Modify: `market-data-service/src/finscope_market_data/discovery/service.py`
- Modify: `market-data-service/src/finscope_market_data/discovery/ranking.py`
- Test: `market-data-service/tests/test_discovery_context_factors.py`
- Test: `market-data-service/tests/test_discovery_service.py`
- Test: `market-data-service/tests/test_discovery_ranking.py`

- [ ] 先写失败测试，证明行业宽度、行业相对强弱、同花顺资金排名质量和成交活跃度排名只使用当前批次数据。
- [ ] 写失败测试，证明股票发现只获取一次沪深 300 历史，并在下游失败时降级而不终止选股。
- [ ] 实现 `enrich_context_factors`，按行业成员集合聚合并对并列值使用中位百分位。
- [ ] 将增强因子纳入轻量排名，并保持风险项与正向项分离。
- [ ] 使用 `build_aligned_context` 把沪深 300 历史传入深度预测和面板训练。
- [ ] 运行聚焦测试并提交 `feat: 增强股票发现点时截面因子`。

### Task 3: Java 结果账本与数据库迁移

**Files:**
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/discovery/StockDiscoveryCandidate.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/discovery/StockDiscoveryModelPrediction.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/quant/StockDiscoveryRepository.java`
- Modify: `backend/finscope-dao/src/test/java/com/finscope/dao/quant/StockDiscoveryRepositoryTest.java`

- [ ] 先写 DAO 失败测试，要求合格候选为 `PENDING`、拒绝候选为 `NOT_APPLICABLE`，并保存深度候选的全部模型影子概率。
- [ ] 增加候选结算列、模型预测表、唯一约束和待结算索引。
- [ ] 扩展 Repository 的保存、待结算扫描、条件结算、不可用标记、评测观察查询。
- [ ] 保证候选与模型结算在短事务内同步，影响行数不为 1 时拒绝覆盖。
- [ ] 运行 `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH mvn -pl finscope-dao -am -Dtest=StockDiscoveryRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test` 并提交 `feat: 增加股票发现到期结果账本`。

### Task 4: Java 自动结算与 Python 评测适配

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/discovery/StockDiscoveryAccuracyReport.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quant/PythonStockDiscoveryEvaluationClient.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/quant/discovery/StockDiscoveryOutcomeService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/quant/discovery/StockDiscoveryScheduler.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/quant/discovery/StockDiscoveryService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/StockDiscoveryController.java`
- Test: matching RPC, service and controller test files.

- [ ] 先写失败测试，覆盖成熟、等待、信号日缺失、行情异常、重复结算和按代码复用行情请求。
- [ ] 实现结算服务，使用现有 `QuantDailyBarSource`，外部调用在事务外执行。
- [ ] 先写 RPC 失败测试，要求拒绝非有限指标、错误窗口和模型角色。
- [ ] 实现 Python 评测客户端与 `/accuracy` 接口。
- [ ] 增加独立定时结算入口；失败日志不得改变股票发现批次状态，也不得影响热点链路。
- [ ] 运行相关 Java 测试并提交 `feat: 自动结算股票发现真实结果`。

### Task 5: 预测能力仪表盘

**Files:**
- Create: `frontend/src/features/strategy/StockDiscoveryAccuracyPanel.tsx`
- Create: `frontend/src/features/strategy/StockDiscoveryAccuracyPanel.css`
- Create: `frontend/src/features/strategy/StockDiscoveryAccuracyPanel.test.tsx`
- Modify: `frontend/src/features/strategy/StockDiscoveryPanel.tsx`
- Modify: `frontend/src/features/strategy/StockDiscoveryPanel.test.tsx`
- Modify: `frontend/src/features/strategy/quantTypes.ts`

- [ ] 先写失败测试，覆盖积累中、健康、观察、可靠性图、Top-K、模型赛马、板块与最近结算。
- [ ] 实现独立加载与降级；评测失败不得隐藏最新选股结果。
- [ ] 使用大字号坐标、完整绘图区、响应式表格和清晰的“样本不足”文案。
- [ ] 运行聚焦测试与 `npm run build`，提交 `feat: 增加股票发现预测能力仪表盘`。

### Task 6: 验收与规范自检

**Files:**
- Modify: `docs/superpowers/plans/2026-08-20-quant-accuracy-loop.md`

- [ ] 运行 Python 全量测试。
- [ ] 使用 JDK 21 运行 Maven 全量测试。
- [ ] 运行前端全量测试和生产构建。
- [ ] 运行 `git diff --check`，检查字段注入、Java `if/for` 大括号、类的模块落点和数据库影响行数。
- [ ] 将验收项改为完成，提交 `chore: 完成量化真实验证闭环验收` 并推送当前分支。
