# 架构说明

FinScope 是一个模块化单体项目。后端采用 Maven 多模块结构，借鉴成熟 Java 项目的分层习惯，同时保持产品定位清晰：它不是热点排行系统、运营后台或企业发布链路。

## 后端分层

```text
finscope-web
  -> finscope-service
      -> finscope-dao
      -> finscope-rpc
  -> finscope-domain

finscope-dao -> finscope-domain + finscope-common
finscope-rpc -> finscope-domain
```

- `finscope-common`：通用工具，不承载业务编排。
- `finscope-domain`：文章、信息源、情报卡片、简报、主题、抓取运行、原始条目和 Agent 运行模型。
- `finscope-dao`：SQLite 仓储、表结构初始化和数据目录初始化。
- `finscope-rpc`：访问外部 RSS/Web/API 页面的信息源适配器，目前包含 RSS、静态网页和 X/Twitter status 适配器。
- `finscope-service`：用例编排、去重/新意判断、简报生成、Vault 写入、导出和查询服务。
- `finscope-web`：Spring Boot 启动入口、REST 控制器、CORS 和 Bean 装配。

预期调用方向是 `web -> service -> dao/rpc -> domain/common`。Controller 不直接调用 Repository；外部抓取统一收敛在 `SourceAdapter` 后面。

## 核心流程

```text
SourceAdapter
  -> RawItem
  -> Article
  -> FingerprintService
  -> NoveltyService
  -> InsightCard
  -> Inbox
  -> BriefGenerator
  -> VaultWriter

Article or Brief
  -> ArticleInterpretationAgent / TopicExtractor fallback
  -> TopicService
  -> SQLite links + Markdown topic notes
```

## 情报卡片流程

V2 之后，FinScope 不再把“简报”直接建立在松散文章摘要上，而是在文章入库后生成一层稳定的 `InsightCard`：

```text
RSS/Web/手动 URL
  -> RawItem
  -> ArticleIngestCoordinator
  -> Article + fingerprints + novelty decision
  -> InsightCardGenerator
  -> insight_card
  -> Inbox card view / Daily Brief / Topic pipeline
```

`Article` 保存原始抓取结果，`InsightCard` 保存固定格式认知资产，包括一句话摘要、核心事件、为什么重要、影响对象、新意判断和后续观察。当前版本已经有两层生成能力：未配置模型时使用确定性规则，并按内容类型区分金融资讯、研究论文和社媒长文；配置 OpenAI 兼容模型后，`ArticleInterpretationAgent` 会先输出结构化解读，再由 `InsightCardGenerator` 渲染成卡片。`ArticleIngestCoordinator`、DAO 和 Web API 不需要感知模型细节。

新增的手动 URL 入口走同一条链路：`POST /api/articles/ingest-url` 会抓取网页、写入文章池、计算去重与新意、生成情报卡片，并在 Inbox 中展示。手动 URL 默认仍可以保存为 `WEB` 类型，但 `SourceAdapterRegistry` 会先让 URL 感知型适配器抢占匹配，例如 X/Twitter status URL 会进入 `XPostSourceAdapter`，避免被普通静态网页解析成登录壳页。这样 RSS、普通网页、社媒长文和临时阅读材料都能变成统一格式的素材。

## 抓取适配器策略

```text
Source 或 Manual URL
  -> SourceAdapterRegistry
  -> URL-aware adapter first
  -> typed adapter fallback
  -> RawItem(title/url/summary/body/contentType/extractionMethod/qualityScore)
```

- `RssSourceAdapter`：使用带请求头的 HTTP 拉取 RSS/Atom，交给 Rome 解析，并保留作者、分类、摘要等结构化证据。
- `WebSourceAdapter`：用于静态 HTML 页面，基于 Jsoup 提取 `article/main/content` 区域，不执行 JavaScript。
- `XPostSourceAdapter`：识别 `x.com`/`twitter.com` 的 status URL，优先使用公开 JSON 适配器解析普通帖子或 X 长文正文，失败时再走备用接口。

新增站点时优先新增独立 Adapter，而不是在 `UrlIngestService` 中堆判断。Adapter 只负责把外部内容还原成可信的 `RawItem`，去重、新意、卡片和简报继续复用主链路。

## 知识沉淀流程

V3 增加了从短期信息到长期知识的沉淀路径：

```text
Article or Brief
  -> ArticleInterpretationAgent if configured
  -> TopicExtractor fallback
  -> Topic
  -> topic_article/topic_brief links
  -> Topic Detail
  -> appended personal notes in data/vault/topics/
```

`TopicRepository` 负责 SQLite 中的主题元数据和关联关系。`VaultWriter` 负责 Markdown 的读写。Web 层只调用 `TopicService`，所以未来可以把主题提取从确定性关键词升级为 LLM/Agent 节点，而不需要改 Controller。

## 文章解读 Agent

```text
Article
  -> ArticleInterpretationAgent
  -> LlmChatClient(OpenAI compatible)
  -> structured JSON
  -> InsightCard / TopicExtraction
  -> AgentRunRepository trace
```

Agent 节点名固定为 `article-interpret`。输入是文章标题、来源、URL、摘要和正文片段；输出要求是 JSON，包含 `contentType`、`topicName`、`topicDescription`、`oneSentenceSummary`、`coreEvent`、`importance`、`impactTargets`、`keyTerms`、`learningQuestions` 和 `confidence`。这层负责“解读和整理”，抓取、去重、入库和 Markdown 写入仍然保持确定性。

LLM 出口在 `finscope-rpc` 的 `LlmChatClient` 后面，目前实现为 `OpenAiCompatibleLlmClient`。配置入口在 `finscope-web` 的 `FinScopeProperties`，通过 `FINSCOPE_LLM_ENABLED`、`FINSCOPE_LLM_BASE_URL`、`FINSCOPE_LLM_API_KEY`、`FINSCOPE_LLM_MODEL` 注入。模型不可用或返回非法 JSON 时，Agent 会记录失败 trace 并回落到 `TopicExtractor` + `InsightCardGenerator` 的确定性结果。

## 扩展边界

- `SourceAdapter`：后续可以新增 RSS、Web、API 或平台专属信息源，而不改变抓取编排；URL 感知型适配器可以覆盖通用 `WEB` 类型。
- `SourceService` 和查询服务：保持 Web Controller 轻量，把用例决策留在 REST 层之外。
- `FingerprintService`：后续可从 URL/标题/SimHash 演进到 Embedding。
- `NoveltyService`：负责跨天重复、后续进展和新事件判断。
- `ArticleInterpretationAgent`：负责调用 LLM 做文章解读、主题命名、术语和学习问题整理，并记录 `agent_run`。
- `LlmChatClient`：OpenAI 兼容模型出口，隔离 baseUrl、API Key、model 和 Chat Completions 协议。
- `InsightCardGenerator`：负责把文章转成固定格式情报卡片，当前支持金融资讯、研究论文、社媒长文三种确定性模板，也可以消费 Agent 的结构化解读结果。
- `BriefGenerator`：负责确定性的 Markdown 简报结构，优先使用情报卡片内容。
- `VaultWriter`：隔离 Markdown 持久化和数据库持久化。
- `TopicExtractor`：从文章或简报中提取初始主题名、术语和学习问题；也是 Agent 不可用时的兜底。
- `AgentRunRepository`：在完整 Agent 编排引入前，先记录 AI/工作流可观测信息。

## 前端页面

- Dashboard：统计概览和最近抓取运行记录。
- Sources：信息源配置和手动抓取。
- Inbox：抓取文章池、手动 URL 生成情报卡片、新意原因展示、文章到主题的沉淀入口。
- Briefs：每日简报生成、简报到主题的沉淀入口。
- Topics：主题卡片、关联数量、术语和 Vault 路径。
- Learning：主题详情、关联文章/简报、学习问题和个人笔记追加。
- Agent Runs：可观测工作流 Trace 列表。
- Settings：本地导出入口。

## 与热点生产系统的差异

FinScope 不做公共热点排行，也不支撑运营发布工作流。它是个人信息摄入、学习和知识沉淀工具。分层、可追踪、标准化、分阶段处理等工程思想只作为通用软件架构方法使用。
