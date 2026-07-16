# Python 市场数据获取服务 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建设独立 Python 数据获取服务，稳定获取关注股票的实时行情、历史 K 线和资金流，并通过稳定 HTTP 契约接入 Java 市场数据网关。

**Architecture:** Python 服务使用能力化 Provider 契约隔离 AkShare、腾讯、新浪、东方财富等来源，路由器负责重试、熔断、切源和最近成功快照。FastAPI 只暴露 FinScope 标准模型；Java 通过内部 Provider 调用，不感知 Python 库和上游字段。

**Tech Stack:** Python 3.11+、FastAPI、Pydantic、httpx、SQLite、可选 AkShare/pytdx、pytest；Java 8、Spring Boot 2.7、Jackson、现有 `MarketDataGateway`。

---

### Task 1: Python 项目骨架与统一数据模型

**Files:**
- Create: `market-data-service/pyproject.toml`
- Create: `market-data-service/src/finscope_market_data/models.py`
- Create: `market-data-service/src/finscope_market_data/providers/base.py`
- Test: `market-data-service/tests/test_models.py`

- [x] **Step 1: 编写失败测试**

验证 `StockSymbol` 规范化沪深北市场代码，`DataEnvelope` 必须包含来源、质量状态、时间和尝试记录。

- [x] **Step 2: 运行测试确认 RED**

Run: `cd market-data-service && uv run pytest tests/test_models.py -q`
Expected: FAIL，因为模型尚不存在。

- [x] **Step 3: 实现最小模型和 Provider 协议**

定义 `DataCapability`、`QualityStatus`、`ProviderAttempt`、`StockQuote`、`DailyBar`、`CapitalFlowPoint`、`DataEnvelope` 与异步 `MarketDataProvider` 协议。

- [x] **Step 4: 运行测试确认 GREEN**

Run: `cd market-data-service && uv run pytest tests/test_models.py -q`
Expected: PASS。

### Task 2: 路由、重试、熔断与最近成功快照

**Files:**
- Create: `market-data-service/src/finscope_market_data/router.py`
- Create: `market-data-service/src/finscope_market_data/snapshot_store.py`
- Create: `market-data-service/src/finscope_market_data/health.py`
- Test: `market-data-service/tests/test_router.py`

- [x] **Step 1: 编写失败测试**

覆盖主源成功、主源失败切换备源、全部在线源失败返回 SQLite 最近快照、无快照返回 `UNAVAILABLE`、连续失败后熔断。

- [x] **Step 2: 运行测试确认 RED**

Run: `cd market-data-service && uv run pytest tests/test_router.py -q`
Expected: FAIL，因为路由器和快照仓储尚不存在。

- [x] **Step 3: 实现最小路由器**

路由器按 capability 和 priority 选择 Provider；每次调用记录耗时、错误类型和 retryable；成功结果写入快照，失败时读取最近快照并标记 `STALE_FALLBACK`。

- [x] **Step 4: 运行测试确认 GREEN**

Run: `cd market-data-service && uv run pytest tests/test_router.py -q`
Expected: PASS。

### Task 3: A 股真实数据 Provider

**Files:**
- Create: `market-data-service/src/finscope_market_data/providers/tencent.py`
- Create: `market-data-service/src/finscope_market_data/providers/sina.py`
- Create: `market-data-service/src/finscope_market_data/providers/eastmoney.py`
- Create: `market-data-service/src/finscope_market_data/providers/akshare_provider.py`
- Create: `market-data-service/tests/fixtures/*.json`
- Test: `market-data-service/tests/test_providers.py`

- [x] **Step 1: 编写固定响应解析测试**

验证腾讯/新浪行情字段，东方财富日 K 与资金流字段，以及 AkShare DataFrame 到标准模型的映射；网络请求不进入单元测试。

- [x] **Step 2: 运行测试确认 RED**

Run: `cd market-data-service && uv run pytest tests/test_providers.py -q`
Expected: FAIL，因为 Provider 尚不存在。

- [x] **Step 3: 实现 Provider**

腾讯和新浪提供不同故障域的实时行情；东方财富提供日 K、实时/历史资金流；AkShare 作为可选生态 Provider 补充日 K、资金流和个股信息。所有 Provider 声明真实 `provider_family`，不把同一上游伪装成独立来源。

- [x] **Step 4: 运行测试确认 GREEN**

Run: `cd market-data-service && uv run pytest tests/test_providers.py -q`
Expected: PASS。

### Task 4: FastAPI 数据接口

**Files:**
- Create: `market-data-service/src/finscope_market_data/app.py`
- Create: `market-data-service/src/finscope_market_data/settings.py`
- Create: `market-data-service/tests/test_api.py`

- [x] **Step 1: 编写 API 契约失败测试**

覆盖 `/health`、`/v1/providers/health`、`/v1/stocks/{market}/{code}/quote`、`daily-bars`、`capital-flow` 和聚合 `overview`。

- [x] **Step 2: 运行测试确认 RED**

Run: `cd market-data-service && uv run pytest tests/test_api.py -q`
Expected: FAIL，因为应用尚不存在。

- [x] **Step 3: 实现应用装配和错误映射**

数据不可用返回 HTTP 503 和结构化 `UNAVAILABLE`；有旧快照返回 HTTP 200 和 `STALE_FALLBACK`；聚合接口允许部分数据成功并逐项保留质量信息。

- [x] **Step 4: 运行测试确认 GREEN**

Run: `cd market-data-service && uv run pytest tests/test_api.py -q`
Expected: PASS。

### Task 5: Java 内部 HTTP Provider 接入

**Files:**
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/python/PythonMarketDataCapitalFlowProvider.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/marketintel/python/PythonMarketDataCapitalFlowProviderTest.java`
- Modify: `backend/finscope-web/src/main/resources/application.yml`

- [x] **Step 1: 编写 Java 解析失败测试**

固定 Python JSON 响应，验证分钟/日级资金流、行情上下文、来源和旧快照质量映射；验证 Provider 无需启用开关即可支持 A 股标的。

- [x] **Step 2: 运行测试确认 RED**

Run: `cd backend && mvn -pl finscope-rpc -am -Dtest=PythonMarketDataCapitalFlowProviderTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL，因为 Java Provider 尚不存在。

- [x] **Step 3: 实现 Java Provider**

Python Provider 固定注册，仅通过 `FINSCOPE_PYTHON_MARKET_DATA_BASE_URL` 适配部署地址；其优先级高于东财直连，Python 服务不可用时现有 Java Provider 自动接管。

- [x] **Step 4: 运行测试确认 GREEN**

Run: `cd backend && mvn -pl finscope-rpc -am -Dtest=PythonMarketDataCapitalFlowProviderTest,MarketDataGatewayCapitalFlowTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS。

### Task 6: 运行、部署与真实取数验证

**Files:**
- Create: `market-data-service/README.md`
- Create: `market-data-service/Dockerfile`
- Create: `market-data-service/.env.example`
- Modify: `README.md`

- [x] **Step 1: 补充运行和部署文档**

记录 `uv sync`、`uv run uvicorn finscope_market_data.app:app`、Java 环境变量、Docker 内网地址、数据目录挂载和端口不暴露公网要求。

- [x] **Step 2: 执行完整验证**

Run: `cd market-data-service && uv run pytest -q`
Expected: PASS。

Run: `cd backend && mvn -pl finscope-rpc,finscope-service,finscope-web -am test`
Expected: PASS。

- [x] **Step 3: 真实股票冒烟测试**

启动 Python 服务后验证 `SH/600519`、`SZ/000001`、`BJ/920002`；记录每类数据的来源、质量状态和无法覆盖的明确原因，不以空数组冒充成功。
