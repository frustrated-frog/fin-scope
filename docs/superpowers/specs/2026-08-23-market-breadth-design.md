# Market Pulse 市场宽度设计

## 目标

为 Market Pulse 增加可回放、可降级、与交易日严格对齐的 A 股市场宽度快照，使“指数涨跌”和“多数股票真实表现”共同参与市场阶段判断。

首期只实现稳定口径：五大指数、上涨/下跌/平盘家数、上涨比例、全市场成交额、涨停/跌停家数、市场涨跌幅中位数和行业上涨比例。20 日新高/新低与炸板率不进入本期。

## 边界

- 外部采集全部位于 `market-data-service`，Java 不直接访问东方财富、新浪或同花顺页面。
- 东方财富全 A 实时行情为主源，新浪全 A 实时行情为备用源。
- 同花顺行业汇总提供行业上涨/下跌家数。
- 涨停和跌停优先使用东方财富显式股票池；失败时字段为空并降低质量，不用价格阈值伪造精确值。
- Market Pulse 允许部分数据工作，但不同业务日期的数据不得拼接成同一份判断。

## Python 行情服务

新增独立市场级契约 `market-breadth-v1`，接口为：

```text
GET /v1/markets/CN-A/breadth
```

响应包含：

- `business_date`
- `source_code`、`source_family`
- `quality_status`
- `retrieved_at`
- `advance_count`、`decline_count`、`flat_count`、`valid_count`
- `advance_ratio`
- `total_amount`
- `limit_up_count`、`limit_down_count`
- `median_change_pct`
- `warnings`

主流程：

```text
东方财富全 A 快照
  -> 失败后切换新浪全 A 快照
  -> 两者均失败时读取最近一次同业务日成功快照
  -> 无合格快照则返回 UNAVAILABLE
```

快照写入 Python 侧现有 SQLite snapshot store，市场级快照使用固定键 `MARKET:CN-A`。主源和备用源必须标准化为同一字段集合，过滤缺少代码、现价或涨跌幅的无效行。

涨跌停池与全 A 快照独立采集。涨跌停池失败不应使宽度主体失败，但质量降为 `PARTIAL_FRESH` 并写入警告。

## 五大指数

Market Pulse 使用以下指数：

- `000001.SH` 上证指数
- `399001.SZ` 深证成指
- `399006.SZ` 创业板指
- `000300.SH` 沪深 300
- `000852.SH` 中证 1000

Java 通过现有 `QuantDailyBarSource` 获取各指数截至业务日的日线，输出当日、5 日和 20 日收益。指数之间允许单项失败，但失败项必须显示数据质量；沪深 300 仍作为趋势和波动率基准。

## 行业宽度

扩展 Python 同花顺行业响应与 Java `SectorMarketEntry`：

- `advance_count`
- `decline_count`
- `flat_count`
- `breadth_ratio`

`breadth_ratio = advance_count / (advance_count + decline_count + flat_count)`。分母为零时保持空值。该值进入现有行业轮动评分，替代“行业宽度未接入”的降权路径。

## Java 集成

在 RPC 模块新增市场宽度客户端与标准化批次对象；Service 层新增 `MarketBreadthService`，负责：

1. 按最新交易日读取 Python 宽度快照；
2. 读取五大指数截面；
3. 检查所有数据的业务日期；
4. 生成宽度解释与质量状态；
5. 将宽度快照交给 Market Pulse 编排器。

`MarketRegimeFeatures.marketBreadth` 写入全市场上涨比例。分类器规则：

- 上涨比例不低于 58%：宽度偏强；
- 上涨比例不高于 42%：宽度偏弱；
- 指数上涨但宽度偏弱：解释为窄幅上涨或权重驱动；
- 指数、成交与宽度共同增强：才允许形成高风险偏好判断；
- 宽度存在且其他必要特征完整时，市场状态质量可为 `READY`。

市场宽度完整快照存入 Java SQLite，以业务日期唯一。`MarketPulseWorkspace` 同时保留冻结副本，确保历史页面不受后续上游修订影响。

## 日期修复

“最新快照”查询必须限定为不晚于最新有效交易日。现有未来日期或周末快照不自动物理删除，避免隐式破坏用户数据；迁移后由查询忽略，并在重新生成正确交易日快照后不再展示错误快照。

## 前端

在 Market Pulse 的市场状态与 5 日节奏之间新增市场宽度区：

- 五大指数对照；
- 上涨、平盘、下跌比例条；
- 上涨/下跌家数；
- 全市场成交额；
- 涨停/跌停家数；
- 市场涨跌幅中位数；
- 指数与宽度是否共振的一句解释；
- 业务日期、来源和质量状态。

字段缺失时显示 `—`，并在数据边界区域展示具体警告。

## 测试与验收

- Python Provider 映射东方财富和新浪字段，并验证主备降级。
- Python API 验证完整、部分和不可用响应。
- Java RPC 验证契约、日期和质量状态。
- Java Service 验证业务日期不一致时拒绝混合。
- 分类器验证普涨、窄幅上涨和普跌情形。
- 行业轮动验证行业宽度进入评分。
- Controller 与前端验证新增字段和降级展示。
- 原有 Market Pulse 专项测试、Python 全量测试、前端全量测试和生产构建通过。

