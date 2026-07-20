# 技术方案：A 股标的研究数据中心

## 1. 文档信息

- 项目：FinScope
- 对应 PRD：`docs/产品需求-A股标的研究数据中心.md`
- 日期：2026-07-14
- 状态：实施中（资金行为与龙虎榜事实切片已实现）
- 首期运行时：Java 8 + Spring Boot 2.7 + SQLite + React/TypeScript

### 1.1 龙虎榜当前落地范围（2026-07-17）

- 能力标识：`MarketDataCapability.DRAGON_TIGER`。
- 主源：东方财富数据中心；摘要、买方席位、卖方席位分别使用独立结构化报表。
- 查询范围：刷新默认最近 120 个自然日，查询接口允许 30、60、120 日。
- 存储：迁移版本 107 建立龙虎榜主记录与席位表，按业务身份和负载指纹保存不可变版本。
- 编排：资金行为与龙虎榜独立执行、独立记错，最终聚合为 `SUCCEEDED/PARTIAL/FAILED`。
- 降级：成功空集不算失败；席位局部失败保留摘要；主源失败可回退最近成功快照并标记过期。
- 页面：展示上榜事实、金额、换手率、买卖 TOP5、机构/北向显式标签和数据健康状态。
- 边界：不做游资人物映射、账户身份推断、意图判断或投资建议。

## 2. 核心技术决策

1. **主链路保持纯 Java**：东财、新浪、腾讯、同花顺、百度等 HTTP/JSON/HTML 数据用 Java 适配器重新实现，不在请求链路执行 Python 脚本。
2. **复刻数据能力，不翻译代码结构**：TradingAgents-astock 是数据源、参数、字段和容错参考；FinScope 建立自己的领域模型、接口合同和测试样本。
3. **市场研究数据是独立领域**：包名为 `marketintel`，所有者是 `Instrument`，不依赖 `WatchlistItem`、策略持仓或量化数据集。
4. **外部请求与数据库事务分离**：网络请求不在数据库事务中执行；每个数据维度独立规范化和事务写入。
5. **快照不可变**：刷新产生新快照或新事件，不覆盖旧数据；重复负载通过指纹和唯一约束去重。
6. **允许部分成功**：刷新运行与每个维度都有独立状态；资金流失败不影响龙虎榜或解禁查询。
7. **不静默降级**：备用源、过期缓存、字段缺失和口径冲突都写入运行记录并传给前端。
8. **Agent 不直连第三方数据源**：Agent 只通过受控服务读取已保存快照或明确的刷新工具，所有输入有来源、时间、质量与指纹。
9. **事实层不做暗盘断言**：第一阶段优先保存大笔资金、成交金额、换手率和时间线；规则层不根据公开聚合数据断言拆单、隐藏账户或所谓暗盘资金。
10. **规则解释常驻、Agent 按需运行**：确定性规则负责默认通俗说明；Agent 仅在用户点击后基于不可变快照生成深度假设，并接受置信度门和 Trace 审计。
11. **模式服务于边界**：Provider 使用 Adapter/Strategy，解读流程使用 Facade/Policy，持久化使用 Repository；不为追求模式数量增加无业务价值的抽象层。

## 3. 现有基础与复用边界

### 3.1 可直接复用

- `Instrument` 作为标的主数据；
- `QuoteService` 和已有 `QuoteAdapter` 作为页面顶部基础行情摘要；
- `ArticleIngestCoordinator` 、`Article` 、新意判断、事件和证据服务作为个股新闻入库主链路；
- `AttributionHarness` 、`AttributionEvidenceGate` 和研究轨道作为第四阶段消费方；
- `AgentHarness`、`AgentRunContext`、`AgentTraceService`、`LlmChatClient` 和结构化 JSON 解析模式用于第一阶段资金解读；
- `agent_run` 的 Trace 语义复用于资金 Agent，但外部数据刷新继续使用独立运行表；
- 前端 `AppShell` 的「投资工作台」导航组。

### 3.2 必须保持隔离

- 实时/近实时 `marketintel` 数据不直接写入 `quant_*` 表；
- 标的研究数据不写入 `watchlist_item`；
- 个股新闻复用 `article`，但资金流、解禁和龙虎榜不伪装成文章；
- 平台提供的文本“Signal”、看多/看空标签和 Agent 报告不进入原始数据模型。
- 规则解释、Agent 假设和原始资金事实分别存储；模型输出不能回写或覆盖原始快照。

## 4. 目标架构

```text
frontend/features/market-intel
  -> MarketIntelController
  -> MarketIntelQueryService
  -> CapitalInterpretationFacade
       -> CapitalRuleExplanationEngine
       -> CapitalFactAssembler
       -> CapitalBehaviorSnapshotService
       -> AgentHarness
       -> CapitalInterpretationAgent
       -> CapitalHypothesisGate
       -> AgentTraceService
  -> MarketIntelRefreshService
       -> MarketIntelRefreshCoordinator
       -> CapitalFlowProvider
       -> DragonTigerProvider
       -> LockupProvider
       -> ClassificationProvider
       -> StockNewsProvider
       -> FundamentalProvider
  -> MarketIntel repositories
  -> SQLite immutable snapshots/events

AttributionHarness / StrategyStockThesisService
  -> StockResearchSnapshotService
  -> saved marketintel data + Article/Evidence
```

分层规则：

- `finscope-domain`：领域对象、状态和 API 无关类型；
- `finscope-rpc`：外部 HTTP、数据源特定字段、请求参数与原始响应解析；
- `finscope-service`：刷新编排、时效判断、质量门、数据聚合和跨领域连接；
- `finscope-dao`：结构化持久化、唯一约束和查询；
- `finscope-web`：REST 输入输出转换，不访问第三方接口；
- `frontend`：按已保存状态展示，不在浏览器抓取财经网站。

## 5. 包结构和主要组件

### 5.1 `finscope-domain`

```text
com.finscope.domain.marketintel
  MarketIntelDimension
  MarketIntelQualityStatus
  MarketIntelRefreshRun
  MarketIntelRefreshStep
  ProviderMetadata
  CapitalFlowSnapshot
  CapitalBehaviorSignal
  CapitalBehaviorSnapshot
  CapitalExplanation
  CapitalInterpretation
  CapitalHypothesis
  DragonTigerRecord
  DragonTigerSeat
  LockupEvent
  InstrumentClassification
  FundamentalResearchSnapshot
  InstrumentArticleLink
  StockResearchSnapshot
```

### 5.2 `finscope-rpc`

```text
com.finscope.rpc.marketintel
  FinanceHttpClient
  FinanceHttpRequest
  FinanceHttpResponse
  ProviderRateLimiter
  ProviderResult<T>
  ProviderWarning
  eastmoney/
  sina/
  tencent/
  baidu/
  disclosure/
```

Provider 接口按数据能力拆分：

```java
public interface CapitalFlowProvider {
    String providerCode();
    boolean supports(Instrument instrument);
    ProviderResult<List<CapitalFlowSnapshot>> fetch(
            Instrument instrument, LocalDate asOfDate) throws Exception;
}

public interface DragonTigerProvider {
    String providerCode();
    ProviderResult<DragonTigerData> fetch(
            Instrument instrument, LocalDate startDate, LocalDate endDate) throws Exception;
}

public interface LockupProvider {
    String providerCode();
    ProviderResult<List<LockupEvent>> fetch(
            Instrument instrument, LocalDate asOfDate, int forwardDays) throws Exception;
}

public interface ClassificationProvider {
    String providerCode();
    ProviderResult<List<InstrumentClassification>> fetch(
            Instrument instrument, LocalDate asOfDate) throws Exception;
}
```

不设计单一的 `TradingAgentsDataAdapter`，也不要求每个数据源实现所有能力。每个 Provider 仅声明它真实支持的能力。

### 5.3 `finscope-service`

```text
com.finscope.service.marketintel
  MarketIntelRefreshService
  MarketIntelRefreshCoordinator
  MarketIntelQueryService
  MarketIntelDataHealthService
  MarketIntelStalenessPolicy
  MarketIntelProviderRouter
  CapitalRuleExplanationEngine
  CapitalFactAssembler
  CapitalBehaviorSnapshotService
  CapitalInterpretationFacade
  CapitalInterpretationAgent
  CapitalHypothesisGate
  StockNewsIngestBridge
  StockResearchSnapshotService
```

### 5.4 `finscope-dao`

```text
com.finscope.dao.marketintel
  MarketIntelRefreshRunRepository
  CapitalFlowRepository
  CapitalBehaviorSignalRepository
  CapitalBehaviorSnapshotRepository
  CapitalInterpretationRepository
  DragonTigerRepository
  LockupRepository
  InstrumentClassificationRepository
  InstrumentArticleLinkRepository
  FundamentalResearchRepository
  StockResearchSnapshotRepository
```

### 5.5 `finscope-web` 与前端

```text
com.finscope.web.controller.MarketIntelController
com.finscope.web.response.marketintel.*
frontend/src/features/market-intel/*
```

## 6. 数据源路由

### 6.1 数据源候选

| 能力 | 主源候选 | 备用/补充候选 | 首期决策 |
| --- | --- | --- | --- |
| 大笔资金分档 | 东财 push2/push2his 资金流 | 暂无 | 第一阶段最高优先级；保存平台口径 |
| 成交价格/金额/量 | 东财行情与分钟 K 线 | 现有 `QuoteService` 仅补摘要 | 与资金时间线按业务时间对齐 |
| 换手率/量比 | 东财行情快照 | 现有报价字段 | 不构造上游未提供的分钟换手率 |
| 龙虎榜 | 东财 datacenter | 后续评估一手披露 | 第一阶段实现 |
| 限售解禁 | 东财 datacenter | 公司/交易所公告作事件印证 | 第一阶段实现 |
| 行业表现 | 东财 push2 | 现有板块行情 | 第一阶段实现 |
| 概念/行业归属 | 百度股市通或东财公开数据 | `Instrument.sectorCode/chainTags` | 实现前做端点稳定性比较 |
| 个股新闻 | 东财个股新闻 | 新浪个股新闻、现有 WebSearch | 第二阶段 |
| 公司公告 | 交易所/公开信息披露源 | 主流财经平台公告索引 | 第二阶段，实现前验证公开访问合同 |
| 当前估值 | 腾讯/东财 | 新浪 | 第三阶段 |
| 财务三表 | 新浪财务公开数据 | 公告披露 | 第三阶段 |
| 盈利预期 | 同花顺公开页面 | 暂无 | 第三阶段，必须保留平台口径 |
| 政策/全球快讯 | 财联社/东财滚动资讯 | 现有 RSS/WebSearch | 第三阶段 |

上表是路由候选，不是对第三方稳定性的承诺。每个阶段开始前都要重新验证返回结构、访问限制、数据时效和使用边界。

### 6.2 Provider 路由原则

1. 根据 `Instrument.type/market/code` 筛选支持的 Provider。
2. 主源成功且达到最小质量条件时不调用备用源。
3. 主源网络错误、结构错误或数据空结果的含义分开；“未上龙虎榜”是成功空集，不是数据源失败。
4. 使用备用源时设置 `fallbackUsed=true` 和 `fallbackReason`。
5. 不同数据源口径冲突时不自动拼成单一值；保留主源结果并生成 `PROVIDER_CONFLICT` 告警。
6. 资金流和行情来自不同响应时，按交易所时区与显式容差对齐；超出容差的记录分开保存并产生质量告警。

## 7. 统一 HTTP 与外部访问治理

### 7.1 `FinanceHttpClient`

首期使用 JDK `HttpURLConnection` 进行统一封装，避免在每个适配器重复实现：

- 连接和读取超时；
- User-Agent、Referer、Origin 和字符集；
- HTTP 状态码分类；
- 响应体大小上限；
- JSON、JSONP、GBK 文本和 HTML 读取；
- 可重试错误分类；
- 日志摘要与敏感参数脱敏；
- 返回负载 SHA-256 指纹。

不修改 JVM 全局 SSL SocketFactory 或 HostnameVerifier，不忽略证书校验。

### 7.2 限流

`ProviderRateLimiter` 按 provider code 维护最小请求间隔。第一阶段对东财系列端点统一串行节流，默认最小间隔 1 秒，并允许通过本地配置调整。

限流等待发生在外部请求层，不持有数据库连接或事务。

### 7.3 重试与熔断

- 连接超时、读取超时、429 和部分 5xx 可进行最多 1 次有界重试；
- 4xx 参数错误、解析错误和 Schema 错误不盲目重试；
- 连续失败达到阈值后在本地进程内短时间熔断；
- 熔断状态进入刷新步骤的 `errorType=CIRCUIT_OPEN`；
- 不引入 Redis 或分布式限流，FinScope 首期是单进程本地应用。

## 8. 领域模型与持久化

### 8.1 统一元数据

每个外部数据对象至少包含：

```text
instrumentId
providerCode
sourceTier
sourceUrl
externalId
dataDate
publishedAt / effectiveAt
retrievedAt
payloadHash
qualityStatus
pointInTimeSafe
refreshRunId
```

`pointInTimeSafe` 首期默认为 `false`；只有数据合同、披露时间和历史可见性经过单独验证后才可设为 `true`。

### 8.2 刷新运行

`market_intel_refresh_run`：

```text
id
instrument_id
requested_dimensions_json
status                 PENDING/RUNNING/SUCCEEDED/PARTIAL/FAILED
trigger_type           MANUAL/SCHEDULED/AGENT
started_at
ended_at
success_count
failure_count
warning_count
created_at
```

`market_intel_refresh_step`：

```text
id
run_id
dimension
provider_code
status                 PENDING/RUNNING/SUCCEEDED/EMPTY/FAILED/SKIPPED
attempt
fallback_used
fallback_reason
output_count
payload_hash
error_type
error_message
started_at
ended_at
UNIQUE(run_id, dimension, provider_code, attempt)
```

`EMPTY` 表示请求成功但当前无业务记录，不参与失败计数。

### 8.3 首批业务表

1. `market_capital_flow_snapshot`
   - `granularity`：`MINUTE_1/MINUTE_5/DAY`，其中 5 分钟记录由本地基于 1 分钟记录确定性聚合；
   - 时间点/交易日、价格、成交量、区间/累计成交金额、换手率和量比；
   - 主力、超大单、大单、中单、小单的流入、流出、净流入及净流入成交额占比；
   - 上游未提供的流入/流出字段保存为 `NULL`，不根据净额反向构造；
   - `observed_at` 必填；日线使用对应交易日的市场收盘时间，避免 SQLite 的 `NULL` 唯一约束产生重复记录；
   - 本地 5 分钟记录保存输入指纹和聚合算法版本，区间成交金额与累计成交金额不得混用；
   - 唯一约束：`instrument_id + provider_code + data_date + observed_at + payload_hash`。

2. `market_capital_behavior_signal`
   - 类型包括 `AMOUNT_EXPANSION_WITH_OUTFLOW`、`LOW_AMOUNT_INFLOW`、`PRICE_FLOW_DIVERGENCE`、`LATE_SESSION_FLOW_SHIFT`；
   - 保存观察窗口、输入记录 ID、阈值、实际值、`algorithm_version` 和生成时间；
   - 信号只描述可复算现象，不含 bullish/bearish、吸筹、出货或暗盘语义；
   - 唯一约束：`instrument_id + signal_type + window_start + window_end + algorithm_version + input_hash`。

3. `market_capital_behavior_snapshot`
   - 保存 Agent 可消费的规范化 JSON、资金记录 ID、信号 ID、数据质量摘要、`as_of` 和 SHA-256 指纹；
   - 快照不可变，且只引用 `retrieved_at <= as_of` 的记录；
   - 唯一约束：`instrument_id + as_of + fingerprint`。

4. `market_capital_interpretation`
   - 类型 `RULE/AGENT`，状态 `PENDING/RUNNING/SUCCEEDED/FALLBACK/FAILED`；
   - 保存 `snapshot_id`、通俗摘要、事实 JSON、假设 JSON、反证 JSON、数据缺口 JSON 和观察点 JSON；
   - `RULE` 保存 `rule_version`，`AGENT` 保存 `model_name`、`prompt_version`、输入/输出指纹和 Trace subject；
   - 同一 snapshot + rule version 的规则解释幂等；同一 snapshot + model + prompt version 的 Agent 解读默认复用，显式重新运行才创建新版本。

5. `market_dragon_tiger_record`
   - 上榜日期、原因、成交额、买入、卖出、净买入、换手率；
   - 版本身份：`instrument_id + provider_code + trade_date + external_id/reason`；
   - 同一业务身份的不同负载指纹保存为新版本，查询只返回每个业务身份的最新版本。

6. `market_dragon_tiger_seat`
   - `record_id`、席位名称、席位代码、买入、卖出、净额、方向、排名、机构标记；
   - 外键关联不可变主记录，不覆盖历史版本。

7. `market_lockup_event`
   - 解禁日期、类型、股数、占总股本/流通股比例、当前状态；
   - 唯一约束：`instrument_id + provider_code + effective_date + lockup_type + external_id`。

8. `instrument_classification`
   - 类型 `INDUSTRY/CONCEPT/REGION`、名称、外部代码、生效日期和当日表现；
   - 用快照记录变化，不直接覆盖 `Instrument.sectorCode/chainTags`。

### 8.4 Agent Trace 通用主题

为避免资金 Agent 再建一套 Trace 表，在 `agent_run` 增加可复用的：

```text
subject_type          CAPITAL_INTERPRETATION/ATTRIBUTION/RESEARCH/...
subject_id
```

`AgentTraceService` 增加接收 `AgentTraceSubject` 的重载，现有 `eventId/articleId/researchRunId` 签名委托给新实现，保持兼容。查询索引为 `subject_type + subject_id + id`。该通用迁移由独立 `AgentTraceSchemaMigrator` 管理，不放入 `MarketIntelSchemaMigrator`。

### 8.5 第二阶段新闻关联

`instrument_article`：

```text
instrument_id
article_id
relation_type          ANNOUNCEMENT/COMPANY/INDUSTRY/POLICY/BACKGROUND
match_method           PROVIDER_EXPLICIT/CODE/NAME/ALIAS/MANUAL
relevance
status                 CANDIDATE/CONFIRMED/REJECTED
provider_code
created_at
updated_at
PRIMARY KEY(instrument_id, article_id, relation_type)
```

文章内容仍以 `article` 为唯一事实源。关联被修正时更新 link 状态，不删除 Article。

### 8.6 第三、四阶段表

- `market_fundamental_snapshot`：报告期、披露日期、估值、财务摘要、预期口径和元数据；
- `stock_research_snapshot`：`instrument_id`、`as_of`、规范化 JSON、指纹、所引用的业务记录 ID 和质量摘要。

### 8.7 迁移方式

延续当前 SQLite `CREATE TABLE IF NOT EXISTS` 与增量补列风格，但将 `marketintel` 建表收口独立为 `MarketIntelSchemaMigrator`，避免继续扩大通用 `DatabaseInitializer`。

## 9. 刷新编排与事务边界

### 9.1 刷新流程

```text
POST refresh
  -> validate STOCK instrument and requested dimensions
  -> find active run for same instrument
  -> create MarketIntelRefreshRun
  -> create one step per dimension
  -> execute provider calls outside database transactions
  -> normalize provider result
  -> transactionally insert one dimension's immutable records
  -> rebuild CapitalBehaviorSnapshot when capital data changed
  -> run deterministic CapitalRuleExplanationEngine
  -> complete dimension step
  -> aggregate run status
  -> expose persisted result to query API
```

### 9.2 并发原则

- 同一标的同一时间只允许一个活跃手动刷新运行；重复请求返回已有 run id。
- 同一资金快照、模型和 Prompt 版本同一时间只允许一个活跃 Agent 解读；重复点击返回已有 interpretation id。
- 不同维度可以并行，但实际外部请求受 provider 限流器约束。
- 首期复用 Spring `TaskExecutor`，不引入 MQ。
- SQLite 写入按维度短事务串行完成，不使用长事务等待网络。

### 9.3 状态聚合

- 全部必需步骤成功或成功空集：`SUCCEEDED`；
- 至少一个维度成功且至少一个失败：`PARTIAL`；
- 所有请求维度失败：`FAILED`；
- 服务重启时长期处于 `RUNNING` 的运行标记为 `FAILED`，原因为 `INTERRUPTED_BY_RESTART`。

## 10. 时效、缓存与数据质量

### 10.1 默认时效策略

| 维度 | 交易时段建议 TTL | 非交易时段建议 TTL |
| --- | --- | --- |
| 资金流实时快照 | 15 分钟 | 当交易日收盘后稳定 |
| 资金流日线 | 1 交易日 | 1 交易日 |
| 成交金额/换手率摘要 | 与资金流实时快照一致 | 当交易日收盘后稳定 |
| 龙虎榜 | 当日收盘前可标记未完整 | 1 交易日 |
| 解禁日历 | 24 小时 | 24 小时 |
| 行业表现 | 15 分钟 | 当交易日收盘后稳定 |
| 行业/概念归属 | 7 天 | 7 天 |
| 个股新闻 | 10 分钟 | 30 分钟 |
| 财务/估值摘要 | 1 交易日 | 1 交易日 |

TTL 是前端与服务的时效标识，不是删除数据的生命周期。

### 10.2 质量门

每个维度有确定性验证：

- 标的代码与请求标的一致；
- 日期可解析且不超过合理的未来边界；
- 金额和比例字段可解析，单位转换明确；
- 资金流与行情时间点使用同一交易所时区；无法在容差窗口内对齐时保留各自记录并生成 `TIMELINE_ALIGNMENT_GAP`，不强行拼接；
- `主力净流入占比 = 主力净流入 / 同口径同窗口成交金额` 的分母必须大于零，并保存计算版本；
- 1 分钟到 5 分钟只对区间成交金额与资金净流入求和，累计成交金额取窗口末值，价格字段按明确的 OHLC/末值规则聚合；
- 响应核心字段不存在时返回 `SCHEMA_DRIFT`；
- 返回数据业务日期与 `asOfDate` 的关系可说明；
- 空数组、数据空对象和请求失败分类处理；
- 不保存上游生成的 bullish/bearish 推断为原始事实。

## 11. 新闻、公告与现有研究链路

### 11.1 入库桥接

`StockNewsProvider` 返回结构化候选：

```text
title
url
publishedAt
summary
body or bodyUrl
providerCode
explicitInstrumentCode
newsType
```

`StockNewsIngestBridge` 负责：

1. 转成现有 `RawItem`；
2. 调用文章入库与去重主链路；
3. 根据 provider 显式代码、标题/正文代码、名称和别名建立 `instrument_article`；
4. 对仅名称命中、存在同名风险的关联保留 `CANDIDATE`；
5. 后续事件聚类与证据抽取仍由现有服务完成。

### 11.2 来源与立场

- 公司/交易所公告标记为 `PRIMARY_DISCLOSURE`；
- 主流平台个股新闻标记为 `MARKET_PLATFORM`；
- 行业或政策资讯默认为 `BACKGROUND`，不因为包含公司名称就变成公司直接证据；
- 同源转载不计为多个独立来源。

## 12. 资金解读与 Agent 边界

### 12.1 第一阶段资金解读

默认查询链路不调用 LLM：

```text
GET capital-behavior
  -> load latest CapitalBehaviorSnapshot
  -> load persisted RULE interpretation
  -> if missing, compute deterministic response without database mutation
  -> return data + signals + plain-language explanation
```

用户点击后的 Agent 链路：

```text
POST capital-interpretations
  -> resolve immutable CapitalBehaviorSnapshot
  -> return cached result or create interpretation run
  -> AgentHarness checks budget and repeated fingerprint
  -> CapitalFactAssembler produces server-trusted facts and metric references
  -> CapitalInterpretationAgent calls LlmChatClient with structured JSON only
  -> parse strict output contract
  -> CapitalHypothesisGate validates hypotheses and caps confidence
  -> merge trusted facts with accepted Agent hypotheses
  -> persist interpretation and Agent Trace
```

最终输出合同如下。`facts` 由服务端确定性组装，不接受模型新增或改写；模型只生成通俗摘要、假设、反证、缺口和观察点，并通过 metric reference 引用事实。

```json
{
  "plainSummary": "通俗总结",
  "facts": [{"claim": "确定性事实", "metricRefs": ["record:101"]}],
  "hypotheses": [{
    "type": "ACCUMULATION|DISTRIBUTION|ORDER_SPLITTING|HIDDEN_FLOW|OTHER",
    "claim": "可能存在的资金行为",
    "confidence": "LOW|MID|HIGH",
    "supportingMetricRefs": ["signal:41"],
    "counterEvidence": ["尚无逐笔委托数据"]
  }],
  "dataGaps": ["缺少 Level-2 逐笔委托/成交"],
  "observationPoints": ["未来 3 个交易日观察成交额和主力净流入是否延续"],
  "disclaimer": "模型假设，不构成投资建议"
}
```

`CapitalHypothesisGate` 使用确定性 Policy 下调而不提高模型置信度：

- 无 Level-2 逐笔数据时，`ORDER_SPLITTING/HIDDEN_FLOW` 最高为 `LOW`；
- 只有单日信号时，`ACCUMULATION/DISTRIBUTION` 最高为 `LOW`；
- 具有多个交易日、价格、成交金额、换手率和资金方向交叉支持时，最高可为 `MID`；
- 第一阶段任何资金意图假设都不得达到 `HIGH`；
- 缺少 metric reference、反证或数据缺口的假设直接丢弃。

LLM 未配置、超时、JSON 非法或被 Gate 全部拒绝时，Agent 运行记录为 `FALLBACK/FAILED`，页面继续展示规则解释，不用模型文本覆盖规则结果。

### 12.2 跨领域研究快照

第四阶段由 `StockResearchSnapshotService` 在明确 `asOf` 时点组装：

```json
{
  "instrument": {"id": 1, "code": "600519", "name": "贵州茅台"},
  "asOf": "2026-07-14T10:30:00+08:00",
  "capitalFlowRecordIds": [101, 102],
  "dragonTigerRecordIds": [],
  "lockupEventIds": [41],
  "classificationIds": [7, 8],
  "articleIds": [201, 202],
  "fundamentalSnapshotId": null,
  "quality": {
    "status": "PARTIAL",
    "warnings": ["DRAGON_TIGER_EMPTY"]
  },
  "fingerprint": "sha256:..."
}
```

组装原则：

1. 只包含 `retrievedAt <= asOf` 且业务时间可见的记录；
2. 快照保存规范化 JSON 与所引用记录 ID；
3. Agent 运行保存 snapshot id 和 fingerprint；
4. Agent 仅能生成研究观察、反证、数据缺口和下一观察点；
5. 结论不得超过 `quality` 和现有证据门给出的最高置信度。

## 13. REST API

### 13.1 查询

```text
GET /api/market-intel/instruments
GET /api/market-intel/instruments/{instrumentId}/overview
GET /api/market-intel/instruments/{instrumentId}/capital-behavior?range=20d&granularity=5m
GET /api/market-intel/capital-interpretations/{interpretationId}
GET /api/market-intel/instruments/{instrumentId}/dragon-tiger?days=120
GET /api/market-intel/instruments/{instrumentId}/lockups
GET /api/market-intel/instruments/{instrumentId}/classifications
GET /api/market-intel/instruments/{instrumentId}/news
GET /api/market-intel/instruments/{instrumentId}/fundamentals
GET /api/market-intel/instruments/{instrumentId}/refresh-runs
GET /api/market-intel/refresh-runs/{runId}
```

`overview` 返回页面首屏所需的已保存摘要、每个维度的健康状态和最后刷新时间，不在 GET 请求中隐式访问外部网络。

`capital-behavior` 返回 `summary`、`intradayTimeline`、`multiDayTrend`、`signals`、`ruleExplanation` 和 `health`。一分钟记录是保存的规范化数据，五分钟记录由服务端按版本化规则聚合；前端不自行重算资金指标。

`dragon-tiger` 仅查询已保存事实，不在 GET 中访问上游。`days` 仅允许 30、60、120；响应包含 `records`、买卖席位、来源、抓取时间、告警和 `NOT_REFRESHED/FRESH_PRIMARY/PARTIAL_FRESH/STALE_FALLBACK/UNAVAILABLE` 健康状态。

### 13.2 命令

```text
POST /api/market-intel/instruments/{instrumentId}/refresh
POST /api/market-intel/instruments/{instrumentId}/snapshots
POST /api/market-intel/instruments/{instrumentId}/capital-interpretations
```

refresh 请求：

```json
{
  "dimensions": ["CAPITAL_FLOW", "DRAGON_TIGER", "LOCKUP", "CLASSIFICATION"]
}
```

返回 `202 Accepted` 和 refresh run；重复活跃请求返回现有 run，不重复调用上游。

`capital-interpretations` 默认接收最新资金快照并返回 `202 Accepted + interpretationId`；相同快照、模型和 Prompt 版本已有成功结果时返回 `200 OK` 与缓存结果。请求体只有显式 `force=true` 才重新运行。

LLM 超时、解析失败或 Gate 拒绝通过 interpretation 状态返回，不把可预期的模型失败升级为整个 Market Intel 的 HTTP 500。

### 13.3 错误合同

局部 provider 失败不用整个 HTTP 500 表达；通过 run/step 状态返回。HTTP 错误仅用于：

- 标的不存在：404；
- 标的类型或市场不支持：400；
- 请求维度非法：400；
- 刷新运行状态冲突：409；
- 未预期内部错误：500。

## 14. 前端方案

### 14.1 导航

在 `View` 增加 `marketIntel`，在 `AppShell` 的「投资工作台」中位于 Watchlist 和 Strategy 之间：

```text
Watchlist    WA
Market Intel MI
Strategy     SG
```

### 14.2 组件

```text
MarketIntelView
  InstrumentResearchSelector
  MarketIntelHealthBar
  MarketIntelRefreshProgress
  CapitalBehaviorPanel
    CapitalBehaviorSummary
    IntradayCapitalTimeline
    MultiDayCapitalTrend
    CapitalBehaviorSignals
    CapitalRuleExplanationCard
    CapitalAgentInterpretationPanel
  DragonTigerPanel
  LockupPanel
  ClassificationPanel
  StockNewsTimeline
  FundamentalPanel
  ProviderHealthDrawer
```

### 14.3 数据加载

- 进入 Tab 只加载标的列表和选中标的的 overview；
- 详细面板可延迟加载；
- 手动刷新后轮询 refresh run，终止状态后重新加载 overview 和受影响面板；
- 页面重新打开时读取后端 run，不依赖浏览器内存恢复状态；
- 规则解释随 `capital-behavior` 自动展示；Agent 面板仅在用户点击后发起 POST 并轮询 interpretation；
- Agent 面板分别展示“确定性事实”和“模型假设”，置信度、反证、数据缺口和输入快照始终可见；
- `FRESH/STALE/PARTIAL/UNAVAILABLE` 不只用颜色区分，同时显示文字和时间。

## 15. 配置

建议增加：

```properties
finscope.market-intel.enabled=true
finscope.market-intel.response-max-bytes=2097152
finscope.market-intel.connect-timeout-ms=5000
finscope.market-intel.read-timeout-ms=10000
finscope.market-intel.providers.eastmoney.enabled=true
finscope.market-intel.providers.eastmoney.min-interval-ms=1000
finscope.market-intel.providers.sina.enabled=true
finscope.market-intel.providers.baidu.enabled=true
finscope.market-intel.interpretation.rule-version=capital-rules-v1
finscope.market-intel.interpretation.agent-enabled=true
finscope.market-intel.interpretation.prompt-version=capital-agent-v1
finscope.market-intel.interpretation.timeout-ms=20000
```

配置仅控制提供方和技术参数，不在配置文件保存 cookie、用户名、付费凭证或私有端点数据。

## 16. 可观测性

1. 每个刷新运行持久化总状态和分维度步骤。
2. 日志使用中文业务描述，包含 run id、instrument id、dimension、provider、耗时和 error type。
3. 不记录完整响应体；记录负载大小、指纹和解析摘要。
4. 数据健康页面可查看最近失败、最近成功、连续失败数、熔断状态和最新 Schema 错误。
5. 上游字段结构变化使用 `SCHEMA_DRIFT`，不归类为泛化 `UNKNOWN`。
6. 资金 Agent 记录 snapshot fingerprint、rule/prompt/model version、Gate 下调原因、缓存命中和 LLM 耗时，不记录完整敏感配置。

## 17. 安全、合规与许可证

1. 仅访问公开可访问数据，不绕过验证码、登录、反爬、付费墙或技术限制。
2. 对外请求只允许 Provider 代码内的固定 host，不接受前端传入任意 URL，避免 SSRF。
3. 所有 instrument code 先通过市场和格式验证，不将用户输入直接拼接到路径或过滤表达式。
4. 源站网页内容视为不可信输入；不执行脚本，限制响应大小，对入库 HTML 继续使用现有清洗链路。
5. 资金 Agent Prompt 只接收白名单结构化字段，不接收源站 HTML、上游 Signal 文本或用户提供的任意指令，降低 Prompt Injection 风险。
6. 如直接翻译或复制 TradingAgents-astock 的实现代码，必须遵守 Apache 2.0，保留 LICENSE/NOTICE 与修改说明；首选根据公开数据合同独立实现。
7. 页面持续展示“用于投资研究与学习，不构成投资建议”。

## 18. 测试策略

### 18.1 RPC 适配器测试

- 每个 Provider 保存经脱敏的固定响应 fixture；
- 测试正常响应、空响应、字段缺失、单位异常、错误日期和超大响应；
- 默认单元和 CI 测试不访问真实外部网络；
- 提供手动运行的 live smoke test，不纳入默认构建。

### 18.2 领域与 Service 测试

- 不同维度部分失败得到 `PARTIAL`；
- 成功空集不被当成失败；
- 备用源、冲突告警和过期状态正确；
- 同标的重复刷新返回同一活跃 run；
- 外部请求失败不回滚其他已完成维度；
- 快照去重、指纹和时效判断正确；
- 1 分钟到 5 分钟聚合对成交金额和资金净额求和，价格按约定规则取值；
- 跨来源时间点在容差内正确对齐，超出容差产生 `TIMELINE_ALIGNMENT_GAP`；
- 5/10/20 日窗口、连续流入/流出天数和净流入成交额占比可由固定样本复算；
- 客观异常标签在相同输入和算法版本下结果稳定，不生成暗盘、吸筹或出货语义；
- 规则解释在相同 snapshot/rule version 下确定性相同，每句话包含有效 metric reference；
- AgentHarness 对相同动作指纹执行重复保护并记录 LLM 调用预算；
- Agent JSON 缺字段、引用不存在、混淆事实与假设时被 Gate 修正或拒绝；
- 无 Level-2 时拆单/隐藏资金假设最高 `LOW`，第一阶段任何资金意图假设最高 `MID`；
- LLM 未配置、超时或非法输出时返回规则解释并记录明确降级原因；
- `pointInTimeSafe=false` 的数据不能被量化数据集服务直接消费。

### 18.3 Repository 测试

- 唯一约束与幂等插入；
- 按 instrument/asOf 查询不返回未来记录；
- 资金时间线与异常标签保持输入记录引用和算法版本；
- 资金快照不可变，同 snapshot/rule version 规则解释幂等；
- Agent 解读保存 model/prompt/input/output 指纹并可按 subject 查询完整 Trace；
- 龙虎榜主记录和席位关系完整；
- Article 关联的确认、拒绝和重新评估不删除文章；
- 服务重启恢复未完成运行状态。

### 18.4 Controller 与前端测试

- overview 不隐式访问外部网络；
- refresh 返回 202 和可查询 run；
- 部分成功、过期、空集和不支持状态正确展示；
- 资金摘要、1/5 分钟切换、5/10/20 日趋势与异常标签展示正确；
- 页面进入时只自动加载规则解释，不自动触发 LLM；
- 点击 Agent 解读后显示进度，完成后事实/假设分区展示；
- LLM 失败时保留规则解释和图表，不显示伪成功结果；
- 切换标的不残留上一标的数据；
- 刷新期间页面重载可恢复运行进度；
- 键盘操作、窄屏和非颜色状态提示可用。

## 19. 分阶段工程交付

### 19.1 第一阶段：底座与首批五维数据

1. 增加 `marketintel` 领域对象、SchemaMigrator 和 Repository。
2. 实现 `FinanceHttpClient`、限流器、基础 ProviderResult 和错误分类。
3. 首先实现东财资金流与行情上下文合并：成交金额、换手率、量比、1/5 分钟时间线和 5/10/20 日趋势。
4. 实现版本化的客观资金异常标签；规则层排除暗盘、拆单、吸筹和出货意图判断。
5. 实现 `CapitalBehaviorSnapshot`、确定性规则解释和 metric reference。
6. 泛化 Agent Trace subject，复用 `AgentHarness/LlmChatClient` 实现点击触发的资金解读 Agent 与置信度 Gate。
7. 龙虎榜事实 Provider、持久化、刷新、查询与页面已实现；继续实现解禁、行业排名以及一个概念/行业归属 Provider。
8. 实现刷新 run/step、查询 Service、REST API 和重启失败恢复。
9. 增加 Market Intel Tab、选择器、健康条、刷新进度和四类主面板，资金行为与规则解释默认位于首屏。
10. 完成 fixture、时间对齐/聚合、规则/Agent/Trace、Repository、Service、Controller、前端测试和构建。

### 19.2 第二阶段：新闻公告与文章链路

1. 实现个股新闻主源/备用源和公告源。
2. 实现 `StockNewsIngestBridge` 与 `instrument_article`。
3. 在 Market Intel 页增加分类时间线、来源标识和事件/证据跳转。
4. 完成 URL/内容去重、代码/名称/别名关联和错关联处理测试。

### 19.3 第三阶段：基本面与数据源可靠性

1. 实现财务、估值、预期和股东变化数据模型。
2. 增加市场热度、政策/全球快讯背景。
3. 增加备用源、冲突告警、熔断、数据源健康页和刷新历史。
4. 为披露日期、口径和 point-in-time 禁用边界增加专门测试。

### 19.4 第四阶段：研究快照与 Agent 整合

1. 将第一阶段 `CapitalBehaviorSnapshot` 作为组成部分，实现跨领域 `StockResearchSnapshotService` 和不可变指纹。
2. 向归因 Harness 提供市场数据上下文，不改变其证据门和预算主导权。
3. 将 snapshot id/fingerprint 写入 Agent Trace 和研究报告元数据。
4. 打通股票命题、复盘与标的研究数据的关联。
5. 实现受证据约束的“支持/反证/缺口/下一观察点”摘要与稳定性测试。

## 20. 验证命令

每个阶段至少执行：

```bash
cd backend && mvn test
cd frontend && npm test
cd frontend && npm run build
```

如 Maven 因当前网络无法访问公司依赖仓库，必须保留完整错误并运行可用的缓存/模块级测试和前端验证，不将未运行的测试声称为通过。

## 21. 延后决策

### 21.1 `mootdx` 与 Python Sidecar

第一至第四阶段都不默认引入 Python。只有满足以下条件时才另立设计：

1. 经过实测，HTTP 来源无法提供某个高价值数据；
2. `mootdx` 数据对实际产品价值是必要条件，而不是仅为了代码复用；
3. Sidecar 只实现数据 Provider HTTP 合同，不共享 SQLite，不运行 Agent，不写 FinScope 业务数据；
4. Java 上层仍通过相同 ProviderResult 和质量门消费结果。

### 21.2 历史回测数据

`marketintel` 聚焦当前研究。如未来要将某维度加入量化回测，必须另行定义历史数据合同、修订政策、披露时间、成分生效时间和数据集指纹，不直接复用当前快照表。
