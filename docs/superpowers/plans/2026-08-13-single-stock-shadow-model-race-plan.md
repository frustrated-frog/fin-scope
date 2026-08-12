# Single-Stock Shadow Model Race Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为单股预测增加轻量多模型时序竞赛、冻结影子预测、真实到期成对比较和保守晋升资格。

**Architecture:** Python 生成所有候选的多折历史证据和当前冻结概率；Java 通过稳定 v6 契约接收并持久化候选，现有结算用例传播真实结果，独立赛马服务只读聚合成对样本；React 分离展示历史资格赛和真实影子赛。所有训练、校准和统计口径留在 Python 或领域服务中，Controller 与前端不重算。

**Tech Stack:** Python 3.12、Pydantic、pytest、Java 21、Spring Boot 2.7、SQLite、JUnit 5、React、TypeScript、Vitest。

---

### Task 1: Python 多折竞赛和状态感知候选

**Files:**
- Modify: `market-data-service/src/finscope_market_data/forecast/model_competition.py`
- Modify: `market-data-service/tests/test_forecast_model_competition.py`

- [ ] 写失败测试，要求候选池包含 `REGIME_LOGISTIC`、开发区至少形成三折、训练标签退出日早于各折验证起点，并验证相同输入输出完全一致。
- [ ] 运行 `cd market-data-service && .venv/bin/pytest tests/test_forecast_model_competition.py -q`，确认因缺少状态模型和折指标失败。
- [ ] 实现 `RegimeAwareLogisticModel`、开发区三折扩展窗口验证和折间 Brier 稳定性惩罚；状态样本不足时回退到全局逻辑回归。
- [ ] 重新运行目标测试并确认通过。
- [ ] 提交 `feat: 增加多折状态感知模型竞赛` 并推送。

### Task 2: Python 冻结所有候选的资格与当前概率

**Files:**
- Modify: `market-data-service/src/finscope_market_data/forecast/schemas.py`
- Modify: `market-data-service/src/finscope_market_data/forecast/service.py`
- Modify: `market-data-service/tests/test_forecast_service.py`

- [ ] 写失败测试，要求 v6 报告的每个候选包含角色、版本、原始/校准概率、影子方向、资格状态和锁定测试概率指标。
- [ ] 运行 `cd market-data-service && .venv/bin/pytest tests/test_forecast_service.py -q`，确认新契约字段不存在而失败。
- [ ] 对四个候选分别执行同口径资格检验和校准，正式报告复用冠军资格对象，输出 `single-stock-research-v6` 和稳定候选版本。
- [ ] 运行 forecast 相关 Python 测试并确认通过。
- [ ] 提交 `feat: 冻结候选模型影子预测证据` 并推送。

### Task 3: Java v6 领域与 RPC 契约

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/forecast/SingleStockForecast.java`
- Modify: `backend/finscope-domain/src/test/java/com/finscope/domain/quant/forecast/SingleStockForecastContractTest.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quant/PythonSingleStockForecastClient.java`
- Modify: `backend/finscope-rpc/src/test/java/com/finscope/rpc/quant/PythonSingleStockForecastClientTest.java`

- [ ] 写失败的领域和 RPC 测试，要求 v6 每个候选概率有限且在 `[0,1]`、角色合法、恰有一个冠军、锁定指标样本为正。
- [ ] 运行对应 Maven 测试确认失败原因来自缺失 v6 契约。
- [ ] 增加候选字段和 v6 校验；保持 v2-v5 历史响应兼容。
- [ ] 重跑目标测试并确认通过。
- [ ] 提交 `feat: 扩展影子模型赛马接口契约` 并推送。

### Task 4: 候选预测持久化和幂等结算

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/forecast/ForecastCandidateRun.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/quant/ForecastCandidateRunRepository.java`
- Create: `backend/finscope-dao/src/test/java/com/finscope/dao/quant/ForecastCandidateRunRepositoryTest.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/SingleStockForecastService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/ForecastOutcomeSettlementService.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/quant/forecast/SingleStockForecastServiceTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/quant/forecast/ForecastOutcomeSettlementServiceTest.java`

- [ ] 写 DAO 失败测试覆盖运行内候选唯一性、批量保存、条件结算和同指纹去重证据查询。
- [ ] 运行 DAO 目标测试确认表和 Repository 尚不存在。
- [ ] 增加候选表、索引、领域对象与 Repository；所有写入参数化，结算条件限定 `PENDING`。
- [ ] 写 Service 失败测试，要求正式预测和候选在同一流程完整留痕，主预测到期后传播同一实际结果。
- [ ] 实现候选映射和结算传播，重跑 DAO/Service 测试。
- [ ] 提交 `feat: 保存并结算候选模型影子结果` 并推送。

### Task 5: 真实赛马聚合与晋升资格

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/forecast/ForecastModelRace.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/ForecastModelRaceService.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/quant/forecast/ForecastModelRaceServiceTest.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/forecast/SingleStockForecastRun.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/SingleStockForecastService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/SingleStockForecastController.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/response/quant/SingleStockForecastRunResponse.java`
- Modify: `backend/finscope-web/src/test/java/com/finscope/web/controller/SingleStockForecastControllerTest.java`

- [ ] 写失败测试覆盖少于 12 个样本、冠军领先、无稳定领先和挑战者达到全部晋升门槛。
- [ ] 实现成对样本聚合、Brier/Log Loss/覆盖率计算和保守状态机；不提供自动换模写接口。
- [ ] 增加只读 race 接口，并在预测与详情响应中附带最新赛马结论。
- [ ] 运行 Service/Web 目标测试并确认通过。
- [ ] 提交 `feat: 增加真实模型赛马晋升门禁` 并推送。

### Task 6: 前端双层模型赛马场

**Files:**
- Modify: `frontend/src/features/strategy/quantTypes.ts`
- Create: `frontend/src/features/strategy/ForecastModelRace.tsx`
- Modify: `frontend/src/features/strategy/SingleStockForecastPanel.tsx`
- Modify: `frontend/src/features/strategy/SingleStockForecastPanel.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] 写失败测试，覆盖四候选历史资格赛、冻结概率、真实赛马证据进度和晋升资格标签。
- [ ] 运行 `cd frontend && npm test -- SingleStockForecastPanel.test.tsx` 确认失败。
- [ ] 实现高密度但可读的双层赛马组件，复用现有纸张工作台视觉语言，并为窄屏提供卡片化布局。
- [ ] 运行目标测试、前端全量测试和生产构建。
- [ ] 提交 `feat: 展示模型历史资格赛与真实影子赛` 并推送。

### Task 7: 全量验证与评审

**Files:**
- Modify only if review discovers an in-scope defect.

- [ ] 运行 `cd market-data-service && .venv/bin/pytest -q`。
- [ ] 运行 `cd backend && mvn test`。
- [ ] 运行 `cd frontend && npm test -- --run`。
- [ ] 运行 `cd frontend && npm run build`。
- [ ] 检查 `git diff --check`、工作区状态、数据库索引和旧 v5 兼容路径。
- [ ] 按《项目开发规范与代码评审清单》复审职责边界、幂等、异常、契约、性能和前端信息口径，修复所有 Critical/Important 问题。
- [ ] 提交必要修复并推送当前分支。
