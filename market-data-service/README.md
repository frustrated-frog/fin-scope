# FinScope Market Data Service

独立的 A 股数据获取服务。它利用 Python 数据生态获取并标准化数据，Java 只依赖稳定 HTTP 契约，不直接感知 AkShare、pytdx、腾讯、新浪或东方财富字段。

## 已提供的数据

| 能力 | 主要字段 | 数据源顺序 |
|---|---|---|
| 实时行情 | 最新价、昨收、开高低、涨跌额/幅、成交量、成交额、买卖价 | 腾讯 → 新浪 → 东方财富 |
| 历史日 K | 前复权 OHLC、成交量/额、振幅、涨跌幅、换手率 | AkShare → 东方财富 → pytdx |
| 资金流 | 分钟/日级主力、超大单、大单、中单、小单净流入及占比 | AkShare（日级）→ 东方财富（分钟+日级） |
| 个股资料 | 名称、行业、上市日期、股本、PE、PB、总/流通市值、扩展字段 | AkShare → 东方财富 |
| 自动股票发现 | 热门板块、板块成分、预算准入、全候选量化与深度预测 | 同花顺 → 东方财富 |

AkShare 的部分接口底层仍来自东方财富，因此会如实标记为同一 `EASTMONEY` 来源家族，不把它伪装成独立故障域。pytdx 属于独立 TDX TCP 故障域。

pytdx 不使用其全网节点扫描器，而是在少量候选节点内以 2 秒连接超时有限回退，并缓存本次成功节点，避免单次日 K 请求触发大量探测和异常日志。需要固定内网允许访问的节点时，可设置 `FINSCOPE_MARKET_DATA_TDX_HOST` 和 `FINSCOPE_MARKET_DATA_TDX_PORT`。

## 可靠性语义

- `FRESH_PRIMARY`：首选来源返回新鲜数据。
- `FRESH_FALLBACK`：首选来源失败，备用来源成功。
- `PARTIAL_FRESH`：聚合请求中部分数据集成功。
- `STALE_FALLBACK`：在线来源均失败，返回 SQLite 中最近一次成功快照。
- `UNAVAILABLE`：在线来源失败且从未产生成功快照。

每个响应都包含 `source_code`、`source_family`、`as_of`、`retrieved_at`、`attempts` 和 `warnings`。网络失败、空数据、Schema 变化和不支持的粒度不会用空数组伪装成成功。

## 本地运行

```bash
cd market-data-service
uv sync --extra dev --extra ecosystem
cp .env.example .env
uv run uvicorn finscope_market_data.app:app --host 127.0.0.1 --port 8000
```

接口文档：`http://127.0.0.1:8000/docs`

常用接口：

```text
GET /health
GET /ready
GET /v1/providers/health
GET /v1/stocks/SH/600519/quote
GET /v1/stocks/SH/600519/daily-bars?limit=120
GET /v1/stocks/SH/600519/capital-flow
GET /v1/stocks/SH/600519/capital-flow?require_minute=true
GET /v1/stocks/SH/600519/profile
GET /v1/stocks/SH/600519/overview
POST /v1/quant/stock-discoveries
```

股票发现接口默认使用 6000 元预算上限、5 日预测周期和最多 5 只最终候选。预算仅判断是否可以买入一手，不参与优势评分。服务会先量化所有合格候选，再对前 15 名运行完整预测；未通过样本外或模型健康门禁时，最终结果可以少于 5 只。

`/health` 只表示进程存活；`/ready` 还会检查快照库可写且至少配置了一个 Provider。运维就绪探针应使用 `/ready`。

## Qlib 研究边界

在线预测不安装、不导入也不调用 Qlib。`forecast.qlib_reference` 只提供前复权日线 CSV 导出和安装状态检查，供未来在独立 Python 环境中做离线基准复核；Qlib 的结果不会直接覆盖 FinScope 的锁定测试结论。

## Java 接入

Java 中的 `PythonMarketDataCapitalFlowProvider` 会固定注册到现有 `MarketDataGateway`，不提供关闭开关。启动 Java 前应先启动 Python 服务：

```bash
export FINSCOPE_PYTHON_MARKET_DATA_BASE_URL=http://127.0.0.1:8000
cd backend
mvn -pl finscope-web -am spring-boot:run
```

Python Provider 的优先级固定高于 Java 东方财富直连；Python 服务停机、超时或返回 503 时，网关继续尝试现有 Java Provider。Java 请求资金行为时带 `require_minute=true`，避免把只有日级数据的来源冒充 5 分钟资金流。`base-url` 仍可配置，以适配 Docker 服务名或云端内网地址。

## Docker / 云部署

```bash
docker build -t finscope-market-data ./market-data-service
docker run --rm \
  -p 127.0.0.1:8000:8000 \
  -v finscope-market-data:/app/data \
  finscope-market-data
```

Docker Compose 中 Java 使用服务名：

```text
FINSCOPE_PYTHON_MARKET_DATA_BASE_URL=http://market-data:8000
```

生产环境不要将 8000 端口暴露到公网；只允许 Java 服务通过本机或容器内网访问，并持久化挂载 `/app/data`，否则重启后会失去最近成功快照。

## 测试

```bash
uv run pytest -q
```

Provider 单元测试使用固定响应，不依赖外网；真实接口只作为冒烟测试，避免上游临时抖动导致持续集成随机失败。
