# AGENTS.md

本文件为 Codex（Codex.ai/code）在本仓库中处理代码时提供指导。

## 项目概览

FinScope 是一个本地优先的个人投资研究信息工作台。它用于建立稳定的信息获取渠道、在 Inbox 中检查抓取文章、识别跨日重复内容、生成每日研究简报，并将有价值的信息保存为可长期维护的 Markdown 知识笔记。

**核心原则：**
- 本项目有意避免公开热榜、运营干预后台和企业级发布链路
- 项目定位：个人学习、秋招项目展示，以及未来自媒体素材积累
- 中英文混合代码库（README/文档使用中文，代码使用英文）

## 架构

**后端：** Java 8、Spring Boot 2.7、Maven 多模块、SQLite、Jsoup、Rome RSS
**前端：** React、TypeScript、Vite
**存储：** `data/finance.db`（SQLite）和 `data/vault/` 中的 Markdown 文件
**AI 扩展：** 兼容 OpenAI 的 `LlmChatClient`、文章解读 Agent、`agent_run` 调用轨迹

### 模块结构

```
backend/
  finscope-common/    通用工具，不包含业务逻辑
  finscope-domain/    领域模型和 DTO
  finscope-dao/       SQLite Repository 和 Schema 初始化
  finscope-rpc/       外部信源适配器（RSS/Web/X/Twitter）
  finscope-service/   业务编排、去重、简报、知识库、导出
  finscope-web/       REST Controller 和应用装配
```

**依赖方向：** `web -> service -> dao/rpc -> domain/common`
- Controller 不应直接调用 Repository
- 外部抓取统一通过 `SourceAdapter` 实现

### 核心流程

**文章摄取：**
```
SourceAdapter -> RawItem -> ArticleIngestCoordinator
  -> Article + 指纹 + 新意判定
  -> InsightCardGenerator
  -> insight_card
  -> Inbox / Daily Brief / Topic 流程
```

**信源适配器策略：**
```
Source 或手动 URL
  -> SourceAdapterRegistry
  -> 优先使用 URL 感知适配器（例如用于 x.com URL 的 XPostSourceAdapter）
  -> 回退到类型适配器（RSS/WEB）
  -> RawItem(title/url/summary/body/contentType/extractionMethod/qualityScore)
```

**知识沉淀：**
```
Article 或 Brief
  -> ArticleInterpretationAgent（如果已配置 LLM）
  -> TopicExtractor 回退方案
  -> TopicService
  -> SQLite 关联关系 + data/vault/topics/ 中的 Markdown 笔记
```

**关键扩展点：**
- `SourceAdapter`：无需修改编排逻辑即可增加新的 RSS/Web/API 信源
- `ArticleInterpretationAgent`：基于 LLM 的文章解读，支持回退方案
- `InsightCardGenerator`：将文章转换为洞察卡片（支持确定性规则或 Agent 输出）
- `NoveltyService`：识别跨日重复、后续进展和新事件
- `VaultWriter`：隔离 Markdown 持久化与数据库持久化

## Git 提交规范

- 提交信息必须使用英文 Conventional Commit 类型，并在其后添加中文描述：`<type>: <中文描述>`
- 类型标识必须保留英文，例如 `feat`、`fix`、`docs`、`refactor`、`test`、`chore` 或 `perf`
- 冒号后的主题应使用简洁中文，不得使用英文主题
- 正确示例：`feat: 增加有界研究模式`、`fix: 修复研究证据评测遗漏`、`docs: 补充研究证据深度方案`
- 错误示例：`feat: add bounded research modes`、`修复: 研究证据评测遗漏`
- 编码过程中，每完成一批可独立验证的改动，就应立即提交并推送到当前分支；不得将所有提交和推送拖延到任务结束时集中处理
- 除非有明确要求在当前分支开发，否则所有修改提交都应从 `main` 分支拉一个新分支进行开发

## 常用命令

### 后端

```bash
# 启动后端（从项目根目录执行）
cd backend
mvn -pl finscope-web -am spring-boot:run

# 运行全部后端测试
cd backend && mvn test

# 运行指定模块测试
cd backend && mvn -pl finscope-service test
```

后端默认运行在 `http://localhost:8080`。

### 前端

```bash
# 安装依赖
cd frontend && npm install

# 启动开发服务器
cd frontend && npm run dev

# 运行测试
cd frontend && npm test

# 生产构建
cd frontend && npm run build
```

前端默认运行在 `http://localhost:5173`（如果端口被占用，则使用 5174）。
前端将 `/api` 代理到 `http://localhost:8080`。

### LLM/Agent 配置

本项目使用兼容 OpenAI 的 Chat Completions 接口，不绑定特定供应商。当前本地部署有意将 LLM 和搜索 API Key 固定在 `backend/finscope-web/src/main/resources/application.yml` 中。除非用户明确要求迁移，否则不得将任一 `api-key` 替换为环境变量表达式。

其他运行时设置仍可通过环境变量覆盖：

```bash
export FINSCOPE_LLM_ENABLED=true
export FINSCOPE_LLM_BASE_URL=https://your-model-service/v1
export FINSCOPE_LLM_MODEL=your_model_name
```

启用后：
- 新文章通过 `article-interpret` Agent 节点生成洞察卡片
- 每次调用都会在 Agent Runs 页面记录节点、状态、耗时和错误信息
- 如果模型调用失败，系统仍会保留抓取流程，并使用确定性回退方案

## 关键实现细节

### 文章摄取流程

`ArticleIngestCoordinator.ingest()` 负责编排：
1. 根据 `RawItem` 创建 Article
2. 生成 URL 指纹、标题归一化结果和正文 simhash
3. 通过 `NoveltyService` 进行新意判定
4. 通过 `InsightCardGenerator` 生成洞察卡片

### 信源适配器

以下三个适配器实现了 `SourceAdapter` 接口：
- `RssSourceAdapter`：通过 Rome 处理 RSS/Atom Feed
- `WebSourceAdapter`：通过 Jsoup 处理静态 HTML（不执行 JavaScript）
- `XPostSourceAdapter`：处理 X/Twitter 状态 URL，并优先使用面向 X 长文的公开 JSON 适配器

增加新站点时，应创建新的适配器，不要向 `UrlIngestService` 中添加逻辑。

### 洞察卡片

`InsightCardGenerator` 支持三种确定性模板：
- 财经新闻卡片
- 研究论文卡片
- 社交媒体长文卡片

它也可以消费 Agent 输出的结构化解读结果。

### 知识库结构

Markdown 文件存储在 `data/vault/` 中：
- `daily-briefs/`：每日简报 Markdown 文件
- `topics/`：主题知识笔记
- `terms/`：术语定义
- `learning-path/`：学习路径笔记

## 数据安全

**Git 已忽略：**
- `data/finance.db`（SQLite 数据库）
- `data/raw/`（原始抓取内容）
- `data/exports/`（导出包）
- `.env` 和 `*.local` 文件
- 除两个有意固定的本地 LLM/搜索条目以外的其他 API Key

**严禁提交：** 公司内部数据、代码、凭据、专有 Prompt 或私有文档。现有两个固定的本地 LLM/搜索 Key 是明确的项目约定；不得打印、复制或移动其值。

## 重要说明

- 处理信源适配器时，URL 感知适配器的优先级高于类型适配器
- Agent 回退方案是确定性的；即使 LLM 不可用，系统也绝不会阻断 Inbox 流程
- 所有外部 API 调用都应通过 `finscope-rpc` 模块
- SQLite 数据库路径是相对路径：从后端运行目录访问 `../data/finance.db`
- 前端采用 TypeScript 严格模式，并使用 Vite 提供快速开发体验

## 进行精准修改

修改现有代码时：

- 仅修改必要部分。
- 保持与现有风格和结构的一致。
- 移除因修改而不再使用的导入、变量或函数。
- 除非被要求不需要删改代码，否则要严谨地重构或删除无关的现有代码。
