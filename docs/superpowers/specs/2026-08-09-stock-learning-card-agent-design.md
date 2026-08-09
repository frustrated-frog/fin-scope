# 股票学习卡独立 Agent 设计

## 目标

股票学习卡只要求用户选择一只股票。页面及其 API 不暴露研究主题、研究命题、通用决策 intent 或通用报告等概念。

学习卡拥有独立的编排、状态、错误和输出契约；搜索、网页正文读取、LLM 调用和 SQLite 持久化仍复用项目基础能力。

## 边界

```text
StockLearningCardController
  -> StockLearningCardService
     -> StockLearningCardAgentExecutor
        -> SearchEvidenceGateway
        -> SearchEvidenceContentService
        -> StockLearningCardSynthesisAgent
     -> StockLearningCardRepository
```

不再调用 `ResearchThesisService`、`ResearchService`、`ResearchDecisionAgent` 或 `ResearchReportService`。股票学习卡不会创建通用研究命题和通用研究运行，也不需要主题编码。

## 固定任务

Agent 根据服务端固定的六维框架创建搜索任务：空间、盈利模式、竞争格局、治理结构、定价观察和反方验证。任务查询由股票名称、代码和维度模板确定，不由模型生成通用工具 intent。

每个维度独立执行：搜索公开材料、读取有限数量正文、生成一张严格结构化卡片、执行交易语言门禁。单个维度失败时保存保守的失败卡片，其他维度继续执行。

## 状态与错误

运行阶段为 `QUEUED`、`COLLECTING_EVIDENCE`、`SYNTHESIZING_CARDS`、`COMPLETED`。运行状态沿用 `RUNNING`、`READY`、`DEGRADED`、`FAILED`，其中部分维度失败为 `DEGRADED`，全部维度失败才为 `FAILED`。

运行公开以下错误字段：

- `failedStage`：失败阶段；
- `errorCode`：稳定错误编码；
- `userMessage`：页面直接展示的中文说明；
- `retryable`：是否允许用户重新生成。

每个维度公开 `status` 和 `failureMessage`。页面优先展示领域错误，不直接显示底层供应商异常、通用研究校验错误或 traceId。

## 异步执行

POST 先持久化 `QUEUED/RUNNING` 运行，再交给独立的 `stockLearningCardExecutor`。后台执行过程中更新同一运行，不追加伪终态版本。GET 只读取学习卡运行，不再通过通用研究报告做状态投影。

## 安全规则

模型只能使用提供的搜索证据；输出固定 JSON：`judgment`、`rationale`、`counterargument`、`unknowns`、`confidence`。输出不得包含买卖、仓位、目标价或收益承诺。模型不可用、输出不合法或证据不足时，该维度降级为可理解的保守结果。

## 验收

1. POST 只使用路径中的六位股票代码，不接受主题或命题。
2. 任一维度失败不会阻止其他维度生成。
3. 页面显示当前阶段、局部失败和可重试说明。
4. 运行不创建 `research_thesis`、`research_run` 或 `research_report` 数据。
5. 后端与前端测试覆盖无主题启动、局部失败、全部失败和用户错误展示。
