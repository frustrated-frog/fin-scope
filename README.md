# FinScope

FinScope 是一个本地优先的个人投研信息工作台。它用于建立稳定的信息获取渠道，在 Inbox 中检查抓取文章，识别跨天重复内容，生成每日投研简报，并把有价值的信息沉淀为可长期维护的 Markdown 知识笔记。

这个项目有意避开公共热点榜单、运营干预后台和企业发布链路。它的定位是服务个人学习、秋招项目展示，以及未来自媒体素材积累。

## 技术栈

- 后端：Java 8、Spring Boot 2.7、Maven 多模块、SQLite、Jsoup、Rome RSS、站点专属抓取适配器
- 市场数据服务：Python 3.11+、FastAPI、AkShare、pytdx、httpx
- 前端：React、TypeScript、Vite
- 存储：`data/finance.db` 与 `data/vault/` 下的 Markdown 文件
- AI 扩展点：OpenAI 兼容 `LlmChatClient`、文章解读 Agent、`agent_run` 调用留痕

## 当前能力

- 配置 RSS/Web 信息源，并手动抓取公开文章进入 Inbox。
- 手动粘贴普通网页或 X/Twitter status URL 时，系统会根据 URL 自动选择更合适的抓取适配器；X 长文会优先通过公开 JSON 适配器解析真实正文。
- 对重复 URL、标题和正文进行去重，并记录新意判断原因。
- 生成不同类型的情报卡片：金融资讯卡片、研究论文卡片、社媒长文卡片。
- 可接入 OpenAI 兼容模型，对抓取文章做结构化解读、主题命名、术语提取和学习问题整理；模型不可用时回到确定性兜底。
- 按固定栏目生成每日 Markdown 简报，优先使用情报卡片内容。
- 将文章或简报沉淀为主题卡片，保留术语、学习问题、关联来源和 Markdown 笔记。
- 在 Learning 页面把个人理解追加到 `data/vault/topics/`。
- 导出包含 SQLite、Vault 文件和 manifest 的本地备份包。

## 项目结构

```text
fin-scope/
  backend/   Spring Boot 模块化单体
    finscope-common/   通用底层工具
    finscope-domain/   领域模型和 DTO 风格对象
    finscope-dao/      SQLite 仓储与表结构初始化
    finscope-rpc/      RSS/Web/X 等外部信息源适配器
    finscope-service/  业务编排、去重、简报、Vault、导出
    finscope-web/      REST 控制器与应用装配
  frontend/  React/Vite Web 工作台
  market-data-service/  Python A 股数据获取、多源降级与快照服务
  data/      本地个人数据目录，除 Vault 占位文件外默认忽略
  docs/      PRD、架构说明和路线图
```

## 本地运行

后端：

```bash
cd backend
mvn -pl finscope-web -am spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

默认打开 `http://localhost:5173`；如果 Vite 提示端口被占用并切换到 `5174` 或其他端口，以终端输出为准。前端会把 `/api` 代理到 `http://localhost:8080`。

Python 市场数据服务：

```bash
cd market-data-service
uv sync --extra ecosystem --extra dev
uv run uvicorn finscope_market_data.app:app --host 127.0.0.1 --port 8000
```

Java 接入方式和数据接口说明见 [market-data-service/README.md](market-data-service/README.md)。

## Agent / LLM 配置

FinScope 使用 OpenAI 兼容 Chat Completions 接口，不绑定具体服务商。API Key 只从本地环境变量读取，不写入代码和仓库。

```bash
export FINSCOPE_LLM_ENABLED=true
export FINSCOPE_LLM_BASE_URL=https://你的模型服务/v1
export FINSCOPE_LLM_API_KEY=你的_api_key
export FINSCOPE_LLM_MODEL=你的模型名
cd backend
mvn -pl finscope-web -am spring-boot:run
```

启用后，新增文章生成情报卡片、从文章沉淀主题时会走 `article-interpret` Agent 节点。每次调用会在 Agent Runs 页面留下节点、状态、耗时和错误信息；如果模型调用失败，系统会保留抓取链路并使用确定性兜底，不会阻断 Inbox。

## 验证命令

```bash
cd backend && mvn test
cd frontend && npm test
cd frontend && npm run build
```

## 数据安全

`data/finance.db`、抓取原文、导出包、本地环境文件和 API Key 都会被 Git 忽略。不要把公司内部数据、代码、凭证、专有 Prompt 或私有文档放进这个项目。
