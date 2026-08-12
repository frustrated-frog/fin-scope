# 单股预测真实结算与模型健康度实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让每次单股预测在到期后按冻结交易口径自动结算，并以真实外推结果决定模型继续输出方向、进入观察或自动暂停。

**Architecture:** Python 继续独占模型训练与回测；Java 使用 `PythonQuantDailyBarSource` 读取服务端 QFQ 日线，独立结算不可变预测记录。健康度按“股票 × 周期 × 模型版本”从去重后的已结算记录动态聚合，预测服务在调用 Python 前读取上一时点健康状态，暂停时保留完整报告但把方向降级为 `ABSTAIN`。前端在现有研究档案中增加模型体检条和真实结果账本。

**Tech Stack:** Java 21、Spring Boot 2.7、SQLite/JdbcTemplate、React、TypeScript、Vitest、Maven、Python 行情服务。

---

### Task 1：扩展结算持久化契约

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/forecast/SingleStockForecastRun.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/quant/SingleStockForecastRunRepository.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/quant/SingleStockForecastRunRepositoryTest.java`

- [ ] 先写失败测试，要求结算字段可持久化且重复结算为幂等更新。
- [ ] 增加实际入场/退出日期与价格、净收益、实际方向、命中结果、结算时间和说明字段。
- [ ] 增加待结算查询与条件更新；只允许 `PENDING` 进入终态。
- [ ] 运行 DAO 测试并提交 `feat: 增加预测真实结算记录`。

### Task 2：实现真实到期结算

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/ForecastOutcomeSettlementService.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/quant/forecast/ForecastOutcomeSettlementServiceTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/SingleStockForecastService.java`

- [ ] 先写失败测试覆盖 T+1 入场、T+N+1 退出、成本扣除、弃权不判命中、未到期保持等待、缺失信号日转不可用与重复调用幂等。
- [ ] 使用 `PythonQuantDailyBarSource` 每个标的批量读取一次最多 5000 根 QFQ 日线。
- [ ] 后续运行、历史读取和详情读取前触发有界结算，不在数据库事务中包裹远程调用。
- [ ] 运行服务测试并提交 `feat: 自动结算到期预测结果`。

### Task 3：模型健康度与自动暂停恢复

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/forecast/ForecastModelHealth.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/ForecastModelHealthService.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/quant/forecast/ForecastModelHealthServiceTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/SingleStockForecastService.java`

- [ ] 先写失败测试覆盖样本不足、健康、观察、暂停和滚动窗口恢复。
- [ ] 聚合去重已结算记录的 Brier、Log Loss、方向命中率、覆盖率、弃权率、实际上涨率和 0.5 无信息基准。
- [ ] 至少 8 个真实样本后启用门禁；最近 20 个样本 Brier 明显劣于 0.25 或方向命中率低于 45% 时暂停，恢复需滚动窗口重新达到基准。
- [ ] 暂停时不篡改概率和研究证据，只把新报告方向降级为 `ABSTAIN` 并解释原因。
- [ ] 运行服务测试并提交 `feat: 增加模型健康门禁`。

### Task 4：API 与模型体检前端

**Files:**
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/SingleStockForecastController.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/response/quant/SingleStockForecastRunResponse.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/response/quant/ForecastModelHealthResponse.java`
- Modify: `frontend/src/features/strategy/quantTypes.ts`
- Modify: `frontend/src/features/strategy/SingleStockForecastPanel.tsx`
- Modify: `frontend/src/features/strategy/SingleStockForecastPanel.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] 先写失败测试，要求详情展示模型健康状态、真实入退场、实际收益、命中/弃权及样本口径。
- [ ] 增加按股票和周期查询健康度的只读接口，并在运行详情中返回对应健康度。
- [ ] 设计“模型体检档案”：状态刻度为签名元素，主信息先展示真实样本量、Brier 对基准、方向命中、覆盖率；技术口径渐进展开。
- [ ] 保持现有深蓝灰研究档案语言，完善暗色、窄屏、键盘焦点和空状态。
- [ ] 运行前端测试与构建并提交 `feat: 展示预测真实验证健康度`。

### Task 5：全链路验证与交付

- [ ] 运行预测相关 Java 测试、后端全模块测试编译、前端测试与生产构建。
- [ ] 使用固定日线样本验证结算日期和净收益口径，无未来数据泄漏。
- [ ] 检查 `git diff --check`、数据库兼容升级与工作区状态。
- [ ] 推送 `codex/forecast-outcome-health`，记录验证结果与已知样本量限制。
