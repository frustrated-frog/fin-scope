# TRD：FinScope 标的归因研究系统

## 1. 文档信息

- 项目名称：FinScope
- 文档类型：Technical Requirements Document
- 目标版本：V6（标的视角扩展）
- 文档状态：Draft
- 目标用户：关注 A 股 / 国内公募基金基本面的个人投研学习者、自媒体内容创作者、求职项目展示者

## 2. 背景与问题

FinScope 当前以「内容视角」组织信息：事件簇（EventCluster）、主题（Topic）围绕宏观、AI 创业等内容维度展开。但用户在做基本面分析时，真实诉求是「标的视角」：

> 我关注的这只股票 / 基金 / 板块，今天为什么涨 / 跌？

现存瓶颈：

1. 缺少标的实体：系统没有把股票 / 基金 / 板块作为一等公民建模，无法围绕标的组织行情与新闻。
2. 归因信息分散：解释一次涨跌需要综合多篇新闻，而这些新闻分散在不同网站，用户无法在一个地方高效关联。
3. 新闻源贫瘠：现有抓取源以宏观 / AI 为主，个股级新闻覆盖不足，标签混乱，纯本地匹配天花板低。
4. Agent 能力未充分体现：现有 Agent 偏单步解读，缺少「主动研究」的多步工作流展示。

## 3. 产品定位

本功能把 FinScope 从「信息聚合器」升级为「AI 投研研究员」：

> 给定一个标的与其行情异动，派出一个 Agent 研究员，主动全网调研 + 综合本地新闻 + 产业链推理，输出可回溯、诚实的基本面归因报告。

与普通行情 App 的差异：

| 维度 | 普通行情 App | FinScope 标的归因 |
| --- | --- | --- |
| 核心问题 | 涨了多少 | 为什么涨跌 |
| 信息组织 | 单条行情 + 资讯流 | 标的 + 行情 + 多源证据网 |
| 分析方式 | 人工翻新闻 | Agent 多步研究工作流 |
| 结果形态 | 数字 | 结构化归因（驱动因素 / 影响力 / 置信度 / 来源分级） |
| 诚实度 | 硬编解读 | 允许「今日无明显消息面驱动」 |

## 4. 目标与非目标

### 4.1 目标

1. 引入标的实体（Instrument）与自选面板（Watchlist），支持股票 / 基金 / 板块。
2. 接入 A 股 / 公募基金行情数据，在面板展示关注标的涨跌。
3. 提供手动触发的「深度归因」，以多步 Agent 工作流完成研究。
4. 归因过程通过 SSE 流式实时可见（问题拆解 → 搜索 → 线索 → 归因）。
5. 输出结构化归因报告，标注影响力、置信度、来源可信度分级。
6. 归因结果沉淀存储，可查看历史、可沉淀为主题笔记，复用现有 Topics/Vault。

### 4.2 非目标（本期）

1. 自动异动触发（收盘批量 / 盘中极端异动）——后期迭代。
2. 次日验证 / 归因复盘——数据模型预留，后期实现。
3. 标的基础库全量预置——改为按需拉取。
4. 投资建议 / 买卖信号——始终为教育型分析，非投资建议。
5. 实时逐笔行情、Level-2 数据。

## 5. 核心链路

```text
用户在 Watchlist 添加标的（按需拉取标的信息）
  -> 抓行情（QuoteAdapter）
  -> 面板展示涨跌
  -> 用户手动点「深度归因」
  -> 归因 Agent 工作流（SSE 流式）
      question-plan   拆解研究子问题
      web-search      全网搜索线索
      local-recall    检索本地已抓新闻
      chain-reason    按标的类型做产业链推理
      evidence-rank   去重 + 来源分级 + 相关度排序
      attribution-synth  综合生成结构化归因
  -> AttributionReport 存储
  -> 面板 / 详情页展示归因 + 可沉淀为主题笔记
```

每个 Agent 节点写入 `agent_run` trace，保持现有可观测体系。

## 6. 数据模型（finscope-domain 新增）

```text
Instrument（标的实体，一等公民）
  code        600519 / 000001 / BK0477
  type        STOCK | FUND | SECTOR
  name        贵州茅台
  market      SH | SZ | (基金无)
  aliases[]   ["茅台","飞天","600519"]  用于新闻匹配
  sectorCode  所属板块（个股用）
  chainTags[] 产业链标签（LLM 生成，上下游）

Watchlist / WatchlistItem（自选面板）
  分组、排序、加入时间

Quote（行情快照）
  instrumentCode, date, close, changePct, volume, turnover

AttributionReport（归因报告）
  instrumentCode, date, changePct
  status       GENERATING | COMPLETED | FAILED
  summary      一句话归因
  drivers[]    驱动因素：
    claim          原因描述
    impactLevel    影响力 HIGH|MID|LOW
    confidence     置信度 HIGH|MID|LOW
    evidenceRefs[] 支撑证据（复用 EvidenceItem）
  disclaimer   诚实说明（允许"无明显驱动"）

（复用）EvidenceItem
  已含 sourceTier / claim，用于存储归因搜到的线索
```

来源可信度分级：

- Tier 1：交易所公告、央行 / 证监会、主流财经（财新 / 华尔街见闻）
- Tier 2：券商研报、知名媒体
- Tier 3：雪球 / 微博 / 自媒体（参考并标注，降权不隐藏）

## 7. 行情数据源（A 股 + 公募基金，公开免费）

| 用途 | 数据源 | 说明 |
| --- | --- | --- |
| A 股实时 / 日线 | 新浪 hq.sinajs.cn / 腾讯 qt.gtimg.cn | 稳定、无鉴权、轻量文本 |
| 板块行情 | 东方财富 push2.eastmoney.com | 行业 / 概念板块 |
| 公募基金 | 天天基金 fundgz（估值）/ eastmoney（净值） | 实时估值 + 历史净值 |
| 标的基础库 | 按需拉取（不全量预置） | 用户添加时按 code 拉取名称等 |

新增 `QuoteAdapter`，隔离在 finscope-rpc，复用 SourceAdapter 适配器模式思想。

## 8. Agent 研究工作流（研究 DAG）

| 节点 | 输入 | 职责 | 输出 |
| --- | --- | --- | --- |
| question-plan | 标的 + 行情异动 | 按标的类型套提问模板拆 2~4 子问题 | 问题清单 |
| web-search | 子问题 | 联网搜索 + 摘要 | 网络线索[] |
| local-recall | 标的别名 | 检索本地已抓新闻 | 本地新闻[] |
| chain-reason | 标的类型 | 个股看上下游 / 板块看政策 / 基金看重仓 | 关联维度 |
| evidence-rank | 全部线索 | 去重 + 来源分级 + 相关度排序 | 证据池 |
| attribution-synth | 证据池 | 生成结构化归因（影响力 / 置信度） | 归因报告 |

按标的类型的归因视角：

- 个股：公司自身新闻 + 产业链上下游 + 所属板块联动
- 板块：政策新闻 + 行业整体 + 龙头带动
- 基金：重仓行业 / 重仓股新闻 + 板块归因

兜底哲学：搜不到明确原因时，诚实报告"今日无明显消息面驱动，可能为板块 / 情绪联动"，绝不硬编。

## 9. 交互设计

### 9.1 Watchlist 面板

- 卡片 / 表格展示关注标的涨跌，红涨绿跌
- 标记：归因就绪（●）、关联新闻条数、异动提示（⚠）、板块分组
- 支持按涨跌幅排序、分组
- 标的添加：MVP 手动输代码，按需拉取信息；名称搜索后期加

### 9.2 归因研究过程（SSE 流式）

- 用户点「深度归因」→ 建立异步研究任务（复用 TaskPhase 思路）
- SSE 端点推送阶段进度 + 实时线索流（细粒度：每搜到一条线索冒一条）
- 阶段：识别标的 → 生成问题 → 全网搜索 → 分析产业链 → 综合归因

### 9.3 归因报告

- 结论先行（一句话归因）+ 驱动因素（按影响力排序，每条带影响力 / 置信度 / 支撑新闻）+ 诚实说明
- 操作：查看全部关联新闻、沉淀为主题笔记（复用 Topics/Vault）

## 10. 前端页面（finscope frontend）

- Watchlist 自选面板：新增 feature 目录，复用现有 api client
- 标的详情页：行情 + 今日归因报告 + 关联新闻 + 历史归因
- SSE 研究过程组件：流式线索展示

### 10.1 UI 零破坏约束（硬性要求）

现有页面 UI 由用户精调，实现时前端只做加法，严禁改动现有布局与样式：

1. 导航：仅在 AppShell navItems 数组追加 watchlist 项（code 14），不改现有项。
2. 路由：View 联合类型仅追加 'watchlist' / 'instrumentReader'，不改现有取值。
3. 页面：新建 features/watchlist/ 独立组件，不修改任何现有 feature 组件。
4. 数据：新增独立 useEffect / 接口拉取，不塞进现有 refresh() 的 Promise.all。
5. 样式：新样式一律用独立 class 前缀（如 .watchlist-*）追加到 styles.css 末尾，禁止修改或覆盖任何现有选择器。
6. 视觉：新页面复用现有 class（.panel / .market-chip / .studio-card 等）以自动对齐既有风格。
7. 后端接触点：归因沉淀为主题笔记时仅调用现有 TopicService，不修改其实现；不改动现有表结构与现有 Controller。

## 11. 分期落地

| 阶段 | 内容 | 价值 |
| --- | --- | --- |
| V1 面板骨架 | Instrument + Watchlist 模型、行情抓取、涨跌面板 | 看到关注标的涨跌 |
| V2 归因 Agent | 手动深度归因、SSE 流式过程、结构化报告 | 核心价值：回答"为什么" |
| V3 产业链增强 | LLM 生成产业链标签、语义关联增强 | 关联更准更广 |
| V4 自动化与复盘 | 自动异动触发、次日验证归因复盘 | 学习闭环 |

## 12. 已确认的产品决策

1. 市场范围：A 股 + 国内公募基金。
2. 归因诚实度：允许"没找到明确原因"。
3. 归因维度：影响力与置信度分离。
4. 归因粒度：按标的类型走不同视角（个股上下游 / 板块政策 / 基金重仓）。
5. 产业链标签：由 Agent/LLM 生成，体现 Agent 能力。
6. 触发模式：本期仅手动深度归因，自动异动触发后期加。
7. 过程可见：SSE 流式，细粒度线索流。
8. 标的基础库：按需拉取，不全量预置。
9. 归因报告：存储，支持历史查看与沉淀为主题笔记。
10. 证据存储：单独建 attribution_evidence 表，保持 event 体系纯净。

## 13. 实现层面已确认的技术决策

1. 联网搜索路线：路线 B——新增独立 WebSearchClient（接口先行，可切换实现），
   与 LlmChatClient 平级放在 finscope-rpc/search。首个实现 TavilyWebSearchClient。
   API Key 由环境变量注入，禁止硬编码。未配置时 web-search 节点跳过并兜底。
   搜索服务选型：Tavily（每月 1000 次免费、免信用卡、返回带摘要、专为 LLM 设计）。
2. SSE：新增独立 SSE 架构件（SseEmitter 注册表 + 事件发布/订阅），与现有轮询解耦；
   后台归因线程经事件总线发进度，SSE 端点订阅推送，任务结束自动关闭连接。
3. 证据存储：单独建 attribution_evidence 表（不复用 EvidenceItem 的 eventId 体系）。
4. 数据库迁移：沿用 DatabaseInitializer 的 CREATE TABLE IF NOT EXISTS + ensureColumn
   增量模式新增表，不破坏现有表结构。
5. LLM 扩展：新增流式能力供 SSE 使用；联网由 WebSearchClient 承担，不依赖模型自带联网。

## 14. 待定 / 风险

1. 搜索 API Key（Tavily）待用户提供后接入；未提供前 web-search 走兜底。
2. LLM API Key 当前硬编码在 application.yml，用户已确认本地个人项目暂不处理（注意勿提交至公开仓库）。
3. 行情数据源为非官方公开接口，需容错与限频处理。
4. 个股新闻源覆盖不足，V2 依赖全网搜索补齐，需评估搜索质量。