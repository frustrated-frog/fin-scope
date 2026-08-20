# 投资观察独立工作台设计

## 目标

为个人投资研究新增一个独立一级 Tab“投资观察”。它在没有真实持仓时仍能自动维护一个非交易性质的研究观察池，帮助用户回答：今天哪些变化值得看、证据如何演化、下一步等待什么验证，以及哪些对象应当升级、降级或归档。

本功能不改变首页热点和新闻雷达的生产、评分与展示逻辑。它只读取已经完成的雷达事件快照，并拥有独立的候选生成、观察评分、生命周期和用户状态。

## 产品边界

### 首页热点

- 回答“全市场今天发生了什么”。
- 继续使用现有热点分和 dashboard 快照。
- 不读取投资观察状态。

### 新闻雷达

- 回答“一个事件有哪些来源、证据和后续进展”。
- 继续负责事件聚类、来源质量、热点生命周期和证据时间线。
- 不因投资观察的接受、忽略或归档而改变排序。

### 投资观察

- 回答“哪些变化值得我持续验证”。
- 读取雷达事件作为候选线索，不参与雷达生产。
- 独立持久化观察对象、评分解释、验证点、生命周期和处理记录。
- 所有建议均为研究动作，不产生买卖指令。

## 首期范围

首期以雷达事件为唯一自动候选来源，预留后续接入板块行情、量化选股和策略学院的来源类型。这样可以先完成清晰、可验证的解耦闭环，避免一次跨越多个尚未统一的领域模型。

首期自动生成最多 20 个活跃观察对象，并稳定提供：

- 今日重点：最多 5 个；
- 持续跟踪：其余达到底线的对象；
- 学习样本：证据不足但具有解释价值的对象；
- 已归档：用户归档或长期无新增变化的对象。

固定名额与最低底线结合，避免空页面，同时阻止明显低质量和重复事件进入重点区。

## 数据模型

### `investment_observation`

- `id`
- `source_type`：首期固定为 `RADAR_EVENT`
- `source_id`：雷达事件 ID
- `title`
- `summary`
- `subject_type`：`COMPANY`、`INDUSTRY`、`THEME`、`FACTOR`、`EVENT`
- `subject_name`
- `stage`：`FOCUS`、`TRACKING`、`LEARNING`、`ARCHIVED`
- `change_type`：订单、价格、政策、业绩、竞争格局、资金、其他
- `score`
- `score_explanation_json`
- `why_it_matters`
- `uncertainty`
- `next_validation`
- `supporting_evidence_count`
- `opposing_evidence_count`
- `independent_source_count`
- `first_observed_at`
- `last_changed_at`
- `last_source_fingerprint`
- `user_disposition`：`ACTIVE`、`LATER`、`IGNORED`
- `revision`
- `created_at`
- `updated_at`

`source_type + source_id` 唯一，保证同一雷达事件不会生成重复观察对象。历史雷达事件被合并时，观察域只通过稳定事件 ID 读取最新信息，不反向维护雷达表。

### `investment_observation_transition`

记录进入、升级、降级、验证点变化和归档：

- `observation_id`
- `from_stage`
- `to_stage`
- `reason`
- `source_fingerprint`
- `occurred_at`

## 自动评分

观察分独立于热点分，由确定性规则生成，并保留逐项解释：

| 维度 | 权重 | 说明 |
|---|---:|---|
| 变化强度 | 20 | 订单、价格、政策、业绩等实质变化优先 |
| 证据可信度 | 20 | 公告、官方数据和高等级来源优先 |
| 独立来源确认 | 15 | 多个独立来源高于重复转载 |
| 持续性 | 15 | 跨日新增证据高于短暂传播 |
| 投资机制 | 15 | 能否解释对收入、利润、供需或估值的传导 |
| 可验证性 | 15 | 是否存在明确的下一验证指标 |

首期可使用雷达已有的热点分、置信分、独立来源数、事件生命周期、解读结果与下一观察点进行确定性映射。缺失字段按零分或保守说明处理，不调用 LLM 补造事实。

分层规则：

- `FOCUS`：总分不低于 70，每期最多 5 个；
- `TRACKING`：总分不低于 50；
- `LEARNING`：低于 50，或证据缺口明显但存在学习价值；
- `ARCHIVED`：用户归档、忽略，或后续生命周期任务确认长期无新增变化。

即使没有对象达到 70 分，也从不低于 50 分的对象中选出最多 3 个“当前最值得验证”，但明确标注“证据仍不足”，不伪装成高置信重点。

## 数据流与解耦

```text
Radar completed snapshot / persisted events
                    |
                    v
InvestmentObservationCandidateService
                    |
                    v
InvestmentObservationScoringService
                    |
                    v
InvestmentObservationLifecycleService
                    |
                    v
investment_observation + transition
                    |
                    v
InvestmentObservationController / 独立前端 Tab
```

- 投资观察刷新失败时，首页和新闻雷达不受影响。
- 雷达刷新不等待投资观察计算完成。
- 投资观察使用独立 view revision：`investment-observation`。
- 首期提供手动刷新接口；后续可添加独立调度器，不复用首页刷新入口。

## 后端模块边界

- `finscope-common`：稳定状态枚举。
- `finscope-domain`：观察对象、转移记录、视图 DTO。
- `finscope-dao`：SQLite Repository 与 Schema 初始化。
- `finscope-service`：候选读取、评分、生命周期、工作台组装。
- `finscope-web`：`/api/investment-observations` REST 接口。

主要接口：

- `GET /api/investment-observations`：读取工作台；
- `POST /api/investment-observations/refresh`：从最近雷达结果刷新观察池；
- `PATCH /api/investment-observations/{id}/state`：稍后看、忽略、恢复；
- `POST /api/investment-observations/{id}/archive`：归档；
- `GET /api/investment-observations/{id}`：读取观察档案和变化记录。

## 前端设计

在主导航“研究”分组新增一级 Tab“投资观察”，不放入新闻页二级切换。

页面视觉采用克制的研究工作台风格：暖白底、墨色文字、低饱和状态色、清晰数字层级，不使用行情终端式密集表格。

### 页面结构

1. 顶部研究状态：活跃观察数、今日变化、等待验证、已归档；
2. 今日决策台：最多 5 张横向重点卡，突出“新增变化”和“下一验证点”；
3. 观察池：按核心观察、持续跟踪、学习样本切换；
4. 右侧/移动端抽屉档案：变化机制、支持与反对证据、信息缺口、验证点、来源事件链接；
5. 最近变化时间线：展示阶段变化而非重复新闻。

用户动作使用“继续观察”“稍后看”“忽略”“归档”“启动研究”等研究语言，不出现“买入”“卖出”或仓位建议。

## 错误与降级

- 雷达没有完成快照：显示首次使用引导，不报系统异常；
- 刷新部分失败：保留上一版观察池并展示警告；
- 某事件缺少解读：用确定性摘要、信息缺口和下一验证点回退；
- 用户动作并发冲突：基于 revision 返回冲突提示，前端重新加载；
- 来源事件已不存在：观察档案仍可读，但标记来源不可用。

## 测试与验收

- Repository：唯一来源约束、更新与转移记录；
- 评分：高质量重点、普通跟踪、弱证据学习样本、无 70 分时的保底展示；
- Service：刷新幂等、雷达为空、失败保留旧数据、用户状态不被自动刷新覆盖；
- Controller：读取、刷新、状态更新、归档；
- Frontend：独立导航、空状态、重点卡、筛选、档案、刷新失败和移动端布局；
- 回归：首页热点和新闻雷达测试保持不变；
- 浏览器验收：桌面与 390px 移动视口，无横向溢出，关键操作可达。

## 非目标

- 不自动交易；
- 不基于新闻给出买卖建议；
- 不要求用户维护真实持仓；
- 首期不重新实现雷达聚类与证据采集；
- 首期不接入外部新数据源；
- 首期不把策略学院、量化选股和板块候选直接写入观察池，只保留扩展点。
