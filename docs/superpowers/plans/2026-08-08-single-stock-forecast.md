# Single Stock Forecast Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在策略工作台中实现基于服务端长历史缓存、20 日可执行收益标签和滚动样本外验证的单标的概率预测。

**Architecture:** Python 行情服务负责最多 5,000 根前复权日线的持久缓存和覆盖完整性；Java 服务负责特征、逻辑回归、滚动验证、结论门禁和 REST 契约；React 以独立组件呈现预测与证据，不把单标的语义塞进现有横截面 TopN 引擎。

**Tech Stack:** Python 3/FastAPI/SQLite/Pydantic、Java 8/Spring Boot 2.7/JUnit 5、React 18/TypeScript/Vitest、SQLite。

---

### Task 1: 长历史行情缓存契约

**Files:**
- Modify: `market-data-service/src/finscope_market_data/app.py`
- Modify: `market-data-service/src/finscope_market_data/router.py`
- Modify: `market-data-service/src/finscope_market_data/providers/eastmoney.py`
- Test: `market-data-service/tests/test_router.py`
- Test: `market-data-service/tests/test_api.py`

- [ ] **Step 1: 写入缓存覆盖不足的失败测试**

构造已缓存 120 根、随后请求 2,500 根的场景，断言 provider 再次收到 `limit=2500`，返回结果长度大于 120；同时保留“缓存 2,500 根后请求 120 根不访问 provider”的测试。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd market-data-service && .venv/bin/pytest tests/test_router.py tests/test_api.py -q`

Expected: 新增长历史覆盖测试因 fresh snapshot 未检查记录数而失败。

- [ ] **Step 3: 实现 5,000 根上限和覆盖检查**

将 API 与 router 上限统一为 5,000；`_fresh_daily_bar_snapshot` 接收 `requested_limit`，只有 `len(stored.data) >= min(requested_limit, available_history)` 时才命中。首版以 provider 实际返回长度作为可用历史边界；相同标的后续短请求只切片缓存。

- [ ] **Step 4: 运行 Python 测试**

Run: `cd market-data-service && .venv/bin/pytest tests/test_router.py tests/test_api.py tests/test_providers.py -q`

Expected: PASS。

- [ ] **Step 5: 提交并推送**

```bash
git add market-data-service docs/superpowers
git commit -m "feat: 扩展单股长历史行情缓存"
git push -u origin codex/single-stock-forecast
```

### Task 2: 单标的预测领域契约与特征

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/forecast/SingleStockForecast.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/forecast/ForecastObservation.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/SingleStockFeatureBuilder.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/ForecastSample.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/quant/forecast/SingleStockFeatureBuilderTest.java`

- [ ] **Step 1: 写特征和标签失败测试**

测试第 T 行特征只读取 `<=T` 的行情，并断言标签严格使用 `open(T+1)` 与 `close(T+20)`，成本从毛收益中扣除；缺少 60 日历史时不产生样本。

- [ ] **Step 2: 运行定向测试确认失败**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=SingleStockFeatureBuilderTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，类型尚不存在。

- [ ] **Step 3: 实现不可变样本构造**

实现固定七维特征：5/20/60 日收益、MA20/MA60 距离、20 日波动、20/60 日成交额比；输出信号日、入场日、退出日、净收益和二元标签，并拒绝重复日期、非法 OHLC 与空成交额。

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=SingleStockFeatureBuilderTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

- [ ] **Step 5: 提交并推送**

```bash
git add backend
git commit -m "feat: 增加单股预测特征与收益标签"
git push
```

### Task 3: 逻辑回归与滚动样本外验证

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/RegularizedLogisticModel.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/SingleStockWalkForwardValidator.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/quant/forecast/RegularizedLogisticModelTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/quant/forecast/SingleStockWalkForwardValidatorTest.java`

- [ ] **Step 1: 写模型确定性与无泄漏失败测试**

用可分离合成样本断言概率位于 `[0,1]` 且正特征概率更高；记录每次训练最大信号日，断言早于被预测样本的入场日；验证样本量、Brier、基准 Brier 和最近记录稳定可复现。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=RegularizedLogisticModelTest,SingleStockWalkForwardValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，模型与验证器尚不存在。

- [ ] **Step 3: 实现固定模型**

训练窗口内计算标准化参数；使用固定学习率、L2 正则、最大迭代和概率裁剪。扩展窗口从 60% 位置开始，每 20 日重训，逐日生成预测，并用每 20 日一个锚点计算独立验证指标。

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=RegularizedLogisticModelTest,SingleStockWalkForwardValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

- [ ] **Step 5: 提交并推送**

```bash
git add backend
git commit -m "feat: 实现单股滚动概率预测"
git push
```

### Task 4: 预测编排与 REST 接口

**Files:**
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quant/PythonQuantDailyBarSource.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/SingleStockForecastService.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/controller/SingleStockForecastController.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/request/quant/RunSingleStockForecastRequest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/quant/forecast/SingleStockForecastServiceTest.java`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/controller/SingleStockForecastControllerTest.java`

- [ ] **Step 1: 写服务和接口失败测试**

断言代码规范化为 `600519.SH`、请求 5,000 根缓存行情、少于 750 根返回 `INSUFFICIENT_DATA`、有效样本返回数据指纹/截止日/20 日概率/区间/验证指标；Controller 只接受六位 A 股代码并返回统一响应。

- [ ] **Step 2: 运行定向测试确认失败**

Run: `cd backend && mvn -pl finscope-web -am -Dtest=SingleStockForecastServiceTest,SingleStockForecastControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，服务与端点尚不存在。

- [ ] **Step 3: 实现编排和诚实结论门禁**

新增 `POST /api/quant/single-stock-forecasts`；服务计算 SHA-256 指纹、当前概率、相似概率样本收益分位数，并按 `INSUFFICIENT_DATA`、`LOW_CONFIDENCE`、`NO_OBSERVED_EDGE`、`CONDITIONAL_EDGE`、`EVIDENCE_SUPPORTED` 输出原因和限制。

- [ ] **Step 4: 运行模块测试**

Run: `cd backend && mvn -pl finscope-web -am test`

Expected: PASS。

- [ ] **Step 5: 提交并推送**

```bash
git add backend
git commit -m "feat: 提供单股二十日预测接口"
git push
```

### Task 5: 单股预测研究终端

**Files:**
- Create: `frontend/src/features/strategy/SingleStockForecastPanel.tsx`
- Create: `frontend/src/features/strategy/SingleStockForecastPanel.test.tsx`
- Modify: `frontend/src/features/strategy/QuantWorkspace.tsx`
- Modify: `frontend/src/features/strategy/quantTypes.ts`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: 写前端失败测试**

覆盖默认入口、代码校验、提交请求、上涨概率/预期区间/证据等级/样本外指标/近期记录，以及不足与无优势状态；使用角色和可访问名称断言，不依赖样式实现细节。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd frontend && npm test -- SingleStockForecastPanel.test.tsx QuantWorkspace.test.tsx`

Expected: FAIL，新组件与页签尚不存在。

- [ ] **Step 3: 实现组件与视觉系统**

将 `forecast` 设为量化工作台首个 pane；构建代码输入、20 日概率主卡、收益分布带、证据矩阵、近期验证表和数据来源脚注。沿用项目青色研究色，但用中性灰/琥珀表达不足或无优势，支持窄屏和 reduced motion。

- [ ] **Step 4: 运行前端测试与构建**

Run: `cd frontend && npm test -- SingleStockForecastPanel.test.tsx QuantWorkspace.test.tsx && npm run build`

Expected: PASS，TypeScript 无错误，Vite 构建成功。

- [ ] **Step 5: 提交并推送**

```bash
git add frontend
git commit -m "feat: 增加单股预测研究终端"
git push
```

### Task 6: 全量验证与交付

**Files:**
- Verify: all files changed in Tasks 1–5

- [ ] **Step 1: 运行 Python 全量测试**

Run: `cd market-data-service && .venv/bin/pytest -q`

Expected: PASS。

- [ ] **Step 2: 运行后端全量测试**

Run: `cd backend && mvn test`

Expected: PASS。

- [ ] **Step 3: 运行前端全量测试和构建**

Run: `cd frontend && npm test && npm run build`

Expected: PASS。

- [ ] **Step 4: 检查改动和凭据安全**

Run: `git diff --check && git status --short && git diff --stat main...HEAD`

Expected: 无空白错误；没有数据库、缓存、密钥或本地运行产物进入提交。

- [ ] **Step 5: 确认远端分支包含最终提交**

Run: `git push && git status --short`

Expected: 推送成功，工作区为空。
