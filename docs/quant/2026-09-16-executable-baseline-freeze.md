# 可执行策略第二版：冻结数据与固定规则基线

## 已交付

新增 Python 冻结工具，将历史量价特征、按时点保存的候选资格、原始执行日线及公司行为覆盖证据组装成上一版 Java 回放接口的输入。可读取现有 SQLite 行情快照中的研究历史，数据库使用只读事务，冻结结果完整保留在新目录中。可以用一条命令完成冻结、提交 Java 回放和保存账户报告。

基线 A 固定为 20 日动量和 20 日低波动的截面标准分各占 50%，不搜索权重。复用现有 `current_features` 的 `MOMENTUM_20` 和 `VOLATILITY_20`；历史至少 61 根日线。流动性使用当时最近 20 根日线平均成交额，阈值写入归档。分数不代表预期收益或上涨概率。相同分数沿用 Java 的证券代码顺序。

新字段 `signalMethod` 区分 `FIXED_RULE` 和 `TRAINED_MODEL`。省略时兼容旧版模型信号；固定规则必须将 `trainingLabelsMaturedBefore` 留空，训练模型仍必须提供真实成熟截止时间。未训练的规则不再填写虚构的训练日期。

## 本地真实数据核验

2026-09-16 对本地 `market-data-service/data/market-data-snapshots.db` 执行只读审计：

| 能力 | 快照数量 |
|---|---:|
| 日线 | 3,136 |
| 资金流 | 8 |
| 公司行为 | 1 |
| 估值 | 1 |

3,136 份日线全部是 QFQ。当前扶摇日线适配器也固定请求 `adjust=forward`。现有日线模型没有开盘交易限制状态，公司行为快照没有保存查询起止范围；因此单个空事件列表不能证明某段历史没有公司行为。当前快照库也不能代替逐日历史准入股票池。

**真实账户回放仍被数据门禁阻断。** 本批没有将前复权价格改名 RAW，也没有填造历史交易状态或选择性删除公司行为股票。实际审计记录在本地忽略目录 `market-data-service/data/quant/executable-source-audit-2026-09-16/audit.json`；这里的数量来自该次扫描，不代表未来持续覆盖。

## 归档契约

完整类型定义位于 `market-data-service/src/finscope_market_data/forecast/executable_archive.py`。根对象包含：

- `schemaVersion`: `EXECUTABLE_ARCHIVE_V1`。
- `protocol`: 上一版完整交易协议，不得省略费用。
- `startDate`、`endDate`: 交易日边界；日历复用项目已核验的交易所日历，不猜测未知年份。
- `priceBasis`: 必须为 `RAW`，仅作用于 `executionBars`。
- `minAverageAmount20d`: 20 日平均成交额准入阈值。
- `universeEvidence`、`completeUniverseDates`: 完整股票池的证据和所有调仓日期，包括空截面。
- `universe`: 每行包含 `signalDate`、`instrumentCode`、`availableAt`、`industry`、`eligible`、`rejectionReason`、`evidence`。时间使用无时区的上海本地时间；信息可用时间不得晚于信号截止。
- `executionBars`: 每行包含 `tradeDate`、`instrumentCode`、`open`、`close`、`openState`、`sourceEvidence`。全部股票日必须覆盖，缺失时不顺延、不默认可交易。
- `corporateCoverage`: 每只候选的 `instrumentCode`、`coverageFrom`、`coverageThrough`、`exDates`、`evidence`。必须覆盖整个区间，区间内存在公司行为则拒绝。
- `researchHistories`: 代码到现有 QFQ `DailyBar` 列表的映射；可由 `--snapshots` 只读加载，与 RAW 执行数据分离。

输入证据由数据生产方负责，字符串和覆盖声明不等于系统已经独立核实供应商真实性。所有候选保留；历史不足、流动性数据不足或当前行情过期会产生明确拒绝理由。缺少执行证据会阻断整批，而非借助未来可交易性筛选历史赢家。

## 使用方式

从 `market-data-service` 目录执行：

```bash
# 只读审计；发现阻断项时以状态码 2 退出并保存 audit.json。
PYTHONPATH=src .venv/bin/python scripts/freeze_executable_baseline.py \
  --audit-only --snapshots data/market-data-snapshots.db \
  --output data/quant/executable-audit-new

# 使用已准备的真实证据清单，自动加载研究历史，生成固定规则信号并回放。
PYTHONPATH=src .venv/bin/python scripts/freeze_executable_baseline.py \
  --manifest /absolute/path/verified-archive.json \
  --snapshots data/market-data-snapshots.db \
  --replay-api http://localhost:8080/api/quant/executable-replays \
  --output data/quant/executable-baseline-new
```

内联提供 `researchHistories` 时省略 `--snapshots`，两种方式不能同时提供历史。省略 `--replay-api` 时仅冻结，便于先审阅输入。输出目录必须不存在，避免覆盖已有实验。

成功冻结保存 `archive.json`、`input.json`、`audit.json`。提交回放成功后额外保存 `account.json` 与 `account.md`。数据校验失败仅保存失败审计；Java 调用失败保留已冻结输入和失败记录，可直接用上一版回放工具重试 `input.json`。

固定规则输入示例见 `examples/executable-baseline-input-synthetic.json`。它由 Python 冻结器从测试量价历史生成，Java Web 测试通过实际 Service 和账本回放；不是手写候选分数，也不是实际市场收益。

## 验证与下一步

测试覆盖固定规则输出、未来价格不改变已冻结信号、候选缺失历史仍保留、交易所节假日、未知年份、公司行为覆盖缺失、晚于截止的候选信息、重复记录、源库只读、输出防覆盖及 Python/Java 契约衔接。保留旧模型信号的训练时间校验。

下一步必须补齐原始执行价格、历史开盘限制、公司行为查询覆盖和时点准入股票池；当前版本已把这些缺口转为明确契约。尚未完成真实历史账户验证、B/C 模型实验或持续模拟，不应根据本批合成数据报告评价策略优势。
