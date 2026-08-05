# FinScope

FinScope 是一个本地优先的个人投资研究工作台。它把公开信息获取、候选筛选、文章研究、事件与证据整理、命题研究、知识沉淀，以及自选股、财报、资金行为和量化实验放在同一个可追溯的工作流中。

项目面向个人学习、求职项目展示和长期研究素材积累，不是行情交易终端，也不包含公共热点榜单、运营后台、多用户协作或企业发布链路。研究结论和模型输出仅用于辅助分析，不构成投资建议。

## 当前产品范围

### 信息获取与研究

- 管理 RSS、普通网页等信息源，支持定时抓取、批次记录和推荐新闻源。
- 在 Intake 中审阅候选内容，再决定是否提升为正式文章；耗时任务可异步执行并查看进度。
- 手动录入普通网页或 X/Twitter status URL 时，按 URL 选择合适的 `SourceAdapter`。X 长文优先通过公开 JSON 适配器解析正文。
- 使用 URL 指纹、标题归一化和正文 SimHash 识别重复内容，并记录重复、后续进展或新事件的判断依据。
- 将文章转换为金融资讯、研究论文或社媒长文情报卡；模型不可用时使用确定性规则兜底，不阻断入库。
- 生成每日 Markdown 简报，并围绕研究命题执行异步研究、保存发现和证据、合成研究报告。
- 自适应研究智能体先建立 Research Contract，再由受校验的 Planning Agent 生成任务 DAG；模型计划不合法或不可用时自动切换到确定性计划。
- 命题研究进入 Observation-driven Decision Loop：每轮基于工作记忆、证据缺口和上一轮 Observation 动态选择搜索、评估、局部重规划或完成请求，不再执行预先写死的搜索轮次。
- 类型化 Research Tool Registry 只开放来源扫描、公开新闻搜索、证据判断和报告合成四种受控能力，模型不能直接执行 SQL、Shell 或任意 HTTP。
- Evidence Gap 根据有效证据、独立来源和正反覆盖决定继续搜索或提前收束，不再依赖固定轮次；ResearchTab 用真实持久化状态展示任务图、活动节点和缺口变化。
- Agent State、Decision 与 Observation 追加写入 SQLite；独立 Finish Verifier 决定是否允许生成报告，规则降级、重复动作、无进展和 Finish 拒绝均可恢复、可审计。
- Deep Research Runtime 为命题研究保存 SQLite 检查点与单调事件流，限制动作预算、重复动作和无进展循环；进程中断后可从已完成节点继续。
- Eval Harness 对运行状态、报告、证据、来源和 Agent 决策轨迹做确定性离线评分，额外计算决策有效率、观察跟进率、重复/无进展率、重规划成功率、首次完成率和降级率。
- ResearchTab 的“Agent 决策流”以服务端真实状态成对展示预期动作与实际 Observation，并明确标识局部重规划、规则降级、无新增和完成校验拒绝。
- 将文章聚类为事件档案，维护事件状态、关联文章和证据账本。
- 研究雷达把跨来源快讯聚合成“正在发生的事”，按新意、自选直接相关性、独立来源、来源质量和时效五项固定规则排序，并展示判断依据、信息缺口和下一观察点。
- 研究雷达是个人使用的短期发现层，不是公共热榜：没有运营策略后台、Prompt 配置或模型选择；自动刷新发现的新内容仍由用户确认后再插入列表。

### 知识与内容沉淀

- 从文章、简报和研究结果生成主题、术语、学习问题与待办任务。
- 在 Knowledge 中管理知识主题、复习计划、证据和学习草稿。
- 将长期内容投影为 `vault/` 下的 Markdown，包括每日简报、主题笔记和研究报告。
- 在 Studio 中管理内容选题及其流转状态。

### 标的、行情与决策研究

- Watchlist：维护股票、基金等关注标的，展示行情、指数和板块信息，并运行标的归因研究。
- Market Intel：刷新个股资金流、资金行为信号和龙虎榜信息，生成带证据引用的结构化解读。
- Financials：维护公司报告期数据，上传财报或研报文档，生成财报解读并回溯原文证据。
- Strategy：管理持仓、策略手册、个股研究命题和复盘记录。
- Quant：管理研究数据集、行情同步、数据质量、因子分析、策略草稿和实验结果；资金信号可以冻结为可复现的因子研究输入。

### 可靠性与可观测性

- Agent Runs 保存模型节点、输入指纹、状态、耗时和错误信息。
- 异步任务和研究运行提供状态查询或 SSE 进度流；进程重启时将运行时标记为可恢复的 `INTERRUPTED`，避免从头重复外部动作。
- `/api/**` 使用统一响应信封、业务错误码和 `traceId`；请求日志通过 MDC 串联。
- 行情网关记录 Provider 尝试、数据来源、时间戳和降级状态，使用缓存、备用源竞速、总预算、熔断和快照兜底控制免费数据源的不稳定性。

## 技术栈与存储选择

- 后端：Java 8、Spring Boot 2.7、Maven 多模块、SQLite、Jsoup、Rome RSS。
- 前端：React 18、TypeScript、Vite 5、Vitest。
- 缓存：Redis 7，用于研究资料和热点查询的可失效加速缓存，SQLite 仍是主数据源。
- 行情侧车：Python 3.11+、FastAPI、AkShare、pytdx、httpx，推荐通过 Docker 运行。
- AI：OpenAI 兼容 Chat Completions 客户端、多个领域 Agent、统一运行留痕。
- 持久化：SQLite 主库、Markdown Vault、抓取原文与上传文档。

当前继续使用 SQLite，不引入 MySQL。FinScope 是单用户、本机运行的应用，SQLite 的单文件迁移、低运维成本和本地优先特性更适合当前阶段；项目已经启用 WAL、外键、连接池限制和 busy timeout。只有未来出现多用户并发写入、远程共享数据库或独立服务扩容需求时，才有必要评估 PostgreSQL/MySQL。

Docker Compose 可以一次启动 Redis、Java 后端、React/Nginx 前端和 Python 行情侧车。SQLite 与 Markdown 数据通过 `./data` 挂载到后端容器，Redis 使用独立卷；Redis 重启或暂时不可用时，应用会回退到原有实时抓取链路。

## 系统结构

```text
Browser :5173
    │ /api proxy
    ▼
Spring Boot :8080
    ├── SQLite: $FINSCOPE_DATA_ROOT/finance.db
    ├── Markdown/files: $FINSCOPE_DATA_ROOT/vault|raw|financials|exports
    ├── Redis :6379 (研究资料加速缓存)
    ├── RSS / Web / X / search / model providers
    └── Python market-data :8000
            ├── Tencent / Sina / Eastmoney / AkShare / pytdx
            └── provider snapshot SQLite in Docker volume

Docker Compose
    └── Nginx :5173 -> Spring Boot :8080 -> Redis / Python market-data
```

后端是模块化单体，依赖方向为 `web -> service -> dao/rpc -> domain/common`。Controller 不直接调用 Repository，外部访问统一放在 `finscope-rpc` 或独立行情服务中。

```text
fin-scope/
  backend/
    finscope-common/    通用工具、API 响应模型
    finscope-domain/    领域模型与跨层数据结构
    finscope-dao/       SQLite Repository、Schema 初始化
    finscope-rpc/       RSS/Web/X、行情、搜索和模型适配器
    finscope-service/   业务编排、Agent、研究、Vault 与导出
    finscope-web/       REST API、配置与应用装配
  frontend/             React/Vite 工作台
  market-data-service/  Python A 股行情获取、标准化与多源降级
  docs/                 产品需求、技术方案、架构说明与路线图
  data/                 仓库内保留的静态目录；不是当前默认运行数据库位置

../data/                默认本地运行数据，位于仓库同级
  finance.db
  vault/
  raw/
  financials/
  exports/
```

更细的后端调用关系见 [docs/架构说明.md](docs/架构说明.md)，行情服务契约见 [market-data-service/README.md](market-data-service/README.md)。

### Adaptive Research Agent、Runtime 与 Eval Harness

ResearchTab 中的深度研究使用 Java 原生编排，不额外引入 Python Agent 框架。LLM 只提出候选任务图，服务端在执行前完成字段、工具白名单、依赖和 DAG 校验；Validator、Tool Registry、Runtime 和 Evidence Gate 共同掌握执行权：

```text
ResearchService
  -> ResearchPlanningAgent
       -> validated task DAG or deterministic fallback
  -> ResearchMissionService
       -> contract / task state / gap snapshots
  -> ResearchAgentLoopService
       -> ResearchAgentContextBuilder
       -> ResearchDecisionAgent
            -> strict JSON + policy validation
            -> model decision or deterministic fallback
       -> ResearchToolDispatcher
            -> public_news_search / evidence_assess
       -> ResearchToolObservation
       -> ResearchAgentStateReducer / local plan patch
       -> ResearchFinishVerifier
  -> ResearchToolRegistry
       -> source_scan / public_news_search
       -> evidence_assess / report_synthesis
  -> ResearchRuntimeService
       -> checkpoint（当前阶段、节点、版本、预算、恢复次数）
       -> event stream（节点开始/完成/失败、状态哈希、进展量）
       -> guard（预算耗尽、重复动作、连续无进展）
  -> ResearchEvidenceGapAnalyzer
       -> evidence / source / support / counter gates
  -> ResearchReport
  -> ResearchEvaluationService
       -> completion / evidence / source diversity
       -> trace integrity / budget safety / recovery
       -> Agent trajectory quality
       -> input fingerprint + PASS/BLOCK
```

运行详情中的“研究作战图”显示研究合同、计划来源、当前任务和证据缺口；其后的“Agent 决策流”展示当前子目标、工作记忆、剩余预算，以及 Decision 与 Observation 的实际配对。页面轮询的都是 SQLite 中的真实状态，不生成伪进度。相关接口：

- `GET /api/research/tools`：读取允许进入研究计划的类型化工具契约。
- `GET /api/research/runs/{id}/mission`：读取合同、任务 DAG、Gap Snapshot 和本次工具摘要。
- `GET /api/research/runs/{id}/runtime`：检查点与事件序列。
- `POST /api/research/runs/{id}/resume`：通过版本检查抢占恢复权，从未完成节点继续。
- `POST /api/research/runs/{id}/evaluations`：对当前持久化快照执行离线评测。
- `GET /api/research/runs/{id}/evaluations/latest`：读取最近评测结果。
- `GET /api/research/runs/{id}`：聚合运行、Mission、Runtime、Agent State、Decision、Observation 和轨迹指标；旧运行的 `agentCore` 为空。

Observation 驱动决策内核见 [决策内核 PRD](docs/产品需求-研究智能体决策内核.md)、[决策内核技术方案](docs/技术方案-研究智能体决策内核.md) 与 [实施计划](docs/superpowers/plans/2026-07-27-research-agent-core.md)。前一阶段的自适应规划与证据闭环见 [产品需求](docs/产品需求-自适应研究智能体与过程可视化.md)；Runtime 和 Eval 的设计边界见 [Runtime 产品需求](docs/产品需求-Deep-Research-Runtime与Eval-Harness.md)。

## 本地启动

### 环境要求

- JDK 8。项目当前以 Java 8 为统一的编译和运行基线。
- Maven 3.8+。
- Node.js 20+，推荐 Node.js 22；使用仓库内 `package-lock.json` 安装依赖。
- Docker Desktop，用于 Python 行情服务。
- Docker Compose v2，用于一键启动完整本地栈。
- 可选：Python 3.11-3.13 与 `uv`，仅在不使用 Docker 运行行情服务时需要。

以下命令均假设终端当前位于仓库根目录 `fin-scope/`。

macOS 安装了多个 JDK 时，先让当前终端和 Maven 都使用 JDK 8，并用 `mvn -version` 确认实际运行时：

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 1.8)"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
```

项目显式声明了 `javax.annotation` 兼容 API，避免依赖某个 JDK 发行版的隐式类路径；仍建议统一使用 JDK 8，以便本地运行行为与 CI 编译目标一致。

### Docker Compose 一键启动（推荐）

Compose 使用仓库内的 `data/finance.db` 作为主库。首次启动前，请确认这个文件已经从现有本地数据目录复制到 `fin-scope/data/finance.db`；应用会拒绝在错误路径静默创建空库。

```bash
test -f data/finance.db
docker compose up --build
```

启动后访问 `http://localhost:5173`。后端、Redis 和行情侧车分别可通过 `http://localhost:8080`、`127.0.0.1:6379` 和 `http://localhost:8000/ready` 检查。后台运行可使用：

```bash
docker compose up --build -d
docker compose logs -f backend
docker compose down
```

`docker compose down` 不会删除 Redis 和行情侧车卷；如需清理缓存卷，再执行 `docker compose down -v`。

### 1. 准备本地数据目录

当前默认布局将实时数据放在仓库同级的 `../data/`。在本机对应：

```text
/Users/你的用户名/code/javaProject/
  fin-scope/
  data/
    finance.db
```

已有数据库时，将整个数据目录迁移到这里。首次空白运行可以先创建数据库文件，Schema 会在应用启动时自动初始化：

```bash
mkdir -p ../data
sqlite3 ../data/finance.db 'PRAGMA journal_mode=WAL;'
```

也可以显式指定现有数据目录；该变量必须是绝对路径：

```bash
export FINSCOPE_DATA_ROOT="$(cd .. && pwd)/data"
```

启动器会从仓库根目录或其子目录识别项目位置，并默认解析到仓库同级 `data/`。如果目录或 `finance.db` 不存在，应用会拒绝启动，避免 SQLite 在错误位置静默创建第二个空库；启动日志会打印最终数据库绝对路径。

### 2. 启动行情服务（Docker，推荐）

首次构建并启动：

```bash
docker build -t finscope-market-data:local ./market-data-service
docker run -d \
  --name finscope-market-data \
  --restart unless-stopped \
  -p 127.0.0.1:8000:8000 \
  -v finscope-market-data:/app/data \
  finscope-market-data:local
curl http://127.0.0.1:8000/ready
```

容器已创建时只需：

```bash
docker start finscope-market-data
```

`/health` 只检查进程存活，`/ready` 还会检查快照库可写和 Provider 配置。端口只绑定本机，持久卷保存最近成功快照。

### 3. 启动后端

```bash
export FINSCOPE_DATA_ROOT="$(cd .. && pwd)/data"
cd backend
mvn -pl finscope-web -am package -DskipTests
java -jar finscope-web/target/finscope-web-0.1.0-SNAPSHOT.jar
```

后端地址为 `http://localhost:8080`。打包后运行可避免 Maven 多模块场景中直接执行 `spring-boot:run` 时选错模块入口。

在 IntelliJ IDEA 中运行时：

- Project SDK 和 Maven Runner JRE 都选择 JDK 8。
- Main class 使用 `com.finscope.web.FinScopeApplication`。
- Working directory 可以设为仓库根目录或 `backend/`。
- 建议在 Run Configuration 中加入绝对路径 `FINSCOPE_DATA_ROOT`，避免不同启动配置指向不同数据目录。

### 4. 启动前端

打开另一个终端：

```bash
cd frontend
npm ci
npm run dev
```

默认访问 `http://localhost:5173`。如果端口占用，Vite 会选择下一个可用端口；前端将 `/api` 代理到 `http://localhost:8080`。

### 不使用 Docker 运行行情服务

```bash
cd market-data-service
uv sync --extra ecosystem --extra dev
uv run uvicorn finscope_market_data.app:app --host 127.0.0.1 --port 8000
```

## 模型、搜索与行情配置

FinScope 使用 OpenAI 兼容 Chat Completions 接口，不绑定具体模型服务商。当前个人本地部署的 LLM 和搜索凭证沿用 `backend/finscope-web/src/main/resources/application.yml` 中的既有配置约定，README 不记录或复制凭证值。若要公开分发项目，应先轮换凭证并单独设计密钥管理方案。

常用运行参数可以通过环境变量覆盖：

```bash
export FINSCOPE_LLM_ENABLED=true
export FINSCOPE_LLM_BASE_URL=https://你的模型服务/v1
export FINSCOPE_LLM_MODEL=你的模型名
export FINSCOPE_SEARCH_ENABLED=true
export FINSCOPE_SEARCH_PROVIDER=tavily
export FINSCOPE_PYTHON_MARKET_DATA_BASE_URL=http://127.0.0.1:8000
export FINSCOPE_CORS_ORIGIN=http://localhost:5173
export FINSCOPE_QUANT_MARKET_DATA_SYNC_CRON='0 30 18 * * MON-FRI'
```

模型调用失败时，文章入库等主流程会保留结果并使用确定性兜底。搜索或行情外部服务不可用时，接口会返回明确的失败、部分成功或快照降级状态，不用空数据伪装成功。

行情可靠性参数：

```bash
export FINSCOPE_MARKET_DATA_FRESH_CACHE_MS=15000
export FINSCOPE_MARKET_DATA_HEDGE_DELAY_MS=300
export FINSCOPE_MARKET_DATA_REQUEST_BUDGET_MS=5000
export FINSCOPE_MARKET_DATA_MAX_FALLBACK_AGE_SECONDS=120
export FINSCOPE_MARKET_DATA_WARMUP_ENABLED=true
export FINSCOPE_MARKET_DATA_WARMUP_INTERVAL_MS=10000
```

免费公开行情源没有 SLA。盘中在线源全部失败时，只接受规定时间内的快照；午休和收盘后可以展示最后收盘事实，但会保留数据时间和降级标记。

## API 约定

除 SSE、文件流和 `204 No Content` 外，所有 `/api/**` JSON 接口都返回统一信封：

```json
{
  "success": true,
  "code": "FS-0000",
  "message": "成功",
  "data": {},
  "traceId": "4b6f...",
  "timestamp": "2026-07-24T12:00:00Z"
}
```

错误响应使用相同结构，并保留正确的 HTTP 状态：

- `FS-1xxx`：参数、认证、权限和限流。
- `FS-2xxx`：资源不存在、业务冲突、重复操作和版本冲突。
- `FS-3xxx`：外部服务、行情和模型服务异常。
- `FS-4xxx`：数据库、文件、异步任务和数据完整性异常。
- `FS-5000`：未分类的系统内部异常。

客户端可传入 `X-Request-Id`；服务端校验后将其用于响应 `traceId` 和日志 MDC，未传入时自动生成。

## 验证命令

```bash
cd backend && mvn test
cd frontend && npm test
cd frontend && npm run build
cd market-data-service && uv run pytest -q
```

Python Provider 单元测试使用固定响应，不依赖外网；真实免费接口只适合作为手工冒烟测试。

## 数据迁移与安全

- 实时主数据位于 `$FINSCOPE_DATA_ROOT`，不要只复制仓库目录后就认为数据已经迁移。
- 迁移前先停止 Java 后端，再复制整个数据目录；SQLite 使用 WAL 时，运行中只复制 `finance.db` 可能遗漏尚未 checkpoint 的数据。
- Settings 可以导出包含 SQLite、Vault 和 manifest 的 ZIP；导入接口当前仍是 MVP 占位能力，不能依赖它自动恢复。
- `finance.db`、WAL/SHM、抓取原文、上传文档、导出包、本地环境文件和凭证不应新增到 Git。
- 不要把公司内部数据、代码、专有 Prompt、私有文档或新的密钥提交到仓库。

项目内更详细的产品和技术背景位于 [docs/](docs/)。
