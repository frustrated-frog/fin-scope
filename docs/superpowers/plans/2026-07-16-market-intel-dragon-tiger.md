# Market Intel 龙虎榜事实维度 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Market Intel 中交付近 120 日龙虎榜主记录、买卖席位 TOP5、版本化持久化、独立刷新步骤、查询 API 和前端事实面板。

**Architecture:** 新增 `DRAGON_TIGER` 市场数据能力和独立 Provider 合同，东财 Adapter 只负责外部 JSON 规范化；`MarketDataGateway` 负责路由、限流、重试、熔断、single-flight 和快照兜底；Market Intel Repository 保存不可变业务版本，刷新协调器汇总资金流与龙虎榜两个独立步骤；React 页面并行读取两个事实维度。

**Tech Stack:** Java 8、Spring Boot 2.7、JdbcTemplate、SQLite、Jackson、JUnit 5、Mockito、React 18、TypeScript 5.6、Vitest、Testing Library。

---

## 文件职责

### 新增文件

- `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/DragonTigerRecord.java`：一条上榜事件及其席位集合。
- `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/DragonTigerSeat.java`：买入或卖出榜单中的席位事实。
- `backend/finscope-domain/src/test/java/com/finscope/domain/marketintel/DragonTigerContractTest.java`：领域约束。
- `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/DragonTigerData.java`：Provider 聚合输出。
- `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/DragonTigerProvider.java`：可路由 Provider 合同。
- `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/eastmoney/EastmoneyDragonTigerProvider.java`：东财数据中心 Adapter。
- `backend/finscope-rpc/src/test/java/com/finscope/rpc/marketintel/eastmoney/EastmoneyDragonTigerProviderTest.java`：解析、空集、部分席位和请求参数测试。
- `backend/finscope-rpc/src/test/resources/marketintel/eastmoney-dragon-tiger-records.json`：主记录 fixture。
- `backend/finscope-rpc/src/test/resources/marketintel/eastmoney-dragon-tiger-buy.json`：买入席位 fixture。
- `backend/finscope-rpc/src/test/resources/marketintel/eastmoney-dragon-tiger-sell.json`：卖出席位 fixture。
- `backend/finscope-dao/src/main/java/com/finscope/dao/marketintel/DragonTigerRepository.java`：版本化保存与最新业务视图查询。
- `backend/finscope-service/src/main/java/com/finscope/service/marketdata/DragonTigerGatewayResult.java`：网关质量结果。
- `backend/finscope-service/src/test/java/com/finscope/service/marketdata/MarketDataGatewayDragonTigerTest.java`：主源、空集、快照兜底和不可用。
- `backend/finscope-service/src/main/java/com/finscope/service/marketintel/DragonTigerView.java`：Web 查询合同。
- `backend/finscope-service/src/main/java/com/finscope/service/marketintel/MarketIntelDragonTigerService.java`：查询窗口和健康状态。
- `frontend/src/features/market-intel/DragonTigerPanel.tsx`：事实面板。
- `frontend/src/features/market-intel/DragonTigerPanel.test.tsx`：组件状态与席位展开。

### 修改文件

- `backend/finscope-domain/src/main/java/com/finscope/domain/marketdata/MarketDataCapability.java`
- `backend/finscope-service/src/main/java/com/finscope/service/marketdata/MarketDataGateway.java`
- `backend/finscope-service/src/main/java/com/finscope/service/marketdata/MarketDataSnapshotCodec.java`
- `backend/finscope-service/src/test/java/com/finscope/service/marketdata/MarketDataSnapshotCodecTest.java`
- `backend/finscope-dao/src/main/java/com/finscope/dao/marketintel/MarketIntelSchemaMigrator.java`
- `backend/finscope-dao/src/test/java/com/finscope/dao/marketintel/MarketIntelPersistenceTest.java`
- `backend/finscope-service/src/main/java/com/finscope/service/marketintel/MarketIntelRefreshCoordinator.java`
- `backend/finscope-service/src/test/java/com/finscope/service/marketintel/MarketIntelRefreshCoordinatorTest.java`
- `backend/finscope-web/src/main/java/com/finscope/web/controller/MarketIntelController.java`
- `backend/finscope-web/src/test/java/com/finscope/web/MarketIntelApiIntegrationTest.java`
- `frontend/src/features/market-intel/marketIntelTypes.ts`
- `frontend/src/features/market-intel/MarketIntelView.tsx`
- `frontend/src/features/market-intel/MarketIntelView.test.tsx`
- `frontend/src/styles.css`

---

### Task 1: 领域合同与能力枚举

**Files:**
- Create: `backend/finscope-domain/src/test/java/com/finscope/domain/marketintel/DragonTigerContractTest.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/DragonTigerRecord.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/DragonTigerSeat.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/marketdata/MarketDataCapability.java`

- [ ] **Step 1: 写失败的领域测试**

```java
@Test
void keepsSeatFactsAndExplicitLabelsWithoutInferringInvestorIdentity() {
    DragonTigerSeat seat = new DragonTigerSeat();
    seat.setDirection("BUY");
    seat.setRank(1);
    seat.setSeatName("机构专用");
    seat.setInstitutional(true);
    DragonTigerRecord record = new DragonTigerRecord();
    record.setInstrumentId(7L);
    record.setTradeDate(LocalDate.of(2026, 7, 15));
    record.setExternalId("100373909");
    record.setReason("日跌幅偏离值达到7%的前5只证券");
    record.setSeats(Collections.singletonList(seat));

    assertEquals("BUY", record.getSeats().get(0).getDirection());
    assertTrue(record.getSeats().get(0).isInstitutional());
    assertTrue(Arrays.asList(MarketDataCapability.values())
            .contains(MarketDataCapability.DRAGON_TIGER));
}
```

- [ ] **Step 2: 验证 RED**

Run:

```bash
mvn -pl finscope-domain -am -Dtest=DragonTigerContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 测试编译失败，提示 `DragonTigerRecord`、`DragonTigerSeat` 或 `DRAGON_TIGER` 不存在。

- [ ] **Step 3: 实现领域对象和能力**

两个领域对象使用项目当前 Java Bean 风格，金额使用 `BigDecimal`，时间使用 `LocalDate/LocalDateTime`，集合 setter 防御性复制，`DragonTigerRecord#getBuySeats/getSellSeats` 按方向和排名返回不可变列表。

```java
public List<DragonTigerSeat> getBuySeats() {
    return seats.stream().filter(value -> "BUY".equals(value.getDirection()))
            .sorted(Comparator.comparing(DragonTigerSeat::getRank))
            .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
}
```

在枚举末尾增加：

```java
DRAGON_TIGER
```

- [ ] **Step 4: 验证 GREEN**

运行 Step 2 命令，Expected: `DragonTigerContractTest` 通过。

- [ ] **Step 5: 提交**

```bash
git add backend/finscope-domain
git commit -m "feat: 定义龙虎榜领域合同"
```

---

### Task 2: 东财龙虎榜 Provider

**Files:**
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/DragonTigerData.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/DragonTigerProvider.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/eastmoney/EastmoneyDragonTigerProvider.java`
- Create: `backend/finscope-rpc/src/test/java/com/finscope/rpc/marketintel/eastmoney/EastmoneyDragonTigerProviderTest.java`
- Create: `backend/finscope-rpc/src/test/resources/marketintel/eastmoney-dragon-tiger-records.json`
- Create: `backend/finscope-rpc/src/test/resources/marketintel/eastmoney-dragon-tiger-buy.json`
- Create: `backend/finscope-rpc/src/test/resources/marketintel/eastmoney-dragon-tiger-sell.json`

- [ ] **Step 1: 写失败的 Provider 测试**

测试必须覆盖：

```java
@Test
void parsesSummaryAndTopFiveSeats() {
    DragonTigerData data = provider(new FixtureHttpClient()).fetch(
            stock(), LocalDate.of(2026, 3, 19), LocalDate.of(2026, 7, 16));
    DragonTigerRecord record = data.getRecords().get(0);
    assertEquals("100373909", record.getExternalId());
    assertEquals(new BigDecimal("-395870676.13"), record.getNetAmount());
    assertEquals(5, record.getBuySeats().size());
    assertEquals(5, record.getSellSeats().size());
    assertTrue(record.getBuySeats().stream().anyMatch(DragonTigerSeat::isInstitutional));
    assertTrue(record.getBuySeats().stream().anyMatch(DragonTigerSeat::isNorthbound));
}

@Test
void requestsOnlyTheTargetSecurityAndRange() {
    RecordingHttpClient client = new RecordingHttpClient();
    provider(client).fetch(stock(), LocalDate.of(2026, 3, 19), LocalDate.of(2026, 7, 16));
    URI summary = client.summary();
    assertTrue(summary.getRawQuery().contains("RPT_DAILYBILLBOARD_DETAILSNEW"));
    assertTrue(URLDecoder.decode(summary.getRawQuery(), "UTF-8")
            .contains("(SECURITY_CODE=\"000021\")"));
}

@Test
void treatsAnEmptySummaryAsSuccess() {
    assertTrue(provider(new EmptyHttpClient()).fetch(stock(), from(), to()).getRecords().isEmpty());
}
```

席位单方向失败用例断言主记录 `qualityStatus=PARTIAL` 且 warnings 包含 `DRAGON_TIGER_SEAT_UNAVAILABLE`。

- [ ] **Step 2: 验证 RED**

```bash
mvn -pl finscope-rpc -am -Dtest=EastmoneyDragonTigerProviderTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 测试编译失败，Provider 合同尚不存在。

- [ ] **Step 3: 实现 Provider**

Provider 元数据：

```java
public String providerCode() { return "EASTMONEY_DRAGON_TIGER"; }
public String providerFamily() { return "EASTMONEY"; }
public Set<MarketDataCapability> capabilities() {
    return Collections.singleton(MarketDataCapability.DRAGON_TIGER);
}
public int priority() { return 10; }
public int batchLimit() { return 1; }
public Duration minimumInterval() { return Duration.ofMillis(800); }
public Duration timeout() { return Duration.ofSeconds(12); }
```

主记录查询使用 `RPT_DAILYBILLBOARD_DETAILSNEW`，filter 同时包含股票和日期：

```text
(SECURITY_CODE="000021")(TRADE_DATE>='2026-03-19')(TRADE_DATE<='2026-07-16')
```

每个不同上榜日期分别查询 `RPT_BILLBOARD_DAILYDETAILSBUY` 和 `RPT_BILLBOARD_DAILYDETAILSSELL`。按 `TRADE_ID` 优先匹配，缺少 ID 时按原因匹配，当天只有一条主记录时允许唯一兜底。席位名称严格映射：

```java
seat.setInstitutional("机构专用".equals(name));
seat.setNorthbound("沪股通专用".equals(name) || "深股通专用".equals(name));
```

外部 ID 缺失时：

```java
record.setExternalId(ProviderResult.hashOf(code + "|" + tradeDate + "|" + reason));
```

- [ ] **Step 4: 验证 GREEN**

运行 Step 2 命令，Expected: Provider 测试全部通过。

- [ ] **Step 5: 提交**

```bash
git add backend/finscope-rpc
git commit -m "feat: 接入东财龙虎榜数据源"
```

---

### Task 3: 版本化 SQLite 持久化

**Files:**
- Modify: `backend/finscope-dao/src/test/java/com/finscope/dao/marketintel/MarketIntelPersistenceTest.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/marketintel/MarketIntelSchemaMigrator.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/marketintel/DragonTigerRepository.java`

- [ ] **Step 1: 写失败的迁移和 Repository 测试**

```java
@Test
void dragonTigerFactsAreVersionedAndQueriesReturnLatestBusinessVersion() {
    assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=107"));
    assertEquals(1, tableCount("market_dragon_tiger_record"));
    assertEquals(1, tableCount("market_dragon_tiger_seat"));

    DragonTigerRecord first = dragonTiger("payload-v1", new BigDecimal("100"));
    DragonTigerRecord revised = dragonTiger("payload-v2", new BigDecimal("120"));
    dragonTiger.saveAll(Collections.singletonList(first));
    dragonTiger.saveAll(Collections.singletonList(first));
    dragonTiger.saveAll(Collections.singletonList(revised));

    assertEquals(2, count("SELECT COUNT(*) FROM market_dragon_tiger_record"));
    List<DragonTigerRecord> latest = dragonTiger.findLatestBusinessVersions(
            7L, LocalDate.of(2026, 3, 19), LocalDate.of(2026, 7, 16));
    assertEquals(1, latest.size());
    assertEquals(new BigDecimal("120"), latest.get(0).getNetAmount());
    assertEquals(1, latest.get(0).getBuySeats().get(0).getRank().intValue());
}
```

- [ ] **Step 2: 验证 RED**

```bash
mvn -pl finscope-dao -am -Dtest=MarketIntelPersistenceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: version 107 和 Repository 不存在。

- [ ] **Step 3: 实现迁移与 Repository**

迁移版本：

```java
private static final int DRAGON_TIGER_VERSION = 107;
```

按规格创建两张表和索引。Repository 保存顺序：

1. `INSERT OR IGNORE` 主记录；
2. 用完整唯一身份查询 record id；
3. 为该版本 `INSERT OR IGNORE` 席位；
4. 查询使用窗口函数：

```sql
SELECT * FROM (
  SELECT r.*, ROW_NUMBER() OVER (
    PARTITION BY instrument_id,provider_code,trade_date,external_id
    ORDER BY retrieved_at DESC,id DESC
  ) rn
  FROM market_dragon_tiger_record r
  WHERE instrument_id=? AND trade_date BETWEEN ? AND ?
) latest
WHERE rn=1
ORDER BY trade_date DESC,id DESC
```

每条最新主记录再按 `direction ASC, rank ASC` 加载席位。

- [ ] **Step 4: 验证 GREEN**

运行 Step 2 命令，Expected: `MarketIntelPersistenceTest` 通过。

- [ ] **Step 5: 提交**

```bash
git add backend/finscope-dao
git commit -m "feat: 持久化龙虎榜事实版本"
```

---

### Task 4: MarketDataGateway 路由与快照兜底

**Files:**
- Create: `backend/finscope-service/src/test/java/com/finscope/service/marketdata/MarketDataGatewayDragonTigerTest.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketdata/DragonTigerGatewayResult.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketdata/MarketDataGateway.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketdata/MarketDataSnapshotCodec.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/marketdata/MarketDataSnapshotCodecTest.java`

- [ ] **Step 1: 写失败的网关测试**

```java
@Test
void routesDragonTigerThroughTheHealthyProvider() {
    DragonTigerGatewayResult result = gateway(primary()).fetchDragonTiger(
            instrument(), LocalDate.of(2026, 3, 19), LocalDate.of(2026, 7, 16));
    assertEquals(MarketDataQualityStatus.FRESH_PRIMARY, result.getQualityStatus());
    assertEquals("EASTMONEY_DRAGON_TIGER", result.getSourceCode());
    assertEquals(1, result.getData().getRecords().size());
}

@Test
void successfulEmptySetIsFreshPrimary() {
    assertEquals(MarketDataQualityStatus.FRESH_PRIMARY,
            gateway(emptyProvider()).fetchDragonTiger(instrument(), from(), to()).getQualityStatus());
}

@Test
void fallsBackToStoredSnapshotWhenAllProvidersFail() {
    assertEquals(MarketDataQualityStatus.STALE_FALLBACK,
            gateway(failingProvider(), storedSnapshot()).fetchDragonTiger(
                    instrument(), from(), to()).getQualityStatus());
}
```

Codec 测试必须验证 schema version、payload hash 损坏返回 empty、records/seats 往返。

- [ ] **Step 2: 验证 RED**

```bash
mvn -pl finscope-service -am \
  -Dtest=MarketDataGatewayDragonTigerTest,MarketDataSnapshotCodecTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: gateway 方法和 codec 方法不存在。

- [ ] **Step 3: 实现 Gateway**

增加 `List<DragonTigerProvider> dragonTigerProviders`，保留现有构造器并委托到完整构造器的空列表，避免破坏既有测试。

```java
public DragonTigerGatewayResult fetchDragonTiger(
        Instrument instrument, LocalDate startDate, LocalDate endDate) {
    String key = MarketDataCapability.DRAGON_TIGER.name() + ":" + instrument.getId()
            + ":" + startDate + ":" + endDate;
    return singleFlight.execute(key, () -> routeDragonTiger(instrument, startDate, endDate));
}
```

路由规则：

- `ProviderRoutePolicy.order`；
- `ProviderRequestGuard.execute`；
- 空 records 是成功；
- fresh 成功后：

```java
snapshots.upsert(codec.dragonTigerSnapshot(
        scopeKey, provider.providerCode(), provider.providerFamily(),
        data, dataAsOf(data, endDate), fetched.getRetrievedAt(), LocalDateTime.now(clock)));
```
- 在线失败时从 `MarketDataSnapshotRepository` 和 codec 解码；
- 审计使用 `MarketDataCapability.DRAGON_TIGER`。

- [ ] **Step 4: 实现 Codec**

```java
private static final int DRAGON_TIGER_SCHEMA_VERSION = 1;

public Optional<DragonTigerData> decodeDragonTiger(MarketDataSnapshot snapshot) {
    if (snapshot == null
            || snapshot.getCapability() != MarketDataCapability.DRAGON_TIGER
            || snapshot.getSchemaVersion() != DRAGON_TIGER_SCHEMA_VERSION
            || !sha256(snapshot.getPayloadJson()).equals(snapshot.getPayloadHash())) {
        return Optional.empty();
    }
    try {
        return Optional.of(mapper.readValue(snapshot.getPayloadJson(), DragonTigerData.class));
    } catch (IOException error) {
        return Optional.empty();
    }
}

public MarketDataSnapshot dragonTigerSnapshot(
        String scopeKey, String providerCode, String providerFamily,
        DragonTigerData data, LocalDateTime dataAsOf,
        LocalDateTime retrievedAt, LocalDateTime updatedAt) {
    try {
        String payload = mapper.writeValueAsString(data);
        return new MarketDataSnapshot(MarketDataCapability.DRAGON_TIGER, scopeKey,
                providerCode, providerFamily, dataAsOf, retrievedAt,
                payload, sha256(payload), DRAGON_TIGER_SCHEMA_VERSION, updatedAt);
    } catch (JsonProcessingException error) {
        throw new IllegalStateException("龙虎榜快照序列化失败", error);
    }
}
```

- [ ] **Step 5: 验证 GREEN**

运行 Step 2 命令，Expected: 两组测试通过。

- [ ] **Step 6: 提交**

```bash
git add backend/finscope-service
git commit -m "feat: 路由并缓存龙虎榜数据"
```

---

### Task 5: 双维度刷新编排

**Files:**
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/marketintel/MarketIntelRefreshCoordinatorTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketintel/MarketIntelRefreshCoordinator.java`

- [ ] **Step 1: 写失败的刷新状态测试**

新增用例：

```java
@Test
void createsIndependentCapitalAndDragonTigerStepsAndFinishesOnce() {
    when(gateway.fetchCapitalFlow(eq(instrument), any(LocalDate.class)))
            .thenReturn(freshCapital());
    when(gateway.fetchDragonTiger(eq(instrument), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(freshDragonTiger());

    coordinator.refresh(run, instrument);

    verify(runs).createStep(11L, "CAPITAL_FLOW", "TEST_CAPITAL", 1);
    verify(runs).createStep(11L, "DRAGON_TIGER", "TEST_DRAGON_TIGER", 1);
    verify(runs).finishRun(11L, MarketIntelRefreshRun.Status.SUCCEEDED, 2, 0);
}

@Test
void oneDimensionFailureMakesTheRunPartialWithoutDiscardingTheOther() {
    when(gateway.fetchCapitalFlow(eq(instrument), any(LocalDate.class)))
            .thenReturn(freshCapital());
    when(gateway.fetchDragonTiger(eq(instrument), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(DragonTigerGatewayResult.unavailable(
                    "EASTMONEY_DRAGON_TIGER", "龙虎榜源不可用", "dt-failed"));

    coordinator.refresh(run, instrument);

    verify(flows).saveAll(anyList());
    verify(runs).finishRun(11L, MarketIntelRefreshRun.Status.PARTIAL, 1, 1);
}

@Test
void successfulDragonTigerEmptySetCountsAsSuccess() {
    when(gateway.fetchCapitalFlow(eq(instrument), any(LocalDate.class)))
            .thenReturn(freshCapital());
    when(gateway.fetchDragonTiger(eq(instrument), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(DragonTigerGatewayResult.freshPrimary(
                    "EASTMONEY_DRAGON_TIGER",
                    new DragonTigerData(Collections.emptyList(), Collections.emptyList()),
                    LocalDateTime.of(2026, 7, 16, 15, 30), "dt-empty"));

    coordinator.refresh(run, instrument);

    verify(runs).updateStep(anyLong(), eq(MarketIntelRefreshStep.Status.EMPTY),
            eq(0), isNull(), isNull());
    verify(runs).finishRun(11L, MarketIntelRefreshRun.Status.SUCCEEDED, 2, 0);
}
```

- [ ] **Step 2: 验证 RED**

```bash
mvn -pl finscope-service -am -Dtest=MarketIntelRefreshCoordinatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 当前协调器只创建资金步骤。

- [ ] **Step 3: 重构协调器**

将维度执行结果收敛为内部值对象：

```java
private static final class DimensionOutcome {
    private final boolean success;
    private final boolean failed;
    static DimensionOutcome success() { return new DimensionOutcome(true, false); }
    static DimensionOutcome failed() { return new DimensionOutcome(false, true); }
}
```

`refresh(run,instrument)` 顺序执行两个维度，但只在最后调用一次 `finishRun`：

```java
DimensionOutcome capitalOutcome = refreshCapital(run, instrument);
DimensionOutcome dragonTigerOutcome = refreshDragonTiger(run, instrument);
finishRun(run.getId(), Arrays.asList(capitalOutcome, dragonTigerOutcome));
```

`refreshDragonTiger`：

- 查询 `[today-119, today]`；
- 创建 `DRAGON_TIGER` step；
- fresh 数据保存到 `DragonTigerRepository`；
- 空集 step=`EMPTY` 但 outcome success；
- stale fallback 保存可用数据并 step=`SKIPPED`、outcome success；
- unavailable step=`FAILED`、outcome failed。

资本维度现有行为保持不变，但内部方法不再提前结束整个 run。

- [ ] **Step 4: 验证 GREEN**

运行 Step 2 命令，Expected: 协调器测试通过。

- [ ] **Step 5: 提交**

```bash
git add backend/finscope-service
git commit -m "feat: 编排资金流与龙虎榜刷新"
```

---

### Task 6: 查询服务与统一 API

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketintel/DragonTigerView.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketintel/MarketIntelDragonTigerService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/MarketIntelController.java`
- Modify: `backend/finscope-web/src/test/java/com/finscope/web/MarketIntelApiIntegrationTest.java`

- [ ] **Step 1: 写失败的 API 集成测试**

```java
@Test
void returnsPersistedDragonTigerFactsAndSeats() throws Exception {
    mvc.perform(post("/api/market-intel/instruments/7/refresh"))
            .andExpect(status().isAccepted());
    mvc.perform(get("/api/market-intel/instruments/7/dragon-tiger?days=120"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.records[0].reason").isNotEmpty())
            .andExpect(jsonPath("$.data.records[0].buySeats[0].direction").value("BUY"));
}

@Test
void returns200ForAConfirmedEmptyDragonTigerWindow() throws Exception {
    when(dragonTigerProvider.fetch(any(), any(), any())).thenReturn(
            ProviderResult.of(new DragonTigerData(
                    Collections.emptyList(), Collections.emptyList()),
                    LocalDateTime.now(), "empty-lhb", Collections.emptyList()));

    mvc.perform(post("/api/market-intel/instruments/7/refresh"))
            .andExpect(status().isAccepted());
    mvc.perform(get("/api/market-intel/instruments/7/dragon-tiger?days=120"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.records").isEmpty());
}

@Test
void rejectsUnsupportedDragonTigerRange() {
    mvc.perform(get("/api/market-intel/instruments/7/dragon-tiger?days=15"))
            .andExpect(status().isBadRequest());
}
```

- [ ] **Step 2: 验证 RED**

```bash
mvn -pl finscope-web -am -Dtest=MarketIntelApiIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `/dragon-tiger` 返回 404。

- [ ] **Step 3: 实现查询服务**

`days` 仅允许：

```java
private static final Set<Integer> ALLOWED_DAYS =
        new LinkedHashSet<Integer>(Arrays.asList(30, 60, 120));
```

查询 `DragonTigerRepository.findLatestBusinessVersions`，健康状态优先读取最近 `DRAGON_TIGER` refresh step；没有步骤且没有数据返回 `NOT_REFRESHED`，有结构化数据返回对应 fresh/stale 状态。

- [ ] **Step 4: 实现 Controller**

```java
@GetMapping("/instruments/{id}/dragon-tiger")
public ApiResponse<DragonTigerView> dragonTiger(
        @PathVariable Long id,
        @RequestParam(defaultValue = "120") int days) {
    return ApiResponses.success(dragonTiger.view(id, days));
}
```

- [ ] **Step 5: 验证 GREEN**

运行 Step 2 命令，Expected: API 集成测试通过。

- [ ] **Step 6: 提交**

```bash
git add backend/finscope-service backend/finscope-web
git commit -m "feat: 提供龙虎榜查询接口"
```

---

### Task 7: React 龙虎榜面板

**Files:**
- Create: `frontend/src/features/market-intel/DragonTigerPanel.tsx`
- Create: `frontend/src/features/market-intel/DragonTigerPanel.test.tsx`
- Modify: `frontend/src/features/market-intel/marketIntelTypes.ts`
- Modify: `frontend/src/features/market-intel/MarketIntelView.tsx`
- Modify: `frontend/src/features/market-intel/MarketIntelView.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: 写失败的组件测试**

```tsx
test('shows summary facts and expands buy and sell seats', async () => {
  const user = userEvent.setup();
  render(<DragonTigerPanel view={dragonTigerView} />);
  expect(screen.getByText('近120日上榜 1 次')).toBeInTheDocument();
  expect(screen.getByText('日跌幅偏离值达到7%的前5只证券')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '查看席位明细' }));
  expect(screen.getByText('买入席位 TOP5')).toBeInTheDocument();
  expect(screen.getByText('卖出席位 TOP5')).toBeInTheDocument();
  expect(screen.getByText('机构专用')).toBeInTheDocument();
  expect(screen.getByText('深股通专用')).toBeInTheDocument();
});

test('explains a confirmed empty window without treating it as an error', () => {
  render(<DragonTigerPanel view={{
    ...dragonTigerView,
    records: [],
    health: { status: 'FRESH_PRIMARY', providerCode: 'EASTMONEY_DRAGON_TIGER', warnings: [] }
  }} />);
  expect(screen.getByText(/近 120 日没有公开龙虎榜记录/)).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

test('shows stale and unavailable states', () => {
  const { rerender } = render(<DragonTigerPanel view={{
    ...dragonTigerView,
    health: {
      status: 'STALE_FALLBACK',
      providerCode: 'EASTMONEY_DRAGON_TIGER',
      warnings: ['龙虎榜在线刷新失败，正在显示最近成功数据']
    }
  }} />);
  expect(screen.getByText(/最近成功数据/)).toBeInTheDocument();
  rerender(<DragonTigerPanel view={{
    ...dragonTigerView,
    records: [],
    health: {
      status: 'UNAVAILABLE',
      providerCode: 'EASTMONEY_DRAGON_TIGER',
      warnings: ['龙虎榜数据源暂不可用']
    }
  }} />);
  expect(screen.getByRole('alert')).toHaveTextContent('龙虎榜数据源暂不可用');
});
```

`MarketIntelView.test.tsx` 增加并行 API、切换标的竞态和刷新后重载两个维度的测试。

- [ ] **Step 2: 验证 RED**

```bash
npm test -- --run \
  src/features/market-intel/DragonTigerPanel.test.tsx \
  src/features/market-intel/MarketIntelView.test.tsx
```

Expected: `DragonTigerPanel` 模块不存在。

- [ ] **Step 3: 实现类型与组件**

新增 `DragonTigerView/Record/Seat` TypeScript 类型，金额格式复用项目现有展示函数或组件内纯函数。

组件必须包含固定边界文案：

```tsx
<p className="dragon-tiger-disclaimer">
  龙虎榜仅覆盖满足公开披露条件的异常交易，席位不等于具体账户或投资者，不构成投资建议。
</p>
```

- [ ] **Step 4: 接入页面**

`MarketIntelView` 为龙虎榜维护独立 state：

```tsx
const [dragonTiger, setDragonTiger] = useState<DragonTigerView | null>(null);
const [dragonTigerError, setDragonTigerError] = useState<string | null>(null);
```

切换标的时用同一 `selectionVersion` 并行请求，不允许旧响应覆盖新标的。刷新成功后同时重新读取：

```tsx
const [latestCapital, latestDragonTiger] = await Promise.all([
  fetchOverview(instrumentId),
  fetchDragonTiger(instrumentId)
]);
```

任一查询失败时保留另一维度。

- [ ] **Step 5: 验证 GREEN**

运行 Step 2 命令，然后：

```bash
npm run build
```

Expected: 测试与构建通过。

- [ ] **Step 6: 提交**

```bash
git add frontend
git commit -m "feat: 展示龙虎榜事实与席位"
```

---

### Task 8: 真实端点与完整回归

**Files:**
- Modify: `docs/产品需求-A股标的研究数据中心.md`
- Modify: `docs/技术方案-A股标的研究数据中心.md`

- [ ] **Step 1: 更新状态文档**

将龙虎榜状态标为“事实型纵向切片已实现”，明确：

- 东财主记录和席位接口；
- 近 120 日窗口；
- 版本化持久化；
- 不包含游资人物映射和 Agent 推断；
- 真实端点限制与失败降级。

- [ ] **Step 2: 运行后端全量**

```bash
mvn test
```

Expected: reactor `BUILD SUCCESS`，0 failures，0 errors。

- [ ] **Step 3: 运行前端全量**

```bash
npm test -- --run
npm run build
```

Expected: Vitest 全绿，Vite build 成功。

- [ ] **Step 4: 真实端点只读探针**

临时测试使用近期上榜股票，断言：

```java
assertFalse(data.getRecords().isEmpty());
assertTrue(data.getRecords().get(0).getBuySeats().size() <= 5);
assertTrue(data.getRecords().get(0).getSellSeats().size() <= 5);
assertTrue(data.getWarnings().isEmpty());
```

运行成功后删除临时测试文件，再重新运行 RPC 定向测试。

- [ ] **Step 5: 最终检查**

```bash
git diff --check
git status --short
```

Expected: 无 whitespace 错误，只包含本功能文档或未提交的最终验收改动。

- [ ] **Step 6: 提交文档和验收改动**

```bash
git add docs backend frontend
git commit -m "docs: 记录龙虎榜接入状态"
```
