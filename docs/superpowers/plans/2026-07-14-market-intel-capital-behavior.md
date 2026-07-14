# Market Intel Capital Behavior Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付可独立使用的 Market Intel 第一纵向切片：A 股大笔资金数据、成交上下文、规则化通俗解释、点击触发的 Agent 解读和独立前端 Tab。

**Architecture:** 以 `Instrument` 为标的主键，RPC Adapter 拉取并规范化东财资金与行情数据，DAO 保存不可变资金点和快照，Service 负责聚合、规则解释、Agent 编排与置信度门，Web 暴露只读查询和异步解读命令，React 页面只消费后端合同。原始事实、确定性解释和模型假设三层隔离；模型只能引用服务端事实，不能覆盖数据。

**Tech Stack:** Java 8、Spring Boot 2.7、JdbcTemplate、SQLite、Jackson、JUnit 5、Mockito、React 18、TypeScript 5.6、Vitest、Testing Library。

---

## 范围与切片边界

本计划实现第一阶段的最高优先级纵向切片，不同时实现龙虎榜、解禁、行业/概念和新闻公告。完成后用户可以选择已有 A 股 `Instrument`，刷新大笔资金数据，查看 1/5 分钟与 5/10/20 日趋势、自动规则解释，并点击运行受约束的 Agent 解读。其余维度分别形成后续实施计划，复用本计划建立的 Provider、刷新运行、快照和页面框架。

## 文件职责图

### 后端新文件

- `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/CapitalFlowPoint.java`：规范化资金时间点的数据载体。
- `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/CapitalBehaviorSignal.java`：可复算的客观异常信号。
- `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/CapitalBehaviorSnapshot.java`：Agent 与规则共享的不可变输入快照。
- `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/CapitalRuleExplanation.java`：规则解释及指标引用。
- `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/CapitalInterpretation.java`：Agent 解读运行与最终报告。
- `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/CapitalHypothesis.java`：受置信度门约束的模型假设。
- `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/MarketIntelRefreshRun.java`：刷新总状态。
- `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/MarketIntelRefreshStep.java`：分维度尝试和错误状态。
- `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/FinanceHttpClient.java`：可替换、可测试的 HTTP 边界。
- `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/JdkFinanceHttpClient.java`：超时、大小限制和状态码治理。
- `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/CapitalFlowProvider.java`：资金数据 Strategy 接口。
- `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/CapitalFlowData.java`：Provider 规范化输出合同。
- `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/ProviderContractException.java`：稳定错误分类。
- `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/ProviderRequestGuard.java`：按 provider 限流、有界重试和短时熔断。
- `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/eastmoney/EastmoneyCapitalFlowProvider.java`：东财 Adapter 与 JSON 解析。
- `backend/finscope-dao/src/main/java/com/finscope/dao/marketintel/MarketIntelSchemaMigrator.java`：独立版本迁移账本和业务表。
- `backend/finscope-dao/src/main/java/com/finscope/dao/marketintel/CapitalFlowRepository.java`：资金点幂等写入与时点查询。
- `backend/finscope-dao/src/main/java/com/finscope/dao/marketintel/CapitalBehaviorSnapshotRepository.java`：快照持久化。
- `backend/finscope-dao/src/main/java/com/finscope/dao/marketintel/CapitalInterpretationRepository.java`：规则/Agent 解读持久化和幂等查询。
- `backend/finscope-dao/src/main/java/com/finscope/dao/marketintel/MarketIntelRefreshRunRepository.java`：刷新 run/step 状态机。
- `backend/finscope-dao/src/main/java/com/finscope/dao/agent/AgentTraceSchemaMigrator.java`：通用 Agent subject 增量迁移。
- `backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalFlowAggregationService.java`：1 分钟到 5 分钟及跨日窗口计算。
- `backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalSignalPolicy.java`：版本化信号阈值。
- `backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalBehaviorSnapshotService.java`：快照组装与指纹。
- `backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalRuleExplanationEngine.java`：确定性通俗解释。
- `backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalFactAssembler.java`：服务端可信事实与 metric reference。
- `backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalHypothesisGate.java`：模型输出 Policy。
- `backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalInterpretationAgent.java`：结构化 LLM 调用与解析。
- `backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalInterpretationFacade.java`：查询、缓存、异步 Agent 的 Facade。
- `backend/finscope-service/src/main/java/com/finscope/service/marketintel/MarketIntelCapitalService.java`：刷新与 overview 用例编排。
- `backend/finscope-service/src/main/java/com/finscope/service/marketintel/MarketIntelRefreshCoordinator.java`：网络、事务和局部失败边界。
- `backend/finscope-web/src/main/java/com/finscope/web/controller/MarketIntelController.java`：REST API。
- `backend/finscope-web/src/main/java/com/finscope/web/response/marketintel/*.java`：稳定的 Web DTO，不直接暴露持久化对象。

### 后端修改文件

- `backend/finscope-domain/src/main/java/com/finscope/domain/agent/AgentRun.java`：增加通用 subject。
- `backend/finscope-dao/src/main/java/com/finscope/dao/agent/AgentRunRepository.java`：保存和按 subject 查询 Trace。
- `backend/finscope-service/src/main/java/com/finscope/service/agent/AgentTraceService.java`：新增 `AgentTraceSubject` 重载并保持旧签名兼容。
- `backend/finscope-web/src/main/java/com/finscope/web/config/FinScopeProperties.java`：增加 Market Intel 配置。

### 前端新文件

- `frontend/src/features/market-intel/marketIntelTypes.ts`：API 类型。
- `frontend/src/features/market-intel/MarketIntelView.tsx`：页面状态与请求编排。
- `frontend/src/features/market-intel/CapitalBehaviorPanel.tsx`：摘要、趋势和健康状态。
- `frontend/src/features/market-intel/CapitalRuleExplanationCard.tsx`：默认通俗解释。
- `frontend/src/features/market-intel/CapitalAgentInterpretationPanel.tsx`：按需 Agent 运行与结果。
- `frontend/src/features/market-intel/MarketIntelView.test.tsx`：页面交互测试。

### 前端修改文件

- `frontend/src/shared/types/index.ts`：增加 `marketIntel` View。
- `frontend/src/app/AppShell.tsx`：在 Watchlist 与 Strategy 之间增加导航项。
- `frontend/src/app/AppShell.test.tsx`：验证导航。
- `frontend/src/App.tsx`：挂载页面与标题。
- `frontend/src/App.test.tsx`：验证路由渲染。
- `frontend/src/styles.css`：Market Intel 语义化样式。

### 测试样本

- `backend/finscope-rpc/src/test/resources/marketintel/eastmoney-fund-flow-minute.json`
- `backend/finscope-rpc/src/test/resources/marketintel/eastmoney-fund-flow-daily.json`
- `backend/finscope-rpc/src/test/resources/marketintel/eastmoney-stock-quote.json`

## Task 1: 领域合同与度量引用

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/CapitalFlowPoint.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/CapitalBehaviorSignal.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/CapitalBehaviorSnapshot.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/CapitalRuleExplanation.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/CapitalInterpretation.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/MarketIntelRefreshRun.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/MarketIntelRefreshStep.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/CapitalHypothesis.java`
- Test: `backend/finscope-domain/src/test/java/com/finscope/domain/marketintel/CapitalMarketIntelContractTest.java`

- [ ] **Step 1: 写失败的领域合同测试**

```java
@Test
void snapshotSeparatesFactsFromHypothesesAndKeepsMetricReferences() {
    CapitalFlowPoint point = new CapitalFlowPoint();
    point.setId(101L);
    point.setInstrumentId(7L);
    point.setGranularity("MINUTE_1");
    point.setObservedAt(LocalDateTime.of(2026, 7, 14, 10, 30));
    point.setTradeAmount(new BigDecimal("120000000"));
    point.setMainNetInflow(new BigDecimal("18000000"));

    CapitalBehaviorSnapshot snapshot = CapitalBehaviorSnapshot.of(
            7L, LocalDateTime.of(2026, 7, 14, 10, 30),
            Collections.singletonList(point), Collections.emptyList(), "sha256:abc");

    CapitalHypothesis hypothesis = new CapitalHypothesis();
    hypothesis.setType("ORDER_SPLITTING");
    hypothesis.setConfidence("LOW");
    hypothesis.setSupportingMetricRefs(Collections.singletonList("flow:101:mainNetInflow"));

    assertEquals("flow:101:mainNetInflow", hypothesis.getSupportingMetricRefs().get(0));
    assertEquals("sha256:abc", snapshot.getFingerprint());
    assertThrows(UnsupportedOperationException.class, () -> snapshot.getFlowPoints().add(point));
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && mvn -pl finscope-domain -am -Dtest=CapitalMarketIntelContractTest test`

Expected: FAIL，编译器报告 `com.finscope.domain.marketintel` 类型不存在。

- [ ] **Step 3: 实现最小领域类型**

`CapitalFlowPoint` 使用 `BigDecimal` 保存金额和比例，包含 `id/instrumentId/providerCode/granularity/dataDate/observedAt/price/tradeVolume/intervalTradeAmount/cumulativeTradeAmount/turnoverRate/volumeRatio/mainInflow/mainOutflow/mainNetInflow/superLargeNetInflow/largeNetInflow/mediumNetInflow/smallNetInflow/calculationVersion/retrievedAt/payloadHash/qualityStatus`。`CapitalBehaviorSnapshot.of` 对输入列表执行 `Collections.unmodifiableList(new ArrayList<>(values))`。解释项使用 `text + metricRefs + level`，假设使用 `type/claim/confidence/supportingMetricRefs/counterEvidence/dataGaps`，Agent 报告使用 `RULE/AGENT` 类型和 `PENDING/RUNNING/SUCCEEDED/FALLBACK/FAILED` 状态。刷新 run 使用 `PENDING/RUNNING/SUCCEEDED/PARTIAL/FAILED`，step 使用 `PENDING/RUNNING/SUCCEEDED/EMPTY/FAILED/SKIPPED`。

```java
public static CapitalBehaviorSnapshot of(Long instrumentId,
                                         LocalDateTime asOf,
                                         List<CapitalFlowPoint> flowPoints,
                                         List<CapitalBehaviorSignal> signals,
                                         String fingerprint) {
    CapitalBehaviorSnapshot value = new CapitalBehaviorSnapshot();
    value.instrumentId = instrumentId;
    value.asOf = asOf;
    value.flowPoints = Collections.unmodifiableList(new ArrayList<CapitalFlowPoint>(flowPoints));
    value.signals = Collections.unmodifiableList(new ArrayList<CapitalBehaviorSignal>(signals));
    value.fingerprint = fingerprint;
    return value;
}
```

- [ ] **Step 4: 运行领域测试**

Run: `cd backend && mvn -pl finscope-domain -am -Dtest=CapitalMarketIntelContractTest test`

Expected: PASS，1 test completed, 0 failures。

- [ ] **Step 5: 提交领域合同**

```bash
git add backend/finscope-domain/src/main/java/com/finscope/domain/marketintel backend/finscope-domain/src/test/java/com/finscope/domain/marketintel
git commit --only -m "feat: add market intel capital contracts" -- backend/finscope-domain/src/main/java/com/finscope/domain/marketintel backend/finscope-domain/src/test/java/com/finscope/domain/marketintel
```

## Task 2: 版本化 Schema 与 Repository

**Files:**
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/marketintel/MarketIntelSchemaMigrator.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/marketintel/CapitalFlowRepository.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/marketintel/CapitalBehaviorSnapshotRepository.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/marketintel/CapitalInterpretationRepository.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/marketintel/MarketIntelRefreshRunRepository.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/marketintel/MarketIntelSchemaMigratorTest.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/marketintel/CapitalRepositoriesTest.java`

- [ ] **Step 1: 写迁移和幂等写入失败测试**

```java
@Test
void migratesExactlyOnceAndRejectsDuplicateFlowPayload() {
    migrator.migrate();
    migrator.migrate();
    assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=100"));
    assertEquals(1, tableCount("market_capital_flow_snapshot"));
    assertEquals(1, tableCount("market_capital_behavior_snapshot"));
    assertEquals(1, tableCount("market_capital_interpretation"));
    assertEquals(1, tableCount("market_intel_refresh_run"));
    assertEquals(1, tableCount("market_intel_refresh_step"));

    CapitalFlowPoint point = fixturePoint("hash-1");
    repository.saveAll(Collections.singletonList(point));
    repository.saveAll(Collections.singletonList(point));
    assertEquals(1, repository.findRange(7L, point.getObservedAt().minusMinutes(1),
            point.getObservedAt().plusMinutes(1)).size());
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && mvn -pl finscope-dao -am -Dtest=MarketIntelSchemaMigratorTest,CapitalRepositoriesTest test`

Expected: FAIL，迁移器与 Repository 类型不存在。

- [ ] **Step 3: 实现迁移版本 100**

创建五张表并使用约束：资金点唯一键为 `(instrument_id,provider_code,granularity,observed_at,payload_hash)`；快照唯一键为 `(instrument_id,as_of,fingerprint)`；解释表保存 `interpretation_type/status/snapshot_id/plain_summary/facts_json/hypotheses_json/counter_evidence_json/data_gaps_json/observation_points_json/rule_version/model_name/prompt_version/input_hash/output_hash/created_at/updated_at`；refresh run 保存标的、触发方式、总状态和计数；refresh step 保存 dimension/provider/attempt/fallback/error/output count，并以 `(run_id,dimension,provider_code,attempt)` 唯一。迁移账本复用全局 `schema_migration`，版本使用 100，避免与 Knowledge 的版本 1 冲突。

```java
private static final int VERSION_100 = 100;

private void applyVersion100() {
    jdbc.execute("CREATE TABLE IF NOT EXISTS market_capital_flow_snapshot (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT,instrument_id INTEGER NOT NULL," +
            "provider_code TEXT NOT NULL,granularity TEXT NOT NULL,data_date TEXT NOT NULL," +
            "observed_at TEXT NOT NULL,price TEXT,trade_volume TEXT,interval_trade_amount TEXT," +
            "cumulative_trade_amount TEXT,turnover_rate TEXT,volume_ratio TEXT," +
            "main_inflow TEXT,main_outflow TEXT,main_net_inflow TEXT," +
            "super_large_net_inflow TEXT,large_net_inflow TEXT," +
            "medium_net_inflow TEXT,small_net_inflow TEXT,retrieved_at TEXT NOT NULL," +
            "payload_hash TEXT NOT NULL,quality_status TEXT NOT NULL,calculation_version TEXT NOT NULL," +
            "FOREIGN KEY(instrument_id) REFERENCES instrument(id) ON DELETE RESTRICT)");
    jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_capital_flow_identity ON " +
            "market_capital_flow_snapshot(instrument_id,provider_code,granularity,observed_at,payload_hash)");
    jdbc.execute("CREATE TABLE IF NOT EXISTS market_intel_refresh_run (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT,instrument_id INTEGER NOT NULL," +
            "trigger_type TEXT NOT NULL,status TEXT NOT NULL,success_count INTEGER NOT NULL DEFAULT 0," +
            "failure_count INTEGER NOT NULL DEFAULT 0,started_at TEXT NOT NULL,finished_at TEXT)");
    jdbc.execute("CREATE TABLE IF NOT EXISTS market_intel_refresh_step (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT,run_id INTEGER NOT NULL,dimension TEXT NOT NULL," +
            "provider_code TEXT NOT NULL,attempt INTEGER NOT NULL,status TEXT NOT NULL," +
            "fallback_used INTEGER NOT NULL DEFAULT 0,error_type TEXT,error_message TEXT," +
            "output_count INTEGER NOT NULL DEFAULT 0,started_at TEXT NOT NULL,finished_at TEXT," +
            "FOREIGN KEY(run_id) REFERENCES market_intel_refresh_run(id) ON DELETE CASCADE)");
    jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_market_intel_refresh_step_identity ON " +
            "market_intel_refresh_step(run_id,dimension,provider_code,attempt)");
}
```

同一迁移内继续建立 behavior snapshot 与 interpretation 表；所有表、索引和迁移记录在一个事务中提交。这里展示资金事实与刷新状态机的关键 DDL，实际实现测试必须验证上述五张表及其唯一约束。

- [ ] **Step 4: 实现 Repository 映射和时点查询**

金额从 SQLite `TEXT` 经 `new BigDecimal(value)` 恢复，所有查询显式按 `observed_at ASC,id ASC` 排序。批量保存使用 `INSERT OR IGNORE`；读取快照 JSON 使用统一 `ObjectMapper` 并在解析失败时抛出带 snapshot id 的 `IllegalStateException`，禁止静默返回空数据。

- [ ] **Step 5: 运行 DAO 测试**

Run: `cd backend && mvn -pl finscope-dao -am -Dtest=MarketIntelSchemaMigratorTest,CapitalRepositoriesTest test`

Expected: PASS，迁移重复执行不增加版本记录，重复资金负载只保存一次。

- [ ] **Step 6: 提交迁移和仓储**

```bash
git add backend/finscope-dao/src/main/java/com/finscope/dao/marketintel backend/finscope-dao/src/test/java/com/finscope/dao/marketintel
git commit --only -m "feat: persist capital behavior snapshots" -- backend/finscope-dao/src/main/java/com/finscope/dao/marketintel backend/finscope-dao/src/test/java/com/finscope/dao/marketintel
```

## Task 3: 统一 Finance HTTP 与东财 Adapter

**Files:**
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/FinanceHttpClient.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/FinanceHttpResponse.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/JdkFinanceHttpClient.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/CapitalFlowProvider.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/CapitalFlowData.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/ProviderContractException.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/ProviderRequestGuard.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/eastmoney/EastmoneyCapitalFlowProvider.java`
- Create: `backend/finscope-rpc/src/test/resources/marketintel/eastmoney-fund-flow-minute.json`
- Create: `backend/finscope-rpc/src/test/resources/marketintel/eastmoney-fund-flow-daily.json`
- Create: `backend/finscope-rpc/src/test/resources/marketintel/eastmoney-stock-quote.json`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/marketintel/ProviderRequestGuardTest.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/marketintel/eastmoney/EastmoneyCapitalFlowProviderTest.java`

- [ ] **Step 1: 写 fixture 解析失败测试**

```java
@Test
void parsesMinuteDailyAndQuotePayloadWithoutInventingMissingFields() throws Exception {
    FinanceHttpClient http = new FixtureFinanceHttpClient(minuteJson(), dailyJson(), quoteJson());
    EastmoneyCapitalFlowProvider provider = new EastmoneyCapitalFlowProvider(http, fixedClock());

    CapitalFlowData data = provider.fetch(stock(7L, "600519", "SH"),
            LocalDate.of(2026, 7, 14));

    CapitalFlowPoint minute = data.getMinutePoints().get(0);
    assertEquals("MINUTE_1", minute.getGranularity());
    assertEquals(new BigDecimal("18000000"), minute.getMainNetInflow());
    assertEquals(new BigDecimal("120000000"), minute.getIntervalTradeAmount());
    assertNull(minute.getMainInflow());
    assertNull(minute.getMainOutflow());
    assertEquals(new BigDecimal("3.21"), data.getTurnoverRate());
}

@Test
void rejectsPayloadWhenCoreKlineFieldsDrift() {
    EastmoneyCapitalFlowProvider provider = providerWithMinutePayload("{\"data\":{\"klines\":[\"10:30,broken\"]}}");
    ProviderContractException error = assertThrows(ProviderContractException.class,
            () -> provider.fetch(stock(7L, "600519", "SH"), LocalDate.of(2026, 7, 14)));
    assertEquals("SCHEMA_DRIFT", error.getErrorType());
}

@Test
void retriesOneRetryableFailureAndOpensCircuitAfterThreshold() {
    FakeClock clock = new FakeClock("2026-07-14T02:00:00Z");
    ProviderRequestGuard guard = guard(clock, noWaitSleeper(), 3, Duration.ofSeconds(60));
    AtomicInteger calls = new AtomicInteger();
    assertEquals("ok", guard.execute("EASTMONEY", () -> {
        if (calls.getAndIncrement() == 0) throw retryable(503);
        return "ok";
    }));
    failThreeGuardedCalls(guard, "EASTMONEY");
    assertEquals("CIRCUIT_OPEN", assertThrows(ProviderContractException.class,
            () -> guard.execute("EASTMONEY", () -> "never")).getErrorType());
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && mvn -pl finscope-rpc -am -Dtest=ProviderRequestGuardTest,EastmoneyCapitalFlowProviderTest test`

Expected: FAIL，HTTP 与 Provider 合同不存在。

- [ ] **Step 3: 实现受控 HTTP 客户端**

`FinanceHttpClient.get` 只接收 Provider 构造的 `URI`；`JdkFinanceHttpClient` 设置 5 秒连接、10 秒读取、2 MiB 响应上限、固定 User-Agent，非 2xx 抛出包含 provider/status 的异常。禁止全局修改 SSL 校验。响应对象返回 `status/body/retrievedAt/payloadHash`。`ProviderRequestGuard` 按 provider 串行保证默认最小间隔 1 秒；连接超时、429、502/503/504 最多重试一次，参数错误和 `SCHEMA_DRIFT` 不重试；连续 3 次可重试失败后熔断 60 秒并返回 `CIRCUIT_OPEN`。

```java
public interface FinanceHttpClient {
    FinanceHttpResponse get(String providerCode, URI uri, Map<String, String> headers) throws Exception;
}

public interface CapitalFlowProvider {
    String providerCode();
    boolean supports(Instrument instrument);
    CapitalFlowData fetch(Instrument instrument, LocalDate asOfDate) throws ProviderContractException;
}
```

`CapitalFlowData` 是独立不可变 DTO，包含分钟资金点、日资金点、换手率、量比、行情对齐 warnings 和 provider 元数据。`ProviderRequestGuard` 注入 `Clock` 与可替换 Sleeper，单元测试不真实等待；业务调用必须经过 Guard，再进入具体 Provider。

- [ ] **Step 4: 实现东财 Adapter**

将 SH 代码映射为 `1.600519`，SZ/BJ 映射为 `0.xxxxxx`。请求 `stock/fflow/kline/get`、`stock/fflow/daykline/get`、`stock/get` 和 `stock/trends2/get`；解析 `f51-f56` 资金字段。行情分钟线提供区间成交金额和价格，资金线按相同分钟时间戳对齐；未对齐记录保留资金点并添加 `TIMELINE_ALIGNMENT_GAP` warning。只保存上游真实提供的净额，流入/流出保持 `null`。

- [ ] **Step 5: 运行 RPC 测试**

Run: `cd backend && mvn -pl finscope-rpc -am -Dtest=ProviderRequestGuardTest,EastmoneyCapitalFlowProviderTest test`

Expected: PASS；fixture 测试不访问网络，字段漂移得到 `SCHEMA_DRIFT`。

- [ ] **Step 6: 提交 Provider**

```bash
git add backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel backend/finscope-rpc/src/test/java/com/finscope/rpc/marketintel backend/finscope-rpc/src/test/resources/marketintel
git commit --only -m "feat: add eastmoney capital flow provider" -- backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel backend/finscope-rpc/src/test/java/com/finscope/rpc/marketintel backend/finscope-rpc/src/test/resources/marketintel
```

## Task 4: 聚合、信号与不可变快照

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalFlowAggregationService.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalBehaviorSignalService.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalSignalPolicy.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalBehaviorSnapshotService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/marketintel/CapitalFlowAggregationServiceTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/marketintel/CapitalBehaviorSnapshotServiceTest.java`

- [ ] **Step 1: 写聚合和信号失败测试**

```java
@Test
void aggregatesFiveMinutesWithoutSummingCumulativeAmount() {
    List<CapitalFlowPoint> minutes = fiveMinutes(
            new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("120"),
            new BigDecimal("130"), new BigDecimal("140"));
    CapitalFlowPoint value = service.aggregateFiveMinute(minutes).get(0);
    assertEquals(new BigDecimal("600"), value.getIntervalTradeAmount());
    assertEquals(new BigDecimal("140"), value.getCumulativeTradeAmount());
    assertEquals(new BigDecimal("15"), value.getMainNetInflow());
    assertEquals("capital-aggregate-v1", value.getCalculationVersion());
}

@Test
void snapshotFingerprintChangesWhenInputRecordChanges() {
    CapitalBehaviorSnapshot first = service.build(7L, asOf, points("hash-a"), signals());
    CapitalBehaviorSnapshot second = service.build(7L, asOf, points("hash-b"), signals());
    assertNotEquals(first.getFingerprint(), second.getFingerprint());
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=CapitalFlowAggregationServiceTest,CapitalBehaviorSnapshotServiceTest test`

Expected: FAIL，聚合与快照 Service 不存在。

- [ ] **Step 3: 实现确定性聚合和窗口指标**

5 分钟窗口按交易日和自然 5 分钟边界分组；价格取窗口末值，区间金额/资金净额求和，累计金额取末值。日线计算 5/10/20 日主力净额、净额占成交金额比例和连续同方向天数。除法统一 `scale=6, HALF_UP`，分母为零返回 `null`。

- [ ] **Step 4: 实现客观信号和快照指纹**

首版只实现四个可复算信号：`AMOUNT_EXPANSION_WITH_OUTFLOW`、`LOW_AMOUNT_INFLOW`、`PRICE_FLOW_DIVERGENCE`、`LATE_SESSION_FLOW_SHIFT`。阈值集中在不可变 `CapitalSignalPolicy.v1()`；信号保存输入 record ids、阈值、实际值、窗口和 `capital-signal-v1`。指纹对排序后的 record id/payload hash/signal input hash/asOf 做 SHA-256。

- [ ] **Step 5: 运行 Service 测试**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=CapitalFlowAggregationServiceTest,CapitalBehaviorSnapshotServiceTest test`

Expected: PASS；相同输入指纹稳定，累计金额不被重复求和。

- [ ] **Step 6: 提交聚合切片**

```bash
git add backend/finscope-service/src/main/java/com/finscope/service/marketintel backend/finscope-service/src/test/java/com/finscope/service/marketintel
git commit --only -m "feat: aggregate capital behavior snapshots" -- backend/finscope-service/src/main/java/com/finscope/service/marketintel backend/finscope-service/src/test/java/com/finscope/service/marketintel
```

## Task 5: 默认通俗规则解释

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalFactAssembler.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalRuleExplanationEngine.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/marketintel/CapitalRuleExplanationEngineTest.java`

- [ ] **Step 1: 写解释可追溯性失败测试**

```java
@Test
void explainsAmountAndFlowInPlainChineseWithMetricReferences() {
    CapitalRuleExplanation result = engine.explain(snapshotWithExpandedAmountAndOutflow());
    assertTrue(result.getSummary().contains("成交明显放大"));
    assertTrue(result.getSummary().contains("大笔资金偏流出"));
    assertEquals("capital-rules-v1", result.getRuleVersion());
    assertTrue(result.getItems().stream().allMatch(item -> !item.getMetricRefs().isEmpty()));
    assertFalse(result.getSummary().contains("出货"));
    assertFalse(result.getSummary().contains("暗盘"));
}

@Test
void saysDataIsInsufficientInsteadOfGuessing() {
    CapitalRuleExplanation result = engine.explain(emptySnapshot());
    assertEquals("当前资金数据不足，暂时无法形成可靠解释。", result.getSummary());
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=CapitalRuleExplanationEngineTest test`

Expected: FAIL，规则解释器不存在。

- [ ] **Step 3: 实现规则解释 Strategy**

`CapitalFactAssembler` 从快照生成事实项，禁止接受 LLM 文本。`CapitalRuleExplanationEngine` 按优先级组合最多 4 条解释：成交环境、当日大笔资金方向、跨日持续性、价格资金背离。每条解释包含 `metricRefs` 和 `level=INFO/NOTICE/RISK`；规则文本不出现吸筹、出货、暗盘、庄家或买卖建议。

- [ ] **Step 4: 运行规则测试**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=CapitalRuleExplanationEngineTest test`

Expected: PASS；空数据明确降级，完整数据产生带引用的中文说明。

- [ ] **Step 5: 提交规则解释**

```bash
git add backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalFactAssembler.java backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalRuleExplanationEngine.java backend/finscope-service/src/test/java/com/finscope/service/marketintel/CapitalRuleExplanationEngineTest.java
git commit --only -m "feat: explain capital behavior with rules" -- backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalFactAssembler.java backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalRuleExplanationEngine.java backend/finscope-service/src/test/java/com/finscope/service/marketintel/CapitalRuleExplanationEngineTest.java
```

## Task 6: 泛化 Agent Trace Subject

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/agent/AgentTraceSubject.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/agent/AgentTraceSchemaMigrator.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/agent/AgentRun.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/agent/AgentRunRepository.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/agent/AgentTraceService.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/agent/AgentRunRepositoryTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/agent/AgentTraceServiceTest.java`

- [ ] **Step 1: 写通用 subject 与兼容性失败测试**

```java
@Test
void recordsAndQueriesCapitalInterpretationSubject() {
    AgentRun run = trace("capital-interpret");
    run.setSubjectType("CAPITAL_INTERPRETATION");
    run.setSubjectId(88L);
    repository.record(run);
    List<AgentRun> saved = repository.findBySubject("CAPITAL_INTERPRETATION", 88L);
    assertEquals(1, saved.size());
    assertEquals("capital-interpret", saved.get(0).getNodeName());
}

@Test
void legacyResearchTraceStillPersistsWithoutSubject() {
    repository.record(300L, null, null, "source-fetch", "SUCCESS", "in", "out", null, 1L);
    assertEquals(1, repository.findByResearchRunId(300L).size());
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=AgentRunRepositoryTest,AgentTraceServiceTest test`

Expected: FAIL，`subjectType/subjectId/findBySubject` 不存在。

- [ ] **Step 3: 实现专用 Trace 迁移与兼容重载**

`AgentTraceSchemaMigrator` 使用全局迁移版本 101，增加 `subject_type TEXT`、`subject_id INTEGER` 和索引 `(subject_type,subject_id,id)`，与 Market Intel 版本 100 严格错开。`AgentTraceService.recordNode(AgentTraceSubject, context, fingerprint, result, durationMs, metadataJson)` 是新主实现；旧 `recordNode(eventId,articleId,...)` 构造 legacy subject 后委托，现有调用不改行为。

- [ ] **Step 4: 运行 Trace 测试**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=AgentRunRepositoryTest,AgentTraceServiceTest test`

Expected: PASS；新 subject 可查询，旧 research trace 兼容。

- [ ] **Step 5: 提交通用 Trace**

```bash
git add backend/finscope-domain/src/main/java/com/finscope/domain/agent backend/finscope-dao/src/main/java/com/finscope/dao/agent backend/finscope-dao/src/test/java/com/finscope/dao/agent backend/finscope-service/src/main/java/com/finscope/service/agent/AgentTraceService.java backend/finscope-service/src/test/java/com/finscope/service/agent/AgentTraceServiceTest.java
git commit --only -m "refactor: generalize agent trace subjects" -- backend/finscope-domain/src/main/java/com/finscope/domain/agent backend/finscope-dao/src/main/java/com/finscope/dao/agent backend/finscope-dao/src/test/java/com/finscope/dao/agent backend/finscope-service/src/main/java/com/finscope/service/agent/AgentTraceService.java backend/finscope-service/src/test/java/com/finscope/service/agent/AgentTraceServiceTest.java
```

## Task 7: Agent 解读、置信度 Gate 与 Facade

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalHypothesisGate.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalInterpretationAgent.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketintel/CapitalInterpretationFacade.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/marketintel/CapitalHypothesisGateTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/marketintel/CapitalInterpretationFacadeTest.java`

- [ ] **Step 1: 写 Gate 和降级失败测试**

```java
@Test
void capsHiddenFlowAtLowWithoutLevelTwoAndDropsUnknownReferences() {
    CapitalHypothesis hidden = hypothesis("HIDDEN_FLOW", "HIGH", "flow:101:mainNetInflow");
    CapitalHypothesis invented = hypothesis("DISTRIBUTION", "MID", "flow:999:mainNetInflow");
    List<CapitalHypothesis> accepted = gate.apply(snapshotWithoutLevelTwo(), Arrays.asList(hidden, invented));
    assertEquals(1, accepted.size());
    assertEquals("LOW", accepted.get(0).getConfidence());
    assertTrue(accepted.get(0).getCounterEvidence().contains("缺少 Level-2 逐笔委托/成交"));
}

@Test
void fallsBackToRuleExplanationWhenLlmIsUnavailable() {
    when(llm.isConfigured()).thenReturn(false);
    CapitalInterpretation result = facade.interpret(7L, false);
    assertEquals("FALLBACK", result.getStatus());
    assertEquals("LLM_NOT_CONFIGURED", result.getFallbackReason());
    assertEquals("capital-rules-v1", result.getRuleVersion());
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=CapitalHypothesisGateTest,CapitalInterpretationFacadeTest test`

Expected: FAIL，Gate、Agent、Facade 不存在。

- [ ] **Step 3: 实现严格 Agent JSON 合同**

Prompt 只包含白名单数字、时间、质量状态和 metric refs，不包含网页 HTML 或上游 Signal 文本。模型 JSON 只接收 `plainSummary/hypotheses/dataGaps/observationPoints/disclaimer`；事实由 `CapitalFactAssembler` 注入最终报告。解析失败返回 `INVALID_MODEL_OUTPUT`，不从自然语言猜测字段。

- [ ] **Step 4: 实现置信度 Policy 与 Facade**

无 Level-2 时 `ORDER_SPLITTING/HIDDEN_FLOW` 最高 LOW；单日 `ACCUMULATION/DISTRIBUTION` 最高 LOW；多日量价资金交叉支持最高 MID；第一阶段不允许 HIGH。Facade 以 `snapshot fingerprint + model + promptVersion` 做动作指纹和缓存键，通过 `AgentHarness` 执行，调用前 `context.recordLlmCall()` 并检查预算，结果与 Trace 同步持久化。

- [ ] **Step 5: 运行 Agent 测试**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=CapitalHypothesisGateTest,CapitalInterpretationFacadeTest test`

Expected: PASS；模型不能提高置信度、不能引用不存在的指标，未配置 LLM 诚实降级。

- [ ] **Step 6: 提交 Agent 解读**

```bash
git add backend/finscope-service/src/main/java/com/finscope/service/marketintel backend/finscope-service/src/test/java/com/finscope/service/marketintel
git commit --only -m "feat: add guarded capital interpretation agent" -- backend/finscope-service/src/main/java/com/finscope/service/marketintel backend/finscope-service/src/test/java/com/finscope/service/marketintel
```

## Task 8: 刷新编排与 REST API

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketintel/MarketIntelCapitalService.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketintel/MarketIntelCapitalView.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketintel/MarketIntelRefreshCoordinator.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/controller/MarketIntelController.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/response/marketintel/MarketIntelInstrumentResponse.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/response/marketintel/CapitalBehaviorResponse.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/response/marketintel/CapitalInterpretationResponse.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/config/FinScopeProperties.java`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/MarketIntelApiIntegrationTest.java`

- [ ] **Step 1: 写 API 失败测试**

```java
@BeforeEach
void seedStockInstrument() {
    insertInstrument(7L, "600519", "SH", "STOCK");
}

@Test
void refreshThenQueryReturnsPersistedDataAndRuleExplanationWithoutCallingLlm() throws Exception {
    when(capitalFlowProvider.supports(any())).thenReturn(true);
    when(capitalFlowProvider.fetch(any(), any())).thenReturn(providerFixture());
    mvc.perform(post("/api/market-intel/instruments/7/refresh"))
            .andExpect(status().isAccepted());
    mvc.perform(get("/api/market-intel/instruments/7/capital-behavior?range=20d&granularity=5m"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ruleExplanation.ruleVersion").value("capital-rules-v1"))
            .andExpect(jsonPath("$.intradayTimeline").isArray())
            .andExpect(jsonPath("$.health.status").value("FRESH"));
    verifyNoInteractions(llmChatClient);
}

@Test
void clickingInterpretationStartsAgentAndReturnsTraceableResult() throws Exception {
    when(llmChatClient.isConfigured()).thenReturn(true);
    when(llmChatClient.modelName()).thenReturn("test-model");
    when(llmChatClient.complete(anyString(), anyString(), anyInt())).thenReturn(validAgentJson());
    MvcResult created = mvc.perform(post("/api/market-intel/instruments/7/capital-interpretations"))
            .andExpect(status().isAccepted()).andReturn();
    long id = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asLong();
    mvc.perform(get("/api/market-intel/capital-interpretations/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.facts").isArray())
            .andExpect(jsonPath("$.hypotheses[0].confidence").value("LOW"));
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && mvn -pl finscope-web -am -Dtest=MarketIntelApiIntegrationTest test`

Expected: FAIL，Market Intel 路由不存在。

- [ ] **Step 3: 实现 Service 用例和异步命令**

刷新校验 `Instrument.type=STOCK`，先创建 run/step，网络请求在事务外完成，规范化数据按维度短事务写入，再生成快照和规则解释，最后聚合 `SUCCEEDED/PARTIAL/FAILED`。刷新和 Agent 分别使用有界的 `marketIntelRefreshExecutor`、`marketIntelAgentExecutor`；同 snapshot/model/prompt 只允许一个 active interpretation。GET 只读已保存数据，不触发外部网络或 LLM。集成测试在 `@BeforeEach` 写入 id=7 的 STOCK Instrument，以 `@MockBean CapitalFlowProvider` 提供 fixture，并用 `SyncTaskExecutor` 覆盖两个异步执行器，保证断言无竞态。

- [ ] **Step 4: 实现 Web DTO 和状态码**

端点为：`GET /instruments`、`POST /instruments/{id}/refresh`、`GET /instruments/{id}/capital-behavior`、`POST /instruments/{id}/capital-interpretations`、`GET /capital-interpretations/{id}`。不存在返回 404，非股票返回 400，创建异步刷新/解读返回 202，缓存命中返回 200，模型失败通过 interpretation 状态表达。`FinScopeProperties.MarketIntel` 显式提供 `enabled`、HTTP connect/read timeout、响应上限、东财最小请求间隔、规则版本、Agent enabled/model timeout/prompt version；默认值写入 properties 类，测试可覆盖。

- [ ] **Step 5: 运行 API 测试**

Run: `cd backend && mvn -pl finscope-web -am -Dtest=MarketIntelApiIntegrationTest test`

Expected: PASS；普通查询未调用 LLM，点击命令产生可追溯结果。

- [ ] **Step 6: 提交 API 切片**

```bash
git add backend/finscope-service/src/main/java/com/finscope/service/marketintel backend/finscope-web/src/main/java/com/finscope/web/controller/MarketIntelController.java backend/finscope-web/src/main/java/com/finscope/web/response/marketintel backend/finscope-web/src/main/java/com/finscope/web/config/FinScopeProperties.java backend/finscope-web/src/test/java/com/finscope/web/MarketIntelApiIntegrationTest.java
git commit --only -m "feat: expose market intel capital api" -- backend/finscope-service/src/main/java/com/finscope/service/marketintel backend/finscope-web/src/main/java/com/finscope/web/controller/MarketIntelController.java backend/finscope-web/src/main/java/com/finscope/web/response/marketintel backend/finscope-web/src/main/java/com/finscope/web/config/FinScopeProperties.java backend/finscope-web/src/test/java/com/finscope/web/MarketIntelApiIntegrationTest.java
```

## Task 9: Market Intel Tab 与按需 Agent 交互

**Files:**
- Create: `frontend/src/features/market-intel/marketIntelTypes.ts`
- Create: `frontend/src/features/market-intel/MarketIntelView.tsx`
- Create: `frontend/src/features/market-intel/CapitalBehaviorPanel.tsx`
- Create: `frontend/src/features/market-intel/CapitalRuleExplanationCard.tsx`
- Create: `frontend/src/features/market-intel/CapitalAgentInterpretationPanel.tsx`
- Create: `frontend/src/features/market-intel/MarketIntelView.test.tsx`
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/app/AppShell.tsx`
- Modify: `frontend/src/app/AppShell.test.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: 写导航和交互失败测试**

```tsx
vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));

test('shows rule explanation automatically and only calls agent after click', async () => {
  const user = userEvent.setup();
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (path === '/api/market-intel/instruments') return instruments as never;
    if (String(path).includes('/capital-behavior')) return capitalBehavior as never;
    if (String(path).includes('/capital-interpretations') && options?.method === 'POST') return { id: 88, status: 'RUNNING' } as never;
    if (path === '/api/market-intel/capital-interpretations/88') return interpretation as never;
    throw new Error(String(path));
  });

  render(<MarketIntelView addToast={vi.fn()} setMessage={vi.fn()} />);
  expect(await screen.findByText(/成交明显放大/)).toBeInTheDocument();
  expect(api).not.toHaveBeenCalledWith(expect.stringContaining('capital-interpretations'), expect.anything());
  await user.click(screen.getByRole('button', { name: 'Agent 解读' }));
  expect(await screen.findByText('模型假设')).toBeInTheDocument();
  expect(screen.getByText('低置信度')).toBeInTheDocument();
});
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd frontend && npm test -- MarketIntelView.test.tsx AppShell.test.tsx App.test.tsx`

Expected: FAIL，`marketIntel` View 和组件不存在。

- [ ] **Step 3: 实现导航和页面状态**

在 `View` union 增加 `marketIntel`，在决策导航组中放到 Watchlist 与 Strategy 之间，代码 `MI`。`App.tsx` 标题为 `Market Intel` 并挂载 `MarketIntelView`。页面独立加载 instrument 列表；切换标的后取消旧轮询并清空旧数据，防止串标。

- [ ] **Step 4: 实现资金面板、规则卡和 Agent 面板**

资金面板展示摘要、5/10/20 日趋势和紧凑时间线表；规则卡默认显示 `summary/items/metricRefs/ruleVersion`。Agent 按钮 POST 后每 750ms 轮询，终态停止；事实与模型假设分区，LOW/MID 使用文字而非只靠颜色，始终显示反证、数据缺口和“不构成投资建议”。

- [ ] **Step 5: 添加局部样式**

所有选择器以 `.market-intel-*` 开头，复用现有按钮、卡片、状态 pill 和 CSS 变量。窄屏将双栏折叠为单栏，时间线容器使用横向滚动，不修改全局字体和主题变量。

- [ ] **Step 6: 运行前端测试和构建**

Run: `cd frontend && npm test -- MarketIntelView.test.tsx AppShell.test.tsx App.test.tsx && npm run build`

Expected: tests PASS；TypeScript build 和 Vite build 成功。

- [ ] **Step 7: 提交前端切片**

```bash
git add frontend/src/features/market-intel frontend/src/shared/types/index.ts frontend/src/app/AppShell.tsx frontend/src/app/AppShell.test.tsx frontend/src/App.tsx frontend/src/App.test.tsx frontend/src/styles.css
git commit --only -m "feat: add market intel capital workspace" -- frontend/src/features/market-intel frontend/src/shared/types/index.ts frontend/src/app/AppShell.tsx frontend/src/app/AppShell.test.tsx frontend/src/App.tsx frontend/src/App.test.tsx frontend/src/styles.css
```

## Task 10: 全链路回归、质量门与文档同步

**Files:**
- Modify: `docs/技术方案-A股标的研究数据中心.md`
- Modify: `docs/产品需求-A股标的研究数据中心.md`
- Create: `docs/验收记录-A股标的研究数据中心-第一阶段A.md`

- [ ] **Step 1: 运行后端完整测试**

Run: `cd backend && mvn test`

Expected: BUILD SUCCESS。若公司 Maven 仓库在当前网络不可达，保存完整失败输出，再运行已缓存的模块命令：`mvn -o -pl finscope-domain,finscope-dao,finscope-rpc,finscope-service,finscope-web -am test`；不得将未运行的测试声称为通过。

- [ ] **Step 2: 运行前端完整测试和构建**

Run: `cd frontend && npm test && npm run build`

Expected: 所有 Vitest 测试通过，TypeScript 无错误，Vite 产物生成成功。

- [ ] **Step 3: 运行静态一致性检查**

```bash
rg -n "TO[D]O|FIX[M]E|bullish|bearish|稳赚|买入建议|卖出建议" backend frontend/src/features/market-intel
git diff --check
```

Expected: 新实现无占位标记，规则层不含多空或买卖建议文本，`git diff --check` 无输出。

- [ ] **Step 4: 完成验收记录**

验收记录必须列出：已完成端点、数据源 fixture、规则版本、Prompt 版本、测试命令和真实结果、未完成的龙虎榜/解禁/行业概念切片、已知数据源限制、LLM 未配置时的降级行为。将 PRD/技术方案状态更新为“第一阶段 A 已实现”，不把未实现维度标记完成。

- [ ] **Step 5: 最终针对性提交**

```bash
git add docs/技术方案-A股标的研究数据中心.md docs/产品需求-A股标的研究数据中心.md docs/验收记录-A股标的研究数据中心-第一阶段A.md
git commit --only -m "docs: record market intel phase 1a delivery" -- docs/技术方案-A股标的研究数据中心.md docs/产品需求-A股标的研究数据中心.md docs/验收记录-A股标的研究数据中心-第一阶段A.md
```

## 完成定义

- Market Intel 独立 Tab 可从已有 A 股 Instrument 选择标的。
- 外部刷新产生不可变资金点、资金快照和确定性规则解释。
- 普通页面查询不调用 LLM。
- 点击 Agent 解读后产生可追溯的结构化结果，事实与假设分离。
- 无 Level-2 时拆单/隐藏资金最高 LOW，所有意图假设最高 MID。
- 单源、LLM 或模型输出失败不影响已保存图表与规则解释。
- 后端 fixture/DAO/Service/API 测试与前端交互测试通过。
- 未实现的龙虎榜、解禁、行业概念和新闻公告保持明确未完成状态。
