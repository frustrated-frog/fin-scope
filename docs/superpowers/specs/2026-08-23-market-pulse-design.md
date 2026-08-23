# Market Pulse 市场机会工作台设计

## 1. 目标

FinScope 新增独立一级 Tab `Market Pulse · 市场机会`，在每个交易日收盘后自动回答四个问题：

1. 当前处于什么市场状态；
2. 哪些行业正在增强、持续、过热或衰减；
3. 哪些 Radar 事件已获得行情与资金确认；
4. 哪些股票满足进一步研究的必要条件。

页面不是周报生成器，也不是承诺收益的荐股器。系统冻结结构化事实，以确定性规则分类市场和行业状态，复用现有股票发现、预测健康及真实结算能力，输出零至五只研究候选。LLM 只解释冻结事实，不生成行情事实、不绕过门禁，也不产生买卖指令。

正式判断以收盘快照为准。盘中数据即使未来接入，也只能作为明确标记的预览，不能覆盖收盘版本。

## 2. 产品边界

现有页面职责保持不变：

| 页面 | 回答的问题 |
|---|---|
| Dashboard | 今天有哪些研究任务 |
| News Wire | 发生了什么事件 |
| 投资观察 | 哪些变化值得持续验证 |
| Watchlist | 用户关注的标的表现如何 |
| Market Intel | 单个标的的资金行为如何 |
| Strategy | 量化策略与预测是否可靠 |
| Market Pulse | 当前是什么市场，机会在哪里 |

`Market Pulse` 放入侧栏“决策”组并排在第一位。Dashboard 只显示最新状态、前三个增强行业和候选数量的摘要入口；Watchlist 继续承载个人关注，不复制全市场扫描。

## 3. 首期范围与阶段

### 3.1 第一阶段：市场状态与行业轮动

- 每日收盘冻结市场状态快照；
- 展示最近五个交易日的状态迁移；
- 每日冻结全行业轮动快照；
- 展示行业 1/5/20 日表现、相对市场强弱、资金、宽度、持续性、拥挤度和阶段；
- 支持历史交易日切换；
- 每个区域独立展示数据质量与降级原因。

### 3.2 第二阶段：事件与市场确认

- 从 Radar 已完成事件中提取行业关联候选；
- 保存带证据、来源和置信度的事件—行业映射；
- 比较事件强度和行业行情反应；
- 输出 `CONFIRMED`、`UNCONFIRMED`、`MARKET_LEADING`、`QUIET`；
- 低置信映射只展示，不参与机会排序。

### 3.3 第三阶段：股票研究候选

- 使用市场状态与行业轮动结果替换股票发现单纯依赖当日热门行业的入口；
- 复用产业链和行业成分确认公司业务暴露；
- 复用 Stock Discovery 的预算、数据质量、流动性、截面排名和完整预测；
- 只允许方向为 `UP`、样本外验证通过且模型健康的候选进入最终列表；
- 输出市场适配、行业状态、催化、Why now、风险和失效条件；
- 允许没有候选，不降低门槛凑数。

### 3.4 第四阶段：真实效果反馈

- 复用现有股票发现候选和模型影子结果账本；
- 按市场状态、行业阶段和事件确认状态切片评估真实结果；
- 展示 Top-1/3/5、合格池对照、概率可靠性和近期失效信号；
- 样本不足时只显示“积累中”，不自动调整权重。

## 4. 非目标

- 不生成周报文档；最近五日只是快照历史视图；
- 不提供仓位、下单、买入或卖出指令；
- 不承诺候选未来上涨；
- 不新增第二套股票预测模型或预测结果账本；
- 不使用当前板块排名回填历史日期；
- 不在第一阶段强制接入北向资金、连板高度、炸板率、美债收益率等缺少稳定合同的数据；
- 不把 LLM 输出直接写入数值评分或状态门禁。

## 5. 页面信息架构

### 5.1 市场状态头部

页面头部显示业务日期、快照状态和一句核心判断：

```text
2026-08-21 · 收盘快照

急跌后的缩量修复

趋势       RANGE
流动性     SHRINKING
风险偏好   LOW
板块轮动   FAST
成长风格   WEAK_REPAIR
防御风格   RELATIVE_STRONG
置信度     78
```

判断下方列出直接支撑证据：指数 1/5/20 日收益、均线位置、波动率、回撤、成交额相对均值、市场宽度、行业收益离散度和风格相对表现。

### 5.2 五日市场节奏

最近五个交易日按业务日期展示状态迁移：

```text
放量上涨 -> 高位分歧 -> 风险释放 -> 缩量修复 -> 修复分化
```

点击交易日切换整页读取的冻结快照。历史查看不重新调用外部行情，也不使用当前数据重算过去判断。

### 5.3 行业轮动雷达

行业列表展示：

- 1/5/20 日收益；
- 相对沪深 300 超额；
- 主力资金排名及变化；
- 行业内部上涨比例；
- 连续进入前列的交易日数；
- 拥挤度；
- 行业阶段与解释。

行业阶段为：

- `EMERGING`：初步转强；
- `ACCELERATING`：加速增强；
- `PERSISTENT`：持续强势；
- `OVERHEATED`：过度拥挤；
- `FADING`：强度衰减；
- `REVERSING`：反转修复；
- `WEAK`：持续弱势；
- `INSUFFICIENT_DATA`：证据不足。

点击行业打开详情抽屉，显示指标历史、关联事件、行业成分和股票研究候选。

### 5.4 事件与市场确认

事件卡同时展示 Radar 证据与市场反应：

- 事件强度和证据可信度；
- 关联行业及映射置信度；
- 行业收益、资金变化、内部宽度和龙头趋势；
- 市场确认状态；
- 原始 Radar 事件入口。

确认状态语义：

| 新闻强度 | 市场反应 | 状态 | 解释 |
|---|---|---|---|
| 强 | 强 | `CONFIRMED` | 事件获得市场确认 |
| 强 | 弱 | `UNCONFIRMED` | 尚未确认或可能已定价 |
| 弱 | 强 | `MARKET_LEADING` | 市场可能先于新闻变化 |
| 弱 | 弱 | `QUIET` | 低优先级 |

### 5.5 股票研究候选

每个候选展示：

- 研究优先级和冻结日期；
- 当前市场环境是否适配；
- 所属行业阶段和拥挤风险；
- 催化事件及公司业务暴露证据；
- 股票发现排名、校准概率、OOS 和模型健康；
- Why now；
- 风险和失效条件；
- 进入单股预测、产业图谱、资金行为和自选关注的操作入口。

候选只能称为“研究候选”，不能显示“推荐买入”等交易语言。

## 6. 数据与领域模型

### 6.1 市场状态快照

新增 `market_regime_snapshot`：

- `id`
- `business_date`，唯一；
- `trend_state`
- `liquidity_state`
- `risk_appetite_state`
- `rotation_state`
- `market_stage`
- `growth_style_state`
- `defensive_style_state`
- `confidence_score`
- `feature_snapshot_json`
- `evidence_json`
- `explanation`
- `quality_status`
- `source_fingerprint`
- `calculated_at`
- `created_at`
- `updated_at`

`business_date` 唯一。相同业务日重复运行时，只有新数据指纹与现有指纹不同时才允许替换；替换必须在当天收盘任务完成窗口内发生，并保留刷新运行审计。

### 6.2 行业轮动快照

新增 `sector_rotation_snapshot`：

- `id`
- `business_date`
- `category`
- `quality_status`
- `source_fingerprint`
- `calculated_at`

唯一键为 `business_date + category`。

新增 `sector_rotation_item`：

- `snapshot_id`
- `sector_code`
- `sector_name`
- `return_1d`
- `return_5d`
- `return_20d`
- `excess_return_5d`
- `excess_return_20d`
- `main_net_inflow`
- `flow_rank`
- `previous_flow_rank`
- `breadth_ratio`
- `new_high_ratio`
- `persistence_days`
- `crowding_score`
- `rotation_score`
- `stage`
- `score_explanation_json`

唯一键为 `snapshot_id + sector_code`。

### 6.3 事件市场确认

新增 `market_event_confirmation`：

- `id`
- `business_date`
- `radar_event_id`
- `sector_code`
- `mapping_source`
- `mapping_evidence_json`
- `mapping_confidence`
- `event_score`
- `market_reaction_score`
- `confirmation_state`
- `reaction_snapshot_json`
- `created_at`
- `updated_at`

唯一键为 `business_date + radar_event_id + sector_code`。映射置信度低于门槛时保留记录，但设置 `eligible_for_ranking=false`。

### 6.4 Market Pulse 运行

新增 `market_opportunity_run`：

- `id`
- `business_date`，唯一；
- `market_regime_snapshot_id`
- `sector_rotation_snapshot_id`
- `stock_discovery_run_id`
- `status`
- `quality_status`
- `warning_json`
- `generated_at`

该表只连接冻结结果，不复制股票候选、概率或真实 Outcome。股票候选继续读取现有 `stock_discovery_candidate` 和 `stock_discovery_model_prediction`。

## 7. 模块职责

### `market-data-service`

- 读取指数、全市场宽度、行业目录、行业成分和前复权日线；
- 计算原始时间序列特征和点时截面指标；
- 返回版本化、有限数值、带来源和业务日期的结构化响应；
- 不访问 FinScope SQLite，不决定最终产品状态。

### `finscope-rpc`

- 封装 Python Market Pulse 数据接口；
- 校验版本、业务日期、有限数值和来源；
- 将下游错误转换为项目语义；
- 禁止把 Python 原始协议扩散到 service/web。

### `finscope-domain`

- 市场状态、行业阶段、事件确认、快照和工作台 DTO；
- 跨层稳定状态枚举放入 `finscope-common` 对应业务子包。

### `finscope-dao`

- Market Pulse Schema 初始化；
- 快照、行业明细、事件确认和运行记录持久化；
- 唯一约束、条件更新和按日期稳定查询。

### `finscope-service`

- `MarketRegimeService`：确定性分类市场状态；
- `SectorRotationService`：计算排名、阶段、持续性和拥挤度；
- `MarketEventConfirmationService`：编排事件映射与市场反应；
- `MarketOpportunityService`：编排已有快照和股票发现；
- `MarketPulseQueryService`：组装前端工作台，不发起慢速刷新。

### `finscope-web`

- REST 参数校验和响应映射；
- 收盘调度入口与应用装配；
- Controller 不直接调用 Repository。

### `frontend`

- 新增 `MarketPulseView`；
- 各区域独立加载、空状态和质量状态；
- 历史日期切换读取冻结快照；
- 提供到 Radar、产业图谱、资金行为、单股预测和自选页的导航。

## 8. 收盘数据流

```text
收盘行情完成
  -> Python 获取指数、宽度、行业和成分行情
  -> Java RPC 校验版本、日期、来源和数值
  -> 冻结 Market Regime Snapshot
  -> 冻结 Sector Rotation Snapshot
  -> 读取当日 Radar 完成事件
  -> 建立事件—行业映射并计算市场确认
  -> 根据市场状态和行业阶段选择股票发现行业入口
  -> 运行现有 Stock Discovery 与 Forecast 门禁
  -> 创建 Market Opportunity Run 关联冻结结果
  -> LLM 可选生成解释
  -> 发布 view revision
```

外部调用不放入数据库事务。每一步保存自己的状态和错误，重复调度使用业务日期与数据指纹保持幂等。关键输入缺失时跳过依赖步骤，不伪造中性数据继续输出高置信候选。

## 9. 评分与门禁

### 9.1 市场状态

状态分类基于趋势、波动、回撤、成交额、市场宽度、风格相对表现和行业离散度。第一版使用版本化确定性阈值；每个状态保存触发证据。缺少关键指标时降低置信度或输出 `INSUFFICIENT_DATA`。

### 9.2 行业轮动

行业先经过数据完整性门禁，再综合：

- 相对强弱；
- 行业内部宽度；
- 资金排名；
- 排名持续性；
- 事件确认；
- 拥挤风险。

第一版权重固定并版本化，不依据少量 Outcome 自动更新。拥挤度作为风险惩罚，不把单日大涨直接等同于高机会。

### 9.3 股票候选

最终候选必须同时满足：

- 所属行业通过轮动底线；
- 公司具有可追溯行业成员或产业链暴露；
- 现有股票发现候选通过预算、流动性和数据质量门禁；
- 深度预测方向为 `UP`；
- 样本外验证通过；
- 模型健康状态允许正式输出；
- 不存在阻断级数据质量警告。

最终数量为零至五。研究优先级只用于候选内部排序，不覆盖硬门禁。

## 10. API

首期提供：

```text
GET  /api/market-pulse/latest
GET  /api/market-pulse/dates?limit=20
GET  /api/market-pulse/{businessDate}
POST /api/market-pulse/refresh
```

`GET` 只读取本地冻结快照。`POST refresh` 创建或复用当日运行，返回运行状态，不在 HTTP 请求中等待完整股票发现结束。

工作台响应包含：

- 市场状态和证据；
- 最近五日节奏；
- 行业轮动列表；
- 事件确认列表；
- 股票研究候选；
- 数据质量、警告和生成时间。

## 11. 错误与降级

统一质量状态：

- `READY`：关键数据完整；
- `PARTIAL`：部分非关键指标缺失；
- `STALE`：只能读取上一版有效数据；
- `UNAVAILABLE`：无法形成可信判断。

规则：

- 市场宽度缺失时不输出高置信市场阶段；
- 行业历史不足时阶段为 `INSUFFICIENT_DATA`；
- 事件映射低置信时不参与股票排序；
- 股票 Forecast 不健康时不进入最终候选；
- LLM 失败时使用规则解释；
- 单一区域失败不清空其他冻结区域；
- 旧数据必须显示业务日期和旧数据标识；
- 无候选是正常结果，不显示系统错误。

## 12. 测试与验收

### Python

- 特征只使用业务日及之前的数据；
- 输入日期、代码和有限数值校验；
- 市场宽度、行业宽度和相对收益边界；
- 数据缺失与陈旧行情降级；
- 无当前数据回填历史日期。

### Java

- Repository 唯一约束、条件更新和日期查询；
- 市场状态每条分类规则与证据；
- 行业阶段、持续性、拥挤惩罚和排序稳定性；
- 事件四象限及低置信映射门禁；
- Market Opportunity 重复运行幂等、部分失败和零候选；
- Controller 参数、读取与异步刷新合同。

### 前端

- 新导航和页面标题；
- 加载、空、部分、旧数据和不可用状态；
- 历史日期切换；
- 行业筛选与详情；
- 零至五只候选；
- 跨工作台导航；
- 桌面和 390px 窄屏无横向溢出。

### 回归与完成门禁

- `cd market-data-service && pytest`
- `cd backend && mvn test`
- `cd frontend && npm test`
- `cd frontend && npm run build`
- 对照《项目开发规范与代码评审清单.md》检查字段注入、完整大括号、模块落点、外部调用边界和敏感配置。

## 13. 成功标准

完成后，用户打开 Market Pulse 可以基于冻结事实理解当前市场状态，识别正在增强或衰减的行业，查看事件是否得到市场确认，并获得零至五只有完整证据、风险和失效条件的研究候选。所有候选在到期后进入现有真实评测闭环，使系统能够逐步回答哪些市场状态、行业阶段和事件确认信号真正有效。
