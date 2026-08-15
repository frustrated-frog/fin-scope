# Backtesting.py 量化验证层设计

## 目标

在不替换现有预测模型、walk-forward、锁定测试与原生回测的前提下，引入 Backtesting.py 0.6.6 作为独立影子回测引擎。单股预测与股票发现继续复用同一个 `build_forecast()`，共同获得逐笔差分审计和可解释的参数鲁棒性结果。

本期提高的是推荐结论的可信度与可审计性，不承诺直接提高原始方向命中率。第三方引擎只做影子验证，不参与本期最终推荐门禁。

## 范围

本期包含：

- 标准化样本外信号事件，信号只能来自已有滚动样本外观察。
- Backtesting.py 独立适配器，不复用原生回测的成交、费用或日期计算实现。
- 归一化两套交易账本，并逐笔比较成交日期、收益、费用和净值。
- 形成 `PASS`、`WARNING`、`UNAVAILABLE` 三态影子审计结论。
- 扩展相邻参数结果，给出均值、中位数、跑赢比例、离散度和稳健区域规模。
- 单股预测页面展示量化验证层；股票发现候选复用同一份报告并展示审计摘要。
- Backtesting.py 缺失、超时或异常时确定性降级，不能阻断预测或选股。

本期不包含：

- 不让影子引擎决定 `UP`、`ABSTAIN` 或最终股票发现名单。
- 不引入 DSR、PBO、因子 IC/RankIC 或收益归因。
- 不通过参数搜索选择线上阈值，不触碰锁定测试集。
- 不 fork Backtesting.py，不把 A 股特殊规则写入第三方库源码。
- 不新增数据库表；报告继续随现有预测报告 JSON 持久化。

## 既有能力与复用边界

`market-data-service` 已经提供：

- `build_forecast()`：单股预测和股票发现的共同入口。
- `validate_walk_forward()`：扩展窗口滚动样本外预测。
- `simulate_strategy()`：原生下一交易日开盘入场、固定持有、非重叠回测。
- `analyze_stability()`：固定相邻参数扰动。
- 同股买入并持有、分年度、趋势阶段、交易成本、换手率和回撤统计。

新验证层必须消费这些模块的稳定输出，不复制预测、训练和候选筛选流程。Java 继续负责 RPC 契约、持久化和页面接口，不承载量化计算。

## 架构

```text
QFQ 日线 + 滚动样本外概率
              │
              ▼
      Standard Signal Events
              │
      ┌───────┴────────┐
      ▼                ▼
FinScope Backtester  Backtesting.py Adapter
      │                │
      ▼                ▼
 Native Ledger      Shadow Ledger
      └───────┬────────┘
              ▼
      Differential Auditor
              │
              ├─ execution agreement
              ├─ return / cost deltas
              ├─ metric deltas
              └─ mismatch details
              ▼
      Forecast Report v7
              │
       ┌──────┴──────┐
       ▼             ▼
  单股预测页面    股票发现深度候选
```

## 组件设计

### 标准化信号

`SignalEvent` 是不可变内部对象，包含：

- `signal_date`
- `entry_date`
- `exit_date`
- `probability`
- `target_position`
- `reason`

只把概率达到当前固定阈值且不与既有持仓重叠的滚动样本外观察转换为信号。当前时点预测概率绝不能回填历史信号。

### Backtesting.py 适配器

适配器在独立模块中完成 DataFrame 构造、Strategy 定义和第三方结果转换。配置固定为下一根 K 线开盘成交、禁止对冲、独占持仓、固定双边成本和回测末强制平仓。

适配器不调用 `simulate_strategy()`，不调用其费用或净值内部函数。两边只共享输入行情、标准化信号契约和对外字段语义。

Backtesting.py 依赖锁定为 0.6.6。导入失败或运行异常时返回 `UNAVAILABLE`，并附非敏感原因，不抛出到预测主流程。

### 差分审计

审计器按交易序号比较两本账：

- 交易数量是否一致。
- 信号日、入场日、退出日是否一致。
- 单笔净收益和成本的绝对差。
- 最终累计收益、最大回撤和 Sharpe 的绝对差。
- 净值曲线末值差异。

日期或交易数量不同属于结构性差异，状态为 `WARNING`。纯浮点、份额舍入或第三方指标定义差异在显式容差内视为 `PASS`。任何差异都必须保留类别和说明，不能只给一个布尔值。

### A 股执行边界

本期以现有研究策略的标准化资金和固定成本为审计基线，不改变线上收益口径。A 股 T+1、涨跌停、停牌、ST、100 股整数手、最低佣金和印花税以执行契约和 Golden Cases 固化，但正式改变回测收益口径属于下一期迁移。

这样可以先证明双引擎日期与数学一致，再引入真实资金和不可成交状态，避免同时改变引擎与业务口径导致无法定位差异。

### 参数鲁棒性

继续使用固定、预先声明的相邻方案，不选择历史最优参数。基于既有场景新增：

- `neighbor_mean_excess_return`
- `neighbor_median_excess_return`
- `outperform_benchmark_ratio`
- `surface_variance`
- `robust_region_size`
- `scenario_count`

`robust_region_size` 表示连续相邻场景中超额收益为正且 Sharpe 不劣于基准要求的数量。本期不使用 SAMBO 选参，避免污染锁定测试。

## 报告契约

预测报告升级为 `single-stock-research-v7`，新增可选 `backtestAudit`：

- `status`: `PASS | WARNING | UNAVAILABLE`
- `mode`: 固定为 `SHADOW`
- `primaryEngine` / `shadowEngine`
- `tradeCountAgreement`
- `entryDateAgreementRate` / `exitDateAgreementRate`
- `returnDelta` / `maxDrawdownDelta` / `sharpeDelta` / `costDelta`
- `mismatches`
- `shadowMetrics`
- `limitations`

数据不足时保持现有 `INSUFFICIENT_DATA` 返回，不强行运行影子回测。旧历史报告没有该字段时，Java 和前端必须兼容。

股票发现的 `deep_evidence.forecast_report` 天然携带同一审计报告；发现服务只抽取状态与关键差异用于候选摘要，不另跑第二套逻辑。

## 前端

单股预测增加“独立回测审计”研究卡：

- 顶部显示 `一致通过`、`存在差异` 或 `影子引擎不可用`。
- 并排展示原生与影子累计收益、回撤、Sharpe、交易数。
- 展示入场日和退出日一致率。
- 差异列表使用可展开交易级明细。
- 明确标注“影子验证，不参与本期方向决策”。

相邻参数区域增加鲁棒性摘要，避免只显示神秘的稳定性百分比。

股票发现候选卡展示审计徽标。没有最终候选时，观察候选仍可从深度证据看到为何未通过；本期不改变最终名单生成规则。

## 失败与性能

- 影子引擎异常只产生 `UNAVAILABLE`，主报告照常成功。
- 不捕获并吞掉原生预测错误。
- 单股每次运行一次影子审计；股票发现对全部深度候选复用该能力。
- 不额外抓取行情，使用调用内已有缓存日线。
- 第三方运行时间记录在报告中，为后续超时和容量决策提供证据。
- 首版先保证正确性；若 15 只并行出现资源竞争，再基于耗时数据限制影子并发。

## 测试

- 标准信号：阈值、非重叠、下一开盘和固定退出日。
- 适配器：正常交易、无信号、异常降级、成本处理。
- 差分审计：完全一致、浮点容差、日期错位、交易缺失。
- 端到端预测：v7 报告包含审计，依赖不可用仍成功。
- 股票发现：深度候选复用审计且不改变既有资格门禁。
- Java RPC：v7 字段反序列化和非法字段拒绝；v6 历史兼容。
- 前端：三种状态、关键指标、差异明细和旧报告兼容。
- 全量 Python、相关 Maven 模块测试、前端测试和生产构建。

## 验收标准

- 正常 Golden Case 的交易数量、信号日、入场日和退出日一致率为 100%。
- 指标差异在文档化容差内时为 `PASS`；结构性差异稳定输出 `WARNING`。
- 影子引擎不可用时单股预测与股票发现仍能成功。
- 单股和股票发现报告来自同一个 `build_forecast()`，没有复制量化流程。
- 锁定测试、模型竞赛、推荐门禁和现有历史报告读取不回归。
- 页面清楚区分预测质量、策略表现和独立回测审计。

## 后续演进

稳定运行并积累差分报告后，下一期才考虑：

1. 将已验证的双引擎一致性纳入推荐门禁。
2. 引入真实 A 股整手、最低佣金、印花税、涨跌停与停牌执行。
3. 建设跨股票、跨行情的 50～100 个 Golden Cases 数据集。
4. 在完整试验登记基础上增加 DSR/PBO。
5. 为市场选股增加横截面 IC/RankIC，为策略增加收益归因。
