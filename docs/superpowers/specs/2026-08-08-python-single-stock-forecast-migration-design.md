# 单股预测 Python 化迁移设计

## 背景与目标

当前实现把行情接入放在 Python、把特征和预测模型放在 Java。真实运行中，长历史请求还暴露了两个边界问题：旧 Python 进程拒绝 5,000 根请求，以及首选前复权源失效后通达信返回未复权行情，Java 为保护量化有效性而拒绝继续。

本次迁移遵循“能在 Python 中完成的数据分析就放在 Python”原则：Python 独立拥有行情复权、特征、标签、滚动训练、概率校准、收益分布和结论门禁；Java 只调用 Python 的预测接口、校验稳定响应并转发给前端。前端响应结构保持不变。

## 方案选择

采用 Python 完整量化链方案。相比“Python 特征 + Java 模型”，它避免跨语言复制样本语义；相比离线任务持久化，它适合当前单股、最多 5,000 行的小数据量，调用成本可控且不引入额外调度和结果表。

## Python 行情层

东方财富/AkShare 仍是优先前复权源。通达信作为可连通的独立降级源时，不再返回 `NONE`：

1. 以每页最多 800 根分页读取，直到满足请求或到达上市起点。
2. 同一连接读取除权除息记录。
3. 使用每 10 股分红、送转、配股和配股价计算除权参考价，并把事件日前的 OHLC 乘以累积因子，生成以最新交易日价格为锚的前复权序列。
4. 成交量和成交额保持原始口径；所有输出行标记 `QFQ`。
5. 除权记录不可读取或字段非法时，该 provider 失败，不能用未复权数据冒充量化输入。

快照继续写入 SQLite，并以实际返回行数和已穷尽历史状态表达覆盖能力，不能把 provider 的页大小误记为完整 5,000 根历史。

## Python 预测层

新增独立 `forecast` 包：

- `features.py`：校验和排序日线，构造 5/20/60 日收益、MA20/60 距离、20 日波动率、20/60 日成交额比，以及 T+1 开盘至 T+20 收盘净收益标签。
- `logistic.py`：固定规则的标准化 L2 逻辑回归，不自动调参。
- `walk_forward.py`：扩展窗口验证，只使用预测时已经成熟的标签，每 20 个交易日重训。
- `service.py`：数据门禁、SHA-256 指纹、基准比较、相似概率收益分布和结论状态。
- `schemas.py`：稳定的 Pydantic 请求/响应契约。

FastAPI 新增 `POST /v1/quant/single-stock-forecasts`。该端点先从同一 `ProviderRouter` 请求 5,000 根日线，再执行预测；数据不足返回结构化状态，行情或模型不可用才返回明确错误。

## Java 边界

Java `SingleStockForecastService` 不再计算特征或训练模型，而是通过 RPC 客户端请求 Python 预测端点，并把响应映射成现有 `SingleStockForecast` DTO。删除 Java 中的 `ForecastSample`、`SingleStockFeatureBuilder`、`RegularizedLogisticModel`、`SingleStockWalkForwardValidator` 及其测试，避免同一算法存在两套实现。

Controller 路径和前端 JSON 结构不变，因此前端无需重写。Java 客户端对状态、概率范围、日期、观测记录和必需字段进行契约校验，防止 Python schema 漂移直接污染页面。

## 错误与运行边界

- 旧 Python 进程的 OpenAPI 上限可通过重启更新；代码侧增加端点契约测试，防止再次回退到 1,000。
- 只有 QFQ 日线能进入预测；没有可靠复权源时返回可理解的 provider 错误。
- 少于 750 根返回 `INSUFFICIENT_DATA`，不是 HTTP 失败。
- 模型数值异常返回 `MODEL_UNAVAILABLE`，不生成伪概率。
- Java 将 Python 4xx/5xx 映射成现有统一业务错误；前端保留上次结果并展示失败原因。

## 验收

- 通达信分页超过 800 根，并对分红、送转、配股事件正确前复权。
- Python 特征、标签、滚动验证和结论结果与固定测试夹具一致且无未来泄漏。
- Python 预测端点对有效、数据不足和无法复权三类场景给出稳定响应。
- Java 只做代理，原前端契约测试继续通过。
- 使用真实运行中的 Python 服务请求 5,000 根不再返回 422，调用 Java 预测接口不再出现 QFQ 异常。
