# Python Single Stock Forecast Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将单股预测的复权、特征、训练和验证全部迁入 Python，并把 Java 收缩为稳定的预测接口代理。

**Architecture:** Python market-data-service 通过可验证的 QFQ 长历史构建完整预测响应；FastAPI 暴露单股预测端点。Java RPC 映射 Python 响应，service 和 controller 保持前端契约，不再拥有量化算法。

**Tech Stack:** Python 3.13、FastAPI、Pydantic、pytest、Java 8、Spring Boot 2.7、Jackson、JUnit 5、React/TypeScript。

---

### Task 1: 通达信长历史前复权

**Files:**
- Modify: `market-data-service/src/finscope_market_data/providers/pytdx_provider.py`
- Modify: `market-data-service/tests/test_providers.py`

- [ ] **Step 1: 写分页和复权失败测试**

用 FakeApi 返回 800 + 200 根分页数据和一条除权记录，断言调用起点为 0、800，事件日前 OHLC 乘以 `(prev_close*10-cash+rights*rights_price)/(prev_close*(10+bonus+rights))`，事件日及以后不变，全部标记 `QFQ`。

- [ ] **Step 2: 运行红灯**

Run: `cd market-data-service && .venv/bin/pytest tests/test_providers.py -k 'pytdx and (qfq or paginates)' -q`

Expected: FAIL，当前 provider 只取 800 根且标记 `NONE`。

- [ ] **Step 3: 实现最小分页与复权**

扩展 `TdxApi` 的 `get_xdxr_info`，同一连接中分页读取 bars 和事件；新增纯函数解析事件、计算累积因子和映射 QFQ bars。无法读取除权记录时抛 `ProviderError("QFQ_UNAVAILABLE", ...)`。

- [ ] **Step 4: 运行绿灯**

Run: `cd market-data-service && .venv/bin/pytest tests/test_providers.py -q`

Expected: PASS。

- [ ] **Step 5: 提交推送**

Run: `git add market-data-service && git commit -m "fix: 为通达信长历史计算前复权" && git push`

### Task 2: Python 特征、标签和模型

**Files:**
- Create: `market-data-service/src/finscope_market_data/forecast/schemas.py`
- Create: `market-data-service/src/finscope_market_data/forecast/features.py`
- Create: `market-data-service/src/finscope_market_data/forecast/logistic.py`
- Create: `market-data-service/tests/test_forecast_features.py`
- Create: `market-data-service/tests/test_forecast_logistic.py`

- [ ] **Step 1: 写特征与模型失败测试**

固定合成日线，断言信号 T 只读取 `<=T`，标签使用 `open(T+1)` 和 `close(T+20)` 并扣 0.15%；逻辑回归概率位于 `[0,1]`、可复现且正特征概率更高。

- [ ] **Step 2: 运行红灯**

Run: `cd market-data-service && .venv/bin/pytest tests/test_forecast_features.py tests/test_forecast_logistic.py -q`

Expected: FAIL，forecast 包不存在。

- [ ] **Step 3: 实现固定七维特征和 L2 模型**

使用 Python 标准库实现均值、标准差、sigmoid、梯度下降和 L2；不增加 numpy 依赖。特征输出不可变 dataclass，训练标准化只使用训练窗口。

- [ ] **Step 4: 运行绿灯并提交**

Run: `cd market-data-service && .venv/bin/pytest tests/test_forecast_features.py tests/test_forecast_logistic.py -q`

Expected: PASS。

Run: `git add market-data-service && git commit -m "feat: 在Python实现单股预测特征与模型" && git push`

### Task 3: Python 滚动验证与预测服务

**Files:**
- Create: `market-data-service/src/finscope_market_data/forecast/walk_forward.py`
- Create: `market-data-service/src/finscope_market_data/forecast/service.py`
- Create: `market-data-service/tests/test_forecast_service.py`

- [ ] **Step 1: 写无泄漏和门禁失败测试**

记录每次训练样本退出日，断言严格早于预测信号日；覆盖少于 750 根、Brier 不优于基准和有效优势三种状态，并固定指纹和最近观测顺序。

- [ ] **Step 2: 运行红灯**

Run: `cd market-data-service && .venv/bin/pytest tests/test_forecast_service.py -q`

Expected: FAIL，验证器与服务不存在。

- [ ] **Step 3: 实现扩展窗口和响应编排**

首次训练使用前 60% 可成熟样本，每 20 日重训；独立指标每 20 日取一个锚点。服务生成当前概率、相似概率收益 P20/期望/P80、Brier、基准、准确率和结论门禁。

- [ ] **Step 4: 运行绿灯并提交**

Run: `cd market-data-service && .venv/bin/pytest tests/test_forecast_service.py -q`

Expected: PASS。

Run: `git add market-data-service && git commit -m "feat: 在Python实现单股滚动预测" && git push`

### Task 4: FastAPI 预测端点

**Files:**
- Modify: `market-data-service/src/finscope_market_data/app.py`
- Modify: `market-data-service/tests/test_api.py`

- [ ] **Step 1: 写端点失败测试**

注入 router，POST `{"code":"600519"}`，断言 router 收到 SH/600519、QFQ 和 limit 5000，响应包含现有前端使用的 camelCase 字段；不足数据返回 HTTP 200 + `INSUFFICIENT_DATA`。

- [ ] **Step 2: 运行红灯**

Run: `cd market-data-service && .venv/bin/pytest tests/test_api.py -k single_stock_forecast -q`

Expected: FAIL，端点不存在。

- [ ] **Step 3: 实现端点并运行全量 Python 测试**

端点调用同一 router 获取日线并交给 forecast service；Pydantic 使用 camelCase alias，校验六位代码并根据首位映射 SH/SZ/BJ。

Run: `cd market-data-service && .venv/bin/pytest -q`

Expected: PASS。

- [ ] **Step 4: 提交推送**

Run: `git add market-data-service && git commit -m "feat: 提供Python单股预测接口" && git push`

### Task 5: Java 收缩为薄代理

**Files:**
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quant/PythonSingleStockForecastClient.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/SingleStockForecastService.java`
- Delete: `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/ForecastSample.java`
- Delete: `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/SingleStockFeatureBuilder.java`
- Delete: `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/RegularizedLogisticModel.java`
- Delete: `backend/finscope-service/src/main/java/com/finscope/service/quant/forecast/SingleStockWalkForwardValidator.java`
- Modify: relevant RPC/service tests

- [ ] **Step 1: 写 RPC 映射和代理失败测试**

断言客户端 POST Python 端点并映射完整 DTO；service 只规范化代码和调用客户端，不读取日线或训练模型；非法概率和缺失状态触发契约异常。

- [ ] **Step 2: 运行红灯**

Run: `cd backend && mvn -pl finscope-web -am -Dtest=PythonSingleStockForecastClientTest,SingleStockForecastServiceTest,SingleStockForecastControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，新客户端不存在。

- [ ] **Step 3: 实现客户端、删除 Java 算法并运行绿灯**

复用 `FinanceHttpClient` 和 Jackson；保持 `POST /api/quant/single-stock-forecasts` 及 DTO 不变。删除不再使用的算法类和对应测试。

- [ ] **Step 4: 提交推送**

Run: `git add backend && git commit -m "refactor: 将单股预测计算迁移至Python" && git push`

### Task 6: 联调与回归

**Files:**
- Verify: all changed files

- [ ] **Step 1: 重启 Python 服务并检查契约**

Run: `curl -s http://127.0.0.1:8000/openapi.json`，确认 daily-bars 最大值 5000 且存在预测端点。

- [ ] **Step 2: 真实调用 Python 与 Java 接口**

请求 600519，确认不返回 422 或 QFQ 异常；若外部源不足，必须返回结构化不足/不可用，而不是伪预测。

- [ ] **Step 3: 全量验证**

Run: `cd market-data-service && .venv/bin/pytest -q`

Run: `cd backend && mvn test`

Run: `cd frontend && npm test -- --run && npm run build`

Expected: 本功能测试与构建全部通过；记录仓库既有基线失败，不混入无关修复。

- [ ] **Step 4: 检查并推送**

Run: `git diff --check && git status --short && git push`

Expected: 无未提交修改，远端分支同步。
