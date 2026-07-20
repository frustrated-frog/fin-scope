# Market Intel 龙虎榜事实维度设计

## 1. 目标

在现有 Market Intel 工作台中增加可追溯的 A 股龙虎榜事实维度。用户选择自选股票后，可以刷新并查看近 120 个自然日内的上榜记录、上榜原因、买卖汇总以及买入/卖出席位 TOP5。

龙虎榜数据必须保存到本地 SQLite，并进入统一数据源路由、限流、重试、熔断、刷新审计和数据质量体系。第一期仅保存与展示公开事实，不把营业部名称推断成具体游资，不生成买入、卖出或看多、看空结论。

## 2. 用户价值

当前资金行为页面能回答资金流向、成交上下文和异常信号，但无法回答以下问题：

- 标的近期是否因为异常交易条件进入龙虎榜；
- 上榜是价格偏离、振幅、换手率还是连续异常波动触发；
- 公开买入和卖出席位的集中度如何；
- 是否出现“机构专用”“沪股通专用”“深股通专用”等明确席位类型；
- 龙虎榜事实发生在什么日期，能否在以后研究中稳定复查。

本期完成后，用户不需要离开 FinScope 即可查看这些事实，并能区分“未上榜”“数据源失败”和“数据尚未刷新”。

## 3. 范围

### 3.1 本期包含

1. 支持沪、深、北交所 A 股 `Instrument`。
2. 默认查询当前日期向前 120 个自然日。
3. 保存每条龙虎榜主记录：
   - 上榜日期；
   - 上榜原因代码或可复用外部标识；
   - 上榜原因原文；
   - 东财解读原文；
   - 收盘价与涨跌幅；
   - 龙虎榜买入额、卖出额、净买入额和成交额；
   - 市场总成交额；
   - 净买额占总成交比、龙虎榜成交额占总成交比；
   - 换手率和流通市值；
   - 数据源、抓取时间、原始载荷指纹和质量状态。
4. 保存每条主记录关联的席位：
   - 席位代码与名称；
   - `BUY` 或 `SELL` 榜单方向；
   - 榜单排名；
   - 买入额、卖出额和净额；
   - 买入额/卖出额占总成交比例；
   - 明确席位类型；
   - `institutional`、`northbound` 两个确定性标记；
   - 外部交易标识和原始载荷指纹。
5. 接入 `MarketDataGateway`：
   - 新增 `DRAGON_TIGER` 能力；
   - 按 Provider 健康状态排序；
   - 复用统一限流、超时、重试、熔断和调用审计；
   - 同一标的、同一时间窗口使用 single-flight 防止重复请求。
6. 接入 Market Intel 刷新：
   - 同一次手动刷新包含 `CAPITAL_FLOW` 和 `DRAGON_TIGER` 两个独立步骤；
   - 一个维度失败不阻断另一个维度；
   - 总状态根据两个步骤计算为 `SUCCEEDED`、`PARTIAL` 或 `FAILED`。
7. 提供查询接口：
   - `GET /api/market-intel/instruments/{instrumentId}/dragon-tiger?days=120`
8. 增加前端独立龙虎榜区域：
   - 记录概览；
   - 买入 TOP5；
   - 卖出 TOP5；
   - 数据质量和空状态；
   - 最近成功刷新时间。

### 3.2 本期不包含

- 不建立“章盟主”“作手新一”等游资人物标签。
- 不根据营业部历史表现推断席位身份。
- 不把龙虎榜直接转成买入、卖出或仓位建议。
- 不进入资金行为因子评价、历史回测或量化横截面评价。
- 不自动注入现有资金行为 Agent。
- 不接入解禁、减持、融资融券或新闻公告。
- 不提供全市场龙虎榜榜单页，只提供当前研究标的维度。

## 4. 数据源

### 4.1 主数据源

第一期使用东方财富数据中心结构化 JSON 接口：

```text
https://datacenter-web.eastmoney.com/api/data/v1/get
```

主记录报表：

```text
RPT_DAILYBILLBOARD_DETAILSNEW
```

买入席位报表：

```text
RPT_BILLBOARD_DAILYDETAILSBUY
```

卖出席位报表：

```text
RPT_BILLBOARD_DAILYDETAILSSELL
```

主记录请求必须同时按 `SECURITY_CODE` 和日期范围过滤，不能先下载全市场 5000 条记录再由本地筛选。席位请求按股票代码和上榜日期过滤。

### 4.2 外部记录匹配

同一股票在同一交易日可能因为不同规则产生多条上榜原因。Provider 解析时按以下优先级关联主记录与席位：

1. 外部 `TRADE_ID` 一致；
2. `SECURITY_CODE + TRADE_DATE + EXPLANATION` 一致；
3. 如果席位响应没有原因字段且当天只有一条主记录，则关联到该记录；
4. 无法唯一关联时不猜测，主记录保留，席位标记为缺失并生成 `DRAGON_TIGER_SEAT_AMBIGUOUS` 警告。

### 4.3 空数据语义

- 接口成功且主记录列表为空：表示近 120 日没有上榜，属于成功空集。
- 主记录成功但某日席位请求失败：保留主记录，状态为 `PARTIAL`。
- 主记录接口网络、HTTP 或结构失败：该 Provider 调用失败，由网关尝试其他 Provider 或历史快照。

### 4.4 备用源

第一期不实现第二个生产 Provider。接口和网关能力必须允许后续增加交易所披露或其他公开源；测试中使用 Fixture Provider 验证路由和降级，不在生产环境伪造备用源。

## 5. 领域模型

### 5.1 `DragonTigerRecord`

```java
public class DragonTigerRecord {
    private Long id;
    private Long instrumentId;
    private String providerCode;
    private LocalDate tradeDate;
    private String externalId;
    private String reasonCode;
    private String reason;
    private String providerExplanation;
    private BigDecimal closePrice;
    private BigDecimal changeRate;
    private BigDecimal buyAmount;
    private BigDecimal sellAmount;
    private BigDecimal netAmount;
    private BigDecimal billboardAmount;
    private BigDecimal marketAmount;
    private BigDecimal netAmountRatio;
    private BigDecimal billboardAmountRatio;
    private BigDecimal turnoverRate;
    private BigDecimal freeMarketCap;
    private List<DragonTigerSeat> seats;
    private LocalDateTime retrievedAt;
    private String payloadHash;
    private String qualityStatus;
}
```

`qualityStatus` 仅允许：

- `COMPLETE`：主记录和两个方向的席位均完整；
- `PARTIAL`：主记录存在，但席位缺失、歧义或只成功一个方向。

### 5.2 `DragonTigerSeat`

```java
public class DragonTigerSeat {
    private Long id;
    private Long recordId;
    private String externalTradeId;
    private String seatCode;
    private String seatName;
    private String direction;
    private Integer rank;
    private BigDecimal buyAmount;
    private BigDecimal sellAmount;
    private BigDecimal netAmount;
    private BigDecimal buyRatio;
    private BigDecimal sellRatio;
    private String seatType;
    private boolean institutional;
    private boolean northbound;
    private LocalDateTime retrievedAt;
    private String payloadHash;
}
```

`direction` 仅允许 `BUY`、`SELL`。

`institutional=true` 只在席位名称或上游明确类型等于“机构专用”时设置。

`northbound=true` 只在席位名称明确为“沪股通专用”或“深股通专用”时设置。

其他营业部名称不做人物或资金风格推断。

### 5.3 `DragonTigerData`

Provider 输出使用不可变聚合对象：

```java
public final class DragonTigerData {
    private final List<DragonTigerRecord> records;
    private final List<String> warnings;
}
```

## 6. Provider 与网关

### 6.1 Provider 合同

```java
public interface DragonTigerProvider extends MarketDataProvider {
    boolean supports(Instrument instrument);

    ProviderResult<DragonTigerData> fetch(
            Instrument instrument,
            LocalDate startDate,
            LocalDate endDate);
}
```

`EastmoneyDragonTigerProvider`：

- `providerCode = EASTMONEY_DRAGON_TIGER`
- `providerFamily = EASTMONEY`
- `capabilities = DRAGON_TIGER`
- `priority = 10`
- `batchLimit = 1`
- `minimumInterval = 800ms`
- `timeout = 12s`

所有 HTTP 请求复用 `FinanceHttpClient`，并通过 `ProviderRequestGuard` 接受厂商级限流、重试和熔断约束。

### 6.2 网关结果

新增：

```java
public final class DragonTigerGatewayResult {
    private final DragonTigerData data;
    private final MarketDataQualityStatus qualityStatus;
    private final String sourceCode;
    private final LocalDateTime dataAsOf;
    private final LocalDateTime retrievedAt;
    private final Long staleAgeSeconds;
    private final String warning;
    private final String refreshId;
}
```

`MarketDataGateway.fetchDragonTiger(instrument, startDate, endDate)`：

1. 使用 `DRAGON_TIGER:{instrumentId}:{startDate}:{endDate}` 作为 single-flight key；
2. 按 `ProviderRoutePolicy` 排序；
3. 用 `ProviderRequestGuard.execute` 调用；
4. 成功空集返回 `FRESH_PRIMARY`，不降级为 `UNAVAILABLE`；
5. 有历史快照且在线失败时返回 `STALE_FALLBACK`；
6. 没有在线结果和历史快照时返回 `UNAVAILABLE`；
7. 写入统一 `market_data_refresh_run` 审计。

### 6.3 通用快照

在 `MarketDataSnapshotCodec` 增加龙虎榜编码和解码：

- `capability = DRAGON_TIGER`
- `scopeKey = instrumentId:startDate:endDate`
- `payload` 保存规范化 `DragonTigerData`
- `dataAsOf` 使用查询结束日期的收盘时点或返回记录最大交易日

通用快照只作为网关级故障兜底。业务查询使用 Market Intel 的结构化龙虎榜表。

## 7. 持久化

新增迁移版本 `107`。

### 7.1 `market_dragon_tiger_record`

```sql
CREATE TABLE market_dragon_tiger_record (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    instrument_id INTEGER NOT NULL,
    provider_code TEXT NOT NULL,
    trade_date TEXT NOT NULL,
    external_id TEXT NOT NULL,
    reason_code TEXT,
    reason TEXT NOT NULL,
    provider_explanation TEXT,
    close_price TEXT,
    change_rate TEXT,
    buy_amount TEXT,
    sell_amount TEXT,
    net_amount TEXT,
    billboard_amount TEXT,
    market_amount TEXT,
    net_amount_ratio TEXT,
    billboard_amount_ratio TEXT,
    turnover_rate TEXT,
    free_market_cap TEXT,
    retrieved_at TEXT NOT NULL,
    payload_hash TEXT NOT NULL,
    quality_status TEXT NOT NULL,
    UNIQUE(instrument_id, provider_code, trade_date, external_id, payload_hash),
    FOREIGN KEY(instrument_id) REFERENCES instrument(id) ON DELETE RESTRICT
);
```

索引：

```sql
CREATE INDEX idx_dragon_tiger_record_range
ON market_dragon_tiger_record(instrument_id, trade_date DESC, id DESC);
```

### 7.2 `market_dragon_tiger_seat`

```sql
CREATE TABLE market_dragon_tiger_seat (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    record_id INTEGER NOT NULL,
    external_trade_id TEXT,
    seat_code TEXT,
    seat_name TEXT NOT NULL,
    direction TEXT NOT NULL,
    rank INTEGER NOT NULL,
    buy_amount TEXT,
    sell_amount TEXT,
    net_amount TEXT,
    buy_ratio TEXT,
    sell_ratio TEXT,
    seat_type TEXT,
    institutional INTEGER NOT NULL DEFAULT 0,
    northbound INTEGER NOT NULL DEFAULT 0,
    retrieved_at TEXT NOT NULL,
    payload_hash TEXT NOT NULL,
    UNIQUE(record_id, direction, rank, seat_code, seat_name),
    FOREIGN KEY(record_id) REFERENCES market_dragon_tiger_record(id) ON DELETE CASCADE
);
```

结构化表以 `INSERT OR IGNORE` 保存不可变事实：

- 同一业务身份和相同 `payload_hash` 的重复刷新不会产生重复行；
- 同一业务身份的上游载荷发生变化时保存为新的事实版本；
- 查询使用 `instrument_id + provider_code + trade_date + external_id` 分组，并按 `retrieved_at DESC, id DESC` 选择最新版本；
- 新版本拥有自己的席位明细，不修改旧版本，也不删除其他日期的历史记录。

## 8. 刷新编排

### 8.1 请求语义

现有：

```text
POST /api/market-intel/instruments/{instrumentId}/refresh
```

保持请求方式不变。后端一次创建一个 `MarketIntelRefreshRun`，异步执行两个步骤：

1. `CAPITAL_FLOW`
2. `DRAGON_TIGER`

每个步骤独立写入 `market_intel_refresh_step`。

### 8.2 总状态

| 资金流 | 龙虎榜 | 总状态 |
| --- | --- | --- |
| 成功 | 成功或成功空集 | `SUCCEEDED` |
| 成功 | 失败但存在历史龙虎榜 | `PARTIAL` |
| 成功 | 失败且无历史龙虎榜 | `PARTIAL` |
| 失败 | 成功或成功空集 | `PARTIAL` |
| 失败 | 失败 | `FAILED` |

成功空集不能增加 `failureCount`。

### 8.3 刷新窗口

- `endDate = LocalDate.now()`
- `startDate = endDate.minusDays(119)`

龙虎榜通常在收盘后形成完整披露。当查询日仍在交易时段或当天席位为空，不把当天空集解释为接口失败。

## 9. 查询服务与 API

### 9.1 服务

新增：

```java
public class MarketIntelDragonTigerService {
    DragonTigerView view(Long instrumentId, int days);
}
```

`days` 允许 `30`、`60`、`120`，默认 `120`，其他值返回参数错误。

### 9.2 返回合同

```json
{
  "instrument": {
    "id": 7,
    "code": "000021",
    "name": "深科技",
    "market": "SZ"
  },
  "range": {
    "days": 120,
    "from": "2026-03-19",
    "to": "2026-07-16"
  },
  "records": [
    {
      "id": 101,
      "tradeDate": "2026-07-15",
      "reason": "日跌幅偏离值达到7%的前5只证券",
      "providerExplanation": null,
      "closePrice": 47.26,
      "changeRate": -9.9981,
      "buyAmount": 909610681.71,
      "sellAmount": 1305481357.84,
      "netAmount": -395870676.13,
      "turnoverRate": 11.6572,
      "qualityStatus": "COMPLETE",
      "buySeats": [],
      "sellSeats": []
    }
  ],
  "health": {
    "status": "FRESH_PRIMARY",
    "providerCode": "EASTMONEY_DRAGON_TIGER",
    "asOf": "2026-07-16T15:30:00",
    "warnings": []
  }
}
```

记录按 `tradeDate DESC, id DESC` 返回。席位按 `rank ASC` 返回。

### 9.3 空状态

没有记录时返回 HTTP 200：

```json
{
  "records": [],
  "health": {
    "status": "FRESH_PRIMARY",
    "warnings": []
  }
}
```

前端显示：

> 近 120 日没有公开龙虎榜记录。未上榜不代表没有资金交易，仅表示没有触发交易所规定的公开披露条件。

## 10. 前端

### 10.1 页面位置

保持现有 Market Intel 页面，不新增顶层导航。资金行为区域下方增加 `DragonTigerPanel`，与历史评价、规则解释和 Agent 区域并列为独立事实卡片。

### 10.2 首屏摘要

有记录时显示：

- 最近上榜日期；
- 近 120 日上榜次数；
- 最近一次净买入额；
- 最近一次换手率；
- 明确机构席位数量；
- 明确沪/深股通席位数量。

这些值只做汇总，不赋予“利好”“利空”语义。

### 10.3 记录展开

每条记录默认展示：

- 日期和上榜原因；
- 涨跌幅、换手率；
- 买入额、卖出额、净额；
- 数据质量。

用户展开后显示左右两组：

- 买入席位 TOP5；
- 卖出席位 TOP5。

机构和沪/深股通使用中性标签。普通营业部仅显示上游原始名称。

### 10.4 加载和错误

- 页面选择标的时并行请求资金行为与龙虎榜；
- 任一请求失败不清空另一维度；
- 龙虎榜首次未刷新显示引导态；
- `STALE_FALLBACK` 显示最近成功数据及过期时间；
- `UNAVAILABLE` 显示失败说明和重新刷新入口。

## 11. 解释边界

以下属于确定性事实：

- 是否上榜；
- 上榜日期和原因；
- 上游公开的买卖金额；
- 席位名称和席位类型；
- 机构专用、沪股通专用、深股通专用等明确文字。

以下不能在本期作为事实：

- 某营业部等于某位游资；
- 净买入代表后续上涨；
- 机构席位代表机构整体一致看多；
- 席位重复出现代表吸筹或出货；
- 龙虎榜未出现代表没有大资金参与。

前端固定显示：

> 龙虎榜仅覆盖满足公开披露条件的异常交易，席位不等于具体账户或投资者，不构成投资建议。

## 12. 错误与质量代码

Provider 错误：

- `DRAGON_TIGER_SCHEMA_DRIFT`
- `DRAGON_TIGER_PRIMARY_UNAVAILABLE`
- `DRAGON_TIGER_SEAT_UNAVAILABLE`
- `DRAGON_TIGER_SEAT_AMBIGUOUS`
- `DRAGON_TIGER_RESPONSE_TOO_LARGE`

业务状态：

- `DRAGON_TIGER_NOT_REFRESHED`
- `DRAGON_TIGER_EMPTY`
- `DRAGON_TIGER_PARTIAL`
- `DRAGON_TIGER_STALE`

内部错误代码由后端健康合同映射为中文说明，前端不直接展示异常类名或第三方字段名。

## 13. 测试策略

### 13.1 Domain

- 主记录和席位字段往返；
- `direction`、质量状态和确定性席位标记；
- 同一日期多原因记录保持独立。

### 13.2 RPC

- Fixture 解析主记录；
- Fixture 解析买入和卖出席位；
- 空集是成功；
- 席位单方向失败保留主记录并降级；
- 多原因按 `TRADE_ID` 或原因关联；
- 无法唯一关联生成歧义警告；
- 东财 URL 只查询目标股票和日期范围；
- Provider 元数据和 `DRAGON_TIGER` 能力正确。

### 13.3 DAO

- 迁移重复执行幂等；
- 主记录唯一约束；
- 席位唯一约束；
- 同一事件载荷变化时保留新旧两个不可变版本，业务查询只返回最新版本；
- 查询按日期倒序且席位按排名升序；
- 外键级联删除只发生在显式删除主记录时。

### 13.4 Gateway

- 主 Provider 成功；
- 成功空集；
- Provider 失败后使用网关快照；
- 没有快照时 `UNAVAILABLE`；
- single-flight；
- 审计记录包含能力、数据源、状态和数量。

### 13.5 Service/Web

- 一个刷新任务生成两个独立步骤；
- 部分成功不会覆盖已有数据；
- 总状态计算正确；
- 查询参数限制；
- API 成功信封；
- 未上榜返回 HTTP 200 空集。

### 13.6 Frontend

- 首次未刷新；
- 空集；
- 主记录摘要；
- 展开买入/卖出席位；
- 机构和沪/深股通标签；
- `PARTIAL`、`STALE_FALLBACK` 和 `UNAVAILABLE`；
- 切换标的时忽略旧请求结果；
- 资金行为失败不影响龙虎榜，龙虎榜失败不影响资金行为。

### 13.7 真实端点验收

使用一个近期实际上榜的股票进行只读探针：

1. 主记录返回至少一条；
2. 买入和卖出席位各返回最多五条；
3. `机构专用` 和 `深股通专用/沪股通专用` 能被确定性识别；
4. 不将真实端点测试保留为默认单元测试；
5. 验收后移除临时探针。

## 14. 文件边界

### 后端新增

- `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/DragonTigerRecord.java`
- `backend/finscope-domain/src/main/java/com/finscope/domain/marketintel/DragonTigerSeat.java`
- `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/DragonTigerData.java`
- `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/DragonTigerProvider.java`
- `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/eastmoney/EastmoneyDragonTigerProvider.java`
- `backend/finscope-dao/src/main/java/com/finscope/dao/marketintel/DragonTigerRepository.java`
- `backend/finscope-service/src/main/java/com/finscope/service/marketdata/DragonTigerGatewayResult.java`
- `backend/finscope-service/src/main/java/com/finscope/service/marketintel/MarketIntelDragonTigerService.java`
- `backend/finscope-service/src/main/java/com/finscope/service/marketintel/DragonTigerView.java`
- `frontend/src/features/market-intel/DragonTigerPanel.tsx`

### 后端修改

- `backend/finscope-domain/src/main/java/com/finscope/domain/marketdata/MarketDataCapability.java`
- `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/ProviderRequestGuard.java`
- `backend/finscope-service/src/main/java/com/finscope/service/marketdata/MarketDataGateway.java`
- `backend/finscope-service/src/main/java/com/finscope/service/marketdata/MarketDataSnapshotCodec.java`
- `backend/finscope-service/src/main/java/com/finscope/service/marketintel/MarketIntelRefreshCoordinator.java`
- `backend/finscope-dao/src/main/java/com/finscope/dao/marketintel/MarketIntelSchemaMigrator.java`
- `backend/finscope-web/src/main/java/com/finscope/web/controller/MarketIntelController.java`

### 前端修改

- `frontend/src/features/market-intel/MarketIntelView.tsx`
- `frontend/src/features/market-intel/marketIntelTypes.ts`
- `frontend/src/styles.css`

每个新增类型只承担一个职责。Provider 不写数据库，Repository 不访问外部网络，Service 不解析第三方 JSON，前端不重算席位指标。

## 15. 验收标准

1. 当前分支支持刷新一个自选 A 股的近 120 日龙虎榜。
2. 数据写入结构化主记录和席位表，重复刷新不产生重复事实。
3. 未上榜是成功空集，不显示错误。
4. 席位部分失败时保留主记录，并显示准确降级原因。
5. 资金流和龙虎榜任一失败不阻断另一维度。
6. API 使用统一 `ApiResponse` 成功信封和现有业务异常体系。
7. 页面展示最近记录、买卖 TOP5 和确定性席位标签。
8. 页面不出现游资人物推断、买卖建议或看多/看空结论。
9. 后端全量 Maven 测试通过。
10. 前端全量 Vitest 和生产构建通过。
11. 真实东财端点只读验收通过。
