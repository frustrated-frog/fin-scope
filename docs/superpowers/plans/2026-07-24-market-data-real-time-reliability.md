# Market Data Real-Time Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在仅使用免费行情源的前提下，消除超时和重试放大，建立交易时段 15 秒新鲜度、两分钟最大降级边界和 10 秒后台预热。

**Architecture:** Java `MarketDataGateway` 是唯一路由与重试控制面，HTTP 客户端只执行单次有界请求。`MarketTradingSession` 统一判定盘中新鲜度，调度器在交易时段预热自选、指数和板块缓存；Provider 故障和审计按能力隔离。

**Tech Stack:** Java 8, Spring Boot 2.7, SQLite, JUnit 5, Mockito, React/TypeScript/Vitest, Python/FastAPI/pytest.

---

### Task 1: 消除双层重试并隔离 Provider 故障域

**Files:**
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/JdkFinanceHttpClient.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/ProviderRequestGuard.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/marketintel/JdkFinanceHttpClientTest.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/marketintel/ProviderRequestGuardTest.java`

- [ ] **Step 1: Write failing HTTP single-attempt and capability-isolation tests**

```java
assertThrows(ProviderContractException.class,
        () -> client.get("EASTMONEY", uri, Collections.emptyMap()));
assertEquals(1, attempts.get());

assertFalse(guard.isAvailable(sector, MarketDataCapability.SECTOR_CATALOG));
assertTrue(guard.isAvailable(flow, MarketDataCapability.CAPITAL_FLOW_5M));
```

- [ ] **Step 2: Run tests and verify RED**

Run: `cd backend && mvn -pl finscope-rpc -am -Dtest=JdkFinanceHttpClientTest,ProviderRequestGuardTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: HTTP client still retries internally and family state still spans capabilities.

- [ ] **Step 3: Make HTTP single-attempt and family state capability-scoped**

```java
public FinanceHttpResponse get(String provider, URI uri, Map<String, String> headers) throws Exception {
    return getOnce(provider, uri, headers);
}
```

Replace `ConcurrentMap<String, FamilyState>` with a key containing both provider family and capability. Set the production default guard retry count to one.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the Step 2 command. Expected: PASS.

### Task 2: 建立交易时段和快照年龄合同

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketdata/MarketTradingSession.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/marketdata/MarketTradingSessionTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketdata/QuoteQualityValidator.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/marketdata/QuoteQualityValidatorTest.java`

- [ ] **Step 1: Write failing session boundary tests**

```java
assertTrue(session.isOpen(LocalDateTime.of(2026, 7, 24, 10, 0)));
assertFalse(session.isOpen(LocalDateTime.of(2026, 7, 24, 12, 0)));
assertFalse(session.isOpen(LocalDateTime.of(2026, 7, 25, 10, 0)));
assertTrue(session.canServeFallback(now.minusSeconds(120), now));
assertFalse(session.canServeFallback(now.minusSeconds(121), now));
```

- [ ] **Step 2: Run tests and verify RED**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=MarketTradingSessionTest,QuoteQualityValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: `MarketTradingSession` and capability-aware freshness validation do not exist.

- [ ] **Step 3: Implement the session policy and validator overload**

```java
public boolean isOpen(LocalDateTime value) {
    if (value.getDayOfWeek() == DayOfWeek.SATURDAY || value.getDayOfWeek() == DayOfWeek.SUNDAY) return false;
    LocalTime time = value.toLocalTime();
    return between(time, morningOpen, morningClose) || between(time, afternoonOpen, afternoonClose);
}
```

Add `accept(MarketDataCapability capability, String requestedCode, Quote quote)` and reject online quote timestamps older than two minutes only while the market is open.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the Step 2 command. Expected: PASS.

### Task 3: 收紧新鲜缓存与交易时段降级

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketdata/MarketDataGateway.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketdata/MarketDataGatewayProperties.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/marketdata/MarketDataGatewayQuoteTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/marketdata/MarketDataGatewayDragonTigerTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/marketdata/MarketDataGatewaySectorCatalogTest.java`

- [ ] **Step 1: Write failing cache and stale-boundary tests**

```java
gateway.fetchQuotes("STOCK", codes, true); // returns stale fallback
gateway.fetchQuotes("STOCK", codes, false);
assertEquals(2, primary.calls.get()); // stale result did not extend fresh cache

assertEquals(MarketDataQualityStatus.UNAVAILABLE,
        gateway.fetchQuotes("STOCK", codes, true).getQualityStatus());
```

The second assertion uses a stored snapshot 121 seconds old during an open session.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=MarketDataGatewayQuoteTest,MarketDataGatewayDragonTigerTest,MarketDataGatewaySectorCatalogTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: stale results are cached and old snapshots remain eligible.

- [ ] **Step 3: Implement capability-aware cache admission and fallback eligibility**

```java
private boolean cacheable(MarketDataQualityStatus status) {
    return status == MarketDataQualityStatus.FRESH_PRIMARY
            || status == MarketDataQualityStatus.FRESH_FALLBACK;
}
```

Use `MarketTradingSession.canServeFallback(snapshot.getRetrievedAt(), now)` before returning quote or sector snapshots. Preserve daily fact semantics for dragon-tiger data.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the Step 2 command. Expected: PASS.

### Task 4: 建立 10 秒后台批量预热

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketdata/MarketDataWarmupScheduler.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/marketdata/MarketDataWarmupSchedulerTest.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/config/AppConfig.java`
- Modify: `backend/finscope-web/src/main/resources/application.yml`

- [ ] **Step 1: Write failing scheduler tests**

```java
scheduler.refreshHotMarketData();
verify(watchlist).listInvestmentItemsWithQuotes(true);
verify(indices).list(true);
verify(sectors).overview(SectorCategory.INDUSTRY, 5, true);
verify(sectors).overview(SectorCategory.CONCEPT, 5, true);
```

Cover closed market, disabled configuration, overlapping invocation and one capability failing without suppressing others.

- [ ] **Step 2: Run the scheduler test and verify RED**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=MarketDataWarmupSchedulerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: class does not exist.

- [ ] **Step 3: Implement guarded parallel warmup**

```java
@Scheduled(fixedDelayString = "${finscope.market-data.warmup-interval-ms:10000}")
public void refreshHotMarketData() {
    if (!enabled || !session.isOpenNow() || !running.compareAndSet(false, true)) return;
    executor.execute(() -> runAllAndRelease());
}
```

Use a dedicated `marketDataWarmupExecutor`; each capability catches and logs its own failure.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the Step 2 command. Expected: PASS.

### Task 5: 增加 Provider 尝试审计

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketdata/MarketDataProviderAttempt.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/marketdata/MarketDataProviderAttemptRepository.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketdata/MarketDataGateway.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/marketdata/MarketDataPersistenceTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/marketdata/MarketDataGatewayQuoteTest.java`

- [ ] **Step 1: Write failing persistence and gateway audit tests**

```java
assertEquals("TENCENT_STOCK", attempts.findByRun(runId).get(0).getProviderCode());
assertEquals("TIMEOUT", attempts.findByRun(runId).get(0).getErrorType());
assertTrue(attempts.findByRun(runId).get(0).getLatencyMs() >= 0);
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=MarketDataPersistenceTest,MarketDataGatewayQuoteTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: attempt schema and repository do not exist.

- [ ] **Step 3: Add append-only attempt persistence**

Create `market_data_provider_attempt` with a foreign key to `market_data_refresh_run` and an index on `(provider_code, started_at)`. Persist success, failure and cancellation outcomes without allowing audit failure to break the quote result.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the Step 2 command. Expected: PASS.

### Task 6: 回归、真实源验收与发布

**Files:**
- Modify: `README.md`
- Verify all changed files.

- [ ] **Step 1: Document runtime settings and quality boundary**

Document warmup enablement, interval, fresh TTL, maximum intraday fallback age and request budget. State that free sources have no external SLA.

- [ ] **Step 2: Run full automated verification**

Run:

```bash
cd backend && mvn test
cd market-data-service && uv run pytest -q
cd frontend && npm test -- --run
cd frontend && npm run build
```

Expected: all commands exit 0.

- [ ] **Step 3: Run bounded real-provider acceptance**

Call the running watchlist, index, sector and market-data health endpoints with explicit curl timeouts. Record HTTP status, total latency, quality status, source and fact time. External-source failure is reported accurately and does not invalidate deterministic tests.

- [ ] **Step 4: Review and publish**

Run: `git diff --check && git status --short`

Commit messages use English type identifiers with Chinese descriptions, for example:

```text
docs: 定义行情数据实时性与可靠性改造
feat: 提升免费行情源刷新成功率与实时性
```

Push `codex/market-data-reliability` to the GitHub remote.
