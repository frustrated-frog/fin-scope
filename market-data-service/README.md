# FinScope Market Data Service

独立的 A 股数据获取服务。它利用 Python 数据生态获取并标准化数据，Java 只依赖稳定 HTTP 契约，不直接感知 AkShare、pytdx、腾讯、新浪或东方财富字段。

## 已提供的数据

| 能力 | 主要字段 | 数据源顺序 |
|---|---|---|
| 实时行情 | 最新价、昨收、开高低、涨跌额/幅、成交量、成交额、买卖价 | 腾讯 → 新浪 → 东方财富 |
| 历史日 K | 前复权 OHLC、成交量/额、振幅、涨跌幅、换手率 | AkShare → 东方财富 → pytdx |
| Market Pulse 指数日 K | 上证、深证、创业板、沪深 300、中证 1000 的日线 | 东方财富指数接口 → 新浪指数接口；不再混用普通股票接口 |
| 资金流 | 分钟/日级主力、超大单、大单、中单、小单净流入及占比 | AkShare（日级）→ 东方财富（分钟+日级） |
| 个股资料 | 名称、行业、上市日期、股本、PE、PB、总/流通市值、扩展字段 | AkShare → 东方财富 |
| A 股市场宽度 | 上涨/下跌/平盘家数、上涨比例、成交额、涨跌停家数、涨跌幅中位数 | 东方财富全 A → 新浪全 A → 同业务日快照；同花顺提供行业上涨比例 |
| 自动股票发现 | 热门板块、板块成分、预算准入、全候选量化与深度预测 | 同花顺 1 日行业唯一热榜；同花顺成分 → 东方财富补全 → 完整快照 |

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
uv run scrapling install
cp .env.example .env
uv run uvicorn finscope_market_data.app:app --host 127.0.0.1 --port 8000
```

接口文档：`http://127.0.0.1:8000/docs`

常用接口：

```text
GET /health
GET /ready
GET /v1/providers/health
GET /v1/markets/CN-A/breadth?business_date=2026-08-21
GET /v1/stocks/SH/600519/quote
GET /v1/stocks/SH/600519/daily-bars?limit=120
GET /v1/stocks/SH/600519/capital-flow
GET /v1/stocks/SH/600519/capital-flow?require_minute=true
GET /v1/stocks/SH/600519/profile
GET /v1/stocks/SH/600519/overview
POST /v1/quant/stock-discoveries
```

市场宽度接口使用固定契约 `market-breadth-v1`。全 A 主体先读东方财富，失败时切换新浪；涨跌停池独立读取，池数据失败只会将质量降为 `PARTIAL_FRESH`，不会丢弃已经取得的涨跌家数和成交额。在线全 A 来源均失败时只接受同一业务日的 SQLite 快照，避免把不同交易日拼成一份市场判断。已结束业务日的精确快照会直接返回，避免每次页面刷新都重新等待慢速公共接口。所有 `retrieved_at` 都带 Asia/Shanghai 时区偏移，Java 会严格校验契约和业务日期。

同花顺用于行业级宽度：行业上涨、下跌、平盘家数及上涨比例随行业行情一起标准化。全市场宽度不依赖同花顺页面结构，因此同花顺采集异常不会同时破坏市场总体分布和行业截面。

股票发现接口默认使用 6000 元预算上限、5 日预测周期和最多 5 只最终候选。预算仅判断是否可以买入一手，不参与优势评分。服务会读取每只候选最多 1,500 根前复权日线，先量化所有合格候选，再对前 15 名运行完整预测；只有当前方向为上涨且通过样本外与模型健康门禁时才可能进入最终结果，因此结果可以少于 5 只。在线热门板块源全部失败时会读取最近一次成功且未超过 4 天的本地板块快照；空、过短、非同一业务日或停牌形成的陈旧行情会在候选级拒绝。

## 同花顺成分采集恢复

同花顺行业成分按成本从低到高使用 `DIRECT_HTTP -> SESSION_HTTP -> BROWSER`。直连页面有效时不会初始化 Scrapling；遇到登录重定向、空成分表或网络异常时，先复用 Scrapling HTTP Session，仍失败才启动并发上限为 1 的 Browser。三种模式返回的 HTML 都使用同一个成分解析器，覆盖率达到 95% 才能标记完整并进入候选池。

Browser 失败、超时或仍只能得到部分成分时，服务继续尝试东方财富完整补全，再读取 30 天内的完整成分快照；都不可用才跳过该板块。恢复路径会写入股票发现报告的 `warnings`，例如 `DIRECT_HTTP -> SESSION_HTTP`，便于判断当前数据是直连还是恢复所得。恢复不会改变热榜权威，`source_family` 仍如实保留同花顺或东方财富来源。

| 环境变量 | 默认值 | 作用 |
|---|---:|---|
| `FINSCOPE_MARKET_DATA_SCRAPLING_ENABLED` | `true` | 是否允许直连失败后升级到 Scrapling |
| `FINSCOPE_MARKET_DATA_SCRAPLING_SESSION_TIMEOUT_SECONDS` | `15` | Session HTTP 单次超时秒数 |
| `FINSCOPE_MARKET_DATA_SCRAPLING_BROWSER_TIMEOUT_SECONDS` | `20` | Browser 单次超时秒数 |
| `FINSCOPE_MARKET_DATA_SCRAPLING_BROWSER_MAX_CONCURRENCY` | `1` | Browser 恢复并发，允许范围 1-2 |
| `FINSCOPE_MARKET_DATA_SCRAPLING_IDLE_TIMEOUT_SECONDS` | `300` | Session/Browser 空闲回收秒数 |

本功能只允许访问 `https://q.10jqka.com.cn` 公共页面，不使用账号登录、验证码识别、代理池或访问控制绕过。未执行 `uv run scrapling install` 时，Browser 初始化失败会转为脱敏诊断并继续补全/快照降级，不会阻断其他板块。

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
